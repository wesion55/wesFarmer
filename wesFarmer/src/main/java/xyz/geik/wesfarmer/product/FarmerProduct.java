package xyz.geik.wesfarmer.product;

import org.bukkit.Material;

/**
 * products.yml'den yüklenen bir ürünün değiştirilemez anlık görüntüsü.
 *
 * @param key         products.yml'deki anahtar (= material adı, büyük harf)
 * @param displayName MiniMessage formatında görünen ad
 * @param material    Bukkit materyali
 * @param price       Ham birim fiyatı (vergi dahil değil)
 * @param tax         Vergi yüzdesi (0-100)
 * @param maxStock    Maksimum stok (0 = sınırsız)
 * @param sellEnabled Satış açık mı
 * @param collectEnabled Toplama açık mı
 */
public record FarmerProduct(
        String key,
        String displayName,
        Material material,
        double price,
        int tax,
        int maxStock,
        boolean sellEnabled,
        boolean collectEnabled
) {
    /**
     * Vergiden sonra oyuncuya kalacak net birim fiyatı.
     * {@code price * (1 - tax / 100)}
     */
    public double netPrice() {
        return price * (1.0 - tax / 100.0);
    }

    /**
     * Bir miktar ürün için toplam vergi tutarı.
     */
    public double taxAmount(int amount) {
        return price * amount * (tax / 100.0);
    }

    /**
     * Bir miktar ürün için net kazanç (vergisiz).
     */
    public double netEarnings(int amount) {
        return netPrice() * amount;
    }
}
