package xyz.geik.wesfarmer.startup;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import xyz.geik.wesfarmer.WesFarmer;
import xyz.geik.wesfarmer.config.ConfigManager;
import xyz.geik.wesfarmer.integration.hologram.FancyHologramsHook;
import xyz.geik.wesfarmer.integration.npc.FancyNpcsHook;
import xyz.geik.wesfarmer.storage.FarmerRecord;
import xyz.geik.wesfarmer.storage.FarmerRepository;
import xyz.geik.wesfarmer.storage.FarmerSettings;
import xyz.geik.wesfarmer.storage.FarmerSettingsRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Sunucu başlangıcında çalışır; NPC ve hologramları geri yükler.
 * Her farmer için {@link FarmerSettings} okunur — gizlenmiş NPC/hologram
 * restart sonrası yeniden spawn edilmez.
 */
public final class FarmerRestoreService {

    private final WesFarmer plugin;
    private final FarmerRepository repository;
    private final ConfigManager configManager;
    private final FarmerSettingsRepository settingsRepository;

    public FarmerRestoreService(WesFarmer plugin,
                                 FarmerRepository repository,
                                 ConfigManager configManager,
                                 FarmerSettingsRepository settingsRepository) {
        this.plugin             = plugin;
        this.repository         = repository;
        this.configManager      = configManager;
        this.settingsRepository = settingsRepository;
    }

    public void runAsync() {
        plugin.getServer().getScheduler().runTaskLater(plugin, this::run, 20L);
    }

    private void run() {
        List<FarmerRecord> records = repository.findAll();
        if (records.isEmpty()) return;

        ConfigManager.Settings settings = configManager.getSettings();
        List<FarmerRecord> orphans = new ArrayList<>();

        int restored = 0;
        int skipped  = 0;

        for (FarmerRecord record : records) {
            Location loc = record.toLocation(plugin.getServer());

            if (loc == null) {
                plugin.getLogger().warning("[WesFarmer] Dünya bulunamadı (" + record.world()
                        + "), kayıt siliniyor: " + record.npcName());
                orphans.add(record);
                continue;
            }

            // Bu farmer'ın ayarlarını oku — gizlenmiş NPC/hologram spawn edilmez
            FarmerSettings fs = settingsRepository.get(record.id());

            boolean npcRestored  = restoreNpc(record, loc, settings, fs);
            boolean holoRestored = restoreHologram(record, loc, settings, fs);

            if (npcRestored || holoRestored) restored++;
            else skipped++;
        }

        for (FarmerRecord orphan : orphans) {
            if (FancyNpcsHook.isAvailable())       FancyNpcsHook.removeNpc(orphan.npcName());
            if (FancyHologramsHook.isAvailable() && orphan.holoName() != null)
                FancyHologramsHook.removeHologram(orphan.holoName());
            repository.deleteByNpcName(orphan.npcName());
        }

        plugin.getLogger().info(String.format(
                "[WesFarmer] Restore tamamlandı — %d yenilendi, %d zaten vardı, %d orphan silindi.",
                restored, skipped, orphans.size()));
    }

    private boolean restoreNpc(FarmerRecord record, Location loc,
                                ConfigManager.Settings settings, FarmerSettings fs) {
        if (!FancyNpcsHook.isAvailable()) return false;
        if (FancyNpcsHook.npcExists(record.npcName())) return false;

        String result = FancyNpcsHook.spawnFarmerNpcOffline(
                record.npcName(), loc, record.ownerUuid(), settings.npcSkin());

        if (result == null) {
            plugin.getLogger().warning("[WesFarmer] NPC restore başarısız: " + record.npcName());
            return false;
        }
        return true;
    }

    private boolean restoreHologram(FarmerRecord record, Location loc,
                                     ConfigManager.Settings settings, FarmerSettings fs) {
        if (!FancyHologramsHook.isAvailable()) return false;
        if (record.holoName() == null) return false;
        if (FancyHologramsHook.hologramExists(record.holoName())) return false;

        // Gizli ise oluşturma
        if (!fs.holoVisible()) return false;

        String ownerName = plugin.getServer().getOfflinePlayer(record.ownerUuid()).getName();
        String resolved  = ownerName != null ? ownerName : record.ownerUuid().toString();

        Location holoLoc = loc.clone().add(0, settings.hologramOffsetY(), 0);
        List<String> lines = settings.hologramLines().stream()
                .map(l -> l.replace("%player%", resolved))
                .collect(Collectors.toList());

        FancyHologramsHook.createHologram(record.holoName(), holoLoc, lines,
                settings.hologramBackground(), settings.hologramTextShadow());
        return true;
    }
}
