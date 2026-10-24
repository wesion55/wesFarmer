package xyz.geik.wesfarmer.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import xyz.geik.wesfarmer.WesFarmer;
import xyz.geik.wesfarmer.config.GuiConfigManager;
import xyz.geik.wesfarmer.config.MessageService;
import xyz.geik.wesfarmer.storage.FarmerPermission;
import xyz.geik.wesfarmer.storage.FarmerSettings;

public final class ControlPanelGuiListener implements Listener {

    private final WesFarmer        plugin;
    private final GuiConfigManager guiConfigManager;
    private final MessageService   messageService;
    private final FarmerMenuGui    farmerMenuGui;
    private final MembersGui       membersGui;
    private final PermissionsGui   permissionsGui;
    private final SettingsGui      settingsGui;

    public ControlPanelGuiListener(WesFarmer plugin,
                                    GuiConfigManager guiConfigManager,
                                    MessageService messageService,
                                    FarmerMenuGui farmerMenuGui,
                                    MembersGui membersGui,
                                    PermissionsGui permissionsGui,
                                    SettingsGui settingsGui) {
        this.plugin           = plugin;
        this.guiConfigManager = guiConfigManager;
        this.messageService   = messageService;
        this.farmerMenuGui    = farmerMenuGui;
        this.membersGui       = membersGui;
        this.permissionsGui   = permissionsGui;
        this.settingsGui      = settingsGui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ControlPanelGuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        GuiConfigManager.ControlPanelSettings s = guiConfigManager.getControlPanelSettings();
        int slot = event.getRawSlot();

        if (slot == s.backSlot()) {
            SoundHelper.playClick(player);
            player.openInventory(farmerMenuGui.build(player, holder.getRecord(), 0));

        } else if (slot == s.settingsSlot()) {
            SoundHelper.playClick(player);
            FarmerSettings settings = plugin.getFarmerSettingsRepository()
                    .get(holder.getRecord().id());
            player.openInventory(settingsGui.build(player, holder.getRecord(), settings));

        } else if (slot == s.membersSlot()) {
            if (!plugin.getFarmerMemberRepository().hasPermission(
                    holder.getRecord().id(), player.getUniqueId(),
                    FarmerPermission.VIEW_MEMBERS,
                    plugin.getConfigManager().getSettings())) {
                messageService.send(player, "no-permission");
                return;
            }
            SoundHelper.playClick(player);
            player.openInventory(membersGui.build(player, holder.getRecord(), 0));

        } else if (slot == s.permsSlot()) {
            SoundHelper.playClick(player);
            player.openInventory(permissionsGui.build(player, holder.getRecord()));
        }
    }
}
