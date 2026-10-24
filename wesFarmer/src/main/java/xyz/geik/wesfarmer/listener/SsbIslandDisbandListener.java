package xyz.geik.wesfarmer.listener;

import com.bgsoftware.superiorskyblock.api.events.IslandDisbandEvent;
import com.bgsoftware.superiorskyblock.api.island.Island;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import xyz.geik.wesfarmer.storage.FarmerRecord;
import xyz.geik.wesfarmer.storage.FarmerRepository;

import java.util.List;

/**
 * SuperiorSkyblock2 event dinleyicisi.
 * Sadece SuperiorSkyblock2 yukluyken register edilmeli.
 *
 * <p>{@link IslandDisbandEvent} — Ada dagitilinca (disband) uzerindeki
 * farmer silinir. Gerek oyuncu gerek admin disband'i kapsar.
 */
public final class SsbIslandDisbandListener implements Listener {

    private final ClaimDeleteListener delegate;
    private final FarmerRepository    farmerRepository;

    public SsbIslandDisbandListener(ClaimDeleteListener delegate,
                                    FarmerRepository farmerRepository) {
        this.delegate         = delegate;
        this.farmerRepository = farmerRepository;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandDisband(IslandDisbandEvent event) {
        Island island   = event.getIsland();
        String claimKey = "ssb:" + island.getUniqueId();

        List<FarmerRecord> all = farmerRepository.findAll();
        for (FarmerRecord record : all) {
            if (claimKey.equals(record.claimKey())) {
                delegate.deleteFarmer(record, "SSB adasi dagitildi");
                return;
            }
        }
    }
}
