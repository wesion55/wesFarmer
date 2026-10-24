package xyz.geik.wesfarmer.gui;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import xyz.geik.wesfarmer.config.GuiConfigManager;
import xyz.geik.wesfarmer.storage.FarmerRecord;

import java.util.List;

/**
 * Kontrol paneli GUI'sini oluşturur.
 * Şimdilik stub — ayarlar/üyeler/izinler içeriği sonradan eklenecek.
 */
public final class ControlPanelGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GuiConfigManager guiConfigManager;

    public ControlPanelGui(GuiConfigManager guiConfigManager) {
        this.guiConfigManager = guiConfigManager;
    }

    public Inventory build(Player player, FarmerRecord record) {
        GuiConfigManager.ControlPanelSettings s = guiConfigManager.getControlPanelSettings();

        ControlPanelGuiHolder holder = new ControlPanelGuiHolder(record);
        Inventory inv = Bukkit.createInventory(holder, 36, MM.deserialize("<!italic>" + s.title()));
        holder.setInventory(inv);

        ItemStack filler = GuiItemBuilder.build(s.fillerMat(), s.fillerName(), List.of());
        // Sadece geri dön satırını (27-35) filler ile doldur
        for (int i = 27; i < 36; i++) inv.setItem(i, filler);

        inv.setItem(s.backSlot(), GuiItemBuilder.build(s.backMat(), s.backName(), s.backLore()));
        inv.setItem(s.settingsSlot(), GuiItemBuilder.build(s.settingsMat(), s.settingsName(), s.settingsLore()));
        inv.setItem(s.membersSlot(),  GuiItemBuilder.build(s.membersMat(),  s.membersName(),  s.membersLore()));
        inv.setItem(s.permsSlot(),    GuiItemBuilder.build(s.permsMat(),    s.permsName(),    s.permsLore()));

        return inv;
    }
}
