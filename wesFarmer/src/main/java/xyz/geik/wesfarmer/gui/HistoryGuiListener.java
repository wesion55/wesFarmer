package xyz.geik.wesfarmer.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import xyz.geik.wesfarmer.WesFarmer;
import xyz.geik.wesfarmer.config.GuiConfigManager;

/**
 * İşlem geçmişi menüsündeki tıklamaları yönetir.
 */
public final class HistoryGuiListener implements Listener {

    private final WesFarmer plugin;
    private final GuiConfigManager guiConfigManager;
    private final HistoryGui historyGui;
    private final FarmerMenuGui farmerMenuGui;

    public HistoryGuiListener(WesFarmer plugin,
                               GuiConfigManager guiConfigManager,
                               HistoryGui historyGui,
                               FarmerMenuGui farmerMenuGui) {
        this.plugin           = plugin;
        this.guiConfigManager = guiConfigManager;
        this.historyGui       = historyGui;
        this.farmerMenuGui    = farmerMenuGui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof HistoryGuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        GuiConfigManager.HistoryMenuSettings s = guiConfigManager.getHistoryMenuSettings();
        int slot = event.getRawSlot();

        if (slot == s.backSlot()) {
            player.openInventory(farmerMenuGui.build(player, holder.getRecord(), 0));

        } else if (slot == s.prevSlot()) {
            int newPage = holder.getPage() - 1;
            if (newPage < 0) return;
            player.openInventory(historyGui.build(player, holder.getRecord(), newPage));

        } else if (slot == s.nextSlot()) {
            // Sonraki ok sadece kayıt varsa yerleştiriliyor, ek kontrol gerekmez
            if (event.getCurrentItem() == null ||
                event.getCurrentItem().getType() == s.fillerMaterial()) return;
            player.openInventory(historyGui.build(player, holder.getRecord(), holder.getPage() + 1));
        }
    }
}
