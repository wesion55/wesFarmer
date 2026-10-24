package xyz.geik.wesfarmer.storage;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Farmer başına, materyal başına collect/sell durumunu saklar.
 *
 * <p>Write-through cache: {@code farmerId → materialName → boolean[]{collect,sell}}.
 * {@code CollectTask} ve GUI'ler DB'ye gitmeden durum okuyabilir.
 */
public final class FarmerProductStateRepository {

    // boolean[] indeksleri
    private static final int IDX_COLLECT = 0;
    private static final int IDX_SELL    = 1;

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS farmer_product_state (
                farmer_id       INTEGER NOT NULL,
                material        TEXT    NOT NULL,
                collect_enabled INTEGER NOT NULL DEFAULT 1,
                sell_enabled    INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY (farmer_id, material)
            );
            """;

    private static final String CREATE_IDX =
            "CREATE INDEX IF NOT EXISTS idx_fps_farmer ON farmer_product_state(farmer_id);";

    private static final String UPSERT = """
            INSERT INTO farmer_product_state (farmer_id, material, collect_enabled, sell_enabled)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(farmer_id, material) DO UPDATE SET
                collect_enabled = excluded.collect_enabled,
                sell_enabled    = excluded.sell_enabled;
            """;

    private static final String SELECT_BY_FARMER =
            "SELECT material, collect_enabled, sell_enabled " +
            "FROM farmer_product_state WHERE farmer_id = ?;";

    private static final String SELECT_ONE =
            "SELECT collect_enabled, sell_enabled " +
            "FROM farmer_product_state WHERE farmer_id = ? AND material = ? LIMIT 1;";

    private static final String DELETE_BY_FARMER =
            "DELETE FROM farmer_product_state WHERE farmer_id = ?;";

    private final JavaPlugin plugin;
    private final Connection connection;

    /** farmerId → materialName → boolean[]{collect, sell} */
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, boolean[]>> cache
            = new ConcurrentHashMap<>();

    public FarmerProductStateRepository(JavaPlugin plugin, Connection connection) {
        this.plugin     = plugin;
        this.connection = connection;
    }

    public void init() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(CREATE_TABLE);
            stmt.execute(CREATE_IDX);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[wesFarmer] farmer_product_state tablosu oluşturulamadı!", e);
        }
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /** Her iki durumu birden kaydeder ve cache'i günceller. */
    public void save(long farmerId, Material material,
                     boolean collectEnabled, boolean sellEnabled) {
        try (PreparedStatement ps = connection.prepareStatement(UPSERT)) {
            ps.setLong  (1, farmerId);
            ps.setString(2, material.name());
            ps.setInt   (3, collectEnabled ? 1 : 0);
            ps.setInt   (4, sellEnabled    ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[wesFarmer] Ürün durumu kaydedilemedi: " + material, e);
        }
        // Write-through: cache'teki mevcut array'i güncelle ya da yenisini koy
        cache.computeIfAbsent(farmerId, id -> new ConcurrentHashMap<>())
             .put(material.name(), new boolean[]{collectEnabled, sellEnabled});
    }

    /** Sadece collect durumunu günceller; sell cache'ten okunur (DB'ye gitme). */
    public void setCollect(long farmerId, Material material, boolean enabled) {
        boolean sell = getSellEnabled(farmerId, material, true);
        save(farmerId, material, enabled, sell);
    }

    /** Sadece sell durumunu günceller; collect cache'ten okunur (DB'ye gitme). */
    public void setSell(long farmerId, Material material, boolean enabled) {
        boolean collect = getCollectEnabled(farmerId, material, true);
        save(farmerId, material, collect, enabled);
    }

    /** Farmer silindiğinde tüm durumları ve cache'i temizler. */
    public void deleteByFarmer(long farmerId) {
        cache.remove(farmerId);
        try (PreparedStatement ps = connection.prepareStatement(DELETE_BY_FARMER)) {
            ps.setLong(1, farmerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "[wesFarmer] Ürün durumları silinemedi: farmerId=" + farmerId, e);
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public boolean getCollectEnabled(long farmerId, Material material, boolean fallback) {
        return getField(farmerId, material, IDX_COLLECT, fallback);
    }

    public boolean getSellEnabled(long farmerId, Material material, boolean fallback) {
        return getField(farmerId, material, IDX_SELL, fallback);
    }

    /** Farmer için tüm kayıtlı ürün durumlarını döndürür (fallback caller'a ait). */
    public Map<String, boolean[]> getAll(long farmerId) {
        // Cache'de tam set varsa döndür
        ConcurrentHashMap<String, boolean[]> farmerMap = cache.get(farmerId);
        if (farmerMap != null && !farmerMap.isEmpty()) {
            return new HashMap<>(farmerMap);
        }

        // DB'den yükle ve cache'e koy
        Map<String, boolean[]> result = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_FARMER)) {
            ps.setLong(1, farmerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    boolean[] state = {
                        rs.getInt("collect_enabled") == 1,
                        rs.getInt("sell_enabled")    == 1
                    };
                    result.put(rs.getString("material"), state);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "[wesFarmer] Ürün durumları okunamadı: farmerId=" + farmerId, e);
        }

        // Cache'e toplu yaz
        if (!result.isEmpty()) {
            ConcurrentHashMap<String, boolean[]> map = new ConcurrentHashMap<>(result);
            cache.put(farmerId, map);
        }
        return result;
    }

    /** Plugin disable'da cache'i temizler. */
    public void clearCache() {
        cache.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean getField(long farmerId, Material material,
                              int fieldIndex, boolean fallback) {
        // Önce cache'e bak
        ConcurrentHashMap<String, boolean[]> farmerMap = cache.get(farmerId);
        if (farmerMap != null) {
            boolean[] state = farmerMap.get(material.name());
            if (state != null) return state[fieldIndex];
        }

        // DB'den oku ve cache'e yaz
        try (PreparedStatement ps = connection.prepareStatement(SELECT_ONE)) {
            ps.setLong  (1, farmerId);
            ps.setString(2, material.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    boolean collect = rs.getInt("collect_enabled") == 1;
                    boolean sell    = rs.getInt("sell_enabled")    == 1;
                    cache.computeIfAbsent(farmerId, id -> new ConcurrentHashMap<>())
                         .put(material.name(), new boolean[]{collect, sell});
                    return fieldIndex == IDX_COLLECT ? collect : sell;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "[wesFarmer] Ürün durumu okunamadı: " + material, e);
        }
        return fallback;
    }
}
