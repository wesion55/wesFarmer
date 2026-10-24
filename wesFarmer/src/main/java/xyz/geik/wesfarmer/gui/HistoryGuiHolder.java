package xyz.geik.wesfarmer.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import xyz.geik.wesfarmer.storage.FarmerRecord;

public final class HistoryGuiHolder implements InventoryHolder {

    private final FarmerRecord record;
    private final int page;
    private Inventory inventory;

    public HistoryGuiHolder(FarmerRecord record, int page) {
        this.record = record;
        this.page   = page;
    }

    public FarmerRecord getRecord() { return record; }
    public int getPage()            { return page; }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }

    void setInventory(Inventory inventory) { this.inventory = inventory; }
}
