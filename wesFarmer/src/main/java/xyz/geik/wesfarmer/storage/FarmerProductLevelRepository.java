package xyz.geik.wesfarmer.storage;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Çiftçi başına ürün seviyelerini yönetir.
 *
 * <p>Write-through cache: {@code farmerId → materialName → level}.
 * {@link CollectTask} ve GUI'ler DB'ye gitmeden seviye okuyabilir.
 */
public final class FarmerProductLevelRepository {

    public static final int MAX_LEVEL          = 10;
    public static final int CAPACITY_PER_LEVEL = 2500;
    public static final int BASE_COST          = 5000;

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS farmer_product_level (
                farmer_id INTEGER NOT NULL,
                material  TEXT    NOT NULL,
                level     INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY (farmer_id, material)
            );
            """;

    private static final String UPSERT = """
            INSERT INTO farmer_product_level (farmer_id, material, level)
            VALUES (?, ?, ?)
            ON CONFLICT(farmer_id, material) DO UPDATE SET level = excluded.level;
            """;

    private static final String SELECT_ONE = """
            SELECT level FROM farmer_product_level
            WHERE farmer_id = ? AND material = ? LIMIT 1;
            """;

    private static final String DELETE_BY_FARMER = """
            DELETE FROM farmer_product_level WHERE farmer_id = ?;
            """;

    private final JavaPlugin plugin;
    private final Connection connection;

    /** farmerId → materialName → level (write-through cache) */
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, Integer>> cache
            = new ConcurrentHashMap<>();

    public FarmerProductLevelRepository(JavaPlugin plugin, Connection connection) {
        this.plugin     = plugin;
        this.connection = connection;
    }

    public void init() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(CREATE_TABLE);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[wesFarmer] farmer_product_level tablosu oluşturulamadı!", e);
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /** Seviyeyi döndürür. Cache hit → RAM, miss → DB (sonra cache'e yazar). */
    public int getLevel(long farmerId, Material material) {
        ConcurrentHashMap<String, Integer> farmerMap = cache.get(farmerId);
        if (farmerMap != null) {
            Integer cached = farmerMap.get(material.name());
            if (cached != null) return cached;
        }

        // DB'den oku
        int level = 1;
        try (PreparedStatement ps = connection.prepareStatement(SELECT_ONE)) {
            ps.setLong(1, farmerId);
            ps.setString(2, material.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) level = rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[wesFarmer] Seviye okunamadı: " + material, e);
        }

        // Cache'e yaz
        cache.computeIfAbsent(farmerId, id -> new ConcurrentHashMap<>())
             .put(material.name(), level);
        return level;
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /** Seviyeyi set eder ve cache'i günceller. */
    public void setLevel(long farmerId, Material material, int level) {
        int clamped = Math.max(1, Math.min(MAX_LEVEL, level));
        try (PreparedStatement ps = connection.prepareStatement(UPSERT)) {
            ps.setLong(1, farmerId);
            ps.setString(2, material.name());
            ps.setInt(3, clamped);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[wesFarmer] Seviye güncellenemedi: " + material, e);
        }
        // Write-through
        cache.computeIfAbsent(farmerId, id -> new ConcurrentHashMap<>())
             .put(material.name(), clamped);
    }

    /** Seviyeyi 1 artırır. Zaten max'taysa false döner. */
    public boolean levelUp(long farmerId, Material material) {
        int current = getLevel(farmerId, material);
        if (current >= MAX_LEVEL) return false;
        setLevel(farmerId, material, current + 1);
        return true;
    }

    /** Çiftçi silinince tüm seviye kayıtlarını ve cache'i temizler. */
    public void deleteByFarmer(long farmerId) {
        cache.remove(farmerId);
        try (PreparedStatement ps = connection.prepareStatement(DELETE_BY_FARMER)) {
            ps.setLong(1, farmerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[wesFarmer] Seviye kayıtları silinemedi: " + farmerId, e);
        }
    }

    /** Plugin disable'da cache'i temizler. */
    public void clearCache() {
        cache.clear();
    }

    // ── Statik hesaplamalar ───────────────────────────────────────────────────

    public static int maxStockAtLevel(int baseMaxStock, int level) {
        return baseMaxStock + (level - 1) * CAPACITY_PER_LEVEL;
    }

    public static double upgradeCost(int currentLevel) {
        return BASE_COST * Math.pow(2, currentLevel - 1);
    }
}
