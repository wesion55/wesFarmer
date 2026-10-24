package xyz.geik.wesfarmer.storage;

/**
 * Bir çiftçiye atanabilecek üç rol seviyesi.
 *
 * <p>Sıralama önemlidir: {@link #ordinal()} değeri arttıkça yetki de artar.
 * Karşılaştırmalarda {@code role.ordinal() >= required.ordinal()} kullanın.
 *
 * <p>Görünen isimler {@code config.yml → settings.roles.*} üzerinden
 * değiştirilebilir; enum sabitleri yalnızca dahili kimlik görevi görür.
 */
public enum FarmerRole {

    /** En düşük yetki: menüyü görüntüleyebilir, toplu satış/al yapabilir. */
    MEMBER,

    /** Orta yetki: üye ekleyip çıkarabilir, ayarları değiştirebilir. */
    LEADER,

    /**
     * En yüksek yetki: çiftçiyi tamamen kontrol eder.
     * Bu rol yalnızca çiftçinin asıl sahibine atanır;
     * başkasına devredilemez.
     */
    OWNER;

    /** Üyenin verilen minimumdan en az bu kadar yetkili olup olmadığını döndürür. */
    public boolean isAtLeast(FarmerRole required) {
        return this.ordinal() >= required.ordinal();
    }
}
