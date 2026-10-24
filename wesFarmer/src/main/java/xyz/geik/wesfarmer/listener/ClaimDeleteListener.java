package xyz.geik.wesfarmer.listener;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import xyz.geik.wesfarmer.WesFarmer;
import xyz.geik.wesfarmer.integration.hologram.FancyHologramsHook;
import xyz.geik.wesfarmer.integration.npc.FancyNpcsHook;
import xyz.geik.wesfarmer.storage.FarmerRecord;
import xyz.geik.wesfarmer.storage.FarmerRepository;
import xyz.geik.wesfarmer.task.CollectTask;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WorldGuard /rg remove|delete komutlarını dinler.
 *
 * <p>Komut metni yakalandıktan <b>1 tick sonra</b> WorldGuard'ın region
 * manager'ı sorgulanır. Region artık yoksa silme başarılıdır → farmer temizlenir.
 * Region hâlâ varsa (yetersiz yetki, yazım hatası, başka plugin engeli) farmer
 * dokunulmaz. Bu yaklaşım sahte/başarısız komutları güvenle elemine eder.
 *
 * <p>GriefPrevention için {@link GpClaimDeleteListener} kullanılır.
 */
public final class ClaimDeleteListener implements Listener {

    /**
     * Yakalanan format örnekleri:
     *   /rg remove myregion
     *   /rg delete myregion world
     *   /region remove myregion
     *   /rg remove -w world myregion   ← -w bayrağı varsa son token region adı değil olabilir
     *
     * Basit tutmak için: "remove" veya "delete" subcommand'ından sonra gelen
     * ilk non-flag token'ı region adı olarak alırız.
     */
    private static final Pattern WG_DELETE = Pattern.compile(
            "^/?(region|rg)\\s+(remove|delete)\\s+(.*)",
            Pattern.CASE_INSENSITIVE);

    private final WesFarmer        plugin;
    private final FarmerRepository farmerRepository;

    public ClaimDeleteListener(WesFarmer plugin, FarmerRepository farmerRepository) {
        this.plugin           = plugin;
        this.farmerRepository = farmerRepository;
    }

    public WesFarmer plugin() { return plugin; }

    // ── WorldGuard command intercept ─────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        handleWgCommand(event.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent event) {
        handleWgCommand("/" + event.getCommand());
    }

    private void handleWgCommand(String raw) {
        Matcher m = WG_DELETE.matcher(raw.trim());
        if (!m.matches()) return;

        // Argüman kısmını parçala, -w <dünya> bayrağını atla, region id'yi bul
        String argStr  = m.group(3).trim();
        String regionId = extractRegionId(argStr);
        if (regionId == null) return;

        // Bu claim_key'e sahip farmer var mı hızlıca kontrol et
        List<FarmerRecord> all = farmerRepository.findAll();
        FarmerRecord candidate = null;
        for (FarmerRecord r : all) {
            String key = r.claimKey();
            if (key == null || key.equals(FarmerRecord.NO_CLAIM)) continue;
            if (key.startsWith("wg:")) {
                // key: "wg:regionId" veya "wg:regionId1,regionId2"
                // Eğer key içinde bu regionId geçiyorsa aday
                String regionPart = key.substring(3);
                for (String part : regionPart.split(",")) {
                    if (part.equalsIgnoreCase(regionId)) {
                        candidate = r;
                        break;
                    }
                }
            }
            if (candidate != null) break;
        }

        if (candidate == null) return; // Bu region'a bağlı farmer yok

        final FarmerRecord finalCandidate = candidate;

        // 1 tick sonra: region gerçekten silindi mi?
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (isRegionStillPresent(regionId)) return; // Silme başarısız — dokunma

            // Region artık yok → farmer'ı temizle
            deleteFarmer(finalCandidate, "WG region silindi: " + regionId);
        }, 2L); // 2 tick — komutun işlenmesine kesin süre tanı
    }

    // ── Silme işlemi ─────────────────────────────────────────────────────────

    public void deleteFarmer(FarmerRecord record, String reason) {
        plugin.getLogger().info("[wesFarmer] " + reason + " — farmer siliniyor: "
                + record.npcName() + " (sahip: " + record.ownerUuid() + ")");

        if (FancyNpcsHook.isAvailable())
            FancyNpcsHook.removeNpc(record.npcName());
        if (FancyHologramsHook.isAvailable() && record.holoName() != null)
            FancyHologramsHook.removeHologram(record.holoName());

        farmerRepository.deleteByNpcName(record.npcName());
        plugin.getFarmerMemberRepository().removeAllByFarmer(record.id());
        plugin.getFarmerSettingsRepository().delete(record.id());
        plugin.getFarmerStockRepository().clearStock(record.id());
        plugin.getFarmerProductLevelRepository().deleteByFarmer(record.id());
        plugin.getFarmerHistoryRepository().deleteByFarmer(record.id());
        plugin.getFarmerProductStateRepository().deleteByFarmer(record.id());

        CollectTask ct = CollectTask.getInstance();
        if (ct != null) ct.cacheRemove(record.claimKey());

        org.bukkit.entity.Player owner = plugin.getServer().getPlayer(record.ownerUuid());
        if (owner != null)
            plugin.getMessageService().send(owner, "farmer-removed-claim-lost");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Argüman dizisinden region ID'sini çıkarır.
     * -w, -g gibi flag'leri ve ardından gelen değerleri atlar.
     * İlk non-flag, non-value token'ı region adı olarak döner.
     */
    private static String extractRegionId(String argStr) {
        String[] tokens = argStr.split("\\s+");
        int i = 0;
        while (i < tokens.length) {
            String t = tokens[i];
            if (t.startsWith("-")) {
                // Flag — bir sonraki token değeri olabilir (örn. -w world)
                i += 2;
                continue;
            }
            return t; // İlk non-flag token = region ID
        }
        return null;
    }

    /**
     * WorldGuard'da bu isimde region hâlâ var mı?
     * Tüm dünyalara bakar — region ID'ler dünya genelinde benzersiz olmak zorunda
     * değildir ama pratikte öyledirler.
     */
    private boolean isRegionStillPresent(String regionId) {
        if (!org.bukkit.Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) return false;
        try {
            com.sk89q.worldguard.WorldGuard wg = com.sk89q.worldguard.WorldGuard.getInstance();
            for (World world : plugin.getServer().getWorlds()) {
                var container = wg.getPlatform().getRegionContainer();
                var manager   = container.get(
                        com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world));
                if (manager != null && manager.getRegion(regionId) != null) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }
}
