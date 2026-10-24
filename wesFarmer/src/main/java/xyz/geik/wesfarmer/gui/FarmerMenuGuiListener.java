package xyz.geik.wesfarmer.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import xyz.geik.wesfarmer.WesFarmer;
import xyz.geik.wesfarmer.config.ConfigManager;
import xyz.geik.wesfarmer.config.GuiConfigManager;
import xyz.geik.wesfarmer.config.MessageService;
import xyz.geik.wesfarmer.storage.FarmerPermission;
import xyz.geik.wesfarmer.product.FarmerProduct;
import xyz.geik.wesfarmer.sell.SellService;
import xyz.geik.wesfarmer.storage.FarmerProductLevelRepository;
import xyz.geik.wesfarmer.storage.FarmerRecord;
import java.util.ArrayList;
import java.util.List;

import static xyz.geik.wesfarmer.config.MessageService.Placeholder.of;

public final class FarmerMenuGuiListener implements Listener {

    private final WesFarmer plugin;
    private final GuiConfigManager guiConfigManager;
    private final MessageService messageService;
    private final FarmerMenuGui farmerMenuGui;
    private final ProductDetailGui productDetailGui;
    private final HistoryGui historyGui;
    private final SellService sellService;
    private final ConfirmGui confirmGui;

    public FarmerMenuGuiListener(WesFarmer plugin,
                                  GuiConfigManager guiConfigManager,
                                  MessageService messageService,
                                  FarmerMenuGui farmerMenuGui,
                                  ProductDetailGui productDetailGui,
                                  HistoryGui historyGui,
                                  SellService sellService,
                                  ConfirmGui confirmGui) {
        this.plugin           = plugin;
        this.guiConfigManager = guiConfigManager;
        this.messageService   = messageService;
        this.farmerMenuGui    = farmerMenuGui;
        this.productDetailGui = productDetailGui;
        this.historyGui       = historyGui;
        this.sellService      = sellService;
        this.confirmGui       = confirmGui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof FarmerMenuGuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        GuiConfigManager.FarmerMenuSettings s = guiConfigManager.getFarmerMenuSettings();
        int slot = event.getRawSlot();
        FarmerRecord record = holder.getRecord();

        // ── İçerik alanı (0-44) → ürün detay GUI ────────────────────────────
        if (slot >= 0 && slot < 45) {
            if (event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) return;
            List<FarmerProduct> products = new ArrayList<>(plugin.getProductManager().getAll());
            int index = holder.getPage() * 45 + slot;
            if (index >= products.size()) return;
            player.openInventory(productDetailGui.build(player, record, products.get(index)));
            return;
        }

        // ── Kontrol paneli ────────────────────────────────────────────────────
        if (slot == s.prevSlot()) {
            int newPage = holder.getPage() - 1;
            if (newPage < 0) return;
            player.openInventory(farmerMenuGui.build(player, record, newPage));

        } else if (slot == s.nextSlot()) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;
            player.openInventory(farmerMenuGui.build(player, record, holder.getPage() + 1));

        } else if (slot == s.histSlot()) {
            player.openInventory(historyGui.build(player, record, 0));

        } else if (slot == s.sellSlot()) {
            // Bukkit permission kontrolü (sell-all-permission)
            ConfigManager.Settings cfg = plugin.getConfigManager().getSettings();
            if (!cfg.hasButtonPermission((org.bukkit.entity.Player) player, cfg.sellAllPermission())) {
                messageService.send(player, "no-permission");
                return;
            }
            if (!plugin.getFarmerMemberRepository().hasPermission(
                    record.id(), player.getUniqueId(),
                    FarmerPermission.SELL_ALL,
                    cfg)) {
                messageService.send(player, "no-permission");
                return;
            }
            player.closeInventory();
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    sellService.sellAll(player, record));

        } else if (slot == s.statsSlot()) {
            // Stats butonu sadece lore gösterir, tıklayınca bir şey açılmaz
            return;
        } else if (slot == s.upgSlot()) {
            // Bukkit permission kontrolü (upgrade-all-permission)
            ConfigManager.Settings cfgUpg = plugin.getConfigManager().getSettings();
            if (!cfgUpg.hasButtonPermission((org.bukkit.entity.Player) player, cfgUpg.upgradeAllPermission())) {
                messageService.send(player, "no-permission");
                return;
            }
            if (!plugin.getFarmerMemberRepository().hasPermission(
                    record.id(), player.getUniqueId(),
                    FarmerPermission.UPGRADE_ALL,
                    cfgUpg)) {
                messageService.send(player, "no-permission");
                return;
            }
            handleBulkUpgrade(player, record);

        } else if (slot == s.ctrlSlot()) {
            ControlPanelGui controlPanelGui = new ControlPanelGui(guiConfigManager);
            player.openInventory(controlPanelGui.build(player, record));
        }
    }

    // ── Toplu yükseltme ───────────────────────────────────────────────────────

    private void handleBulkUpgrade(Player player, FarmerRecord record) {
        var levelRepo = plugin.getFarmerProductLevelRepository();
        var economy   = plugin.getEconomyService();
        var products  = new ArrayList<>(plugin.getProductManager().getAll());

        // Yükseltilebilecek ürünleri ve toplam maliyeti hesapla
        double totalCost       = 0;
        int    upgradableCount = 0;
        int    maxedCount      = 0;

        for (FarmerProduct product : products) {
            int level = levelRepo.getLevel(record.id(), product.material());
            if (level >= FarmerProductLevelRepository.MAX_LEVEL) {
                maxedCount++;
                continue;
            }
            totalCost += FarmerProductLevelRepository.upgradeCost(level);
            upgradableCount++;
        }

        // Tüm ürünler max seviyedeyse bildir
        if (upgradableCount == 0) {
            messageService.send(player, "upgrade-all-all-maxed");
            return;
        }

        if (!economy.isAvailable()) {
            messageService.send(player, "economy-not-found");
            return;
        }

        // Hint mesajı göster — confirm GUI aç
        messageService.send(player, "upgrade-all-confirm-hint",
                of("%count%", String.valueOf(upgradableCount)),
                of("%cost%",  String.format("%.0f", totalCost)));

        // Confirm GUI action
        final var productsCopy = new ArrayList<>(plugin.getProductManager().getAll());
        Runnable upgradeAction = () -> {
            int    upgraded   = 0;
            int    skipped    = 0;
            double totalSpent = 0;

            for (FarmerProduct product : productsCopy) {
                int level = levelRepo.getLevel(record.id(), product.material());

                // Koruma: max seviye geçme
                if (level >= FarmerProductLevelRepository.MAX_LEVEL) continue;

                double cost = FarmerProductLevelRepository.upgradeCost(level);

                // Koruma: yeterli para yoksa atla, durdurma (diğerleri yükselebilir)
                if (!economy.has(player, cost)) {
                    skipped++;
                    continue;
                }

                economy.withdraw(player, cost);
                levelRepo.levelUp(record.id(), product.material());
                totalSpent += cost;
                upgraded++;
            }

            if (upgraded == 0) {
                messageService.send(player, "upgrade-all-no-money");
                return;
            }

            if (skipped > 0) {
                messageService.send(player, "upgrade-all-partial",
                        of("%upgraded%", String.valueOf(upgraded)),
                        of("%skipped%",  String.valueOf(skipped)),
                        of("%cost%",     String.format("%.0f", totalSpent)));
            } else {
                messageService.send(player, "upgrade-all-success",
                        of("%count%", String.valueOf(upgraded)),
                        of("%cost%",  String.format("%.0f", totalSpent)));
            }
        };

        player.openInventory(confirmGui.buildAction(upgradeAction, "upgrade-all-cancelled",
                String.format("%.0f$", totalCost)));
    }
}
