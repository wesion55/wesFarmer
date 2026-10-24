package xyz.geik.wesfarmer.storage;

/**
 * Bir çiftçiye ait değiştirilebilir ayarlar.
 *
 * @param farmerId      Hangi çiftçiye ait (farmers.id)
 * @param holoVisible   Hologram tüm oyunculara görünür mü?
 * @param sellLocked    Satışlar kilitli mi? (true = satış yapılamaz)
 * @param collectLocked Toplama kilitli mi? (true = yere düşenler toplanmaz)
 */
public record FarmerSettings(
        long    farmerId,
        boolean holoVisible,
        boolean sellLocked,
        boolean collectLocked
) {
    /** Yeni farmer için varsayılan ayarlar. */
    public static FarmerSettings defaults(long farmerId) {
        return new FarmerSettings(farmerId, true, false, false);
    }

    public FarmerSettings withHoloVisible(boolean v)    { return new FarmerSettings(farmerId, v,           sellLocked,  collectLocked); }
    public FarmerSettings withSellLocked(boolean v)     { return new FarmerSettings(farmerId, holoVisible,  v,           collectLocked); }
    public FarmerSettings withCollectLocked(boolean v)  { return new FarmerSettings(farmerId, holoVisible,  sellLocked,  v); }
}
