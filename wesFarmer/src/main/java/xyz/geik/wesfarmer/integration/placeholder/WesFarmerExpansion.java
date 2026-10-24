package xyz.geik.wesfarmer.integration.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.geik.wesfarmer.WesFarmer;
import xyz.geik.wesfarmer.storage.FarmerHistoryRepository;
import xyz.geik.wesfarmer.storage.FarmerProductLevelRepository;
import xyz.geik.wesfarmer.storage.FarmerRecord;
import xyz.geik.wesfarmer.storage.FarmerRepository;
import xyz.geik.wesfarmer.storage.FarmerStockRepository;

import java.util.List;

/**
 * PlaceholderAPI expansion for WesFarmer.
 *
 * <table>
 *   <tr><th>Placeholder</th><th>Açıklama</th></tr>
 *   <tr><td>%wesfarmer_count%</td><td>Oyuncunun sahip olduğu toplam çiftçi sayısı</td></tr>
 *   <tr><td>%wesfarmer_limit%</td><td>Config'deki max-farmers-per-player (0 = ∞)</td></tr>
 *   <tr><td>%wesfarmer_earnings%</td><td>Tüm çiftçilerden toplam kazanç</td></tr>
 *   <tr><td>%wesfarmer_stock_MATERIAL%</td><td>Belirli materyalin toplam stoğu (örn. WHEAT)</td></tr>
 *   <tr><td>%wesfarmer_level_MATERIAL%</td><td>Belirli materyalin seviyesi (ilk çiftçi)</td></tr>
 *   <tr><td>%wesfarmer_sell_count%</td><td>Toplam satış işlem sayısı</td></tr>
 *   <tr><td>%wesfarmer_collect_total%</td><td>Toplam toplanan ürün adedi</td></tr>
 * </table>
 */
public final class WesFarmerExpansion extends PlaceholderExpansion {

    private final WesFarmer              plugin;
    private final FarmerRepository       farmerRepository;
    private final FarmerStockRepository  stockRepository;
    private final FarmerHistoryRepository historyRepository;
    private final FarmerProductLevelRepository levelRepository;

    public WesFarmerExpansion(WesFarmer plugin,
                               FarmerRepository farmerRepository,
                               FarmerStockRepository stockRepository,
                               FarmerHistoryRepository historyRepository,
                               FarmerProductLevelRepository levelRepository) {
        this.plugin            = plugin;
        this.farmerRepository  = farmerRepository;
        this.stockRepository   = stockRepository;
        this.historyRepository = historyRepository;
        this.levelRepository   = levelRepository;
    }

    @Override public @NotNull String getIdentifier() { return "wesfarmer"; }

    @Override public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().isEmpty()
                ? "geik"
                : plugin.getDescription().getAuthors().get(0);
    }

    @Override public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        String lower = params.toLowerCase();

        // ── Basit sayaçlar ────────────────────────────────────────────────────
        if (lower.equals("count")) {
            return String.valueOf(farmerRepository.countByOwner(player.getUniqueId()));
        }

        if (lower.equals("limit")) {
            int limit = plugin.getConfigManager().getSettings().maxFarmersPerPlayer();
            return limit == 0 ? "∞" : String.valueOf(limit);
        }

        // ── Tarihsel istatistikler (tüm çiftçiler toplamı) ───────────────────
        if (lower.equals("earnings")) {
            List<FarmerRecord> farmers = farmerRepository.findByOwner(player.getUniqueId());
            double total = farmers.stream()
                    .mapToDouble(f -> historyRepository.totalEarnings(f.id()))
                    .sum();
            return String.format("%.2f", total);
        }

        if (lower.equals("sell_count")) {
            List<FarmerRecord> farmers = farmerRepository.findByOwner(player.getUniqueId());
            long total = farmers.stream()
                    .mapToLong(f -> historyRepository.totalSellTransactions(f.id()))
                    .sum();
            return String.valueOf(total);
        }

        if (lower.equals("collect_total")) {
            List<FarmerRecord> farmers = farmerRepository.findByOwner(player.getUniqueId());
            long total = farmers.stream()
                    .mapToLong(f -> historyRepository.totalCollectedAmount(f.id()))
                    .sum();
            return String.valueOf(total);
        }

        // ── Materyal bazlı: stock_<material> ─────────────────────────────────
        if (lower.startsWith("stock_")) {
            String matName = params.substring(6).toUpperCase();
            Material mat = Material.matchMaterial(matName);
            if (mat == null) return "?";
            List<FarmerRecord> farmers = farmerRepository.findByOwner(player.getUniqueId());
            int total = farmers.stream()
                    .mapToInt(f -> stockRepository.getStock(f.id(), mat))
                    .sum();
            return String.valueOf(total);
        }

        // ── Materyal bazlı: level_<material> — ilk çiftçiyi baz alır ─────────
        if (lower.startsWith("level_")) {
            String matName = params.substring(6).toUpperCase();
            Material mat = Material.matchMaterial(matName);
            if (mat == null) return "?";
            List<FarmerRecord> farmers = farmerRepository.findByOwner(player.getUniqueId());
            if (farmers.isEmpty()) return "0";
            // Tüm çiftçilerdeki en yüksek seviyeyi döndür
            int maxLevel = farmers.stream()
                    .mapToInt(f -> levelRepository.getLevel(f.id(), mat))
                    .max()
                    .orElse(0);
            return String.valueOf(maxLevel);
        }

        return null;
    }
}
