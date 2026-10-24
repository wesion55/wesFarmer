package xyz.geik.wesfarmer.gui;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import xyz.geik.wesfarmer.config.ConfigManager;
import xyz.geik.wesfarmer.config.GuiConfigManager;
import xyz.geik.wesfarmer.economy.EconomyService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the "are you sure?" panel shown after a player clicks the buy item,
 * from {@code guis/confirm.yml}. Click handling lives in
 * {@link ConfirmGuiListener}; this class only builds the inventory.
 */
public final class ConfirmGui {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final ConfigManager configManager;
    private final GuiConfigManager guiConfigManager;
    private final EconomyService economyService;

    public ConfirmGui(ConfigManager configManager, GuiConfigManager guiConfigManager, EconomyService economyService) {
        this.configManager = configManager;
        this.guiConfigManager = guiConfigManager;
        this.economyService = economyService;
    }

    public Inventory build(Player player, Location purchaseLocation) {
        GuiConfigManager.ConfirmGuiSettings gui = guiConfigManager.getConfirmSettings();
        ConfigManager.Settings settings = configManager.getSettings();

        ConfirmGuiHolder holder = new ConfirmGuiHolder(purchaseLocation);
        return buildInventory(holder, gui, settings, null);
    }

    /**
     * Admin/aksiyon onayı için — konum yerine Runnable action taşır.
     * @param cancelMessageKey İptal mesajı anahtarı.
     * @param priceText        Confirm lore'unda %price% yerine geçecek metin. Null ise buy price kullanılır.
     */
    public Inventory buildAction(Runnable action, String cancelMessageKey, String priceText) {
        GuiConfigManager.ConfirmGuiSettings gui = guiConfigManager.getConfirmSettings();
        ConfigManager.Settings settings = configManager.getSettings();
        ConfirmGuiHolder holder = new ConfirmGuiHolder(action, cancelMessageKey);
        return buildInventory(holder, gui, settings, priceText);
    }

    public Inventory buildAction(Runnable action, String cancelMessageKey) {
        return buildAction(action, cancelMessageKey, null);
    }

    public Inventory buildAction(Runnable action) {
        return buildAction(action, "action-cancelled", null);
    }

    private Inventory buildInventory(ConfirmGuiHolder holder,
                                      GuiConfigManager.ConfirmGuiSettings gui,
                                      ConfigManager.Settings settings,
                                      String overridePrice) {
        Inventory inventory = Bukkit.createInventory(holder, gui.size(), MINI_MESSAGE.deserialize("<!italic>" + gui.title()));
        holder.setInventory(inventory);

        ItemStack filler = GuiItemBuilder.build(gui.fillerMaterial(), gui.fillerName(), List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        String priceText = overridePrice != null ? overridePrice
                : (economyService.isAvailable()
                    ? economyService.format(settings.buyPrice())
                    : String.valueOf(settings.buyPrice()));

        List<String> confirmLore = gui.confirmLore().stream()
                .map(line -> line.replace("%price%", priceText))
                .collect(Collectors.toList());
        List<String> cancelLore = gui.cancelLore().stream()
                .map(line -> line.replace("%price%", priceText))
                .collect(Collectors.toList());

        if (gui.confirmSlot() >= 0 && gui.confirmSlot() < inventory.getSize()) {
            inventory.setItem(gui.confirmSlot(), GuiItemBuilder.build(gui.confirmMaterial(), gui.confirmName(), confirmLore));
        }
        if (gui.cancelSlot() >= 0 && gui.cancelSlot() < inventory.getSize()) {
            inventory.setItem(gui.cancelSlot(), GuiItemBuilder.build(gui.cancelMaterial(), gui.cancelName(), cancelLore));
        }

        return inventory;
    }

    private ItemStack buildItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MINI_MESSAGE.deserialize(name));
            meta.lore(lore.stream().map(MINI_MESSAGE::deserialize).collect(Collectors.toList()));
            item.setItemMeta(meta);
        }
        return item;
    }
}
