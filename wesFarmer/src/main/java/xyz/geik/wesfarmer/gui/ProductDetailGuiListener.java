package xyz.geik.wesfarmer.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import xyz.geik.wesfarmer.WesFarmer;
import xyz.geik.wesfarmer.config.GuiConfigManager;
import xyz.geik.wesfarmer.config.MessageService;
import xyz.geik.wesfarmer.product.FarmerProduct;
import xyz.geik.wesfarmer.sell.SellService;
import xyz.geik.wesfarmer.storage.FarmerPermission;
import xyz.geik.wesfarmer.storage.FarmerRecord;
import xyz.geik.wesfarmer.storage.FarmerStockRepository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static xyz.geik.wesfarmer.config.MessageService.Placeholder.of;

/**
 * Ürün detay menüsündeki tıklamaları yönetir.
 *
 * <p>Butonlar:
 * <ul>
 *   <li><b>Geri</b> → ana çiftçi menüsüne döner</li>
 *   <li><b>Toplama Toggle</b> → collect durumunu tersine çevirir (in-memory + stok etkilenmez)</li>
 *   <li><b>Hepsini Sat</b> → SellService.sellProduct() tüm stok</li>
 *   <li><b>Envantere Al</b> → chat'e miktar yazar, envantere alır, doluysa uyarır</li>
 * </ul>
 */
public final class ProductDetailGuiListener implements Listener {

    /** Envantere al için sohbet beklenen oyuncular: UUID → (record, product) */
    private static final Map<UUID, PendingTake> PENDING = new ConcurrentHashMap<>();

    private final WesFarmer plugin;
    private final GuiConfigManager guiConfigManager;
    private final MessageService messageService;
    private final ProductDetailGui productDetailGui;
    private final FarmerMenuGui farmerMenuGui;
    private final SellService sellService;
    private final FarmerStockRepository stockRepository;

    public ProductDetailGuiListener(WesFarmer plugin,
                                     GuiConfigManager guiConfigManager,
                                     MessageService messageService,
                                     ProductDetailGui productDetailGui,
                                     FarmerMenuGui farmerMenuGui,
                                     SellService sellService,
                                     FarmerStockRepository stockRepository) {
        this.plugin            = plugin;
        this.guiConfigManager  = guiConfigManager;
        this.messageService    = messageService;
        this.productDetailGui  = productDetailGui;
        this.farmerMenuGui     = farmerMenuGui;
        this.sellService       = sellService;
        this.stockRepository   = stockRepository;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ProductDetailGuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        GuiConfigManager.ProductDetailSettings s = guiConfigManager.getProductDetailSettings();
        int slot = event.getRawSlot();
        FarmerRecord  record  = holder.getRecord();
        FarmerProduct product = holder.getProduct();

        if (slot == s.backSlot()) {
            // Ana menüye geri dön
            player.openInventory(farmerMenuGui.build(player, record, 0));

        } else if (slot == s.toggleSlot()) {
            // collectLocked kontrolü
            if (plugin.getFarmerSettingsRepository().get(record.id()).collectLocked()) {
                messageService.send(player, "settings-collect-locked-action");
                return;
            }
            // İzin: COLLECT_TOGGLE
            if (!plugin.getFarmerMemberRepository().hasPermission(
                    record.id(), player.getUniqueId(),
                    FarmerPermission.COLLECT_TOGGLE,
                    plugin.getConfigManager().getSettings())) {
                messageService.send(player, "no-permission");
                return;
            }
            var stateRepo = plugin.getFarmerProductStateRepository();
            boolean current = stateRepo.getCollectEnabled(
                    record.id(), product.material(), product.collectEnabled());
            boolean newState = !current;
            stateRepo.setCollect(record.id(), product.material(), newState);
            messageService.send(player, newState ? "collect-enabled" : "collect-disabled");
            player.openInventory(productDetailGui.build(player, record, product));

        } else if (slot == s.sellToggleSlot()) {
            // Satış kilidi açıksa ürün bazlı toggle yapılamaz
            if (plugin.getFarmerSettingsRepository().get(record.id()).sellLocked()) {
                messageService.send(player, "sell-locked");
                return;
            }
            // İzin: SELL_TOGGLE
            if (!plugin.getFarmerMemberRepository().hasPermission(
                    record.id(), player.getUniqueId(),
                    FarmerPermission.SELL_TOGGLE,
                    plugin.getConfigManager().getSettings())) {
                messageService.send(player, "no-permission");
                return;
            }
            var stateRepo = plugin.getFarmerProductStateRepository();
            boolean current = stateRepo.getSellEnabled(
                    record.id(), product.material(), product.sellEnabled());
            boolean newState = !current;
            stateRepo.setSell(record.id(), product.material(), newState);
            messageService.send(player, newState ? "sell-product-enabled" : "sell-product-disabled");
            player.openInventory(productDetailGui.build(player, record, product));

        } else if (slot == s.sellSlot()) {
            // İzin: SELL
            if (!plugin.getFarmerMemberRepository().hasPermission(
                    record.id(), player.getUniqueId(),
                    FarmerPermission.SELL,
                    plugin.getConfigManager().getSettings())) {
                messageService.send(player, "no-permission");
                return;
            }
            int stock = stockRepository.getStock(record.id(), product.material());
            if (stock <= 0) {
                messageService.send(player, "sell-no-stock");
                return;
            }
            player.closeInventory();
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    sellService.sellProduct(player, record, product, stock));

        } else if (slot == s.takeSlot()) {
            // İzin: TAKE
            if (!plugin.getFarmerMemberRepository().hasPermission(
                    record.id(), player.getUniqueId(),
                    FarmerPermission.TAKE,
                    plugin.getConfigManager().getSettings())) {
                messageService.send(player, "no-permission");
                return;
            }
            int stock = stockRepository.getStock(record.id(), product.material());
            if (stock <= 0) {
                messageService.send(player, "sell-no-stock");
                return;
            }
            player.closeInventory();
            PENDING.put(player.getUniqueId(), new PendingTake(record, product));
            messageService.send(player, "take-prompt",
                    of("%max%", String.valueOf(stock)));

        } else if (slot == s.upgradeSlot()) {
            // İzin: UPGRADE
            if (!plugin.getFarmerMemberRepository().hasPermission(
                    record.id(), player.getUniqueId(),
                    FarmerPermission.UPGRADE,
                    plugin.getConfigManager().getSettings())) {
                messageService.send(player, "no-permission");
                return;
            }
            var levelRepo = plugin.getFarmerProductLevelRepository();
            int level = levelRepo.getLevel(record.id(), product.material());

            if (level >= xyz.geik.wesfarmer.storage.FarmerProductLevelRepository.MAX_LEVEL) {
                messageService.send(player, "upgrade-max-level");
                return;
            }

            double cost = xyz.geik.wesfarmer.storage.FarmerProductLevelRepository.upgradeCost(level);
            if (!plugin.getEconomyService().has(player, cost)) {
                messageService.send(player, "upgrade-not-enough-money",
                        of("%cost%", String.format("%.0f", cost)));
                return;
            }

            plugin.getEconomyService().withdraw(player, cost);
            levelRepo.levelUp(record.id(), product.material());

            FarmerProduct updated = plugin.getProductManager()
                    .findByKey(product.key()).orElse(product);
            player.openInventory(productDetailGui.build(player, record, updated));
            messageService.send(player, "upgrade-success",
                    of("%level%", String.valueOf(level + 1)),
                    of("%cost%",  String.format("%.0f", cost)));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PendingTake pending = PENDING.remove(player.getUniqueId());
        if (pending == null) return;

        event.setCancelled(true);

        String raw = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        // iptal
        if (raw.equalsIgnoreCase("iptal") || raw.equalsIgnoreCase("cancel")) {
            messageService.send(player, "take-cancelled");
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            messageService.send(player, "take-invalid-number");
            return;
        }

        if (amount <= 0) {
            messageService.send(player, "take-invalid-number");
            return;
        }

        // Ana thread'de işle (envanter işlemleri main thread gerektirir)
        final int finalAmount = amount;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            FarmerRecord  record  = pending.record();
            FarmerProduct product = pending.product();

            int stock = stockRepository.getStock(record.id(), product.material());
            if (stock <= 0) {
                messageService.send(player, "sell-no-stock");
                return;
            }

            int toTake = Math.min(finalAmount, stock);

            // Envanter kapasitesi kontrolü — doğrudan item adedi
            int canFit = countFreeInventorySpace(player, product.material());

            if (canFit <= 0) {
                messageService.send(player, "take-inventory-full");
                return;
            }

            if (toTake > canFit) {
                messageService.send(player, "take-inventory-limit",
                        of("%max%", String.valueOf(canFit)));
                toTake = canFit;
            }

            // Envantere ekle
            var overflow = player.getInventory().addItem(
                    new org.bukkit.inventory.ItemStack(product.material(), toTake));

            // addItem geri dönen overflow (doldu) kontrolü
            int actuallyAdded = toTake;
            if (!overflow.isEmpty()) {
                int overflowAmount = overflow.values().stream()
                        .mapToInt(org.bukkit.inventory.ItemStack::getAmount).sum();
                actuallyAdded = toTake - overflowAmount;
                // Overflow'u yere bırak
                overflow.values().forEach(item ->
                        player.getWorld().dropItemNaturally(player.getLocation(), item));
            }

            stockRepository.addStock(record.id(), product.material(), -actuallyAdded);
            messageService.send(player, "take-success",
                    of("%amount%", String.valueOf(actuallyAdded)),
                    of("%item%",   product.displayName()));
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Envanterde bu materyal için kaç adet boş alan var (item sayısı olarak). */
    private int countFreeInventorySpace(Player player, org.bukkit.Material material) {
        int maxStack = material.getMaxStackSize();
        int free = 0;
        for (org.bukkit.inventory.ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                free += maxStack;
            } else if (slot.getType() == material && slot.getAmount() < maxStack) {
                free += maxStack - slot.getAmount();
            }
        }
        return free; // item sayısı (stack'e çevirme yok — yuvarlama kayıplarını önler)
    }

    /** Chat bekleme verisi. */
    record PendingTake(FarmerRecord record, FarmerProduct product) {}

    /** Dışarıdan bekleyen oyuncuyu temizle (GUI kapanınca). */
    public static void cancelPending(UUID uuid) {
        PENDING.remove(uuid);
    }
}
