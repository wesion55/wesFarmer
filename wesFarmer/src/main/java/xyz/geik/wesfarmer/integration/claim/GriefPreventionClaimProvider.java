package xyz.geik.wesfarmer.integration.claim;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * GriefPrevention integration. Farmer NPC yalnizca oyuncunun kendi
 * claiminin icine yerleştirilebilir:
 * <ul>
 *   <li>Sahipsiz alan (wilderness) → reddedilir.</li>
 *   <li>Baskasinin claimi       → reddedilir.</li>
 *   <li>Oyuncunun kendi claimi  → izin verilir.</li>
 * </ul>
 */
public final class GriefPreventionClaimProvider implements ClaimProvider {

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("GriefPrevention");
    }

    @Override
    public boolean canBuild(Player player, Location location) {
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, null);

        // Sahipsiz alan — farmer yerlestirilemez.
        if (claim == null) return false;

        // Claim sahibi oyuncu degilse — farmer yerlestirilemez.
        return player.getUniqueId().equals(claim.getOwnerID());
    }

    /**
     * Returns a stable "gp:&lt;claim id&gt;" key for the claim at this
     * location, or {@code null} when the location is unclaimed. Only called
     * after {@link #isAvailable()} returned {@code true}.
     */
    @Override
    public String claimKey(Location location) {
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, null);
        return claim == null ? null : "gp:" + claim.getID();
    }
}
