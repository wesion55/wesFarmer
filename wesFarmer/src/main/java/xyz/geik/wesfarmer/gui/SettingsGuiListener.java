package xyz.geik.wesfarmer.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import xyz.geik.wesfarmer.WesFarmer;
import xyz.geik.wesfarmer.config.ConfigManager;
import xyz.geik.wesfarmer.config.GuiConfigManager;
import xyz.geik.wesfarmer.config.MessageService;
import xyz.geik.wesfarmer.integration.hologram.FancyHologramsHook;
import xyz.geik.wesfarmer.storage.FarmerRecord;
import xyz.geik.wesfarmer.storage.FarmerRole;
import xyz.geik.wesfarmer.storage.FarmerSettings;
import xyz.geik.wesfarmer.storage.FarmerSettingsRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Ayarlar panelindeki tıklamaları yönetir.
 * Sadece LEADER+ erişebilir.
 */
public final class SettingsGuiListener implements Listener {

    private final WesFarmer              plugin;
    private final GuiConfigManager       guiConfigManager;
    private final ConfigManager          configManager;
    private final MessageService         messageService;
    private final SettingsGui            settingsGui;
    private final ControlPanelGui        controlPanelGui;
    private final FarmerSettingsRepository settingsRepository;

    public SettingsGuiListener(WesFarmer plugin,
                                GuiConfigManager guiConfigManager,
                                ConfigManager configManager,
                                MessageService messageService,
                                SettingsGui settingsGui,
                                ControlPanelGui controlPanelGui,
                                FarmerSettingsRepository settingsRepository) {
        this.plugin             = plugin;
        this.guiConfigManager   = guiConfigManager;
        this.configManager      = configManager;
        this.messageService     = messageService;
        this.settingsGui        = settingsGui;
        this.controlPanelGui    = controlPanelGui;
        this.settingsRepository = settingsRepository;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SettingsGuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        FarmerRecord record = holder.getRecord();
        int slot = event.getRawSlot();

        GuiConfigManager.SettingsMenuSettings s = guiConfigManager.getSettingsMenuSettings();

        // Geri
        if (slot == s.backSlot()) {
            SoundHelper.playClick(player);
            player.openInventory(controlPanelGui.build(player, record));
            return;
        }

        // Sadece LEADER+ değiştirebilir
        if (!plugin.getFarmerMemberRepository()
                .hasAccess(record.id(), player.getUniqueId(), FarmerRole.LEADER)) {
            messageService.send(player, "no-permission");
            return;
        }

        FarmerSettings current = settingsRepository.get(record.id());
        FarmerSettings updated = null;

        if (slot == SettingsGui.SLOT_COLLECT) {
            boolean nowLocked = !current.collectLocked();
            updated = current.withCollectLocked(nowLocked);
            messageService.send(player, nowLocked
                    ? "settings-collect-locked"
                    : "settings-collect-unlocked");

        } else if (slot == SettingsGui.SLOT_HOLO) {
            boolean newVal = !current.holoVisible();
            updated = current.withHoloVisible(newVal);
            applyHoloVisibility(record, newVal);
            messageService.send(player, newVal
                    ? "settings-holo-shown"
                    : "settings-holo-hidden");

        } else if (slot == SettingsGui.SLOT_SELL) {
            boolean nowLocked = !current.sellLocked();
            updated = current.withSellLocked(nowLocked);
            messageService.send(player, nowLocked
                    ? "settings-sell-locked"
                    : "settings-sell-unlocked");
        }

        if (updated == null) return;

        settingsRepository.save(updated);
        SoundHelper.playClick(player);
        player.openInventory(settingsGui.build(player, record, updated));
    }

    // ── Hologram side-effect ──────────────────────────────────────────────────

    private void applyHoloVisibility(FarmerRecord record, boolean visible) {
        if (!FancyHologramsHook.isAvailable() || record.holoName() == null) return;
        if (visible) {
            org.bukkit.Location loc = record.toLocation(plugin.getServer());
            if (loc == null) return;
            ConfigManager.Settings cfg = configManager.getSettings();
            loc = loc.clone().add(0, cfg.hologramOffsetY(), 0);
            String ownerName = plugin.getServer()
                    .getOfflinePlayer(record.ownerUuid()).getName();
            String resolved = ownerName != null ? ownerName : record.ownerUuid().toString();
            List<String> lines = cfg.hologramLines().stream()
                    .map(l -> l.replace("%player%", resolved))
                    .collect(Collectors.toList());
            FancyHologramsHook.createHologram(record.holoName(), loc, lines,
                    cfg.hologramBackground(), cfg.hologramTextShadow());
        } else {
            FancyHologramsHook.hideHologram(record.holoName());
        }
    }
}
