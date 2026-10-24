package xyz.geik.wesfarmer.storage;

import org.bukkit.plugin.java.JavaPlugin;
import xyz.geik.wesfarmer.config.ConfigManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * SQLite-backed repository for {@link FarmerMember}.
 *
 * <p>Write-through cache: {@code farmerId → sorted member list}.
 * {@link #hasAccess}, {@link #hasPermission}, {@link #getRole},
 * {@link #countMembers} hepsi RAM'den karşılanır.
 */
public final class FarmerMemberRepository {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS farmer_members (
                farmer_id  INTEGER NOT NULL,
                uuid       TEXT    NOT NULL,
                name       TEXT    NOT NULL,
                role       TEXT    NOT NULL DEFAULT 'MEMBER',
                PRIMARY KEY (farmer_id, uuid)
            );
            """;

    private static final String CREATE_IDX_FARMER =
            "CREATE INDEX IF NOT EXISTS idx_fm_farmer ON farmer_members(farmer_id);";

    private static final String CREATE_IDX_UUID =
            "CREATE INDEX IF NOT EXISTS idx_fm_uuid ON farmer_members(uuid);";

    private static final String UPSERT = """
            INSERT INTO farmer_members (farmer_id, uuid, name, role)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(farmer_id, uuid) DO UPDATE SET name = excluded.name, role = excluded.role;
            """;

    private static final String DELETE =
            "DELETE FROM farmer_members WHERE farmer_id = ? AND uuid = ?;";

    private static final String DELETE_ALL_BY_FARMER =
            "DELETE FROM farmer_members WHERE farmer_id = ?;";

    private static final String SELECT_BY_FARMER =
            "SELECT farmer_id, uuid, name, role FROM farmer_members WHERE farmer_id = ?;";

    private static final String SELECT_ONE =
            "SELECT farmer_id, uuid, name, role FROM farmer_members WHERE farmer_id = ? AND uuid = ? LIMIT 1;";

    private static final String SELECT_BY_UUID =
            "SELECT farmer_id, uuid, name, role FROM farmer_members WHERE uuid = ?;";

    private static final String COUNT_BY_FARMER =
            "SELECT COUNT(*) FROM farmer_members WHERE farmer_id = ?;";

    private static final String UPDATE_ROLE =
            "UPDATE farmer_members SET role = ? WHERE farmer_id = ? AND uuid = ?;";

    private static final String UPDATE_NAME =
            "UPDATE farmer_members SET name = ? WHERE uuid = ?;";

    private final JavaPlugin plugin;
    private final Connection connection;

    /** farmerId → sorted member list (write-through cache) */
    private final ConcurrentHashMap<Long, List<FarmerMember>> memberCache
            = new ConcurrentHashMap<>();

    public FarmerMemberRepository(JavaPlugin plugin, Connection connection) {
        this.plugin     = plugin;
        this.connection = connection;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void init() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(CREATE_TABLE);
            stmt.execute(CREATE_IDX_FARMER);
            stmt.execute(CREATE_IDX_UUID);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[wesFarmer] farmer_members tablosu oluşturulamadı!", e);
        }
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /** Üyeyi kaydeder/günceller ve farmer cache'ini geçersiz kılar. */
    public void save(FarmerMember member) {
        try (PreparedStatement ps = connection.prepareStatement(UPSERT)) {
            ps.setLong  (1, member.farmerId());
            ps.setString(2, member.uuid().toString());
            ps.setString(3, member.name());
            ps.setString(4, member.role().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[wesFarmer] Üye kaydedilemedi: " + member.uuid(), e);
        }
        memberCache.remove(member.farmerId());
    }

    /** Rolü günceller ve cache'i geçersiz kılar. */
    public void updateRole(long farmerId, UUID uuid, FarmerRole role) {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_ROLE)) {
            ps.setString(1, role.name());
            ps.setLong  (2, farmerId);
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[wesFarmer] Üye rolü güncellenemedi: " + uuid, e);
        }
        memberCache.remove(farmerId);
    }

    /**
     * Tüm farmerlardaki bu UUID'nin adını günceller.
     * Bu UUID'nin bulunduğu tüm farmer cache'lerini temizler.
     */
    public void updateName(UUID uuid, String name) {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_NAME)) {
            ps.setString(1, name);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "[wesFarmer] Üye adı güncellenemedi: " + uuid, e);
        }
        // Bu UUID'yi içeren tüm cache girişlerini temizle
        String uuidStr = uuid.toString();
        memberCache.entrySet().removeIf(entry ->
                entry.getValue().stream()
                     .anyMatch(m -> uuidStr.equals(m.uuid().toString())));
    }

    /** Bir üyeyi kaldırır ve farmer cache'ini geçersiz kılar. */
    public void remove(long farmerId, UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(DELETE)) {
            ps.setLong  (1, farmerId);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[wesFarmer] Üye silinemedi: " + uuid, e);
        }
        memberCache.remove(farmerId);
    }

    /** Farmer'ın tüm üyelerini siler ve cache'i temizler. */
    public void removeAllByFarmer(long farmerId) {
        memberCache.remove(farmerId);
        try (PreparedStatement ps = connection.prepareStatement(DELETE_ALL_BY_FARMER)) {
            ps.setLong(1, farmerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[wesFarmer] Farmer üyeleri silinemedi: farmerId=" + farmerId, e);
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Farmer üyelerini döndürür. Cache hit → RAM, miss → DB.
     * Sonuç owner-first sıralanmıştır.
     */
    public List<FarmerMember> findByFarmer(long farmerId) {
        List<FarmerMember> cached = memberCache.get(farmerId);
        if (cached != null) return cached;

        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_FARMER)) {
            ps.setLong(1, farmerId);
            try (ResultSet rs = ps.executeQuery()) {
                List<FarmerMember> list = mapRows(rs);
                memberCache.put(farmerId, list);
                return list;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[wesFarmer] Farmer üyeleri alınamadı: farmerId=" + farmerId, e);
            return Collections.emptyList();
        }
    }

    /** Bir oyuncunun tüm farmer üyeliklerini döndürür (DB sorgusu, cache'lenmez). */
    public List<FarmerMember> findByUuid(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_UUID)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return mapRows(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "[wesFarmer] Oyuncu üyelikleri alınamadı: " + uuid, e);
            return Collections.emptyList();
        }
    }

    /** Belirli farmer+uuid üyeliğini döndürür — önce cache'ten. */
    public Optional<FarmerMember> find(long farmerId, UUID uuid) {
        List<FarmerMember> members = findByFarmer(farmerId);
        String uuidStr = uuid.toString();
        return members.stream()
                      .filter(m -> uuidStr.equals(m.uuid().toString()))
                      .findFirst();
    }

    /** Oyuncunun bu farmer'daki rolünü döndürür; üye değilse null. */
    public FarmerRole getRole(long farmerId, UUID uuid) {
        return find(farmerId, uuid).map(FarmerMember::role).orElse(null);
    }

    /** Oyuncunun en az {@code required} rolüne sahip olup olmadığını döndürür. */
    public boolean hasAccess(long farmerId, UUID uuid, FarmerRole required) {
        FarmerRole role = getRole(farmerId, uuid);
        return role != null && role.isAtLeast(required);
    }

    /** Oyuncunun belirli izne sahip olup olmadığını döndürür. OWNER her zaman geçer. */
    public boolean hasPermission(long farmerId, UUID uuid,
                                  FarmerPermission perm,
                                  ConfigManager.Settings settings) {
        FarmerRole role = getRole(farmerId, uuid);
        if (role == null) return false;
        if (role == FarmerRole.OWNER) return true;
        return role.isAtLeast(settings.requiredRole(perm));
    }

    /** Farmer üye sayısını döndürür (owner dahil) — cache'ten. */
    public int countMembers(long farmerId) {
        return findByFarmer(farmerId).size();
    }

    /** Plugin disable'da cache'i temizler. */
    public void clearCache() {
        memberCache.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<FarmerMember> mapRows(ResultSet rs) throws SQLException {
        List<FarmerMember> list = new ArrayList<>();
        while (rs.next()) {
            FarmerRole role;
            try {
                role = FarmerRole.valueOf(rs.getString("role"));
            } catch (IllegalArgumentException ignored) {
                role = FarmerRole.MEMBER;
            }
            list.add(new FarmerMember(
                    rs.getLong  ("farmer_id"),
                    UUID.fromString(rs.getString("uuid")),
                    rs.getString("name"),
                    role
            ));
        }
        list.sort((a, b) -> b.role().ordinal() - a.role().ordinal());
        return list;
    }
}
