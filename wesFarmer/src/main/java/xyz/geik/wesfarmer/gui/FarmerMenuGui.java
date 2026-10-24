package xyz.geik.wesfarmer.gui;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import xyz.geik.wesfarmer.config.GuiConfigManager;
import xyz.geik.wesfarmer.product.FarmerProduct;
import xyz.geik.wesfarmer.product.ProductManager;
import xyz.geik.wesfarmer.storage.FarmerHistoryRepository;
import xyz.geik.wesfarmer.storage.FarmerMemberRepository;
import xyz.geik.wesfarmer.storage.FarmerProductLevelRepository;
import xyz.geik.wesfarmer.storage.FarmerProductStateRepository;
import xyz.geik.wesfarmer.storage.FarmerRecord;
import xyz.geik.wesfarmer.storage.FarmerSettings;
import xyz.geik.wesfarmer.storage.FarmerSettingsRepository;
import xyz.geik.wesfarmer.storage.FarmerStockRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class FarmerMenuGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int CONTENT_SIZE       = 45;
    private static final int PANEL_FILLER_LEFT  = 46;
    private static final int PANEL_FILLER_RIGHT = 52;
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    private final GuiConfigManager         guiConfigManager;
    private final ProductManager           productManager;
    private final FarmerStockRepository    stockRepository;
    private final FarmerProductLevelRepository levelRepository;
    private final FarmerSettingsRepository settingsRepository;
    private final FarmerHistoryRepository  historyRepository;
    private final FarmerMemberRepository   memberRepository;
    private final FarmerProductStateRepository stateRepository;

    public FarmerMenuGui(GuiConfigManager guiConfigManager,
                          ProductManager productManager,
                          FarmerStockRepository stockRepository,
                          FarmerProductLevelRepository levelRepository,
                          FarmerSettingsRepository settingsRepository,
                          FarmerHistoryRepository historyRepository,
                          FarmerMemberRepository memberRepository,
                          FarmerProductStateRepository stateRepository) {
        this.guiConfigManager  = guiConfigManager;
        this.productManager    = productManager;
        this.stockRepository   = stockRepository;
        this.levelRepository   = levelRepository;
        this.settingsRepository = settingsRepository;
        this.historyRepository = historyRepository;
        this.memberRepository  = memberRepository;
        this.stateRepository   = stateRepository;
    }

    public Inventory build(Player player, FarmerRecord record, int page) {
        GuiConfigManager.FarmerMenuSettings s = guiConfigManager.getFarmerMenuSettings();

        FarmerMenuGuiHolder holder = new FarmerMenuGuiHolder(record, page);
        Inventory inv = Bukkit.createInventory(holder, 54, MM.deserialize("<!italic>" + s.title()));
        holder.setInventory(inv);

        // ── Ürün slotları (0-44) ─────────────────────────────────────────────
        List<FarmerProduct> products = new ArrayList<>(productManager.getAll());
        int fromIndex = page * CONTENT_SIZE;
        int toIndex   = Math.min(fromIndex + CONTENT_SIZE, products.size());

        if (fromIndex < products.size()) {
            List<FarmerProduct> pageProducts = products.subList(fromIndex, toIndex);
            for (int i = 0; i < pageProducts.size(); i++) {
                inv.setItem(i, buildProductItem(pageProducts.get(i), record));
            }
        }

        // ── Kontrol paneli (45-53) ───────────────────────────────────────────
        ItemStack panelFill = GuiItemBuilder.build(s.panelFiller(), s.panelFillerName(), List.of());
        inv.setItem(PANEL_FILLER_LEFT,  panelFill);
        inv.setItem(PANEL_FILLER_RIGHT, panelFill);

        // Satış durumu burada hesapla (aşağıda tekrar kullanılıyor)
        FarmerSettings fs = settingsRepository.get(record.id());
        if (page > 0) {
            setItem(inv, s.prevSlot(), s.prevMat(), s.prevName(), s.prevLore());
        } else {
            inv.setItem(s.prevSlot(), panelFill);
        }

        boolean hasNext = (page + 1) * CONTENT_SIZE < products.size();
        if (hasNext) {
            setItem(inv, s.nextSlot(), s.nextMat(), s.nextName(), s.nextLore());
        } else {
            inv.setItem(s.nextSlot(), panelFill);
        }

        setItem(inv, s.histSlot(), s.histMat(), s.histName(), s.histLore());

        // İstatistikler butonu — kağıt, dinamik lore
        inv.setItem(s.statsSlot(), buildStatsItem(record));

        setItem(inv, s.ctrlSlot(), s.ctrlMat(), s.ctrlName(), s.ctrlLore());

        // Satış — kilitliyse kırmızı barrier
        if (fs.sellLocked()) {
            setItem(inv, s.sellSlot(), Material.BARRIER,
                    "<red>✖ <bold>ᴛümünü ѕᴀᴛ</bold> <dark_gray>[Kilitli]</dark_gray></red>",
                    List.of(
                            "",
                            "<red>Bilgiler:</red>",
                            " <white>Satışlar şu an kilitli.</white>",
                            " <white>Kontrol paneli → Ayarlar</white>",
                            " <white>üzerinden açabilirsin.</white>"
                    ));
        } else {
            setItem(inv, s.sellSlot(), s.sellMat(), s.sellName(), s.sellLore());
        }

        setItem(inv, s.upgSlot(), s.upgMat(), s.upgName(), s.upgLore());

        return inv;
    }

    // ── İstatistikler item ────────────────────────────────────────────────────

    private ItemStack buildStatsItem(FarmerRecord record) {
        long   farmerId       = record.id();
        double totalEarnings  = historyRepository.totalEarnings(farmerId);
        long   totalSold      = historyRepository.totalSoldAmount(farmerId);
        long   totalCollected = historyRepository.totalCollectedAmount(farmerId);
        long   sellTx         = historyRepository.totalSellTransactions(farmerId);
        String topSold        = formatMaterial(historyRepository.topSoldMaterial(farmerId));
        String topCollected   = formatMaterial(historyRepository.topCollectedMaterial(farmerId));
        long   lastTs         = historyRepository.lastActivityTimestamp(farmerId);
        int    memberCount    = memberRepository.countMembers(farmerId);

        String lastActivity = lastTs > 0
                ? DATE_FMT.format(new Date(lastTs * 1000L))
                : "Henüz yok";

        String ownerName = Bukkit.getServer()
                .getOfflinePlayer(record.ownerUuid()).getName();
        if (ownerName == null) ownerName = record.ownerUuid().toString().substring(0, 8) + "…";

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<#D09019>Genel Bakış</#D09019>");
        lore.add(" <white>▸ Sahip: <#facc15>" + ownerName + "</#facc15></white>");
        lore.add(" <white>▸ Üye Sayısı: <#a78bfa>" + memberCount + "</#a78bfa></white>");
        lore.add(" <white>▸ Konum: <gray>" + record.world()
                + " (" + (int) record.x() + ", " + (int) record.y()
                + ", " + (int) record.z() + ")</gray></white>");
        lore.add("");
        lore.add("<#D09019>Kazanç & Satış</#D09019>");
        lore.add(" <white>▸ Toplam Kazanç: <#4ade80>" + String.format("%.2f", totalEarnings) + "$</#4ade80></white>");
        lore.add(" <white>▸ Satış İşlemi: <#4ade80>" + sellTx + "</#4ade80></white>");
        lore.add(" <white>▸ Satılan Adet: <#4ade80>" + totalSold + "</#4ade80></white>");
        lore.add(" <white>▸ En Çok Satılan: <#4ade80>" + topSold + "</#4ade80></white>");
        lore.add("");
        lore.add("<#D09019>Toplama</#D09019>");
        lore.add(" <white>▸ Toplanan Adet: <#38bdf8>" + totalCollected + "</#38bdf8></white>");
        lore.add(" <white>▸ En Çok Toplanan: <#38bdf8>" + topCollected + "</#38bdf8></white>");
        lore.add("");
        lore.add("<#D09019>Son Aktivite</#D09019>");
        lore.add(" <white>▸ <#D09019>" + lastActivity + "</#D09019></white>");

        return GuiItemBuilder.build(Material.PAPER,
                "<#D09019>♦ <bold>ɪѕᴛᴀᴛɪѕᴛɪᴋʟᴇʀ</bold></#D09019>",
                lore);
    }

    // ── Ürün item ─────────────────────────────────────────────────────────────

    private ItemStack buildProductItem(FarmerProduct product, FarmerRecord record) {
        int    stock    = stockRepository.getStock(record.id(), product.material());
        int    level    = levelRepository.getLevel(record.id(), product.material());
        int    maxStock = FarmerProductLevelRepository.maxStockAtLevel(product.maxStock(), level);
        double fillPct  = maxStock > 0 ? (stock * 100.0 / maxStock) : 0;
        int    taxPct   = product.tax();
        String stockTag    = (maxStock > 0 && stock >= maxStock) ? "<red>" : "<#D09019>";
        String stockTagEnd = stockTag.replace("<", "</");

        // Farmer-bazlı collect/sell durumu — DB'den, yoksa products.yml varsayılanı
        boolean collectOn = stateRepository.getCollectEnabled(record.id(), product.material(), product.collectEnabled());
        boolean sellOn    = stateRepository.getSellEnabled(record.id(), product.material(), product.sellEnabled());

        // sellLocked / collectLocked — settings'ten al
        xyz.geik.wesfarmer.storage.FarmerSettings productFs = settingsRepository.get(record.id());
        boolean sellLocked     = productFs.sellLocked();
        boolean collectLocked2 = productFs.collectLocked();
        String  sellStatus    = sellLocked     ? "<red>Kilitli</red>" : (sellOn    ? "<green>Açık</green>" : "<red>Kapalı</red>");
        String  collectStatus = collectLocked2 ? "<red>Kilitli</red>" : (collectOn ? "<green>Açık</green>" : "<red>Kapalı</red>");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<#D09019>Genel Bakış</#D09019>");
        lore.add(" <white>▸ Mevcut Stok: " + stockTag + stock + "/" + maxStock + stockTagEnd + "</white>");
        lore.add(" <white>▸ Doluluk Oranı: <#D09019>%" + String.format("%.0f", fillPct) + "</#D09019></white>");
        lore.add(" <white>▸ Seviye: <#f97316>" + level + " / " + FarmerProductLevelRepository.MAX_LEVEL + "</#f97316></white>");
        lore.add(" <white>▸ Birim Değeri: <#D09019>" + String.format("%.2f", product.price())
                + "$</white> <dark_gray>(<yellow>%" + taxPct + " Vergi</yellow>)</dark_gray>");
        lore.add("");
        lore.add("<#D09019>Ayarlar</#D09019>");
        lore.add(" <white>▸ Satış: " + sellStatus + "</white>");
        lore.add(" <white>▸ Toplama: " + collectStatus + "</white>");
        lore.add("");
        lore.add("<#D09019>●</#D09019> <white><u><bold><#D09019>ʏöɴᴇᴛᴍᴇᴋ</#D09019></bold></u> ɪçɪɴ ᴛıᴋʟᴀ!</white>");

        return GuiItemBuilder.build(product.material(), product.displayName(), lore);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setItem(Inventory inv, int slot, Material mat, String name, List<String> lore) {
        if (slot < 0 || slot >= inv.getSize()) return;
        inv.setItem(slot, GuiItemBuilder.build(mat, name, lore));
    }

    private String formatMaterial(String rawName) {
        if (rawName == null || rawName.equals("—")) return "—";
        // Bilinen materyaller için Türkçe karşılıkları
        return switch (rawName.toUpperCase()) {
            case "WHEAT"             -> "Buğday";
            case "WHEAT_SEEDS"       -> "Buğday Tohumu";
            case "CARROT"            -> "Havuç";
            case "POTATO"            -> "Patates";
            case "BEETROOT"          -> "Pancar";
            case "BEETROOT_SEEDS"    -> "Pancar Tohumu";
            case "MELON"             -> "Karpuz";
            case "MELON_SEEDS"       -> "Karpuz Tohumu";
            case "PUMPKIN"           -> "Balkabağı";
            case "PUMPKIN_SEEDS"     -> "Balkabağı Tohumu";
            case "SUGAR_CANE"        -> "Şeker Kamışı";
            case "CACTUS"            -> "Kaktüs";
            case "BAMBOO"            -> "Bambu";
            case "SWEET_BERRIES"     -> "Tatlı Böğürtlen";
            case "GLOW_BERRIES"      -> "Işıltılı Böğürtlen";
            case "NETHER_WART"       -> "Nether Siğili";
            case "COCOA_BEANS"       -> "Kakao Fasulyesi";
            case "KELP"              -> "Yosun";
            case "SEA_PICKLE"        -> "Deniz Turşusu";
            case "CHORUS_FRUIT"      -> "Koro Meyvesi";
            case "APPLE"             -> "Elma";
            case "BROWN_MUSHROOM"    -> "Kahverengi Mantar";
            case "RED_MUSHROOM"      -> "Kırmızı Mantar";
            case "LILY_PAD"          -> "Su Zambağı";
            case "VINE"              -> "Sarmaşık";
            case "FERN"              -> "Eğreltiotu";
            case "GRASS"             -> "Çimen";
            case "TALL_GRASS"        -> "Uzun Çimen";
            case "DEAD_BUSH"         -> "Kuru Çalı";
            case "SUNFLOWER"         -> "Ayçiçeği";
            case "ROSE_BUSH"         -> "Gül Çalısı";
            case "PEONY"             -> "Şakayık";
            case "LILAC"             -> "Leylak";
            case "LARGE_FERN"        -> "Büyük Eğreltiotu";
            case "POPPY"             -> "Gelincik";
            case "DANDELION"         -> "Karahindiba";
            case "BLUE_ORCHID"       -> "Mavi Orkide";
            case "ALLIUM"            -> "Sarımsak Çiçeği";
            case "OXEYE_DAISY"       -> "Papatya";
            case "CORNFLOWER"        -> "Peygamber Çiçeği";
            case "LILY_OF_THE_VALLEY"-> "Vadi Zambağı";
            case "TORCHFLOWER"       -> "Meşale Çiçeği";
            case "PITCHER_PLANT"     -> "Sürahi Bitkisi";
            case "SPORE_BLOSSOM"     -> "Spor Çiçeği";
            case "STRING"            -> "İp";
            case "SPIDER_EYE"        -> "Örümcek Gözü";
            case "FEATHER"           -> "Tüy";
            case "LEATHER"           -> "Deri";
            case "INK_SAC"           -> "Mürekkep Kesesi";
            case "BONE"              -> "Kemik";
            case "SLIME_BALL"        -> "Slime Topu";
            case "BLAZE_ROD"         -> "Kor Değneği";
            case "GHAST_TEAR"        -> "Hayalet Gözyaşı";
            case "GOLD_NUGGET"       -> "Altın Nugget";
            case "IRON_NUGGET"       -> "Demir Nugget";
            case "GOLD_INGOT"        -> "Altın Külçe";
            case "IRON_INGOT"        -> "Demir Külçe";
            case "DIAMOND"           -> "Elmas";
            case "EMERALD"           -> "Zümrüt";
            case "LAPIS_LAZULI"      -> "Lapis Lazuli";
            default -> {
                // Bilinmeyen materyal: alt çizgileri boşluğa çevir, baş harfi büyüt
                String lower = rawName.replace("_", " ").toLowerCase();
                yield Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
            }
        };
    }
}
