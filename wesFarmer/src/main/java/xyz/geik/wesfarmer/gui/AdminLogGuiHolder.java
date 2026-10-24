package xyz.geik.wesfarmer.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import xyz.geik.wesfarmer.storage.FarmerRecord;

/** InventoryHolder for the admin log GUI. */
public final class AdminLogGuiHolder implements InventoryHolder {

    private final FarmerRecord record;
    private final int          page;
    private Inventory inventory;

    public AdminLogGuiHolder(FarmerRecord record, int page) {
        this.record = record;
        this.page   = page;
    }

    @Override public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }
    public FarmerRecord getRecord() { return record; }
    public int getPage()            { return page; }
}
