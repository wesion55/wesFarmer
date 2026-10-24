package xyz.geik.wesfarmer.storage;

import java.util.UUID;

/**
 * Bir çiftçiye bağlı tek bir üyeyi temsil eden değer nesnesi.
 *
 * @param farmerId  Hangi çiftçiye ait (farmers.id)
 * @param uuid      Üyenin Minecraft UUID'si
 * @param name      Üyenin son bilinen kullanıcı adı (görüntüleme için)
 * @param role      Üyenin rolü ({@link FarmerRole})
 */
public record FarmerMember(
        long     farmerId,
        UUID     uuid,
        String   name,
        FarmerRole role
) {
    /** Rol güncellenmiş yeni bir kopya döndürür. */
    public FarmerMember withRole(FarmerRole newRole) {
        return new FarmerMember(farmerId, uuid, name, newRole);
    }
}
