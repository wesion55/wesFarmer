package xyz.geik.wesfarmer.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import xyz.geik.wesfarmer.product.FarmerProduct;
import xyz.geik.wesfarmer.storage.FarmerRecord;

/**
 * Ürün detay menüsünün holder'ı.
 * Hangi çiftçiye ve hangi ürüne ait olduğunu taşır.
 */
public final class ProductDetailGuiHolder implements InventoryHolder {

    private final FarmerRecord record;
    private final FarmerProduct product;
    private Inventory inventory;

    public ProductDetailGuiHolder(FarmerRecord record, FarmerProduct product) {
        this.record  = record;
        this.product = product;
    }

    public FarmerRecord getRecord()   { return record; }
    public FarmerProduct getProduct() { return product; }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }

    void setInventory(Inventory inventory) { this.inventory = inventory; }
}
