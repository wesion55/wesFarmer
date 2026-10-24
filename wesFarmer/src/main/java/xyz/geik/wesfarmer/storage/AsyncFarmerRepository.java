package xyz.geik.wesfarmer.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Asynchronous facade over {@link FarmerRepository}.
 *
 * <p>Every method mirrors its sync counterpart but returns a
 * {@link CompletableFuture} that completes on Bukkit's async thread pool.
 * Callers must NOT touch Bukkit API inside the future's completion stage
 * unless they schedule back onto the main thread first — typically:
 *
 * <pre>{@code
 * repo.findByOwner(uuid)
 *     .thenAccept(farmers ->
 *         plugin.getServer().getScheduler().runTask(plugin, () -> {
 *             // safe to call Bukkit here
 *         }));
 * }</pre>
 *
 * <p>The sync {@link FarmerRepository} is still available for cases that
 * genuinely must run on the main thread (e.g. inside event handlers that
 * already hold all the locks they need).
 */
public final class AsyncFarmerRepository {

    private final FarmerRepository sync;
    private final Executor asyncExecutor;

    public AsyncFarmerRepository(FarmerRepository sync, JavaPlugin plugin) {
        this.sync          = sync;
        // Re-use Bukkit's scheduler thread pool so we don't create an extra pool.
        this.asyncExecutor = task ->
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    public CompletableFuture<List<FarmerRecord>> findAll() {
        return CompletableFuture.supplyAsync(sync::findAll, asyncExecutor);
    }

    public CompletableFuture<List<FarmerRecord>> findByOwner(UUID ownerUuid) {
        return CompletableFuture.supplyAsync(() -> sync.findByOwner(ownerUuid), asyncExecutor);
    }

    public CompletableFuture<FarmerRecord> findByNpcName(String npcName) {
        return CompletableFuture.supplyAsync(() -> sync.findByNpcName(npcName), asyncExecutor);
    }

    public CompletableFuture<Boolean> claimHasFarmer(String claimKey) {
        return CompletableFuture.supplyAsync(() -> sync.claimHasFarmer(claimKey), asyncExecutor);
    }

    public CompletableFuture<Integer> countByOwner(UUID ownerUuid) {
        return CompletableFuture.supplyAsync(() -> sync.countByOwner(ownerUuid), asyncExecutor);
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    public CompletableFuture<FarmerRecord> insert(FarmerRecord record) {
        return CompletableFuture.supplyAsync(() -> sync.insert(record), asyncExecutor);
    }

    public CompletableFuture<Void> deleteByNpcName(String npcName) {
        return CompletableFuture.runAsync(() -> sync.deleteByNpcName(npcName), asyncExecutor);
    }

    public CompletableFuture<Integer> deleteAllByOwner(UUID ownerUuid) {
        return CompletableFuture.supplyAsync(() -> sync.deleteAllByOwner(ownerUuid), asyncExecutor);
    }

    public CompletableFuture<Boolean> updateLocation(String npcName,
                                                      String world, double x, double y, double z,
                                                      String claimKey) {
        return CompletableFuture.supplyAsync(
                () -> sync.updateLocation(npcName, world, x, y, z, claimKey), asyncExecutor);
    }

    // -------------------------------------------------------------------------
    // Passthrough — lifecycle stays on main thread
    // -------------------------------------------------------------------------

    /** @see FarmerRepository#isAvailable() */
    public boolean isAvailable() {
        return sync.isAvailable();
    }
}
