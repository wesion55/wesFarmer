package xyz.geik.wesfarmer.listener;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.events.ClaimDeletedEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimResizeEvent;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import xyz.geik.wesfarmer.storage.FarmerRecord;
import xyz.geik.wesfarmer.storage.FarmerRepository;

import java.util.List;

/**
 * GriefPrevention event dinleyicisi.
 * Sadece GP yüklüyken register edilmeli.
 *
 * <ul>
 *   <li>{@link ClaimDeletedEvent} — Claim silinince farmer silinir.</li>
 *   <li>{@link ClaimResizeEvent}  — Claim küçülünce farmer dışarıda kalıyorsa silinir.</li>
 * </ul>
 */
public final class GpClaimDeleteListener implements Listener {

    private final ClaimDeleteListener delegate;
    private final FarmerRepository    farmerRepository;

    public GpClaimDeleteListener(ClaimDeleteListener delegate,
                                  FarmerRepository farmerRepository) {
        this.delegate         = delegate;
        this.farmerRepository = farmerRepository;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClaimDeleted(ClaimDeletedEvent event) {
        String claimKey = "gp:" + event.getClaim().getID();
        findAndDelete(claimKey, "Claim silindi");
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClaimResize(ClaimResizeEvent event) {
        Claim  claim    = event.getClaim();
        String claimKey = "gp:" + claim.getID();

        List<FarmerRecord> all = farmerRepository.findAll();
        for (FarmerRecord record : all) {
            if (!claimKey.equals(record.claimKey())) continue;

            Location farmerLoc = record.toLocation(delegate.plugin().getServer());
            if (farmerLoc == null) {
                delegate.deleteFarmer(record, "Dünya bulunamadı (resize sonrası)");
                return;
            }
            // claim.contains(loc, ignoreAltitude, ignoreSubdivisions)
            if (!claim.contains(farmerLoc, true, false)) {
                delegate.deleteFarmer(record, "Claim yeniden boyutlandırıldı, farmer dışarıda kaldı");
            }
            return;
        }
    }

    private void findAndDelete(String claimKey, String reason) {
        List<FarmerRecord> all = farmerRepository.findAll();
        for (FarmerRecord record : all) {
            if (claimKey.equals(record.claimKey())) {
                delegate.deleteFarmer(record, reason);
                return;
            }
        }
    }
}
