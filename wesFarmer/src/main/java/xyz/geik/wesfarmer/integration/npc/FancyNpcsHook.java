package xyz.geik.wesfarmer.integration.npc;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Soft-dependency hook for FancyNpcs. {@link #isAvailable()} must be checked
 * by the caller before any of the other methods are used.
 */
public final class FancyNpcsHook {

    private FancyNpcsHook() {}

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("FancyNpcs");
    }

    /** FancyNpcs'in bu isimde bir NPC'si kayıtlı mı? */
    public static boolean npcExists(String name) {
        try {
            return FancyNpcsPlugin.get().getNpcManager().getNpc(name) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Çevrimiçi bir oyuncu için farmer NPC'si oluşturur.
     *
     * @return NPC'nin benzersiz adı başarıda, başarısızlıkta {@code null}.
     */
    @Nullable
    public static String spawnFarmerNpc(String name, Location location, Player owner, String skin) {
        return spawnFarmerNpcOffline(name, location, owner.getUniqueId(), skin);
    }

    /**
     * Restore sırasında (oyuncu çevrimdışıyken) farmer NPC'si oluşturur.
     *
     * @return NPC'nin benzersiz adı başarıda, başarısızlıkta {@code null}.
     */
    @Nullable
    public static String spawnFarmerNpcOffline(String name, Location location,
                                                UUID ownerUuid, String skin) {
        try {
            NpcData data = new NpcData(name, ownerUuid, location);
            if (skin != null && !skin.isBlank()) data.setSkin(skin);
            // <empty> → FancyNpcs name tag'i tamamen gizler
            data.setDisplayName("<empty>");

            Npc npc = FancyNpcsPlugin.get().getNpcAdapter().apply(data);
            FancyNpcsPlugin.get().getNpcManager().registerNpc(npc);
            npc.create();
            npc.spawnForAll();
            return name;
        } catch (Exception exception) {
            Bukkit.getLogger().warning("[WesFarmer] FancyNpcs NPC '" + name + "' oluşturulamadı: " + exception.getMessage());
            return null;
        }
    }

    public static void removeNpc(String name) {
        try {
            Npc npc = FancyNpcsPlugin.get().getNpcManager().getNpc(name);
            if (npc == null) return;
            FancyNpcsPlugin.get().getNpcManager().removeNpc(npc);
            npc.removeForAll();
        } catch (Exception exception) {
            Bukkit.getLogger().warning("[WesFarmer] FancyNpcs NPC '" + name + "' silinemedi: " + exception.getMessage());
        }
    }
}
