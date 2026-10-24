package xyz.geik.wesfarmer.storage;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Çiftçi başına ürün stoğunu SQLite'da yönetir.
 *
 * <p>Schema (farmer_stock tablosu):
 * <pre>
 *  farmer_id  INTEGER NOT NULL  → farmers.id FK
 *  material   TEXT    NOT NULL  → Material.name()
 *  amount     INTEGER NOT NULL DEFAULT 0
 *  PRIMARY KEY (farmer_id, material)
 * </pre>
 */
public final class FarmerStockRepository {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS farmer_stock (
                farmer_id INTEGER NOT NULL,
                material  TEXT    NOT NULL,
                amount    INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (farmer_id, material)
            );
            """;

    private static final String UPSERT = """
            INSERT INTO farmer_stock (farmer_id, material, amount)
            VALUES (?, ?, ?)
            ON CONFLICT(farmer_id, material) DO UPDATE SET amount = excluded.amount;
            """;

    private static final String ADD_AMOUNT = """
            INSERT INTO farmer_stock (farmer_id, material, amount)
            VALUES (?, ?, ?)
            ON CONFLICT(farmer_id, material) DO UPDATE SET amount = amount + excluded.amount;
            """;

    private static final String SELECT_BY_FARMER = """
            SELECT material, amount FROM farmer_stock WHERE farmer_id = ?;
            """;

    private static final String SELECT_ONE = """
            SELECT amount FROM farmer_stock WHERE farmer_id = ? AND material = ? LIMIT 1;
            """;

    private static final String DELETE_BY_FARMER = """
            DELETE FROM farmer_stock WHERE farmer_id = ?;
            """;

    private static final String DELETE_ONE = """
            DELETE FROM farmer_stock WHERE farmer_id = ? AND material = ?;
            """;

    private final JavaPlugin plugin;
    private final Connection connection;

    public FarmerStockRepository(JavaPlugin plugin, Connection connection) {
        this.plugin     = plugin;
        this.connection = connection;
    }

    // -------------------------------------------------------------------------
    // Schema
    // -------------------------------------------------------------------------

    public void init() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(CREATE_TABLE);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[WesFarmer] farmer_stock tablosu oluşturulamadı!", e);
        }
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /** Stok miktarını tam olarak set eder. */
    public void setStock(long farmerId, Material material, int amount) {
        try (PreparedStatement ps = connection.prepareStatement(UPSERT)) {
            ps.setLong(1, farmerId);
            ps.setString(2, material.name());
            ps.setInt(3, Math.max(0, amount));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[WesFarmer] Stok güncellenemedi: " + material, e);
        }
    }

    /**
     * Mevcut stoğa {@code delta} kadar ekler. Negatif delta çıkarır;
     * stok hiçbir zaman 0'ın altına düşmez.
     */
    public void addStock(long farmerId, Material material, int delta) {
        if (delta == 0) return;
        if (delta > 0) {
            try (PreparedStatement ps = connection.prepareStatement(ADD_AMOUNT)) {
                ps.setLong(1, farmerId);
                ps.setString(2, material.name());
                ps.setInt(3, delta);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "[WesFarmer] Stok eklenemedi: " + material, e);
            }
        } else {
            // Çıkarma: önce mevcut değeri oku, sonra max(0, mevcut+delta) yaz
            int current = getStock(farmerId, material);
            setStock(farmerId, material, Math.max(0, current + delta));
        }
    }

    /** Çiftçinin tüm stoğunu sıfırlar. */
    public void clearStock(long farmerId) {
        try (PreparedStatement ps = connection.prepareStatement(DELETE_BY_FARMER)) {
            ps.setLong(1, farmerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[WesFarmer] Stok temizlenemedi: farmer " + farmerId, e);
        }
    }

    /** Belirli bir ürünün stoğunu sıfırlar. */
    public void clearStock(long farmerId, Material material) {
        try (PreparedStatement ps = connection.prepareStatement(DELETE_ONE)) {
            ps.setLong(1, farmerId);
            ps.setString(2, material.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[WesFarmer] Stok temizlenemedi: " + material, e);
        }
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /** Bir ürünün stok miktarını döndürür (kayıt yoksa 0). */
    public int getStock(long farmerId, Material material) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_ONE)) {
            ps.setLong(1, farmerId);
            ps.setString(2, material.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[WesFarmer] Stok okunamadı: " + material, e);
            return 0;
        }
    }

    /** Bir çiftçinin tüm stok kayıtlarını döndürür. */
    public List<FarmerStock> getAllStock(long farmerId) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_FARMER)) {
            ps.setLong(1, farmerId);
            try (ResultSet rs = ps.executeQuery()) {
                List<FarmerStock> list = new ArrayList<>();
                while (rs.next()) {
                    Material mat = Material.matchMaterial(rs.getString("material"));
                    if (mat == null) continue;
                    list.add(new FarmerStock(farmerId, mat, rs.getInt("amount")));
                }
                return list;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[WesFarmer] Stok listesi okunamadı: farmer " + farmerId, e);
            return Collections.emptyList();
        }
    }
}
