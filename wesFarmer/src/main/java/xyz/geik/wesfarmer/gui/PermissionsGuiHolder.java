package xyz.geik.wesfarmer.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import xyz.geik.wesfarmer.storage.FarmerRecord;

/** InventoryHolder for the permissions panel GUI. */
public final class PermissionsGuiHolder implements InventoryHolder {

    private final FarmerRecord record;
    private Inventory inventory;

    public PermissionsGuiHolder(FarmerRecord record) {
        this.record = record;
    }

    @Override public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }
    public FarmerRecord getRecord() { return record; }
}
