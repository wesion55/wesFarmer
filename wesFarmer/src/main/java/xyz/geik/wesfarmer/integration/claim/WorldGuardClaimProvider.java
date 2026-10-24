package xyz.geik.wesfarmer.integration.claim;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * WorldGuard entegrasyonu.
 *
 * <p>GP ile tutarlılık için, farmer yalnızca oyuncunun <b>owner</b> (sahip)
 * olduğu bir bölgeye yerleştirilebilir — yalnızca member/BUILD izni yeterli
 * değil. Wilderness (bölgesiz alan) her zaman reddedilir.
 *
 * <p>Birden fazla örtüşen bölge varsa oyuncunun hepsinde owner olması gerekir;
 * tek bir bölgede bile owner değilse yerleştirme reddedilir.
 */
public final class WorldGuardClaimProvider implements ClaimProvider {

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
    }

    @Override
    public boolean canBuild(Player player, Location location) {
        ApplicableRegionSet regions = getRegions(location);

        // Bölgesiz alan (wilderness) — reddedilir.
        if (regions == null || regions.size() == 0) return false;

        // Tüm bölgelerde owner olmalı.
        for (ProtectedRegion region : regions) {
            if (!region.getOwners().contains(player.getUniqueId())) {
                return false;
            }
        }
        return true;
    }

    /**
     * WorldGuard'da tek bir numeric claim ID yoktur — bir konum birden fazla
     * örtüşen bölge içinde olabilir. Tüm bölge ID'lerini sıralayıp
     * {@code "wg:<id1>,<id2>,..."} biçiminde birleştiriyoruz; böylece aynı
     * örtüşme her zaman aynı anahtarı üretir.
     *
     * @return bölge anahtarı, ya da konum hiçbir bölge içinde değilse {@code null}
     */
    @Override
    public String claimKey(Location location) {
        ApplicableRegionSet regions = getRegions(location);
        if (regions == null || regions.size() == 0) return null;

        List<String> ids = new ArrayList<>();
        for (ProtectedRegion region : regions) {
            ids.add(region.getId());
        }
        Collections.sort(ids);
        return "wg:" + String.join(",", ids);
    }

    // -------------------------------------------------------------------------

    private static ApplicableRegionSet getRegions(Location location) {
        try {
            RegionQuery query = WorldGuard.getInstance()
                    .getPlatform().getRegionContainer().createQuery();
            return query.getApplicableRegions(BukkitAdapter.adapt(location));
        } catch (Exception e) {
            return null;
        }
    }
}
