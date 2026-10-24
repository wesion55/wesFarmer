package xyz.geik.wesfarmer.integration.claim;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * A claim/region protection plugin that WesFarmer consults before letting a
 * player place a farmer NPC at a location. Implementations must be safe to
 * instantiate even when their backing plugin is not installed; only
 * {@link #canBuild(Player, Location)} may touch the plugin's API, and only
 * after {@link #isAvailable()} returned {@code true}.
 */
public interface ClaimProvider {

    boolean isAvailable();

    /**
     * @return {@code true} if the player may build/place at the location
     *         according to this provider (this also covers unclaimed land,
     *         where there is nothing to deny).
     */
    boolean canBuild(Player player, Location location);

    /**
     * @return a stable identifier for the claim/region at this location, or
     *         {@code null} when the location isn't inside anything this
     *         provider tracks. Used by {@code FarmerRepository} to enforce
     *         "one farmer per claim" regardless of which claim plugin is
     *         actually installed. Only called after {@link #isAvailable()}
     *         returned {@code true}. Default: unsupported (no claim key).
     */
    default String claimKey(Location location) {
        return null;
    }
}
