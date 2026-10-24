package xyz.geik.wesfarmer.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import xyz.geik.wesfarmer.WesFarmer;
import xyz.geik.wesfarmer.config.ConfigManager;
import xyz.geik.wesfarmer.config.GuiConfigManager;
import xyz.geik.wesfarmer.config.MessageService;
import xyz.geik.wesfarmer.storage.FarmerPermission;
import xyz.geik.wesfarmer.storage.FarmerRecord;
import xyz.geik.wesfarmer.storage.FarmerRole;

import java.util.EnumMap;
import java.util.Map;

/**
 * İzinler panelindeki tıklamaları yönetir.
 *
 * <ul>
 *   <li>Sol tık → izin için gereken minimum rolü <b>yükselt</b> (MEMBER→LEADER→OWNER)</li>
 *   <li>Sağ tık → izin için gereken minimum rolü <b>düşür</b>  (OWNER→LEADER→MEMBER)</li>
 * </ul>
 *
 * Değişiklikler anında config.yml'e yazılır.
 * Sadece OWNER bu paneli değiştirebilir.
 */
public final class PermissionsGuiListener implements Listener {

    private final WesFarmer        plugin;
    private final GuiConfigManager guiConfigManager;
    private final ConfigManager    configManager;
    private final MessageService   messageService;
    private final PermissionsGui   permissionsGui;
    private final ControlPanelGui  controlPanelGui;

    public PermissionsGuiListener(WesFarmer plugin,
                                   GuiConfigManager guiConfigManager,
                                   ConfigManager configManager,
                                   MessageService messageService,
                                   PermissionsGui permissionsGui,
                                   ControlPanelGui controlPanelGui) {
        this.plugin           = plugin;
        this.guiConfigManager = guiConfigManager;
        this.configManager    = configManager;
        this.messageService   = messageService;
        this.permissionsGui   = permissionsGui;
        this.controlPanelGui  = controlPanelGui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PermissionsGuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        GuiConfigManager.PermissionsMenuSettings s = guiConfigManager.getPermissionsMenuSettings();
        FarmerRecord record = holder.getRecord();
        int  slot    = event.getRawSlot();
        boolean isRight = event.getClick().name().contains("RIGHT");

        // Geri
        if (slot == s.backSlot()) {
            SoundHelper.playClick(player);
            player.openInventory(controlPanelGui.build(player, record));
            return;
        }

        // İzin ikonu tıklaması
        PermissionsGui.PermClick click = PermissionsGui.resolveClick(slot);
        if (click == null) return;

        // Sadece OWNER değiştirebilir
        if (!plugin.getFarmerMemberRepository()
                .hasAccess(record.id(), player.getUniqueId(), FarmerRole.OWNER)) {
            messageService.send(player, "no-permission");
            return;
        }

        FarmerPermission perm    = click.permission();
        FarmerRole       current = configManager.getSettings().requiredRole(perm);

        // Sol tık = yükselt, Sağ tık = düşür
        FarmerRole newRole = isRight
                ? PermissionsGui.prevRole(current)
                : PermissionsGui.nextRole(current);

        // Limit: MEMBER'dan aşağı veya OWNER'dan yukarı gidilmez
        if (newRole == null) return;

        // Runtime + config güncelle
        ConfigManager.Settings old = configManager.getSettings();
        Map<FarmerPermission, FarmerRole> newMap = new EnumMap<>(old.permissions());
        newMap.put(perm, newRole);
        configManager.applyPermissions(Map.copyOf(newMap));

        plugin.getConfig().set("settings.permissions." + perm.configKey(), newRole.name());
        plugin.saveConfig();

        SoundHelper.playClick(player);

        String permName = permDisplayName(perm);
        String roleName = configManager.getSettings().roleDisplayName(newRole);
        messageService.send(player, "permission-updated",
                MessageService.Placeholder.of("%permission%", permName),
                MessageService.Placeholder.of("%role%", roleName));

        player.openInventory(permissionsGui.build(player, record));
    }

    private static String permDisplayName(FarmerPermission perm) {
        return switch (perm) {
            case SELL               -> "Ürün Sat";
            case SELL_ALL           -> "Tümünü Sat";
            case TAKE               -> "Envantere Al";
            case UPGRADE            -> "Seviye Yükselt";
            case UPGRADE_ALL        -> "Toplu Yükselt";
            case COLLECT_TOGGLE     -> "Toplama Aç/Kapat";
            case COLLECT_TOGGLE_ALL -> "Toplama Toplu Aç/Kapat";
            case VIEW_MEMBERS       -> "Üyeleri Görüntüle";
            case SELL_TOGGLE        -> "Satış Durumu Değiştir";
        };
    }
}
