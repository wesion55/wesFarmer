package xyz.geik.wesfarmer.gui;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import xyz.geik.wesfarmer.config.ConfigManager;
import xyz.geik.wesfarmer.config.GuiConfigManager;
import xyz.geik.wesfarmer.economy.EconomyService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the purchase panel inventory from {@code guis/buy.yml}.
 */
public final class BuyGui {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final ConfigManager configManager;
    private final GuiConfigManager guiConfigManager;
    private final EconomyService economyService;

    public BuyGui(ConfigManager configManager, GuiConfigManager guiConfigManager, EconomyService economyService) {
        this.configManager = configManager;
        this.guiConfigManager = guiConfigManager;
        this.economyService = economyService;
    }

    public Inventory build(Player player) {
        GuiConfigManager.BuyGuiSettings gui = guiConfigManager.getBuySettings();
        ConfigManager.Settings settings = configManager.getSettings();

        BuyGuiHolder holder = new BuyGuiHolder();
        Inventory inventory = Bukkit.createInventory(holder, gui.size(), MINI_MESSAGE.deserialize("<!italic>" + gui.title()));
        holder.setInventory(inventory);

        ItemStack filler = GuiItemBuilder.build(gui.fillerMaterial(), gui.fillerName(), List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        String priceText = economyService.isAvailable()
                ? economyService.format(settings.buyPrice())
                : String.valueOf(settings.buyPrice());

        List<String> lore = gui.buyLore().stream()
                .map(line -> line.replace("%price%", priceText))
                .collect(Collectors.toList());

        if (gui.buySlot() >= 0 && gui.buySlot() < inventory.getSize()) {
            inventory.setItem(gui.buySlot(), GuiItemBuilder.build(gui.buyMaterial(), gui.buyName(), lore));
        }

        return inventory;
    }
}
