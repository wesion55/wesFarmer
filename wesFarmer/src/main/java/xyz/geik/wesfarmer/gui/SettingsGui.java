package xyz.geik.wesfarmer.gui;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import xyz.geik.wesfarmer.config.GuiConfigManager;
import xyz.geik.wesfarmer.product.ProductManager;
import xyz.geik.wesfarmer.storage.FarmerRecord;
import xyz.geik.wesfarmer.storage.FarmerSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Çiftçi Ayarlar paneli (36 slot / 9x4).
 *
 * <p>Slot düzeni:
 * <pre>
 *  Satır 1 (0-8):   boş
 *  Satır 2 (9-17):  boş | toplama=10 | boş | holo=12 | boş | sat=14 | boş | boş | boş
 *  Satır 3 (18-26): boş
 *  Satır 4 (27-35): filler x4 | geri=31 | filler x4
 * </pre>
 */
public final class SettingsGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static final int SLOT_HOLO    = 12;
    public static final int SLOT_COLLECT = 13;
    public static final int SLOT_SELL    = 14;

    private final GuiConfigManager guiConfigManager;
    private final ProductManager   productManager;

    public SettingsGui(GuiConfigManager guiConfigManager, ProductManager productManager) {
        this.guiConfigManager = guiConfigManager;
        this.productManager   = productManager;
    }

    public Inventory build(Player player, FarmerRecord record, FarmerSettings settings) {
        GuiConfigManager.SettingsMenuSettings s = guiConfigManager.getSettingsMenuSettings();

        SettingsGuiHolder holder = new SettingsGuiHolder(record);
        Inventory inv = Bukkit.createInventory(holder, 36,
                MM.deserialize("<!italic>" + s.title()));
        holder.setInventory(inv);

        // Filler sadece geri dön satırı
        ItemStack filler = GuiItemBuilder.build(Material.BLACK_STAINED_GLASS_PANE, "", List.of());
        for (int i = 27; i < 36; i++) inv.setItem(i, filler);

        // Geri
        inv.setItem(s.backSlot(),
                GuiItemBuilder.build(s.backMat(), s.backName(), s.backLore()));

        // Toplama toggle — global ürün durumuna bakıyor
        boolean anyCollectOn = productManager.getAll().stream()
                .anyMatch(xyz.geik.wesfarmer.product.FarmerProduct::collectEnabled);
        boolean collectLocked = settings.collectLocked();

        inv.setItem(SLOT_COLLECT, buildToggle(
                "Toplama Durumu",
                !collectLocked,                          // kilitli değilse "Açık"
                collectLocked ? "<red>Kilitli — Düşenler Toplanmaz</red>"
                              : (anyCollectOn ? "<green>Aktif</green>" : "<gray>Tüm Ürünler Kapalı</gray>"),
                Material.HOPPER,
                Material.BARRIER
        ));

        // Hologram görünürlüğü
        inv.setItem(SLOT_HOLO, buildToggle(
                "Hologram Görünürlüğü",
                settings.holoVisible(),
                null,
                Material.END_ROD,
                Material.BARRIER
        ));

        // Satış kilidi
        inv.setItem(SLOT_SELL, buildToggle(
                "Satış Durumu",
                !settings.sellLocked(),
                null,
                Material.EMERALD,
                Material.REDSTONE
        ));

        return inv;
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /**
     * @param extraStatusLine null ise varsayılan Açık/Kapalı gösterilir,
     *                        null değilse o satır kullanılır.
     */
    private ItemStack buildToggle(String label, boolean active,
                                   String extraStatusLine,
                                   Material matOn, Material matOff) {
        Material mat = active ? matOn : matOff;
        String statusColor = active ? "<green>" : "<red>";
        String statusEnd   = active ? "</green>" : "</red>";
        String statusText  = extraStatusLine != null
                ? extraStatusLine
                : (active ? "<green>Açık</green>" : "<red>Kapalı</red>");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<#D09019>Bilgiler:</#D09019>");
        lore.add(" <#D09019>Durum:</#D09019> " + statusText + "");
        lore.add("");
        lore.add("<gray>●</gray> <white><gray><u><b>ᴅᴇğɪşᴛɪʀᴍᴇᴋ</b></u></gray> ɪçɪɴ ᴛıᴋʟᴀ</white>");

        String name = statusColor + "♦ <bold>" + label + "</bold>" + statusEnd;
        return GuiItemBuilder.build(mat, name, lore);
    }
}
