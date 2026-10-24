package xyz.geik.wesfarmer.integration.claim;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * SuperiorSkyblock2 entegrasyonu. Farmer NPC yalnizca oyuncunun kendi
 * adasinin icine yerlestirilebilir:
 * <ul>
 *   <li>Sahipsiz alan (wilderness/spawn) → reddedilir.</li>
 *   <li>Baskasinin adasi            → reddedilir.</li>
 *   <li>Oyuncunun kendi adasi       → izin verilir.</li>
 * </ul>
 *
 * <p>GP ve WorldGuard saglayicilariyla tutarlilik icin yalnizca ada
 * <b>sahibi</b> farmer yerlestirebilir; ada uyeleri (member) yerlestiremez.
 */
public final class SuperiorSkyblockClaimProvider implements ClaimProvider {

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("SuperiorSkyblock2");
    }

    @Override
    public boolean canBuild(Player player, Location location) {
        Island island = getIslandAt(location);

        // Sahipsiz alan (wilderness) — farmer yerlestirilemez.
        if (island == null) return false;

        // Ada sahibi degilse — farmer yerlestirilemez.
        SuperiorPlayer owner = island.getOwner();
        return owner != null && owner.getUniqueId().equals(player.getUniqueId());
    }

    /**
     * Adanin benzersiz UUID'sini {@code "ssb:<uuid>"} biçiminde dondurur.
     * Konum hicbir adaya ait degilse {@code null} doner.
     * Ada transferinde UUID degismez — farmer adada kalir.
     */
    @Override
    public String claimKey(Location location) {
        Island island = getIslandAt(location);
        return island == null ? null : "ssb:" + island.getUniqueId();
    }

    // -------------------------------------------------------------------------

    private static Island getIslandAt(Location location) {
        try {
            return SuperiorSkyblockAPI.getIslandAt(location);
        } catch (Exception e) {
            return null;
        }
    }
}
