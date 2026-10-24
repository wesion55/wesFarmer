package xyz.geik.wesfarmer.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Marker holder so {@link BuyGuiListener} can reliably recognise our GUI
 * instead of comparing inventory titles, which is fragile (titles can be
 * legitimately reused by other menus or renamed).
 */
public final class BuyGuiHolder implements InventoryHolder {

    private Inventory inventory;

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
