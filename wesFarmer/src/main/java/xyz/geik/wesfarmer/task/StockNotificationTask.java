package xyz.geik.wesfarmer.task;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import xyz.geik.wesfarmer.WesFarmer;
import xyz.geik.wesfarmer.product.FarmerProduct;
import xyz.geik.wesfarmer.storage.FarmerProductLevelRepository;
import xyz.geik.wesfarmer.storage.FarmerRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Stok dolu bildirim sistemi.
 *
 * <p>İki tetikleyici:
 * <ol>
 *   <li>Oyuncu oyuna girdiğinde — dolu stoklar için anında bildirim.</li>
 *   <li>Her 30 dakikada bir tekrar — oyuncu hâlâ çevrimiçiyse ve stok hâlâ doluysa.</li>
 * </ol>
 */
public final class StockNotificationTask implements Listener {

    /** 30 dakika = 20 tick/s × 60 s × 30 = 36 000 tick */
    private static final long REPEAT_INTERVAL_TICKS = 20L * 60 * 30;

    private final WesFarmer plugin;

    public StockNotificationTask(WesFarmer plugin) {
        this.plugin = plugin;
    }

    // ── Login bildirimi ───────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 2 saniye geciktir — dünya tamamen yüklensin
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> notifyPlayer(player), 40L);
    }

    // ── 30 dakikalık tekrar ───────────────────────────────────────────────────

    /** Periyodik scheduler'ı başlatır. */
    public void startRepeating() {
        // Ana thread'de oyuncu listesini al, DB işini notifyPlayer içindeki async'e bırak
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                notifyPlayer(player);
            }
        }, REPEAT_INTERVAL_TICKS, REPEAT_INTERVAL_TICKS);
    }

    // ── Bildirim gönderici ────────────────────────────────────────────────────

    private void notifyPlayer(Player player) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<FarmerRecord> farmers = plugin.getFarmerRepository()
                    .findByOwner(player.getUniqueId());
            if (farmers.isEmpty()) return;

            List<String> fullProducts = new ArrayList<>();

            for (FarmerRecord record : farmers) {
                for (FarmerProduct product : plugin.getProductManager().getAll()) {
                    int level    = plugin.getFarmerProductLevelRepository()
                            .getLevel(record.id(), product.material());
                    int maxStock = FarmerProductLevelRepository.maxStockAtLevel(
                            product.maxStock(), level);
                    if (maxStock <= 0) continue; // sınırsız stok — bildirim gönderme

                    int current = plugin.getFarmerStockRepository()
                            .getStock(record.id(), product.material());

                    if (current >= maxStock) {
                        fullProducts.add(product.displayName());
                    }
                }
            }

            if (fullProducts.isEmpty()) return;

            // Ana thread'de mesaj gönder
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                for (String name : fullProducts) {
                    plugin.getMessageService().send(player, "stock-full",
                            xyz.geik.wesfarmer.config.MessageService.Placeholder.of(
                                    "%item%", name));
                }
            });
        });
    }

    /** Listener + scheduler kaydeder. */
    public static StockNotificationTask start(WesFarmer plugin) {
        StockNotificationTask task = new StockNotificationTask(plugin);
        plugin.getServer().getPluginManager().registerEvents(task, plugin);
        task.startRepeating();
        return task;
    }
}
