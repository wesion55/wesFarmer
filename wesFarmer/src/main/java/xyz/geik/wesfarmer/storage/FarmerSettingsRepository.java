package xyz.geik.wesfarmer.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * SQLite-backed repository for {@link FarmerSettings}.
 *
 * <p>Write-through in-memory cache: her {@link #get} sonucu RAM'de tutulur.
 * {@link #save} cache'i günceller, {@link #delete} temizler.
 * Bu sayede {@code CollectTask} ve GUI'ler DB'ye gitmez.
 */
public final class FarmerSettingsRepository {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS farmer_settings (
                farmer_id      INTEGER PRIMARY KEY,
                holo_visible   INTEGER NOT NULL DEFAULT 1,
                sell_locked    INTEGER NOT NULL DEFAULT 0,
                collect_locked INTEGER NOT NULL DEFAULT 0
            );
            """;

    private static final String DROP_NPC_COL =
            "ALTER TABLE farmer_settings DROP COLUMN IF EXISTS npc_visible;";

    private static final String UPSERT = """
            INSERT INTO farmer_settings (farmer_id, holo_visible, sell_locked, collect_locked)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(farmer_id) DO UPDATE SET
                holo_visible   = excluded.holo_visible,
                sell_locked    = excluded.sell_locked,
                collect_locked = excluded.collect_locked;
            """;

    private static final String SELECT =
            "SELECT farmer_id, holo_visible, sell_locked, collect_locked " +
            "FROM farmer_settings WHERE farmer_id = ? LIMIT 1;";

    private static final String DELETE =
            "DELETE FROM farmer_settings WHERE farmer_id = ?;";

    private final JavaPlugin plugin;
    private final Connection connection;

    /** farmerId → FarmerSettings (write-through cache) */
    private final ConcurrentHashMap<Long, FarmerSettings> cache = new ConcurrentHashMap<>();

    public FarmerSettingsRepository(JavaPlugin plugin, Connection connection) {
        this.plugin     = plugin;
        this.connection = connection;
    }

    public void init() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(CREATE_TABLE);
            try { stmt.execute(DROP_NPC_COL); } catch (SQLException ignored) {}
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[wesFarmer] farmer_settings tablosu oluşturulamadı!", e);
        }
    }

    /** Ayarları kaydeder ve cache'i günceller (write-through). */
    public void save(FarmerSettings s) {
        try (PreparedStatement ps = connection.prepareStatement(UPSERT)) {
            ps.setLong(1, s.farmerId());
            ps.setInt (2, s.holoVisible()   ? 1 : 0);
            ps.setInt (3, s.sellLocked()    ? 1 : 0);
            ps.setInt (4, s.collectLocked() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[wesFarmer] farmer_settings kaydedilemedi: " + s.farmerId(), e);
        }
        // Write-through: DB başarısız olsa bile cache'e yaz (en kötü geçici tutarsızlık)
        cache.put(s.farmerId(), s);
    }

    /**
     * Ayarları döndürür. Cache hit → RAM'den. Miss → DB'den okur, cache'e yazar.
     * DB'de kayıt yoksa varsayılan oluşturur.
     */
    public FarmerSettings get(long farmerId) {
        FarmerSettings cached = cache.get(farmerId);
        if (cached != null) return cached;

        // DB'den oku
        try (PreparedStatement ps = connection.prepareStatement(SELECT)) {
            ps.setLong(1, farmerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    FarmerSettings s = new FarmerSettings(
                            rs.getLong("farmer_id"),
                            rs.getInt("holo_visible")   == 1,
                            rs.getInt("sell_locked")    == 1,
                            rs.getInt("collect_locked") == 1
                    );
                    cache.put(farmerId, s);
                    return s;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "[wesFarmer] farmer_settings okunamadı: " + farmerId, e);
        }

        // Kayıt yok → varsayılan oluştur ve kaydet
        FarmerSettings defaults = FarmerSettings.defaults(farmerId);
        save(defaults); // cache'e de yazar
        return defaults;
    }

    /** Ayarları siler ve cache'ten temizler. */
    public void delete(long farmerId) {
        cache.remove(farmerId);
        try (PreparedStatement ps = connection.prepareStatement(DELETE)) {
            ps.setLong(1, farmerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "[wesFarmer] farmer_settings silinemedi: " + farmerId, e);
        }
    }

    /** Plugin disable'da cache'i temizler. */
    public void clearCache() {
        cache.clear();
    }
}
