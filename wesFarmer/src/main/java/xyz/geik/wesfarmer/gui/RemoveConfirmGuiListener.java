package xyz.geik.wesfarmer.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import xyz.geik.wesfarmer.WesFarmer;
import xyz.geik.wesfarmer.config.GuiConfigManager;
import xyz.geik.wesfarmer.config.MessageService;
import xyz.geik.wesfarmer.integration.hologram.FancyHologramsHook;
import xyz.geik.wesfarmer.integration.npc.FancyNpcsHook;
import xyz.geik.wesfarmer.storage.FarmerRecord;
import xyz.geik.wesfarmer.storage.FarmerRepository;

/**
 * Handles clicks in the farmer-removal confirmation panel.
 * "Sil" → NPC + hologram kaldır + DB'den sil.
 * "İptal" → kapat, mesaj gönder.
 */
public final class RemoveConfirmGuiListener implements Listener {

    private final WesFarmer plugin;
    private final GuiConfigManager guiConfigManager;
    private final MessageService messageService;
    private final FarmerRepository farmerRepository;

    public RemoveConfirmGuiListener(WesFarmer plugin, GuiConfigManager guiConfigManager,
                                     MessageService messageService,
                                     FarmerRepository farmerRepository) {
        this.plugin            = plugin;
        this.guiConfigManager  = guiConfigManager;
        this.messageService    = messageService;
        this.farmerRepository  = farmerRepository;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RemoveConfirmGuiHolder holder)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        GuiConfigManager.RemoveConfirmGuiSettings settings = guiConfigManager.getRemoveConfirmSettings();
        int rawSlot = event.getRawSlot();

        if (rawSlot == settings.confirmSlot()) {
            String npcName = holder.getNpcName();
            player.closeInventory();

            // 1 tick sonra işle — inventory kapanma paketi önce gitsin
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                FarmerRecord record = farmerRepository.findByNpcName(npcName);

                if (record == null) {
                    // Başka biri zaten silmiş olabilir
                    messageService.send(player, "remove-already-gone");
                    return;
                }

                // Sadece kendi farmerını silebilir
                if (!record.ownerUuid().equals(player.getUniqueId())) {
                    messageService.send(player, "no-permission");
                    return;
                }

                if (FancyNpcsHook.isAvailable())                         FancyNpcsHook.removeNpc(npcName);
                if (FancyHologramsHook.isAvailable() && record.holoName() != null)
                    FancyHologramsHook.removeHologram(record.holoName());

                farmerRepository.deleteByNpcName(npcName);
                // Üye, settings, stok ve seviye kayıtlarını da temizle
                plugin.getFarmerMemberRepository().removeAllByFarmer(record.id());
                plugin.getFarmerSettingsRepository().delete(record.id());
                plugin.getFarmerStockRepository().clearStock(record.id());
                plugin.getFarmerProductLevelRepository().deleteByFarmer(record.id());
                plugin.getFarmerHistoryRepository().deleteByFarmer(record.id());
                plugin.getFarmerProductStateRepository().deleteByFarmer(record.id());
                // CollectTask cache'den çıkar
                xyz.geik.wesfarmer.task.CollectTask ct = xyz.geik.wesfarmer.task.CollectTask.getInstance();
                if (ct != null) ct.cacheRemove(record.claimKey());
                messageService.send(player, "remove-success");
            });

        } else if (rawSlot == settings.cancelSlot()) {
            player.closeInventory();
            messageService.send(player, "remove-cancelled");
        }
    }
}
