package xyz.geik.wesfarmer.task;

import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import xyz.geik.wesfarmer.WesFarmer;
import xyz.geik.wesfarmer.integration.claim.ClaimGuard;
import xyz.geik.wesfarmer.product.FarmerProduct;
import xyz.geik.wesfarmer.storage.FarmerProductLevelRepository;
import xyz.geik.wesfarmer.storage.FarmerProductStateRepository;
import xyz.geik.wesfarmer.storage.FarmerRecord;
import xyz.geik.wesfarmer.storage.FarmerRepository;
import xyz.geik.wesfarmer.storage.FarmerSettingsRepository;
import xyz.geik.wesfarmer.storage.FarmerStockRepository;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Item yere düştüğü anda {@link ItemSpawnEvent} ile yakalar.
 *
 * <p>Toplama kararı mesafeye değil, <b>claim eşleşmesine</b> dayanır.
 * Her event'te DB'ye gitmek yerine claimKey → FarmerRecord haritası
 * bellekte tutulur; sadece insert/delete/updateLocation'da yenilenir.
 */
public final class CollectTask implements Listener {

    private final WesFarmer                plugin;
    private final FarmerRepository         farmerRepository;
    private final FarmerStockRepository    stockRepository;
    private final ClaimGuard               claimGuard;
    private final FarmerSettingsRepository settingsRepository;
    private final FarmerProductStateRepository stateRepository;

    /**
     * claimKey → FarmerRecord önbelleği.
     */
    private final Map<String, FarmerRecord> claimCache = new ConcurrentHashMap<>();

    public CollectTask(WesFarmer plugin,
                        FarmerRepository farmerRepository,
                        FarmerStockRepository stockRepository,
                        ClaimGuard claimGuard,
                        FarmerSettingsRepository settingsRepository,
                        FarmerProductStateRepository stateRepository) {
        this.plugin             = plugin;
        this.farmerRepository   = farmerRepository;
        this.stockRepository    = stockRepository;
        this.claimGuard         = claimGuard;
        this.settingsRepository = settingsRepository;
        this.stateRepository    = stateRepository;
    }

    // ── Cache yönetimi ────────────────────────────────────────────────────────

    /** Tüm cache'i DB'den yeniden yükler. Startup'ta bir kez çağrılır. */
    public void rebuildCache() {
        Map<String, FarmerRecord> fresh = new HashMap<>();
        for (FarmerRecord r : farmerRepository.findAll()) {
            if (!FarmerRecord.NO_CLAIM.equals(r.claimKey())) {
                fresh.put(r.claimKey(), r);
            }
        }
        claimCache.clear();
        claimCache.putAll(fresh);
    }

    /** Yeni farmer eklendiğinde veya taşındığında cache'e ekle. */
    public void cacheAdd(FarmerRecord record) {
        if (!FarmerRecord.NO_CLAIM.equals(record.claimKey())) {
            claimCache.put(record.claimKey(), record);
        }
    }

    /**
     * Farmer silindiğinde veya taşındığında eski claim'i cache'den kaldır.
     * Taşıma sonrası yeni claim için {@link #cacheAdd(FarmerRecord)} çağrılmalı.
     */
    public void cacheRemove(String claimKey) {
        if (claimKey != null) {
            claimCache.remove(claimKey);
        }
    }

    // ── Event handler ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item      itemEntity = event.getEntity();
        ItemStack stack      = itemEntity.getItemStack();

        if (stack.getType().isAir()) return;

        // Bu materyal products.yml'de tanımlı mı?
        Optional<FarmerProduct> productOpt =
                plugin.getProductManager().findByMaterial(stack.getType());
        if (productOpt.isEmpty()) return;

        FarmerProduct product = productOpt.get();
        // Not: global product.collectEnabled() burada kontrol edilmez.
        // Farmer-bazlı durum 121. satırda stateRepository üzerinden fallback ile kontrol edilir.

        // Item'ın düştüğü claim'i bul
        String dropClaimKey = claimGuard.claimKey(itemEntity.getLocation());
        if (dropClaimKey == null) return;

        // Cache'den O(1) lookup — DB'ye gitme
        FarmerRecord record = claimCache.get(dropClaimKey);
        if (record == null) return;

        // Toplama kilitli mi? (farmer-bazlı settings)
        if (settingsRepository.get(record.id()).collectLocked()) return;

        // Bu ürün bu farmer için DB'de kapalı mı? (farmer-bazlı state, fallback: products.yml)
        if (!stateRepository.getCollectEnabled(record.id(), product.material(), product.collectEnabled())) return;

        // Stok kapasitesi kontrolü
        int level    = plugin.getFarmerProductLevelRepository()
                .getLevel(record.id(), product.material());
        int maxStock = FarmerProductLevelRepository.maxStockAtLevel(
                product.maxStock(), level);
        int current  = stockRepository.getStock(record.id(), product.material());

        if (maxStock > 0 && current >= maxStock) return;

        int canCollect = maxStock > 0 ? maxStock - current : stack.getAmount();
        int toCollect  = Math.min(stack.getAmount(), canCollect);

        stockRepository.addStock(record.id(), product.material(), toCollect);

        if (toCollect >= stack.getAmount()) {
            event.setCancelled(true);
        } else {
            stack.setAmount(stack.getAmount() - toCollect);
            itemEntity.setItemStack(stack);
        }
    }

    /** Listener'ı kaydeder ve cache'i başlatır. */
    public static CollectTask start(WesFarmer plugin) {
        CollectTask task = new CollectTask(
                plugin,
                plugin.getFarmerRepository(),
                plugin.getFarmerStockRepository(),
                plugin.getClaimGuard(),
                plugin.getFarmerSettingsRepository(),
                plugin.getFarmerProductStateRepository());
        plugin.getServer().getPluginManager().registerEvents(task, plugin);
        task.rebuildCache();
        INSTANCE = task;
        return task;
    }

    private static volatile CollectTask INSTANCE;

    /** Aktif CollectTask örneğini döndürür. Null olabilir (plugin disable sonrası). */
    public static CollectTask getInstance() { return INSTANCE; }
}
