package xyz.geik.wesfarmer.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import xyz.geik.wesfarmer.storage.FarmerRecord;

/**
 * InventoryHolder for the members management GUI.
 * Carries the farmer record and the current page so the listener
 * can re-build the GUI after any action.
 */
public final class MembersGuiHolder implements InventoryHolder {

    private final FarmerRecord record;
    private final int page;
    private Inventory inventory;

    public MembersGuiHolder(FarmerRecord record, int page) {
        this.record = record;
        this.page   = page;
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    public FarmerRecord getRecord() { return record; }

    public int getPage() { return page; }
}
