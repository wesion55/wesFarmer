package xyz.geik.wesfarmer.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import xyz.geik.wesfarmer.storage.FarmerRecord;

public final class ControlPanelGuiHolder implements InventoryHolder {
    private final FarmerRecord record;
    private Inventory inventory;

    public ControlPanelGuiHolder(FarmerRecord record) { this.record = record; }
    public FarmerRecord getRecord() { return record; }

    @Override public @NotNull Inventory getInventory() { return inventory; }
    void setInventory(Inventory inventory) { this.inventory = inventory; }
}
