package xyz.geik.wesfarmer.storage;

/**
 * Bir çiftçide tanımlanabilecek tüm aksiyon izinleri.
 *
 * <p>Her izin hangi rolün yapabileceğini {@code config.yml → settings.permissions.*}
 * bölümünden alır. Kod içinde doğrudan rol sınırı yoktur; her şey config'den gelir.
 */
public enum FarmerPermission {

    /** Tek üründen tüm stoğu sat. */
    SELL("sell"),

    /** Tüm ürünleri toplu sat. */
    SELL_ALL("sell-all"),

    /** Tek ürünün depo seviyesini yükselt. */
    UPGRADE("upgrade"),

    /** Tüm ürünleri toplu yükselt. */
    UPGRADE_ALL("upgrade-all"),

    /** Üründen envantere eşya al. */
    TAKE("take"),

    /** Tek ürünün toplama durumunu aç/kapat. */
    COLLECT_TOGGLE("collect-toggle"),

    /** Tüm ürünlerin toplama durumunu toplu aç/kapat. */
    COLLECT_TOGGLE_ALL("collect-toggle-all"),

    /** Üye menüsünü aç (sadece görüntüle). */
    VIEW_MEMBERS("view-members"),

    /** Tek ürünün satış durumunu aç/kapat. */
    SELL_TOGGLE("sell-toggle");

    /** config.yml'deki anahtar adı (settings.permissions.&lt;configKey&gt;). */
    private final String configKey;

    FarmerPermission(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }
}
