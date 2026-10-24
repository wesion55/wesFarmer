package xyz.geik.wesfarmer.storage;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Çiftçi işlem geçmişini SQLite'da yönetir.
 *
 * Schema (farmer_history):
 *   id         INTEGER PRIMARY KEY AUTOINCREMENT
 *   farmer_id  INTEGER NOT NULL
 *   type       TEXT    NOT NULL  (COLLECT | SELL)
 *   material   TEXT    NOT NULL
 *   amount     INTEGER NOT NULL
 *   earnings   REAL    NOT NULL DEFAULT 0
 *   timestamp  INTEGER NOT NULL
 */
public final class FarmerHistoryRepository {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS farmer_history (
                id        INTEGER PRIMARY KEY AUTOINCREMENT,
                farmer_id INTEGER NOT NULL,
                type      TEXT    NOT NULL,
                material  TEXT    NOT NULL,
                amount    INTEGER NOT NULL,
                earnings  REAL    NOT NULL DEFAULT 0,
                timestamp INTEGER NOT NULL
            );
            """;

    private static final String INSERT = """
            INSERT INTO farmer_history (farmer_id, type, material, amount, earnings, timestamp)
            VALUES (?, ?, ?, ?, ?, ?);
            """;

    private static final String SELECT_RECENT = """
            SELECT id, farmer_id, type, material, amount, earnings, timestamp
            FROM farmer_history WHERE farmer_id = ?
            ORDER BY timestamp DESC LIMIT ?;
            """;

    private static final String SELECT_PAGE = """
            SELECT id, farmer_id, type, material, amount, earnings, timestamp
            FROM farmer_history WHERE farmer_id = ?
            ORDER BY timestamp DESC LIMIT ? OFFSET ?;
            """;

    private static final String COUNT_BY_FARMER =
            "SELECT COUNT(*) FROM farmer_history WHERE farmer_id = ?;";

    private static final String DELETE_BY_FARMER = """
            DELETE FROM farmer_history WHERE farmer_id = ?;
            """;

    // ── Aggregate sorgular ────────────────────────────────────────────────────

    private static final String TOTAL_EARNINGS =
            "SELECT COALESCE(SUM(earnings), 0) FROM farmer_history WHERE farmer_id = ?;";

    private static final String TOTAL_SOLD_AMOUNT =
            "SELECT COALESCE(SUM(amount), 0) FROM farmer_history WHERE farmer_id = ? AND type = 'SELL';";

    private static final String TOTAL_COLLECTED_AMOUNT =
            "SELECT COALESCE(SUM(amount), 0) FROM farmer_history WHERE farmer_id = ? AND type = 'COLLECT';";

    private static final String TOTAL_SELL_TX =
            "SELECT COUNT(*) FROM farmer_history WHERE farmer_id = ? AND type = 'SELL';";

    private static final String TOP_SOLD_MATERIAL =
            "SELECT material, SUM(amount) AS total FROM farmer_history " +
            "WHERE farmer_id = ? AND type = 'SELL' GROUP BY material ORDER BY total DESC LIMIT 1;";

    private static final String TOP_COLLECTED_MATERIAL =
            "SELECT material, SUM(amount) AS total FROM farmer_history " +
            "WHERE farmer_id = ? AND type = 'COLLECT' GROUP BY material ORDER BY total DESC LIMIT 1;";

    private static final String LAST_TIMESTAMP =
            "SELECT MAX(timestamp) FROM farmer_history WHERE farmer_id = ?;";

    private final JavaPlugin plugin;
    private final Connection connection;

    public FarmerHistoryRepository(JavaPlugin plugin, Connection connection) {
        this.plugin     = plugin;
        this.connection = connection;
    }

    public void init() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(CREATE_TABLE);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[WesFarmer] farmer_history tablosu oluşturulamadı!", e);
        }
    }

    public void insert(long farmerId, FarmerHistoryRecord.Type type,
                        Material material, int amount, double earnings) {
        try (PreparedStatement ps = connection.prepareStatement(INSERT)) {
            ps.setLong(1, farmerId);
            ps.setString(2, type.name());
            ps.setString(3, material.name());
            ps.setInt(4, amount);
            ps.setDouble(5, earnings);
            ps.setLong(6, System.currentTimeMillis() / 1000L);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[WesFarmer] Geçmiş kaydı eklenemedi.", e);
        }
    }

    public List<FarmerHistoryRecord> findRecent(long farmerId, int limit) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_RECENT)) {
            ps.setLong(1, farmerId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<FarmerHistoryRecord> list = new ArrayList<>();
                while (rs.next()) {
                    Material mat = Material.matchMaterial(rs.getString("material"));
                    if (mat == null) continue;
                    list.add(new FarmerHistoryRecord(
                            rs.getLong("id"),
                            rs.getLong("farmer_id"),
                            FarmerHistoryRecord.Type.valueOf(rs.getString("type")),
                            mat,
                            rs.getInt("amount"),
                            rs.getDouble("earnings"),
                            rs.getLong("timestamp")
                    ));
                }
                return list;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[WesFarmer] Geçmiş okunamadı: farmer " + farmerId, e);
            return Collections.emptyList();
        }
    }

    /** Sayfalı geçmiş sorgusu. */
    public List<FarmerHistoryRecord> findPage(long farmerId, int page, int pageSize) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_PAGE)) {
            ps.setLong(1, farmerId);
            ps.setInt (2, pageSize);
            ps.setInt (3, page * pageSize);
            try (ResultSet rs = ps.executeQuery()) {
                List<FarmerHistoryRecord> list = new java.util.ArrayList<>();
                while (rs.next()) {
                    Material mat = Material.matchMaterial(rs.getString("material"));
                    if (mat == null) continue;
                    list.add(new FarmerHistoryRecord(
                            rs.getLong("id"),
                            rs.getLong("farmer_id"),
                            FarmerHistoryRecord.Type.valueOf(rs.getString("type")),
                            mat,
                            rs.getInt("amount"),
                            rs.getDouble("earnings"),
                            rs.getLong("timestamp")));
                }
                return list;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[WesFarmer] Sayfalı geçmiş okunamadı: farmer " + farmerId, e);
            return Collections.emptyList();
        }
    }

    /** Toplam kayıt sayısı. */
    public int countByFarmer(long farmerId) {
        try (PreparedStatement ps = connection.prepareStatement(COUNT_BY_FARMER)) {
            ps.setLong(1, farmerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[WesFarmer] Geçmiş sayısı alınamadı: farmer " + farmerId, e);
            return 0;
        }
    }

    public void deleteByFarmer(long farmerId) {
        try (PreparedStatement ps = connection.prepareStatement(DELETE_BY_FARMER)) {
            ps.setLong(1, farmerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[WesFarmer] Geçmiş silinemedi: farmer " + farmerId, e);
        }
    }

    // ── Aggregate helpers ─────────────────────────────────────────────────────

    /** Toplam kazanç (tüm satışlar). */
    public double totalEarnings(long farmerId) {
        return queryDouble(TOTAL_EARNINGS, farmerId);
    }

    /** Toplam satılan ürün adedi. */
    public long totalSoldAmount(long farmerId) {
        return queryLong(TOTAL_SOLD_AMOUNT, farmerId);
    }

    /** Toplam toplanan ürün adedi. */
    public long totalCollectedAmount(long farmerId) {
        return queryLong(TOTAL_COLLECTED_AMOUNT, farmerId);
    }

    /** Satış işlem sayısı. */
    public long totalSellTransactions(long farmerId) {
        return queryLong(TOTAL_SELL_TX, farmerId);
    }

    /** En çok satılan materyal adı, yoksa "—". */
    public String topSoldMaterial(long farmerId) {
        return queryTopMaterial(TOP_SOLD_MATERIAL, farmerId);
    }

    /** En çok toplanan materyal adı, yoksa "—". */
    public String topCollectedMaterial(long farmerId) {
        return queryTopMaterial(TOP_COLLECTED_MATERIAL, farmerId);
    }

    /** Son işlem unix timestamp'i (saniye), 0 = hiç kayıt yok. */
    public long lastActivityTimestamp(long farmerId) {
        return queryLong(LAST_TIMESTAMP, farmerId);
    }

    // ── Internal query helpers ────────────────────────────────────────────────

    private double queryDouble(String sql, long farmerId) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, farmerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[WesFarmer] Stat sorgusu başarısız.", e);
            return 0.0;
        }
    }

    private long queryLong(String sql, long farmerId) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, farmerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[WesFarmer] Stat sorgusu başarısız.", e);
            return 0L;
        }
    }

    private String queryTopMaterial(String sql, long farmerId) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, farmerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("material") : "—";
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[WesFarmer] Top material sorgusu başarısız.", e);
            return "—";
        }
    }
}
