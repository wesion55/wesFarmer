package xyz.geik.wesfarmer.storage;

import org.bukkit.plugin.java.JavaPlugin;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * SQLite-backed repository for {@link FarmerRecord}.
 *
 * <p>All operations are synchronous and intended to be called from the main
 * server thread (or an async task when explicitly noted). The connection is
 * kept open for the lifetime of the plugin; {@link #close()} must be called
 * in {@code onDisable}.
 *
 * <p>Every method degrades to a safe no-op/default (instead of throwing a
 * {@link NullPointerException}) when {@link #init()} failed to open the
 * connection — check {@link #isAvailable()} at startup to decide whether the
 * plugin should keep running at all.
 *
 * <p>Schema (farmers table):
 * <pre>
 *  id         INTEGER PRIMARY KEY AUTOINCREMENT
 *  owner_uuid TEXT    NOT NULL
 *  claim_key  TEXT    NOT NULL     -- "gp:&lt;id&gt;" / "wg:&lt;region ids&gt;" / "none"
 *  npc_name   TEXT    NOT NULL UNIQUE
 *  holo_name  TEXT
 *  world      TEXT    NOT NULL
 *  x          REAL    NOT NULL
 *  y          REAL    NOT NULL
 *  z          REAL    NOT NULL
 * </pre>
 * A partial unique index on {@code claim_key} (excluding the {@code "none"}
 * sentinel) enforces the one-farmer-per-claim rule at the database level, so
 * two concurrent purchases in the same claim can never both succeed even if
 * they both pass the in-memory pre-check (see {@link #insert}).
 */
public final class FarmerRepository {

    private static final String NONE_CLAIM_KEY = FarmerRecord.NO_CLAIM;

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS farmers (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                owner_uuid TEXT    NOT NULL,
                claim_key  TEXT    NOT NULL DEFAULT 'none',
                npc_name   TEXT    NOT NULL UNIQUE,
                holo_name  TEXT,
                world      TEXT    NOT NULL,
                x          REAL    NOT NULL,
                y          REAL    NOT NULL,
                z          REAL    NOT NULL
            );
            """;

    private static final String CREATE_UNIQUE_CLAIM_INDEX = """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_farmers_claim_key_unique
            ON farmers(claim_key) WHERE claim_key <> 'none';
            """;

    private static final String INSERT = """
            INSERT INTO farmers (owner_uuid, claim_key, npc_name, holo_name, world, x, y, z)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?);
            """;

    private static final String DELETE_BY_NPC = "DELETE FROM farmers WHERE npc_name = ?;";

    private static final String DELETE_ALL_BY_OWNER = "DELETE FROM farmers WHERE owner_uuid = ?;";

    private static final String UPDATE_LOCATION = """
            UPDATE farmers SET world = ?, x = ?, y = ?, z = ?, claim_key = ?
            WHERE npc_name = ?;
            """;

    private static final String SELECT_BY_NPC = """
            SELECT id, owner_uuid, claim_key, npc_name, holo_name, world, x, y, z
            FROM farmers WHERE npc_name = ? LIMIT 1;
            """;

    private static final String SELECT_BY_ID = """
            SELECT id, owner_uuid, claim_key, npc_name, holo_name, world, x, y, z
            FROM farmers WHERE id = ? LIMIT 1;
            """;

    private static final String SELECT_ALL = """
            SELECT id, owner_uuid, claim_key, npc_name, holo_name, world, x, y, z
            FROM farmers;
            """;

    private static final String SELECT_BY_OWNER = """
            SELECT id, owner_uuid, claim_key, npc_name, holo_name, world, x, y, z
            FROM farmers WHERE owner_uuid = ?;
            """;

    private static final String EXISTS_CLAIM = "SELECT 1 FROM farmers WHERE claim_key = ? LIMIT 1;";

    private static final String COUNT_BY_OWNER = "SELECT COUNT(*) FROM farmers WHERE owner_uuid = ?;";

    private final JavaPlugin plugin;
    private Connection connection;

    public FarmerRepository(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Opens (or creates) the SQLite database and initialises the schema.
     * Must be called before any other method.
     *
     * @return {@code true} if the connection and schema are ready, {@code
     *         false} if initialisation failed. Callers MUST check this —
     *         every other method degrades safely on failure, but a plugin
     *         that thinks it has persistent storage when it doesn't will
     *         silently lose every farmer on the next restart.
     */
    public boolean init() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "farmers.db");
            plugin.getDataFolder().mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(CREATE_TABLE);
            }
            migrateLegacyClaimIdColumn();
            deduplicateClaimKeysBeforeIndexing();
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(CREATE_UNIQUE_CLAIM_INDEX);
            }
            plugin.getLogger().info("[WesFarmer] SQLite veritabani hazir: " + dbFile.getName());
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[WesFarmer] SQLite baglantisi kurulamadi!", e);
            connection = null;
            return false;
        }
    }

    /**
     * @return {@code true} once {@link #init()} has succeeded. Every other
     *         method already checks this internally, but callers can use it
     *         to fail fast (e.g. disabling the plugin) instead of limping
     *         along with a repository that can't persist anything.
     */
    public boolean isAvailable() {
        return connection != null;
    }

    /** Paylaşılan JDBC bağlantısını döndürür. FarmerStockRepository aynı DB dosyasını kullanır. */
    public Connection getConnection() {
        return connection;
    }

    /** Closes the database connection gracefully. Call from {@code onDisable}. */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[WesFarmer] DB baglantisi kapatilirken hata!", e);
        }
    }

    // -------------------------------------------------------------------------
    // One-time migration helpers (run inside init(), before the plugin ever
    // touches connection == null)
    // -------------------------------------------------------------------------

    /**
     * v1.0.0 stored a GriefPrevention-only {@code claim_id INTEGER} column
     * ({@code -1} meaning "no claim"). This generalises it to a provider-
     * agnostic {@code claim_key TEXT} column ("gp:&lt;id&gt;" / "wg:&lt;ids&gt;" /
     * "none") so WorldGuard-only servers get working claim enforcement too.
     * Safe to run on every startup: it's a no-op once the column has already
     * been renamed.
     */
    private void migrateLegacyClaimIdColumn() throws SQLException {
        if (!columnExists("claim_id") || columnExists("claim_key")) return;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("ALTER TABLE farmers RENAME COLUMN claim_id TO claim_key;");
            stmt.execute("UPDATE farmers SET claim_key = 'none' WHERE claim_key = '-1';");
            stmt.execute("UPDATE farmers SET claim_key = 'gp:' || claim_key "
                    + "WHERE claim_key <> 'none' AND claim_key NOT LIKE 'gp:%' AND claim_key NOT LIKE 'wg:%';");
        }
        plugin.getLogger().info("[WesFarmer] Veritabani semasi guncellendi (claim_id -> claim_key).");
    }

    /**
     * Removes any pre-existing duplicate {@code claim_key} rows before the
     * unique index is created (upgrades from a build that predates SEC-1
     * could already have double-bought farmers on disk; creating the index
     * against dirty data would otherwise fail outright). Keeps the oldest
     * row (lowest id) per claim.
     */
    private void deduplicateClaimKeysBeforeIndexing() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            int removed = stmt.executeUpdate("""
                    DELETE FROM farmers
                    WHERE claim_key <> 'none'
                      AND id NOT IN (
                          SELECT MIN(id) FROM farmers WHERE claim_key <> 'none' GROUP BY claim_key
                      );
                    """);
            if (removed > 0) {
                plugin.getLogger().warning("[WesFarmer] " + removed + " adet mukerrer (ayni claim'e ait) "
                        + "farmer kaydi veritabanindan temizlendi. Bu kayitlarin NPC/hologram varliklari "
                        + "FancyNpcs/FancyHolograms tarafinda hala mevcut olabilir, manuel kontrol onerilir.");
            }
        }
    }

    private boolean columnExists(String column) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, "farmers", column)) {
            return rs.next();
        }
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Inserts a new farmer record and returns it with the generated id filled
     * in, or {@code null} on failure.
     *
     * @throws ClaimAlreadyClaimedException if another farmer was inserted for
     *         the same claim key between the caller's pre-check and this
     *         call (the DB-level unique index is the actual source of truth
     *         for the one-farmer-per-claim rule; the earlier check is just
     *         an optimistic fast path).
     */
    public FarmerRecord insert(FarmerRecord record) {
        if (!isAvailable()) {
            plugin.getLogger().severe("[WesFarmer] Farmer kaydedilemedi (DB baglantisi yok): " + record.npcName());
            return null;
        }
        try (PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, record.ownerUuid().toString());
            ps.setString(2, record.claimKey());
            ps.setString(3, record.npcName());
            ps.setString(4, record.holoName());
            ps.setString(5, record.world());
            ps.setDouble(6, record.x());
            ps.setDouble(7, record.y());
            ps.setDouble(8, record.z());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    return new FarmerRecord(id, record.ownerUuid(), record.claimKey(),
                            record.npcName(), record.holoName(),
                            record.world(), record.x(), record.y(), record.z());
                }
            }
        } catch (SQLException e) {
            if (isUniqueClaimViolation(e)) {
                throw new ClaimAlreadyClaimedException(record.claimKey(), e);
            }
            plugin.getLogger().log(Level.SEVERE, "[WesFarmer] Farmer kaydedilemedi: " + record.npcName(), e);
        }
        return null;
    }

    private boolean isUniqueClaimViolation(SQLException e) {
        if (e instanceof SQLiteException sqliteException) {
            return sqliteException.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE;
        }
        // Fallback for when the driver doesn't surface a typed SQLiteException
        // (e.g. wrapped by a pool): SQLite's generic constraint state is 23000.
        return "23000".equals(e.getSQLState());
    }

    /** Thrown by {@link #insert} when the claim's unique slot was already taken. */
    public static final class ClaimAlreadyClaimedException extends RuntimeException {
        private final String claimKey;

        public ClaimAlreadyClaimedException(String claimKey, Throwable cause) {
            super("Claim already has a farmer: " + claimKey, cause);
            this.claimKey = claimKey;
        }

        public String claimKey() {
            return claimKey;
        }
    }

    /** Removes the farmer row identified by the NPC's unique name. */
    public void deleteByNpcName(String npcName) {
        if (!isAvailable()) return;
        try (PreparedStatement ps = connection.prepareStatement(DELETE_BY_NPC)) {
            ps.setString(1, npcName);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[WesFarmer] Farmer silinemedi: " + npcName, e);
        }
    }

    /** Removes ALL farmer rows owned by the given player. Returns the deleted count. */
    public int deleteAllByOwner(UUID ownerUuid) {
        if (!isAvailable()) return 0;
        try (PreparedStatement ps = connection.prepareStatement(DELETE_ALL_BY_OWNER)) {
            ps.setString(1, ownerUuid.toString());
            return ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[WesFarmer] Oyuncu farmerlari silinemedi: " + ownerUuid, e);
            return 0;
        }
    }

    /**
     * Updates the stored location and claim key for the given NPC.
     * Used by the /çiftçi move command.
     *
     * @return {@code true} if a row was updated
     */
    public boolean updateLocation(String npcName, String world, double x, double y, double z,
                                   String claimKey) {
        if (!isAvailable()) return false;
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_LOCATION)) {
            ps.setString(1, world);
            ps.setDouble(2, x);
            ps.setDouble(3, y);
            ps.setDouble(4, z);
            ps.setString(5, claimKey == null ? FarmerRecord.NO_CLAIM : claimKey);
            ps.setString(6, npcName);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[WesFarmer] Farmer konumu guncellenemedi: " + npcName, e);
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /** Returns every farmer record in the database. */
    public List<FarmerRecord> findAll() {
        if (!isAvailable()) return Collections.emptyList();
        try (PreparedStatement ps = connection.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            return mapRows(rs);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[WesFarmer] Farmer listesi alinamadi!", e);
            return Collections.emptyList();
        }
    }

    /** Returns all farmers owned by the given player. */
    public List<FarmerRecord> findByOwner(UUID ownerUuid) {
        if (!isAvailable()) return Collections.emptyList();
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_OWNER)) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return mapRows(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[WesFarmer] Oyuncu farmerlari alinamadi: " + ownerUuid, e);
            return Collections.emptyList();
        }
    }

    /** Returns the farmer record identified by NPC name, or {@code null} if not found. */
    public FarmerRecord findByNpcName(String npcName) {
        if (!isAvailable()) return null;
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_NPC)) {
            ps.setString(1, npcName);
            try (ResultSet rs = ps.executeQuery()) {
                List<FarmerRecord> rows = mapRows(rs);
                return rows.isEmpty() ? null : rows.get(0);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[wesFarmer] NPC ismiyle farmer alınamadı: " + npcName, e);
            return null;
        }
    }

    /** Returns the farmer record by its primary key, or {@code null} if not found. */
    public FarmerRecord findById(long id) {
        if (!isAvailable()) return null;
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                List<FarmerRecord> rows = mapRows(rs);
                return rows.isEmpty() ? null : rows.get(0);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[wesFarmer] ID ile farmer alınamadı: " + id, e);
            return null;
        }
    }

    /**
     * Returns {@code true} when the given claim already contains a farmer.
     * Used to enforce the one-farmer-per-claim rule. This is only an
     * optimistic fast path — {@link #insert} still throws
     * {@link ClaimAlreadyClaimedException} if a race slips past it.
     */
    public boolean claimHasFarmer(String claimKey) {
        if (!isAvailable() || NONE_CLAIM_KEY.equals(claimKey)) return false;
        try (PreparedStatement ps = connection.prepareStatement(EXISTS_CLAIM)) {
            ps.setString(1, claimKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[WesFarmer] Claim sorgusu basarisiz: " + claimKey, e);
            return false;
        }
    }

    /** Returns the number of farmers owned by the given player. */
    public int countByOwner(UUID ownerUuid) {
        if (!isAvailable()) return 0;
        try (PreparedStatement ps = connection.prepareStatement(COUNT_BY_OWNER)) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[WesFarmer] Farmer sayisi alinamadi: " + ownerUuid, e);
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<FarmerRecord> mapRows(ResultSet rs) throws SQLException {
        List<FarmerRecord> list = new ArrayList<>();
        while (rs.next()) {
            list.add(new FarmerRecord(
                    rs.getLong("id"),
                    UUID.fromString(rs.getString("owner_uuid")),
                    rs.getString("claim_key"),
                    rs.getString("npc_name"),
                    rs.getString("holo_name"),
                    rs.getString("world"),
                    rs.getDouble("x"),
                    rs.getDouble("y"),
                    rs.getDouble("z")
            ));
        }
        return list;
    }
}
