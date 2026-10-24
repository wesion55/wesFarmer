package xyz.geik.wesfarmer.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Tüm WesFarmer GUI'leri için merkezi ses yöneticisi.
 * Menü açılışında sandık sesi, butona tıklamada UI click sesi.
 */
public final class GuiSoundListener implements Listener {

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        InventoryHolder holder = event.getInventory().getHolder();
        // Sandık açılış sesi SADECE ana çiftçi menüsünde
        if (holder instanceof FarmerMenuGuiHolder) {
            SoundHelper.playOpen(player);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isWesFarmerGui(event.getInventory().getHolder())) return;
        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType().isAir()) return;
        // Filler'lara tıklanınca ses çıkmasın
        if (event.getCurrentItem().getType().name().contains("STAINED_GLASS_PANE")) return;
        SoundHelper.playClick(player);
    }

    private boolean isWesFarmerGui(InventoryHolder holder) {
        return holder instanceof FarmerMenuGuiHolder
                || holder instanceof ProductDetailGuiHolder
                || holder instanceof HistoryGuiHolder
                || holder instanceof ControlPanelGuiHolder
                || holder instanceof MembersGuiHolder
                || holder instanceof PermissionsGuiHolder
                || holder instanceof SettingsGuiHolder
                || holder instanceof BuyGuiHolder
                || holder instanceof ConfirmGuiHolder
                || holder instanceof RemoveConfirmGuiHolder;
    }
}
