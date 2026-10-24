package xyz.geik.wesfarmer.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import xyz.geik.wesfarmer.storage.FarmerRecord;

/**
 * Ana çiftçi menüsünün holder'ı.
 * Hangi çiftçiye ait olduğunu ve şu anki sayfayı taşır.
 */
public final class FarmerMenuGuiHolder implements InventoryHolder {

    private final FarmerRecord record;
    private final int page;
    private Inventory inventory;

    public FarmerMenuGuiHolder(FarmerRecord record, int page) {
        this.record = record;
        this.page   = page;
    }

    public FarmerRecord getRecord() { return record; }
    public int getPage()            { return page; }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }

    void setInventory(Inventory inventory) { this.inventory = inventory; }
}
