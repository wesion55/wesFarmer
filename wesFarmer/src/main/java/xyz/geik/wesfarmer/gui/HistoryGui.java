package xyz.geik.wesfarmer.gui;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import xyz.geik.wesfarmer.WesFarmer;
import xyz.geik.wesfarmer.config.GuiConfigManager;
import xyz.geik.wesfarmer.storage.FarmerHistoryRecord;
import xyz.geik.wesfarmer.storage.FarmerHistoryRepository;
import xyz.geik.wesfarmer.storage.FarmerRecord;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * İşlem geçmişi menüsünü oluşturur.
 * Slot 0-44: işlemler, Slot 45-53: kontrol (geri, önceki/sonraki sayfa).
 */
public final class HistoryGui {

    private static final MiniMessage MM         = MiniMessage.miniMessage();
    private static final int         PAGE_SIZE  = 45;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final WesFarmer plugin;
    private final GuiConfigManager guiConfigManager;
    private final FarmerHistoryRepository historyRepository;

    public HistoryGui(WesFarmer plugin,
                       GuiConfigManager guiConfigManager,
                       FarmerHistoryRepository historyRepository) {
        this.plugin            = plugin;
        this.guiConfigManager  = guiConfigManager;
        this.historyRepository = historyRepository;
    }

    public Inventory build(Player player, FarmerRecord record, int page) {
        GuiConfigManager.HistoryMenuSettings s = guiConfigManager.getHistoryMenuSettings();

        HistoryGuiHolder holder = new HistoryGuiHolder(record, page);
        Inventory inv = Bukkit.createInventory(holder, 54, MM.deserialize("<!italic>" + s.title()));
        holder.setInventory(inv);

        // Son 45*(page+1) kaydı çek, sayfayı göster
        int limit     = PAGE_SIZE * (page + 1);
        List<FarmerHistoryRecord> all = historyRepository.findRecent(record.id(), limit + PAGE_SIZE);

        int from = page * PAGE_SIZE;
        int to   = Math.min(from + PAGE_SIZE, all.size());

        List<FarmerHistoryRecord> pageRecords = from < all.size()
                ? all.subList(from, to)
                : List.of();

        for (int i = 0; i < pageRecords.size(); i++) {
            inv.setItem(i, buildEntryItem(pageRecords.get(i)));
        }

        // Kontrol paneli filler
        ItemStack filler = GuiItemBuilder.build(s.fillerMaterial(), s.fillerName(), List.of());
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        // Geri
        inv.setItem(s.backSlot(), GuiItemBuilder.build(s.backMat(), s.backName(), s.backLore()));

        // Önceki sayfa
        if (page > 0) {
            inv.setItem(s.prevSlot(), GuiItemBuilder.build(s.prevMat(), s.prevName(), s.prevLore()));
        }

        // Sonraki sayfa
        if (all.size() > (page + 1) * PAGE_SIZE) {
            inv.setItem(s.nextSlot(), GuiItemBuilder.build(s.nextMat(), s.nextName(), s.nextLore()));
        }

        return inv;
    }

    private ItemStack buildEntryItem(FarmerHistoryRecord h) {
        boolean isSell = h.type() == FarmerHistoryRecord.Type.SELL;
        String typeColor = isSell ? "<#4ade80>" : "<#38bdf8>";
        String typeEnd   = typeColor.replace("<", "</");
        String typeLabel = isSell ? "Satış" : "Toplama";

        String dateStr = DATE_FMT.format(Instant.ofEpochSecond(h.timestamp()));

        // products.yml'deki Türkçe ismi al, yoksa materyal adını kullan
        String displayName = plugin.getProductManager()
                .findByMaterial(h.material())
                .map(p -> p.displayName())
                .orElse(formatMaterialName(h.material()));

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(typeColor + typeLabel + typeEnd);
        lore.add(" <white>▸ Miktar: <#D09019>" + h.amount() + "</#D09019></white>");
        if (isSell) {
            lore.add(" <white>▸ Kazanç: <#4ade80>+" + String.format("%.2f", h.earnings()) + "$</#4ade80></white>");
        }
        lore.add(" <white>▸ Tarih: <gray>" + dateStr + "</gray></white>");

        String name = typeColor + "♦ <bold>" + displayName + "</bold>" + typeEnd;
        return GuiItemBuilder.build(h.material(), name, lore);
    }

    /** WHEAT → Wheat gibi okunabilir forma çevirir. */
    private String formatMaterialName(Material mat) {
        String raw = mat.name().replace("_", " ");
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1).toLowerCase())
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }
}
