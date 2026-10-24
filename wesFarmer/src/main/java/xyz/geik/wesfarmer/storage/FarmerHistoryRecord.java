package xyz.geik.wesfarmer.storage;

import org.bukkit.Material;

/**
 * Bir çiftçinin tek işlem geçmişi kaydı.
 *
 * @param id        PK
 * @param farmerId  farmers.id FK
 * @param type      COLLECT veya SELL
 * @param material  İşlemdeki materyal
 * @param amount    Miktar
 * @param earnings  Kazanç (COLLECT'te 0, SELL'de net tutar)
 * @param timestamp Unix epoch saniye
 */
public record FarmerHistoryRecord(
        long id,
        long farmerId,
        Type type,
        Material material,
        int amount,
        double earnings,
        long timestamp
) {
    public enum Type { COLLECT, SELL }
}
