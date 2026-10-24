package xyz.geik.wesfarmer.product;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

/**
 * {@code products.yml} dosyasını yükler ve {@link FarmerProduct} kayıtlarını
 * önbelleğe alır.
 *
 * <p><b>Önemli:</b> {@code FarmerProduct.collectEnabled()} ve
 * {@code FarmerProduct.sellEnabled()} burada artık yalnızca sunucu geneli
 * <em>varsayılan</em> değerleri temsil eder. Farmer başına gerçek açık/kapalı
 * durumu {@link xyz.geik.wesfarmer.storage.FarmerProductStateRepository}'de
 * saklanır. Her toggle işlemi o repository üzerinden yapılmalıdır.
 */
public final class ProductManager {

    private final JavaPlugin plugin;

    private final Map<String, FarmerProduct> products = new LinkedHashMap<>();
    private int defaultTax = 10;

    public ProductManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void load() {
        products.clear();

        File file = new File(plugin.getDataFolder(), "products.yml");
        if (!file.exists()) plugin.saveResource("products.yml", false);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        defaultTax = config.getInt("settings.default-tax", 10);

        ConfigurationSection section = config.getConfigurationSection("products");
        if (section == null) {
            plugin.getLogger().warning("[wesFarmer] products.yml içinde 'products' bölümü bulunamadı.");
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) continue;

            Material material = Material.matchMaterial(entry.getString("material", key));
            if (material == null) {
                plugin.getLogger().warning("[wesFarmer] Geçersiz materyal '" + key + "', ürün atlandı.");
                continue;
            }

            String  displayName  = entry.getString("display-name", key);
            double  price        = entry.getDouble("price", 0.0);
            int     tax          = entry.getInt("tax", defaultTax);
            int     maxStock     = entry.getInt("max-stock", 0);
            // products.yml'deki collect/sell = sunucu geneli varsayılan
            boolean sellDefault    = entry.getBoolean("sell", true);
            boolean collectDefault = entry.getBoolean("collect", true);

            products.put(key.toUpperCase(), new FarmerProduct(
                    key.toUpperCase(), displayName, material,
                    price, tax, maxStock, sellDefault, collectDefault));
        }

        plugin.getLogger().info("[wesFarmer] " + products.size() + " ürün yüklendi.");
    }

    public void reload() { load(); }

    // ── Queries ───────────────────────────────────────────────────────────────

    public Collection<FarmerProduct> getAll() {
        return Collections.unmodifiableCollection(products.values());
    }

    public Optional<FarmerProduct> findByMaterial(Material material) {
        return products.values().stream()
                .filter(p -> p.material() == material)
                .findFirst();
    }

    public Optional<FarmerProduct> findByKey(String key) {
        return Optional.ofNullable(products.get(key.toUpperCase()));
    }

    public int getDefaultTax() { return defaultTax; }
}
