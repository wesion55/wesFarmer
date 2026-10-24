package xyz.geik.wesfarmer.integration.claim;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Kurulu her {@link ClaimProvider}'ı danışır.
 *
 * <p><b>Kural:</b> bir provider yalnızca o konumda aktif bir claim/region
 * takip ediyorsa söz hakkı alır. Provider kurulu ama konumda claim/region
 * yoksa (wilderness) o provider atlanır — başka bir provider'ın kararını
 * bloke etmez.
 *
 * <p>Örnek: GP + WorldGuard aynı anda kurulu. Oyuncu GP claimindeyse ama
 * WG region'ı yoksa, WG sessiz kalır ve yalnızca GP karar verir. Bu
 * sayede iki plugin çakışmaz.
 */
public final class ClaimGuard {

    private final List<ClaimProvider> providers = List.of(
            new GriefPreventionClaimProvider(),
            new WorldGuardClaimProvider(),
            new SuperiorSkyblockClaimProvider()
    );

    /**
     * O konumda en az bir provider aktif claim takip ediyorsa, takip eden
     * her provider'ın izin vermesi gerekir. Hiçbir provider o konumu
     * tanımıyorsa (tam wilderness) → reddedilir (claim zorunlu).
     */
    public boolean canBuild(Player player, Location location) {
        boolean anyProviderActive = false;

        for (ClaimProvider provider : providers) {
            if (!provider.isAvailable()) continue;

            // Provider bu konumda bir claim/region takip ediyor mu?
            String key = provider.claimKey(location);
            if (key == null) continue; // bu provider'ın alanı değil, atla

            anyProviderActive = true;

            // Provider aktif — izin kontrolünü yap
            if (!provider.canBuild(player, location)) return false;
        }

        // Hiçbir provider bu konumu tanımıyorsa wilderness — reddedilir
        return anyProviderActive;
    }

    /**
     * İlk tanıyan provider'ın claim anahtarını döndürür.
     * Birden fazla provider aynı konumu tanıyorsa ilki kazanır.
     */
    public String claimKey(Location location) {
        for (ClaimProvider provider : providers) {
            if (!provider.isAvailable()) continue;
            String key = provider.claimKey(location);
            if (key != null) return key;
        }
        return null;
    }
}
