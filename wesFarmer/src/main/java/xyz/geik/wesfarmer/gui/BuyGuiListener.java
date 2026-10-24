package xyz.geik.wesfarmer.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import xyz.geik.wesfarmer.config.GuiConfigManager;
import xyz.geik.wesfarmer.purchase.PurchaseService;

/**
 * Handles clicks inside the purchase panel. Clicking the buy item no longer
 * purchases immediately — it opens the confirmation panel; the actual
 * purchase only happens from {@link ConfirmGuiListener}.
 */
public final class BuyGuiListener implements Listener {

    private final GuiConfigManager guiConfigManager;
    private final PurchaseService purchaseService;

    public BuyGuiListener(GuiConfigManager guiConfigManager, PurchaseService purchaseService) {
        this.guiConfigManager = guiConfigManager;
        this.purchaseService = purchaseService;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BuyGuiHolder)) return;

        // Never let items be taken out of/into this GUI, regardless of which slot was clicked.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() != guiConfigManager.getBuySettings().buySlot()) return;

        purchaseService.requestPurchase(player);
    }
}
