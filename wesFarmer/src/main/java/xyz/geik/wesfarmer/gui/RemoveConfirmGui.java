package xyz.geik.wesfarmer.gui;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import xyz.geik.wesfarmer.config.GuiConfigManager;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the farmer-removal confirmation panel from {@code guis/remove-confirm.yml}.
 * Click handling lives in {@link RemoveConfirmGuiListener}.
 */
public final class RemoveConfirmGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GuiConfigManager guiConfigManager;

    public RemoveConfirmGui(GuiConfigManager guiConfigManager) {
        this.guiConfigManager = guiConfigManager;
    }

    public Inventory build(String npcName) {
        GuiConfigManager.RemoveConfirmGuiSettings gui = guiConfigManager.getRemoveConfirmSettings();

        RemoveConfirmGuiHolder holder = new RemoveConfirmGuiHolder(npcName);
        Inventory inv = Bukkit.createInventory(holder, gui.size(), MM.deserialize("<!italic>" + gui.title()));
        holder.setInventory(inv);

        ItemStack filler = GuiItemBuilder.build(gui.fillerMaterial(), gui.fillerName(), List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        if (gui.confirmSlot() >= 0 && gui.confirmSlot() < inv.getSize()) {
            inv.setItem(gui.confirmSlot(), GuiItemBuilder.build(gui.confirmMaterial(), gui.confirmName(), gui.confirmLore()));
        }
        if (gui.cancelSlot() >= 0 && gui.cancelSlot() < inv.getSize()) {
            inv.setItem(gui.cancelSlot(), GuiItemBuilder.build(gui.cancelMaterial(), gui.cancelName(), gui.cancelLore()));
        }

        return inv;
    }
}
