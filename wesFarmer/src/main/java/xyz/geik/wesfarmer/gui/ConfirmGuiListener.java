package xyz.geik.wesfarmer.gui;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import xyz.geik.wesfarmer.WesFarmer;
import xyz.geik.wesfarmer.config.GuiConfigManager;
import xyz.geik.wesfarmer.config.MessageService;
import xyz.geik.wesfarmer.purchase.PurchaseService;

/**
 * Confirmation panelindeki tıklamaları yönetir.
 *
 * <p>İki mod:
 * <ul>
 *   <li><b>Purchase modu</b> ({@link ConfirmGuiHolder#getPurchaseLocation()} != null) —
 *       onayla → {@link PurchaseService#confirmPurchase}, iptal → {@link PurchaseService#cancelPurchase}.</li>
 *   <li><b>Action modu</b> ({@link ConfirmGuiHolder#getAction()} != null) —
 *       onayla → action Runnable'ı çalıştır, iptal → sadece kapat.</li>
 * </ul>
 */
public final class ConfirmGuiListener implements Listener {

    private final WesFarmer plugin;
    private final GuiConfigManager guiConfigManager;
    private final PurchaseService purchaseService;
    private final MessageService messageService;

    public ConfirmGuiListener(WesFarmer plugin, GuiConfigManager guiConfigManager,
                               PurchaseService purchaseService, MessageService messageService) {
        this.plugin          = plugin;
        this.guiConfigManager = guiConfigManager;
        this.purchaseService = purchaseService;
        this.messageService  = messageService;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ConfirmGuiHolder holder)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        GuiConfigManager.ConfirmGuiSettings settings = guiConfigManager.getConfirmSettings();
        int rawSlot = event.getRawSlot();

        if (rawSlot == settings.confirmSlot()) {
            player.closeInventory();

            if (holder.getAction() != null) {
                // Action modu — removeall gibi admin işlemleri
                Runnable action = holder.getAction();
                plugin.getServer().getScheduler().runTask(plugin, action);
            } else if (holder.getPurchaseLocation() != null) {
                // Purchase modu — satın alma onayı
                Location purchaseLocation = holder.getPurchaseLocation();
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        purchaseService.confirmPurchase(player, purchaseLocation));
            }

        } else if (rawSlot == settings.cancelSlot()) {
            player.closeInventory();

            if (holder.getAction() != null) {
                String key = holder.getCancelMessageKey() != null
                        ? holder.getCancelMessageKey()
                        : "action-cancelled";
                messageService.send(player, key);
            } else {
                purchaseService.cancelPurchase(player);
            }
        }
    }
}
