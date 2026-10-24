package xyz.geik.wesfarmer.storage;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Immutable snapshot of a single farmer entry stored in SQLite.
 *
 * @param id          auto-increment primary key (0 for unsaved records)
 * @param ownerUuid   UUID of the player who purchased the farmer
 * @param claimKey    provider-agnostic claim identifier used for the
 *                    "one farmer per claim" check — {@code "gp:<id>"} for a
 *                    GriefPrevention claim, {@code "wg:<region ids>"} for a
 *                    WorldGuard region set, or {@link #NO_CLAIM} when the
 *                    farmer wasn't placed inside anything either provider
 *                    tracks (or no claim plugin is installed)
 * @param npcName     unique name passed to FancyNpcs
 * @param holoName    unique name passed to FancyHolograms (may be null when
 *                    FancyHolograms is not installed)
 * @param world       world name where the farmer was placed
 * @param x           block X
 * @param y           block Y
 * @param z           block Z
 */
public record FarmerRecord(
        long id,
        UUID ownerUuid,
        String claimKey,
        String npcName,
        @Nullable String holoName,
        String world,
        double x,
        double y,
        double z
) {
    /** Sentinel {@link #claimKey} value meaning "not inside a tracked claim". */
    public static final String NO_CLAIM = "none";

    /**
     * Convenience factory — used before the row is inserted (id unknown yet).
     *
     * @param claimKey the value from {@code ClaimGuard.claimKey(...)}, or
     *                 {@code null} when the location isn't in a tracked claim
     *                 (normalised here to {@link #NO_CLAIM}).
     */
    public static FarmerRecord unsaved(UUID ownerUuid, @Nullable String claimKey,
                                       String npcName, @Nullable String holoName,
                                       Location location) {
        return new FarmerRecord(
                0,
                ownerUuid,
                claimKey == null ? NO_CLAIM : claimKey,
                npcName,
                holoName,
                location.getWorld() == null ? "world" : location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ()
        );
    }

    /** Rebuilds the spawn {@link Location} from the stored coordinates. */
    public @Nullable Location toLocation(org.bukkit.Server server) {
        World w = server.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z);
    }
}
