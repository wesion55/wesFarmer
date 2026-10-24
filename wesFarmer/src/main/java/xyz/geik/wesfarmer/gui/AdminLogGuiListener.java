package xyz.geik.wesfarmer.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Admin log GUI tıklamalarını yönetir.
 * Slot 45 → önceki, 49 → kapat, 53 → sonraki.
 */
public final class AdminLogGuiListener implements Listener {

    private final AdminLogGui adminLogGui;

    public AdminLogGuiListener(AdminLogGui adminLogGui) {
        this.adminLogGui = adminLogGui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AdminLogGuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();

        if (slot == 49) {
            // Kapat
            SoundHelper.playClick(player);
            player.closeInventory();

        } else if (slot == 45 && holder.getPage() > 0) {
            // Önceki sayfa
            SoundHelper.playClick(player);
            player.openInventory(adminLogGui.build(player, holder.getRecord(), holder.getPage() - 1));

        } else if (slot == 53) {
            // Sonraki sayfa
            SoundHelper.playClick(player);
            player.openInventory(adminLogGui.build(player, holder.getRecord(), holder.getPage() + 1));
        }
    }
}
