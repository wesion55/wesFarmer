package xyz.geik.wesfarmer.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import xyz.geik.wesfarmer.storage.FarmerRecord;

public final class SettingsGuiHolder implements InventoryHolder {

    private final FarmerRecord record;
    private Inventory inventory;

    public SettingsGuiHolder(FarmerRecord record) { this.record = record; }

    @Override public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }
    public FarmerRecord getRecord() { return record; }
}
