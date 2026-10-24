package xyz.geik.wesfarmer.storage;

import org.bukkit.Material;

/**
 * Bir çiftçinin belirli bir ürün için anlık stok durumu.
 *
 * @param farmerId DB'deki çiftçi kaydının id'si (farmers.id)
 * @param material Ürün materyali
 * @param amount   Mevcut stok miktarı
 */
public record FarmerStock(
        long farmerId,
        Material material,
        int amount
) {}
