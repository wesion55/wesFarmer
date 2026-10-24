package xyz.geik.wesfarmer.gui;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import xyz.geik.wesfarmer.config.GuiConfigManager;
import xyz.geik.wesfarmer.product.FarmerProduct;
import xyz.geik.wesfarmer.storage.FarmerProductLevelRepository;
import xyz.geik.wesfarmer.storage.FarmerProductStateRepository;
import xyz.geik.wesfarmer.storage.FarmerRecord;
import xyz.geik.wesfarmer.storage.FarmerStockRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class ProductDetailGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GuiConfigManager             guiConfigManager;
    private final FarmerStockRepository        stockRepository;
    private final FarmerProductLevelRepository levelRepository;
    private final FarmerProductStateRepository stateRepository;

    public ProductDetailGui(GuiConfigManager guiConfigManager,
                             FarmerStockRepository stockRepository,
                             FarmerProductLevelRepository levelRepository,
                             FarmerProductStateRepository stateRepository) {
        this.guiConfigManager = guiConfigManager;
        this.stockRepository  = stockRepository;
        this.levelRepository  = levelRepository;
        this.stateRepository  = stateRepository;
    }

    public Inventory build(Player player, FarmerRecord record, FarmerProduct product) {
        GuiConfigManager.ProductDetailSettings s = guiConfigManager.getProductDetailSettings();

        ProductDetailGuiHolder holder = new ProductDetailGuiHolder(record, product);
        Inventory inv = Bukkit.createInventory(holder, 45, MM.deserialize("<!italic>" + s.title()));
        holder.setInventory(inv);

        // Filler sadece geri dön satırı (36-44)
        ItemStack filler = GuiItemBuilder.build(s.fillerMaterial(), s.fillerName(), List.of());
        for (int i = 36; i < 45; i++) inv.setItem(i, filler);

        inv.setItem(s.backSlot(), GuiItemBuilder.build(s.backMat(), s.backName(), s.backLore()));

        int     stock    = stockRepository.getStock(record.id(), product.material());
        int     level    = levelRepository.getLevel(record.id(), product.material());
        int     maxStock = FarmerProductLevelRepository.maxStockAtLevel(product.maxStock(), level);

        // Collect durumu: farmer-bazlı DB'den, yoksa products.yml varsayılanı
        boolean collectOn = stateRepository.getCollectEnabled(
                record.id(), product.material(), product.collectEnabled());
        // Sell durumu: farmer-bazlı DB'den (sellLocked değil, ürün bazlı)
        boolean sellOn = stateRepository.getSellEnabled(
                record.id(), product.material(), product.sellEnabled());

        // Collect toggle
        String collectStatusLine = collectOn ? "<green>Açık</green>" : "<red>Kapalı</red>";
        List<String> toggleLore = s.toggleLore().stream()
                .map(l -> l.replace("{status}", collectStatusLine))
                .collect(Collectors.toList());
        inv.setItem(s.toggleSlot(), GuiItemBuilder.build(
                collectOn ? s.toggleMatOn() : s.toggleMatOff(),
                collectOn ? s.toggleNameOn() : s.toggleNameOff(),
                toggleLore));

        // Sell toggle
        String sellStatusLine = sellOn ? "<green>Açık</green>" : "<red>Kapalı</red>";
        List<String> sellToggleLore = s.sellToggleLore().stream()
                .map(l -> l.replace("{status}", sellStatusLine))
                .collect(Collectors.toList());
        inv.setItem(s.sellToggleSlot(), GuiItemBuilder.build(
                sellOn ? s.sellToggleMatOn() : s.sellToggleMatOff(),
                sellOn ? s.sellToggleNameOn() : s.sellToggleNameOff(),
                sellToggleLore));

        inv.setItem(s.productSlot(), buildProductIcon(product, stock, maxStock, level));

        // Hepsini sat — satış kapalıysa BARRIER göster
        if (!sellOn) {
            inv.setItem(s.sellSlot(), GuiItemBuilder.build(
                    org.bukkit.Material.BARRIER,
                    "<red>✖ <bold>ʜᴇᴘꜱɪɴɪ ꜱᴀᴛ</bold> <dark_gray>[Kapalı]</dark_gray></red>",
                    List.of("", "<red>Bu ürünün satışı kapalı.</red>")));
        } else {
            List<String> sellLore = s.sellLore().stream()
                    .map(l -> l.replace("%earnings%", String.format("%.2f", product.netEarnings(stock))))
                    .collect(Collectors.toList());
            inv.setItem(s.sellSlot(), GuiItemBuilder.build(s.sellMat(), s.sellName(), sellLore));
        }

        inv.setItem(s.upgradeSlot(), buildUpgradeItem(s, product, level));

        List<String> takeLore = s.takeLore().stream()
                .map(l -> l.replace("%stock%", String.valueOf(stock)))
                .collect(Collectors.toList());
        inv.setItem(s.takeSlot(), GuiItemBuilder.build(s.takeMat(), s.takeName(), takeLore));

        return inv;
    }

    private ItemStack buildUpgradeItem(GuiConfigManager.ProductDetailSettings s,
                                        FarmerProduct product, int level) {
        boolean maxed        = level >= FarmerProductLevelRepository.MAX_LEVEL;
        int     nextLevel    = Math.min(level + 1, FarmerProductLevelRepository.MAX_LEVEL);
        int     curCapacity  = FarmerProductLevelRepository.maxStockAtLevel(product.maxStock(), level);
        int     nextCapacity = FarmerProductLevelRepository.maxStockAtLevel(product.maxStock(), nextLevel);
        double  cost         = FarmerProductLevelRepository.upgradeCost(level);

        List<String> lore = s.upgradeLore().stream()
                .map(l -> l.replace("%level%",         String.valueOf(level))
                           .replace("%capacity%",      String.valueOf(curCapacity))
                           .replace("%next_capacity%", maxed ? "MAX" : String.valueOf(nextCapacity))
                           .replace("%cost%",          maxed ? "-" : String.format("%.0f", cost)))
                .collect(Collectors.toList());

        String name = maxed ? "<gray>♦ <bold>ᴍᴀx ѕᴇᴠɪʏᴇ</bold></gray>" : s.upgradeName();
        return GuiItemBuilder.build(s.upgradeMat(), name, lore);
    }

    private ItemStack buildProductIcon(FarmerProduct product, int stock, int maxStock, int level) {
        double fillPct     = maxStock > 0 ? (stock * 100.0 / maxStock) : 0;
        String stockTag    = (maxStock > 0 && stock >= maxStock) ? "<red>" : "<#D09019>";
        String stockTagEnd = stockTag.replace("<", "</");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<#D09019>Genel Bakış</#D09019>");
        lore.add(" <white>▸ Stok: " + stockTag + stock + "/" + maxStock + stockTagEnd + "</white>");
        lore.add(" <white>▸ Doluluk: <#D09019>%" + String.format("%.0f", fillPct) + "</#D09019></white>");
        lore.add(" <white>▸ Seviye: <#f97316>" + level + " / "
                + FarmerProductLevelRepository.MAX_LEVEL + "</#f97316></white>");
        lore.add(" <white>▸ Birim Fiyat: <#D09019>" + String.format("%.2f", product.price())
                + "$</white> <dark_gray>(<yellow>%" + product.tax() + " Vergi</yellow>)</dark_gray>");
        lore.add(" <white>▸ Net Fiyat: <#4ade80>" + String.format("%.2f", product.netPrice())
                + "$</#4ade80></white>");

        return GuiItemBuilder.build(product.material(), product.displayName(), lore);
    }
}
