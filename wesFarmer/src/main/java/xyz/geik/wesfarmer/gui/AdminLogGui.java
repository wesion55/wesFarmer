package xyz.geik.wesfarmer.gui;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import xyz.geik.wesfarmer.storage.FarmerHistoryRecord;
import xyz.geik.wesfarmer.storage.FarmerHistoryRepository;
import xyz.geik.wesfarmer.storage.FarmerRecord;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Admin işlem log GUI'si (54 slot / 9x6).
 *
 * <p>Slot düzeni:
 * <pre>
 *  Satır 1-5 (0-44): log kayıtları (sayfa başına 45)
 *  Satır 6   (45-53): önceki=45 | filler x3 | kapat=49 | filler x3 | sonraki=53
 * </pre>
 */
public final class AdminLogGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    static final int PAGE_SIZE = 45;

    private final FarmerHistoryRepository historyRepository;

    public AdminLogGui(FarmerHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    public Inventory build(Player viewer, FarmerRecord record, int page) {
        AdminLogGuiHolder holder = new AdminLogGuiHolder(record, page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                MM.deserialize("<!italic><#D09019>ʟᴏɢ → <white>" + record.npcName()));
        holder.setInventory(inv);

        // ── Log kayıtları ─────────────────────────────────────────────────────
        List<FarmerHistoryRecord> logs = historyRepository.findPage(record.id(), page, PAGE_SIZE);
        int total = historyRepository.countByFarmer(record.id());

        for (int i = 0; i < logs.size(); i++) {
            inv.setItem(i, buildLogItem(logs.get(i)));
        }

        // ── Alt satır ─────────────────────────────────────────────────────────
        ItemStack filler = GuiItemBuilder.build(Material.BLACK_STAINED_GLASS_PANE, "", List.of());
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        // Önceki sayfa
        if (page > 0) {
            inv.setItem(45, GuiItemBuilder.build(Material.ARROW,
                    "<#ffea00>← <bold>öɴᴄᴇᴋɪ ѕᴀʏғᴀ</bold></#ffea00>",
                    List.of("", "<#ffea00>●</#ffea00> <white><u><bold><#ffea00>ɢᴇʀɪ ɢɪᴛᴍᴇᴋ</#ffea00></bold></u> ɪçɪɴ ᴛıᴋʟᴀ!</white>")));
        }

        // Kapat (ortada)
        inv.setItem(49, GuiItemBuilder.build(Material.BARRIER,
                "<red>❌ <bold>ᴋᴀᴘᴀᴛ</bold></red>",
                List.of("", "<red>●</red> <white><u><bold><red>ᴋᴀᴘᴀᴛᴍᴀᴋ</red></bold></u> ɪçɪɴ ᴛıᴋʟᴀ!</white>")));

        // Sonraki sayfa
        boolean hasNext = (long)(page + 1) * PAGE_SIZE < total;
        if (hasNext) {
            inv.setItem(53, GuiItemBuilder.build(Material.ARROW,
                    "<#ffea00>→ <bold>ѕᴏɴʀᴀᴋɪ ѕᴀʏғᴀ</bold></#ffea00>",
                    List.of("", "<#ffea00>●</#ffea00> <white><u><bold><#ffea00>ɪʟᴇʀɪ ɢɪᴛᴍᴇᴋ</#ffea00></bold></u> ɪçɪɴ ᴛıᴋʟᴀ!</white>")));
        }

        return inv;
    }

    // ── Item builder ──────────────────────────────────────────────────────────

    private ItemStack buildLogItem(FarmerHistoryRecord log) {
        boolean isSell   = log.type() == FarmerHistoryRecord.Type.SELL;
        Material icon    = isSell ? Material.EMERALD : Material.HOPPER;
        String typeColor = isSell ? "<#4ade80>" : "<#38bdf8>";
        String typeEnd   = typeColor.replace("<", "</");
        String typeLabel = isSell ? "Satış" : "Toplama";

        String matName   = formatMaterial(log.material().name());
        String date      = DATE_FMT.format(new Date(log.timestamp() * 1000L));

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<#D09019>Bilgiler:</#D09019>");
        lore.add(" <white>▸ İşlem: " + typeColor + typeLabel + typeEnd + "</white>");
        lore.add(" <white>▸ Ürün: <white>" + matName + "</white>");
        lore.add(" <white>▸ Miktar: <#D09019>" + log.amount() + "</#D09019></white>");
        if (isSell && log.earnings() > 0) {
            lore.add(" <white>▸ Kazanç: <#4ade80>"
                    + String.format("%.2f", log.earnings()) + "$</#4ade80></white>");
        }
        lore.add(" <white>▸ Tarih: <gray>" + date + "</gray></white>");

        String name = typeColor + "♦ <bold>" + typeLabel + " — " + matName + "</bold>" + typeEnd;
        return GuiItemBuilder.build(icon, name, lore);
    }

    private static String formatMaterial(String raw) {
        return switch (raw.toUpperCase()) {
            case "WHEAT"          -> "Buğday";
            case "CACTUS"         -> "Kaktüs";
            case "SUGAR_CANE"     -> "Şeker Kamışı";
            case "CARROT"         -> "Havuç";
            case "POTATO"         -> "Patates";
            case "MELON"          -> "Karpuz";
            case "PUMPKIN"        -> "Balkabağı";
            case "BAMBOO"         -> "Bambu";
            case "NETHER_WART"    -> "Nether Siğili";
            case "BEETROOT"       -> "Pancar";
            case "CHORUS_FRUIT"   -> "Koro Meyvesi";
            default -> {
                String s = raw.replace("_", " ").toLowerCase();
                yield Character.toUpperCase(s.charAt(0)) + s.substring(1);
            }
        };
    }
}
