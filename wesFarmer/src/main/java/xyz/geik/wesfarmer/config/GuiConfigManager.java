package xyz.geik.wesfarmer.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

/**
 * Her {@code guis/*.yml} dosyasını yükleyip değiştirilemez bir settings
 * record'unda önbelleğe alır.
 */
public final class GuiConfigManager {

    private final JavaPlugin plugin;
    private volatile BuyGuiSettings buySettings;
    private volatile ConfirmGuiSettings confirmSettings;
    private volatile RemoveConfirmGuiSettings removeConfirmSettings;
    private volatile FarmerMenuSettings farmerMenuSettings;
    private volatile ProductDetailSettings productDetailSettings;
    private volatile HistoryMenuSettings historyMenuSettings;
    private volatile ControlPanelSettings controlPanelSettings;
    private volatile MembersMenuSettings membersMenuSettings;
    private volatile PermissionsMenuSettings permissionsMenuSettings;
    private volatile SettingsMenuSettings settingsMenuSettings;
    private volatile StatsMenuSettings statsMenuSettings;

    public GuiConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        this.buySettings           = loadBuyGui();
        this.confirmSettings       = loadConfirmGui();
        this.removeConfirmSettings = loadRemoveConfirmGui();
        this.farmerMenuSettings    = loadFarmerMenu();
        this.productDetailSettings = loadProductDetailMenu();
        this.historyMenuSettings   = loadHistoryMenu();
        this.controlPanelSettings  = loadControlPanel();
        this.membersMenuSettings   = loadMembersMenu();
        this.permissionsMenuSettings = loadPermissionsMenu();
        this.settingsMenuSettings    = loadSettingsMenu();
        this.statsMenuSettings       = loadStatsMenu();
    }

    public void reload() { load(); }

    public BuyGuiSettings getBuySettings()                    { return buySettings; }
    public ConfirmGuiSettings getConfirmSettings()            { return confirmSettings; }
    public RemoveConfirmGuiSettings getRemoveConfirmSettings() { return removeConfirmSettings; }
    public FarmerMenuSettings getFarmerMenuSettings()         { return farmerMenuSettings; }
    public ProductDetailSettings getProductDetailSettings()   { return productDetailSettings; }
    public HistoryMenuSettings getHistoryMenuSettings()        { return historyMenuSettings; }
    public ControlPanelSettings getControlPanelSettings()      { return controlPanelSettings; }
    public MembersMenuSettings getMembersMenuSettings()         { return membersMenuSettings; }
    public PermissionsMenuSettings getPermissionsMenuSettings() { return permissionsMenuSettings; }
    public SettingsMenuSettings getSettingsMenuSettings()       { return settingsMenuSettings; }
    public StatsMenuSettings getStatsMenuSettings()             { return statsMenuSettings; }

    // -------------------------------------------------------------------------
    // Loaders
    // -------------------------------------------------------------------------

    private BuyGuiSettings loadBuyGui() {
        YamlConfiguration c = loadGuiFile("guis/buy.yml");
        String title         = c.getString("title", "wesfarmer");
        int    size          = normalizeInventorySize(c.getInt("size", 27));
        Material fillerMat   = parseMaterial(c.getString("filler.material"), Material.BLACK_STAINED_GLASS_PANE);
        String fillerName    = c.getString("filler.name", "");
        int    buySlot       = c.getInt("buy-item.slot", 13);
        Material buyMat      = parseMaterial(c.getString("buy-item.material"), Material.HAY_BLOCK);
        String buyName       = c.getString("buy-item.display-name", "<#00ff8d>♦ <bold>çiftçi satın al</bold>");
        List<String> buyLore = c.getStringList("buy-item.lore");
        return new BuyGuiSettings(title, size, fillerMat, fillerName, buySlot, buyMat, buyName, buyLore);
    }

    private ConfirmGuiSettings loadConfirmGui() {
        YamlConfiguration c  = loadGuiFile("guis/confirm.yml");
        String title          = c.getString("title", "wesfarmer → onay");
        int    size           = normalizeInventorySize(c.getInt("size", 27));
        Material fillerMat    = parseMaterial(c.getString("filler.material"), Material.BLACK_STAINED_GLASS_PANE);
        String fillerName     = c.getString("filler.name", "");
        int    confirmSlot    = c.getInt("confirm-item.slot", 11);
        Material confirmMat   = parseMaterial(c.getString("confirm-item.material"), Material.LIME_STAINED_GLASS_PANE);
        String confirmName    = c.getString("confirm-item.display-name", "<green>✔ <bold>onayla</bold></green>");
        List<String> confirmLore = c.getStringList("confirm-item.lore");
        int    cancelSlot     = c.getInt("cancel-item.slot", 15);
        Material cancelMat    = parseMaterial(c.getString("cancel-item.material"), Material.RED_STAINED_GLASS_PANE);
        String cancelName     = c.getString("cancel-item.display-name", "<red>❌ <bold>iptal</bold></red>");
        List<String> cancelLore = c.getStringList("cancel-item.lore");
        return new ConfirmGuiSettings(title, size, fillerMat, fillerName,
                confirmSlot, confirmMat, confirmName, confirmLore,
                cancelSlot, cancelMat, cancelName, cancelLore);
    }

    private RemoveConfirmGuiSettings loadRemoveConfirmGui() {
        YamlConfiguration c  = loadGuiFile("guis/remove-confirm.yml");
        String title          = c.getString("title", "wesfarmer → sil");
        int    size           = normalizeInventorySize(c.getInt("size", 27));
        Material fillerMat    = parseMaterial(c.getString("filler.material"), Material.BLACK_STAINED_GLASS_PANE);
        String fillerName     = c.getString("filler.name", "");
        int    confirmSlot    = c.getInt("confirm-item.slot", 11);
        Material confirmMat   = parseMaterial(c.getString("confirm-item.material"), Material.RED_STAINED_GLASS_PANE);
        String confirmName    = c.getString("confirm-item.display-name", "<red>✖ <bold>sil</bold></red>");
        List<String> confirmLore = c.getStringList("confirm-item.lore");
        int    cancelSlot     = c.getInt("cancel-item.slot", 15);
        Material cancelMat    = parseMaterial(c.getString("cancel-item.material"), Material.LIME_STAINED_GLASS_PANE);
        String cancelName     = c.getString("cancel-item.display-name", "<green>✔ <bold>iptal</bold></green>");
        List<String> cancelLore = c.getStringList("cancel-item.lore");
        return new RemoveConfirmGuiSettings(title, size, fillerMat, fillerName,
                confirmSlot, confirmMat, confirmName, confirmLore,
                cancelSlot, cancelMat, cancelName, cancelLore);
    }

    private FarmerMenuSettings loadFarmerMenu() {
        YamlConfiguration c = loadGuiFile("guis/farmer-menu.yml");

        String title = c.getString("title", "<gradient:#D09019:#E6C400><bold>çɪꜰᴛçɪ ᴘᴀɴᴇʟɪ</bold></gradient>");

        Material panelFiller       = parseMaterial(c.getString("panel-filler.material"), Material.BLACK_STAINED_GLASS_PANE);
        String   panelFillerName   = c.getString("panel-filler.name", "");

        int      prevSlot  = c.getInt("previous-page.slot", 45);
        Material prevMat   = parseMaterial(c.getString("previous-page.material"), Material.ARROW);
        String   prevName  = c.getString("previous-page.display-name", "<#ffea00>← <bold>öɴᴄᴇᴋɪ ѕᴀʏғᴀ</bold></#ffea00>");
        List<String> prevLore = c.getStringList("previous-page.lore");

        int      histSlot  = c.getInt("history.slot", 47);
        Material histMat   = parseMaterial(c.getString("history.material"), Material.BOOK);
        String   histName  = c.getString("history.display-name", "<#D09019>♦ <bold>ɪşʟᴇᴍ ɢᴇçᴍɪşɪ</bold></#D09019>");
        List<String> histLore = c.getStringList("history.lore");

        int      statsSlot = c.getInt("stats.slot", 48);
        Material statsMat  = parseMaterial(c.getString("stats.material"), Material.GOLD_INGOT);
        String   statsName = c.getString("stats.display-name", "<#D09019>♦ <bold>ɪѕᴛᴀᴛɪѕᴛɪᴋʟᴇʀ</bold></#D09019>");
        List<String> statsLore = c.getStringList("stats.lore");

        int      ctrlSlot  = c.getInt("control-panel.slot", 49);
        Material ctrlMat   = parseMaterial(c.getString("control-panel.material"), Material.COMPARATOR);
        String   ctrlName  = c.getString("control-panel.display-name", "<#D09019>♦ <bold>ᴋᴏɴᴛʀᴏʟ ᴘᴀɴᴇʟɪ</bold></#D09019>");
        List<String> ctrlLore = c.getStringList("control-panel.lore");

        int      sellSlot  = c.getInt("sell-all.slot", 50);
        Material sellMat   = parseMaterial(c.getString("sell-all.material"), Material.EMERALD);
        String   sellName  = c.getString("sell-all.display-name", "<#D09019>♦ <bold>ᴛümünü ѕᴀᴛ</bold></#D09019>");
        List<String> sellLore = c.getStringList("sell-all.lore");

        int      upgSlot   = c.getInt("upgrade-all.slot", 51);
        Material upgMat    = parseMaterial(c.getString("upgrade-all.material"), Material.EXPERIENCE_BOTTLE);
        String   upgName   = c.getString("upgrade-all.display-name", "<#D09019>♦ <bold>ᴛᴏᴘʟᴜ ʏüᴋѕᴇʟᴛ</bold></#D09019>");
        List<String> upgLore = c.getStringList("upgrade-all.lore");

        int      nextSlot  = c.getInt("next-page.slot", 53);
        Material nextMat   = parseMaterial(c.getString("next-page.material"), Material.ARROW);
        String   nextName  = c.getString("next-page.display-name", "<#ffea00>→ <bold>ѕᴏɴʀᴀᴋɪ ѕᴀʏғᴀ</bold></#ffea00>");
        List<String> nextLore = c.getStringList("next-page.lore");

        return new FarmerMenuSettings(
                title, panelFiller, panelFillerName,
                prevSlot, prevMat, prevName, prevLore,
                histSlot, histMat, histName, histLore,
                statsSlot, statsMat, statsName, statsLore,
                ctrlSlot, ctrlMat, ctrlName, ctrlLore,
                sellSlot, sellMat, sellName, sellLore,
                upgSlot,  upgMat,  upgName,  upgLore,
                nextSlot, nextMat, nextName, nextLore
        );
    }

    private ProductDetailSettings loadProductDetailMenu() {
        YamlConfiguration c = loadGuiFile("guis/product-detail-menu.yml");

        String title       = c.getString("title", "çɪꜰᴛçɪ → üʀüɴ");
        Material fillerMat = parseMaterial(c.getString("filler.material"), Material.BLACK_STAINED_GLASS_PANE);
        String fillerName  = c.getString("filler.name", "");

        int      backSlot  = c.getInt("back.slot", 40);
        Material backMat   = parseMaterial(c.getString("back.material"), Material.ARROW);
        String   backName  = c.getString("back.display-name", "<#ffea00>← <bold>ɢᴇʀɪ</bold></#ffea00>");
        List<String> backLore = c.getStringList("back.lore");

        int      toggleSlot    = c.getInt("collect-toggle.slot", 20);
        Material toggleMatOn   = parseMaterial(c.getString("collect-toggle.material-enabled"),  Material.HOPPER);
        Material toggleMatOff  = parseMaterial(c.getString("collect-toggle.material-disabled"), Material.HOPPER);
        String   toggleNameOn  = c.getString("collect-toggle.display-name-enabled",  "<#4ade80>♦ <bold>ᴛᴏᴘʟᴀᴍᴀ</bold></#4ade80>");
        String   toggleNameOff = c.getString("collect-toggle.display-name-disabled", "<red>♦ <bold>ᴛᴏᴘʟᴀᴍᴀ</bold></red>");
        List<String> toggleLore = c.getStringList("collect-toggle.lore");

        int      sellToggleSlot    = c.getInt("sell-toggle.slot", 19);
        Material sellToggleMatOn   = parseMaterial(c.getString("sell-toggle.material-enabled"),  Material.EMERALD);
        Material sellToggleMatOff  = parseMaterial(c.getString("sell-toggle.material-disabled"), Material.REDSTONE);
        String   sellToggleNameOn  = c.getString("sell-toggle.display-name-enabled",  "<#4ade80>♦ <bold>ѕᴀᴛɪş</bold></#4ade80>");
        String   sellToggleNameOff = c.getString("sell-toggle.display-name-disabled", "<red>♦ <bold>ѕᴀᴛɪş</bold></red>");
        List<String> sellToggleLore = c.getStringList("sell-toggle.lore");

        int productSlot = c.getInt("product-icon.slot", 13);

        int      sellSlot  = c.getInt("sell-all.slot", 25);
        Material sellMat   = parseMaterial(c.getString("sell-all.material"), Material.EMERALD);
        String   sellName  = c.getString("sell-all.display-name", "<#4ade80>♦ <bold>ʜᴇᴘꜱɪɴɪ ꜱᴀᴛ</bold></#4ade80>");
        List<String> sellLore = c.getStringList("sell-all.lore");

        int      takeSlot  = c.getInt("take-to-inventory.slot", 24);
        Material takeMat   = parseMaterial(c.getString("take-to-inventory.material"), Material.CHEST);
        String   takeName  = c.getString("take-to-inventory.display-name", "<#38bdf8>♦ <bold>ᴇɴᴠᴀɴᴛᴇʀᴇ ᴀʟ</bold></#38bdf8>");
        List<String> takeLore = c.getStringList("take-to-inventory.lore");

        int      upgradeSlot = c.getInt("upgrade.slot", 22);
        Material upgradeMat  = parseMaterial(c.getString("upgrade.material"), Material.ANVIL);
        String   upgradeName = c.getString("upgrade.display-name", "<#f97316>♦ <bold>ѕᴇᴠɪʏᴇ ʏüᴋѕᴇʟᴛ</bold></#f97316>");
        List<String> upgradeLore = c.getStringList("upgrade.lore");

        return new ProductDetailSettings(
                title, fillerMat, fillerName,
                backSlot, backMat, backName, backLore,
                toggleSlot, toggleMatOn, toggleMatOff, toggleNameOn, toggleNameOff, toggleLore,
                sellToggleSlot, sellToggleMatOn, sellToggleMatOff, sellToggleNameOn, sellToggleNameOff, sellToggleLore,
                productSlot,
                sellSlot, sellMat, sellName, sellLore,
                takeSlot, takeMat, takeName, takeLore,
                upgradeSlot, upgradeMat, upgradeName, upgradeLore
        );    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private YamlConfiguration loadGuiFile(String relativePath) {
        File file = new File(plugin.getDataFolder(), relativePath);
        if (!file.exists()) {
            try {
                plugin.saveResource(relativePath, false);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[WesFarmer] '" + relativePath
                        + "' jar içinde bulunamadı, varsayılan değerler kullanılacak.");
                return new YamlConfiguration();
            }
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) return fallback;
        Material m = Material.matchMaterial(name);
        if (m == null) {
            plugin.getLogger().warning("Geçersiz materyal '" + name + "', varsayılan " + fallback + " kullanılıyor.");
            return fallback;
        }
        return m;
    }

    private int normalizeInventorySize(int size) {
        int clamped   = Math.max(9, Math.min(54, size));
        int remainder = clamped % 9;
        return remainder == 0 ? clamped : clamped - remainder;
    }

    // -------------------------------------------------------------------------
    // Records
    // -------------------------------------------------------------------------

    public record BuyGuiSettings(
            String title, int size,
            Material fillerMaterial, String fillerName,
            int buySlot, Material buyMaterial, String buyName, List<String> buyLore
    ) {}

    public record ConfirmGuiSettings(
            String title, int size,
            Material fillerMaterial, String fillerName,
            int confirmSlot, Material confirmMaterial, String confirmName, List<String> confirmLore,
            int cancelSlot,  Material cancelMaterial,  String cancelName,  List<String> cancelLore
    ) {}

    public record RemoveConfirmGuiSettings(
            String title, int size,
            Material fillerMaterial, String fillerName,
            int confirmSlot, Material confirmMaterial, String confirmName, List<String> confirmLore,
            int cancelSlot,  Material cancelMaterial,  String cancelName,  List<String> cancelLore
    ) {}

    public record FarmerMenuSettings(
            String title,
            Material panelFiller, String panelFillerName,
            // Önceki sayfa
            int prevSlot, Material prevMat, String prevName, List<String> prevLore,
            // İşlem geçmişi
            int histSlot, Material histMat, String histName, List<String> histLore,
            // İstatistikler
            int statsSlot, Material statsMat, String statsName, List<String> statsLore,
            // Kontrol paneli
            int ctrlSlot, Material ctrlMat, String ctrlName, List<String> ctrlLore,
            // Tümünü sat
            int sellSlot, Material sellMat, String sellName, List<String> sellLore,
            // Toplu yükselt
            int upgSlot, Material upgMat, String upgName, List<String> upgLore,
            // Sonraki sayfa
            int nextSlot, Material nextMat, String nextName, List<String> nextLore
    ) {}

    private HistoryMenuSettings loadHistoryMenu() {
        YamlConfiguration c = loadGuiFile("guis/history-menu.yml");

        String title      = c.getString("title", "çɪꜰᴛçɪ → ɢᴇçᴍɪş");
        Material fillerMat = parseMaterial(c.getString("filler.material"), Material.BLACK_STAINED_GLASS_PANE);
        String fillerName  = c.getString("filler.name", "");

        int      backSlot  = c.getInt("back.slot", 49);
        Material backMat   = parseMaterial(c.getString("back.material"), Material.ARROW);
        String   backName  = c.getString("back.display-name", "<#ffea00>← <bold>ɢᴇʀɪ</bold></#ffea00>");
        List<String> backLore = c.getStringList("back.lore");

        int      prevSlot  = c.getInt("previous-page.slot", 45);
        Material prevMat   = parseMaterial(c.getString("previous-page.material"), Material.ARROW);
        String   prevName  = c.getString("previous-page.display-name", "<#ffea00>← <bold>öɴᴄᴇᴋɪ ѕᴀʏғᴀ</bold></#ffea00>");
        List<String> prevLore = c.getStringList("previous-page.lore");

        int      nextSlot  = c.getInt("next-page.slot", 53);
        Material nextMat   = parseMaterial(c.getString("next-page.material"), Material.ARROW);
        String   nextName  = c.getString("next-page.display-name", "<#ffea00>→ <bold>ѕᴏɴʀᴀᴋɪ ѕᴀʏғᴀ</bold></#ffea00>");
        List<String> nextLore = c.getStringList("next-page.lore");

        return new HistoryMenuSettings(title, fillerMat, fillerName,
                backSlot, backMat, backName, backLore,
                prevSlot, prevMat, prevName, prevLore,
                nextSlot, nextMat, nextName, nextLore);
    }

    public record HistoryMenuSettings(
            String title,
            Material fillerMaterial, String fillerName,
            int backSlot, Material backMat, String backName, List<String> backLore,
            int prevSlot, Material prevMat, String prevName, List<String> prevLore,
            int nextSlot, Material nextMat, String nextName, List<String> nextLore
    ) {}

    private ControlPanelSettings loadControlPanel() {
        YamlConfiguration c = loadGuiFile("guis/control-panel-menu.yml");

        String title      = c.getString("title", "çɪꜰᴛçɪ → ᴋᴏɴᴛʀᴏʟ");
        Material fillerMat = parseMaterial(c.getString("filler.material"), Material.BLACK_STAINED_GLASS_PANE);
        String fillerName  = c.getString("filler.name", "");

        int      backSlot  = c.getInt("back.slot", 49);
        Material backMat   = parseMaterial(c.getString("back.material"), Material.ARROW);
        String   backName  = c.getString("back.display-name", "<#ffea00>← <bold>ɢᴇʀɪ</bold></#ffea00>");
        List<String> backLore = c.getStringList("back.lore");

        int      statsSlot   = c.getInt("stats-title.slot", 11);
        Material statsMat    = parseMaterial(c.getString("stats-title.material"), Material.BOOK);
        String   statsName   = c.getString("stats-title.display-name", "<#D09019>♦ <bold>ɪѕᴛᴀᴛɪѕᴛɪᴋʟᴇʀ</bold></#D09019>");
        List<String> statsLore = c.getStringList("stats-title.lore");

        int      settingsSlot = c.getInt("settings-title.slot", 13);
        Material settingsMat  = parseMaterial(c.getString("settings-title.material"), Material.COMPARATOR);
        String   settingsName = c.getString("settings-title.display-name", "<#f97316>♦ <bold>ᴀʏᴀʀʟᴀʀ</bold></#f97316>");
        List<String> settingsLore = c.getStringList("settings-title.lore");

        int      membersSlot = c.getInt("members-title.slot", 14);
        Material membersMat  = parseMaterial(c.getString("members-title.material"), Material.PLAYER_HEAD);
        String   membersName = c.getString("members-title.display-name", "<#38bdf8>♦ <bold>üʏᴇʟᴇʀ</bold></#38bdf8>");
        List<String> membersLore = c.getStringList("members-title.lore");

        int      permsSlot = c.getInt("permissions-title.slot", 12);
        Material permsMat  = parseMaterial(c.getString("permissions-title.material"), Material.WRITABLE_BOOK);
        String   permsName = c.getString("permissions-title.display-name", "<#a78bfa>♦ <bold>ɪᴢɪɴʟᴇʀ</bold></#a78bfa>");
        List<String> permsLore = c.getStringList("permissions-title.lore");

        return new ControlPanelSettings(
                title, fillerMat, fillerName,
                backSlot, backMat, backName, backLore,
                settingsSlot, settingsMat, settingsName, settingsLore,
                membersSlot, membersMat, membersName, membersLore,
                permsSlot, permsMat, permsName, permsLore
        );
    }

    public record ControlPanelSettings(
            String title,
            Material fillerMat, String fillerName,
            int backSlot, Material backMat, String backName, List<String> backLore,
            int settingsSlot, Material settingsMat, String settingsName, List<String> settingsLore,
            int membersSlot,  Material membersMat,  String membersName,  List<String> membersLore,
            int permsSlot,    Material permsMat,    String permsName,    List<String> permsLore
    ) {}

    public record ProductDetailSettings(
            String title,
            Material fillerMaterial, String fillerName,
            int backSlot, Material backMat, String backName, List<String> backLore,
            // Toplama toggle
            int toggleSlot, Material toggleMatOn, Material toggleMatOff,
            String toggleNameOn, String toggleNameOff, List<String> toggleLore,
            // Satış toggle
            int sellToggleSlot, Material sellToggleMatOn, Material sellToggleMatOff,
            String sellToggleNameOn, String sellToggleNameOff, List<String> sellToggleLore,
            int productSlot,
            int sellSlot, Material sellMat, String sellName, List<String> sellLore,
            int takeSlot, Material takeMat, String takeName, List<String> takeLore,
            int upgradeSlot, Material upgradeMat, String upgradeName, List<String> upgradeLore
    ) {}

    // ── Members GUI ──────────────────────────────────────────────────────────

    private MembersMenuSettings loadMembersMenu() {
        YamlConfiguration c = loadGuiFile("guis/members-menu.yml");

        String   title     = c.getString("title", "çɪꜰᴛçɪ → üʏᴇʟᴇʀ");
        Material fillerMat = parseMaterial(c.getString("filler.material"), Material.BLACK_STAINED_GLASS_PANE);
        String   fillerName = c.getString("filler.name", "");

        int      backSlot  = c.getInt("back.slot", 49);
        Material backMat   = parseMaterial(c.getString("back.material"), Material.ARROW);
        String   backName  = c.getString("back.display-name", "<#ffea00>← <bold>ɢᴇʀɪ</bold></#ffea00>");
        List<String> backLore = c.getStringList("back.lore");

        int      addSlot   = c.getInt("add-member.slot", 50);
        Material addMat    = parseMaterial(c.getString("add-member.material"), Material.LIME_DYE);
        String   addName   = c.getString("add-member.display-name", "<#4ade80>✚ <bold>üʏᴇ ᴇᴋʟᴇ</bold></#4ade80>");
        List<String> addLore = c.getStringList("add-member.lore");

        int      prevSlot  = c.getInt("previous-page.slot", 45);
        Material prevMat   = parseMaterial(c.getString("previous-page.material"), Material.ARROW);
        String   prevName  = c.getString("previous-page.display-name", "<#ffea00>← <bold>öɴᴄᴇᴋɪ</bold></#ffea00>");
        List<String> prevLore = c.getStringList("previous-page.lore");

        int      nextSlot  = c.getInt("next-page.slot", 53);
        Material nextMat   = parseMaterial(c.getString("next-page.material"), Material.ARROW);
        String   nextName  = c.getString("next-page.display-name", "<#ffea00>→ <bold>ѕᴏɴʀᴀᴋɪ</bold></#ffea00>");
        List<String> nextLore = c.getStringList("next-page.lore");

        // Rol ikonları
        Material memberMat = parseMaterial(c.getString("role-icons.member.material"), Material.CYAN_DYE);
        Material leaderMat = parseMaterial(c.getString("role-icons.leader.material"), Material.ORANGE_DYE);
        Material ownerMat  = parseMaterial(c.getString("role-icons.owner.material"),  Material.YELLOW_DYE);

        return new MembersMenuSettings(
                title, fillerMat, fillerName,
                backSlot, backMat, backName, backLore,
                addSlot,  addMat,  addName,  addLore,
                prevSlot, prevMat, prevName, prevLore,
                nextSlot, nextMat, nextName, nextLore,
                memberMat, leaderMat, ownerMat
        );
    }

    public record MembersMenuSettings(
            String title,
            Material fillerMat, String fillerName,
            int backSlot, Material backMat, String backName, List<String> backLore,
            int addSlot,  Material addMat,  String addName,  List<String> addLore,
            int prevSlot, Material prevMat, String prevName, List<String> prevLore,
            int nextSlot, Material nextMat, String nextName, List<String> nextLore,
            Material memberRoleIcon,
            Material leaderRoleIcon,
            Material ownerRoleIcon
    ) {}

    // ── Permissions GUI ───────────────────────────────────────────────────────

    private PermissionsMenuSettings loadPermissionsMenu() {
        YamlConfiguration c = loadGuiFile("guis/permissions-menu.yml");

        String   title    = c.getString("title", "çɪꜰᴛçɪ → ɪᴢɪɴʟᴇʀ");
        int      backSlot = c.getInt("back.slot", 31);
        Material backMat  = parseMaterial(c.getString("back.material"), Material.ARROW);
        String   backName = c.getString("back.display-name", "<#ffea00>← <bold>ɢᴇʀɪ</bold></#ffea00>");
        List<String> backLore = c.getStringList("back.lore");

        return new PermissionsMenuSettings(title, backSlot, backMat, backName, backLore);
    }

    public record PermissionsMenuSettings(
            String title,
            int backSlot, Material backMat, String backName, List<String> backLore
    ) {}

    // ── Settings GUI ──────────────────────────────────────────────────────────

    private SettingsMenuSettings loadSettingsMenu() {
        YamlConfiguration c = loadGuiFile("guis/settings-menu.yml");

        String   title    = c.getString("title", "çɪꜰᴛçɪ → ᴀʏᴀʀʟᴀʀ");
        int      backSlot = c.getInt("back.slot", 31);
        Material backMat  = parseMaterial(c.getString("back.material"), Material.ARROW);
        String   backName = c.getString("back.display-name", "<#ffea00>← <bold>ɢᴇʀɪ</bold></#ffea00>");
        List<String> backLore = c.getStringList("back.lore");

        return new SettingsMenuSettings(title, backSlot, backMat, backName, backLore);
    }

    public record SettingsMenuSettings(
            String title,
            int backSlot, Material backMat, String backName, List<String> backLore
    ) {}

    // ── Stats GUI ─────────────────────────────────────────────────────────────

    private StatsMenuSettings loadStatsMenu() {
        YamlConfiguration c = loadGuiFile("guis/stats-menu.yml");

        String   title    = c.getString("title", "çɪꜰᴛçɪ → ɪѕᴛᴀᴛɪѕᴛɪᴋʟᴇʀ");
        int      backSlot = c.getInt("back.slot", 31);
        Material backMat  = parseMaterial(c.getString("back.material"), Material.ARROW);
        String   backName = c.getString("back.display-name", "<#ffea00>← <bold>ɢᴇʀɪ</bold></#ffea00>");
        List<String> backLore = c.getStringList("back.lore");

        return new StatsMenuSettings(title, backSlot, backMat, backName, backLore);
    }

    public record StatsMenuSettings(
            String title,
            int backSlot, Material backMat, String backName, List<String> backLore
    ) {}
}
