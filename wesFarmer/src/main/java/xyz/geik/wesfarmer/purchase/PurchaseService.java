package xyz.geik.wesfarmer.purchase;

import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import xyz.geik.wesfarmer.config.ConfigManager;
import xyz.geik.wesfarmer.config.MessageService;
import xyz.geik.wesfarmer.economy.EconomyService;
import xyz.geik.wesfarmer.gui.ConfirmGui;
import xyz.geik.wesfarmer.integration.claim.ClaimGuard;
import xyz.geik.wesfarmer.integration.hologram.FancyHologramsHook;
import xyz.geik.wesfarmer.integration.npc.FancyNpcsHook;
import xyz.geik.wesfarmer.storage.FarmerRecord;
import xyz.geik.wesfarmer.storage.FarmerRepository;
import xyz.geik.wesfarmer.storage.FarmerMember;
import xyz.geik.wesfarmer.storage.FarmerMemberRepository;
import xyz.geik.wesfarmer.storage.FarmerRole;
import xyz.geik.wesfarmer.storage.FarmerSettings;
import xyz.geik.wesfarmer.storage.FarmerSettingsRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static xyz.geik.wesfarmer.config.MessageService.Placeholder.of;

/**
 * Core business logic behind the purchase panel, split into two steps:
 * <ol>
 *     <li>{@link #requestPurchase(Player)} — runs every pre-check (economy,
 *     NPC plugin, funds, claim permission) and, if they all pass, opens the
 *     confirmation panel instead of buying immediately.</li>
 *     <li>{@link #confirmPurchase(Player, Location)} — re-checks funds/claim
 *     (time may have passed since step 1) then actually charges the player
 *     and spawns the NPC + hologram.</li>
 * </ol>
 * Every early return already sent the player a reason, so callers don't
 * need to inspect a result.
 */
public final class PurchaseService {

    private final ConfigManager configManager;
    private final MessageService messageService;
    private final EconomyService economyService;
    private final ClaimGuard claimGuard;
    private final ConfirmGui confirmGui;
    private final FarmerRepository farmerRepository;
    private final FarmerMemberRepository memberRepository;
    private final FarmerSettingsRepository settingsRepository;

    public PurchaseService(ConfigManager configManager, MessageService messageService,
                            EconomyService economyService, ClaimGuard claimGuard,
                            ConfirmGui confirmGui, FarmerRepository farmerRepository,
                            FarmerMemberRepository memberRepository,
                            FarmerSettingsRepository settingsRepository) {
        this.configManager      = configManager;
        this.messageService     = messageService;
        this.economyService     = economyService;
        this.claimGuard         = claimGuard;
        this.confirmGui         = confirmGui;
        this.farmerRepository   = farmerRepository;
        this.memberRepository   = memberRepository;
        this.settingsRepository = settingsRepository;
    }

    public void requestPurchase(Player player) {
        ConfigManager.Settings settings = configManager.getSettings();
        double price = settings.buyPrice();

        if (!checkPreconditions(player, price, player.getLocation())) return;

        player.closeInventory();
        player.openInventory(confirmGui.build(player, player.getLocation()));
    }

    public void confirmPurchase(Player player, Location location) {
        ConfigManager.Settings settings = configManager.getSettings();
        double price = settings.buyPrice();

        if (!checkPreconditions(player, price, location)) return;

        EconomyResponse withdrawal = economyService.withdraw(player, price);
        if (!withdrawal.transactionSuccess()) {
            messageService.send(player, "purchase-failed",
                    of("%reason%", withdrawal.errorMessage == null ? "" : withdrawal.errorMessage));
            return;
        }

        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String npcName = "wesfarmer_" + id;

        // Yönü 0 / 90 / 180 / 270 derecelerinden en yakin olana yuvarla,
        // pitch'i 0'a sabitle — NPC hep duz karsi tarafa baksin.
        Location spawnLocation = location.clone();
        spawnLocation.setYaw(normalizeYaw(location.getYaw()));
        spawnLocation.setPitch(0f);

        String createdNpc = FancyNpcsHook.spawnFarmerNpc(npcName, spawnLocation, player, settings.npcSkin());
        if (createdNpc == null) {
            // We took the money but couldn't deliver — refund immediately.
            refund(player, price, "NPC olusturulamadi");
            return;
        }

        String holoName = null;
        if (FancyHologramsHook.isAvailable()) {
            holoName = "wesfarmer_holo_" + id;
            Location hologramLocation = spawnLocation.clone().add(0, settings.hologramOffsetY(), 0);
            List<String> lines = settings.hologramLines().stream()
                    .map(line -> line.replace("%player%", player.getName()))
                    .collect(Collectors.toList());
            FancyHologramsHook.createHologram(holoName, hologramLocation, lines,
                    settings.hologramBackground(), settings.hologramTextShadow());
        } else {
            Bukkit.getLogger().info("[WesFarmer] FancyHolograms bulunamadi, hologram olusturulmadi.");
        }

        // Claim anahtarini al (kurulu bir claim eklentisi yoksa/claim disindaysa null) ve DB'ye kaydet.
        String claimKey = claimGuard.claimKey(spawnLocation);
        FarmerRecord record = FarmerRecord.unsaved(player.getUniqueId(), claimKey, npcName, holoName, spawnLocation);

        FarmerRecord saved;
        try {
            saved = farmerRepository.insert(record);
        } catch (FarmerRepository.ClaimAlreadyClaimedException raceLost) {
            // Someone else's purchase for the same claim won the race between
            // our pre-check above and this insert — undo everything we just did.
            FancyNpcsHook.removeNpc(npcName);
            if (holoName != null) FancyHologramsHook.removeHologram(holoName);
            refund(player, price, null);
            messageService.send(player, "claim-already-has-farmer");
            return;
        }

        if (saved == null) {
            // insert() already logged the underlying SQL error.
            FancyNpcsHook.removeNpc(npcName);
            if (holoName != null) FancyHologramsHook.removeHologram(holoName);
            refund(player, price, "Veritabanina kaydedilemedi");
            return;
        }

        String priceText = economyService.format(price);
        messageService.send(player, "purchase-success", of("%price%", priceText));

        // Satın alan oyuncuyu OWNER olarak üye tablosuna ekle
        memberRepository.save(new FarmerMember(
                saved.id(), player.getUniqueId(), player.getName(), FarmerRole.OWNER));
        // Varsayılan ayar satırını oluştur
        settingsRepository.save(FarmerSettings.defaults(saved.id()));
        // CollectTask cache'ine ekle
        xyz.geik.wesfarmer.task.CollectTask ct = xyz.geik.wesfarmer.task.CollectTask.getInstance();
        if (ct != null) ct.cacheAdd(saved);
    }

    /**
     * Refunds the player after a purchase failed partway through. Unlike the
     * original code, this checks the deposit actually succeeded — Vault can
     * fail a deposit too (economy plugin down, balance cap hit, etc.), and
     * silently eating that would mean the player paid for nothing.
     *
     * @param reasonKey if non-null, sent as the {@code %reason%} of a
     *                  "purchase-failed" message once the refund is
     *                  confirmed; pass {@code null} when a different message
     *                  (e.g. "claim-already-has-farmer") is sent by the caller.
     */
    private void refund(Player player, double price, String reasonKey) {
        EconomyResponse refund = economyService.deposit(player, price);
        if (!refund.transactionSuccess()) {
            Bukkit.getLogger().severe("[WesFarmer] IADE BASARISIZ - oyuncu: " + player.getName()
                    + " (" + player.getUniqueId() + "), tutar: " + price
                    + ", sebep: " + refund.errorMessage);
            messageService.send(player, "refund-failed", of("%price%", economyService.format(price)));
            return;
        }
        if (reasonKey != null) {
            messageService.send(player, "purchase-failed", of("%reason%", reasonKey));
        }
    }

    public void cancelPurchase(Player player) {
        messageService.send(player, "purchase-cancelled");
    }

    /**
     * Mevcut bir farmeri oyuncunun şu anki konumuna taşır.
     * Eski NPC + hologram kaldırılır, yeni konumda spawn edilir, DB güncellenir.
     * claim başına 1 kuralı yeni konum için de kontrol edilir.
     */
    public void moveFarmer(Player player, FarmerRecord record) {
        ConfigManager.Settings settings = configManager.getSettings();
        Location newLocation = player.getLocation().clone();
        newLocation.setYaw(normalizeYaw(newLocation.getYaw()));
        newLocation.setPitch(0f);

        // Dünya izin kontrolü
        String worldName = newLocation.getWorld() == null ? "" : newLocation.getWorld().getName();
        if (!settings.isWorldAllowed(worldName)) {
            messageService.send(player, "world-not-allowed");
            return;
        }

        // Yeni konumun claim kontrolü
        if (!claimGuard.canBuild(player, newLocation)) {
            messageService.send(player, "claim-not-allowed");
            return;
        }
        String newClaimKey = claimGuard.claimKey(newLocation);

        // Yeni claim farklıysa ve orada başka bir farmer varsa reddet
        if (newClaimKey != null
                && !newClaimKey.equals(record.claimKey())
                && farmerRepository.claimHasFarmer(newClaimKey)) {
            messageService.send(player, "claim-already-has-farmer");
            return;
        }

        // Eski NPC + hologram kaldır
        if (FancyNpcsHook.isAvailable()) FancyNpcsHook.removeNpc(record.npcName());
        if (FancyHologramsHook.isAvailable() && record.holoName() != null) {
            FancyHologramsHook.removeHologram(record.holoName());
        }

        // Yeni konumda NPC spawn et
        String createdNpc = FancyNpcsHook.spawnFarmerNpc(
                record.npcName(), newLocation, player, settings.npcSkin());
        if (createdNpc == null) {
            messageService.send(player, "move-failed", of("%reason%", "NPC yeniden oluşturulamadı"));
            return;
        }

        // Yeni konumda hologram oluştur
        if (FancyHologramsHook.isAvailable() && record.holoName() != null) {
            Location holoLoc = newLocation.clone().add(0, settings.hologramOffsetY(), 0);
            String ownerName = player.getName();
            java.util.List<String> lines = settings.hologramLines().stream()
                    .map(l -> l.replace("%player%", ownerName))
                    .collect(java.util.stream.Collectors.toList());
            FancyHologramsHook.createHologram(record.holoName(), holoLoc, lines,
                    settings.hologramBackground(), settings.hologramTextShadow());
        }

        // DB güncelle — worldName zaten yukarıda tanımlı
        farmerRepository.updateLocation(record.npcName(),
                worldName, newLocation.getX(), newLocation.getY(), newLocation.getZ(),
                newClaimKey);

        // CollectTask cache: eski claim'i çıkar, yeni claim'i ekle
        xyz.geik.wesfarmer.task.CollectTask ct = xyz.geik.wesfarmer.task.CollectTask.getInstance();
        if (ct != null) {
            ct.cacheRemove(record.claimKey());
            // Güncel record'u DB'den çek (updateLocation sonrası)
            xyz.geik.wesfarmer.storage.FarmerRecord updated =
                    farmerRepository.findByNpcName(record.npcName());
            if (updated != null) ct.cacheAdd(updated);
        }

        messageService.send(player, "move-success");
    }

    /**
     * Shared between the request and confirm steps so nothing can be bought
     * by re-clicking "onayla" after Vault or the claim state changed
     * underneath the player while the confirmation panel was open.
     */
    private boolean checkPreconditions(Player player, double price, Location location) {
        ConfigManager.Settings settings = configManager.getSettings();

        // Dünya izin kontrolü
        String worldName = location.getWorld() == null ? "" : location.getWorld().getName();
        if (!settings.isWorldAllowed(worldName)) {
            messageService.send(player, "world-not-allowed");
            return false;
        }
        if (!economyService.isAvailable()) {
            messageService.send(player, "economy-not-found");
            return false;
        }
        if (!FancyNpcsHook.isAvailable()) {
            messageService.send(player, "npc-plugin-not-found");
            return false;
        }
        if (!economyService.has(player, price)) {
            messageService.send(player, "not-enough-money", of("%price%", economyService.format(price)));
            return false;
        }
        if (!claimGuard.canBuild(player, location)) {
            messageService.send(player, "claim-not-allowed");
            return false;
        }
        // Claim basina 1 farmer: hangi claim eklentisi kuruluysa onun anahtarini kontrol et.
        String claimKey = claimGuard.claimKey(location);
        if (claimKey != null && farmerRepository.claimHasFarmer(claimKey)) {
            messageService.send(player, "claim-already-has-farmer");
            return false;
        }
        // Oyuncu başına max farmer kontrolü (0 = sınırsız)
        int maxPerPlayer = settings.maxFarmersPerPlayer();
        if (maxPerPlayer > 0 && farmerRepository.countByOwner(player.getUniqueId()) >= maxPerPlayer) {
            messageService.send(player, "max-farmers-reached",
                    of("%limit%", String.valueOf(maxPerPlayer)));
            return false;
        }
        return true;
    }

    /**
     * Yaw degerini en yakin 90 derece katsayisina yuvarlar (0 / 90 / 180 / 270).
     * Bukkit yaw araligi [-180, 180) oldugu icin once [0, 360) araligina tasiyoruz.
     *
     * <pre>
     *   0   → Guney (south)
     *  90   → Bati  (west)
     * 180   → Kuzey (north)
     * 270   → Dogu  (east)
     * </pre>
     */
    private static float normalizeYaw(float yaw) {
        // [-180, 180) -> [0, 360)
        float normalized = ((yaw % 360f) + 360f) % 360f;
        // En yakin 90'in katsayisina yuvarla
        float rounded = Math.round(normalized / 90f) * 90f;
        // 360 → 0'a donustur
        return rounded % 360f;
    }
}
