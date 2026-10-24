package xyz.geik.wesfarmer;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.geik.wesfarmer.command.WesFarmerCommand;
import xyz.geik.wesfarmer.config.ConfigManager;
import xyz.geik.wesfarmer.config.GuiConfigManager;
import xyz.geik.wesfarmer.config.MessageService;
import xyz.geik.wesfarmer.economy.EconomyService;
import xyz.geik.wesfarmer.gui.*;
import xyz.geik.wesfarmer.gui.GuiSoundListener;
import xyz.geik.wesfarmer.integration.claim.ClaimGuard;
import xyz.geik.wesfarmer.integration.hologram.FancyHologramsHook;
import xyz.geik.wesfarmer.integration.npc.FancyNpcsHook;
import xyz.geik.wesfarmer.integration.npc.FancyNpcsInteractListener;
import xyz.geik.wesfarmer.integration.placeholder.WesFarmerExpansion;
import xyz.geik.wesfarmer.product.ProductManager;
import xyz.geik.wesfarmer.purchase.PurchaseService;
import xyz.geik.wesfarmer.sell.SellService;
import xyz.geik.wesfarmer.startup.FarmerRestoreService;
import xyz.geik.wesfarmer.storage.*;
import xyz.geik.wesfarmer.task.CollectTask;
public final class WesFarmer extends JavaPlugin {

    private ConfigManager configManager;
    private GuiConfigManager guiConfigManager;
    private MessageService messageService;
    private EconomyService economyService;
    private FarmerRepository farmerRepository;
    private AsyncFarmerRepository asyncFarmerRepository;
    private FarmerStockRepository farmerStockRepository;
    private FarmerHistoryRepository farmerHistoryRepository;
    private FarmerProductLevelRepository farmerProductLevelRepository;
    private FarmerMemberRepository farmerMemberRepository;
    private FarmerSettingsRepository farmerSettingsRepository;
    private FarmerProductStateRepository farmerProductStateRepository;
    private ClaimGuard claimGuard;
    private ProductManager productManager;
    private SellService sellService;
    private WesFarmerExpansion placeholderExpansion;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        configManager.load();

        this.guiConfigManager = new GuiConfigManager(this);
        guiConfigManager.load();

        this.messageService = new MessageService(this);
        messageService.load();

        this.economyService = new EconomyService();
        economyService.setup();
        if (!economyService.isAvailable()) {
            getLogger().warning("Vault/ekonomi eklentisi bulunamadı. Satın alma çalışmayacak.");
        }

        this.farmerRepository = new FarmerRepository(this);
        if (!farmerRepository.init()) {
            getLogger().severe("[WesFarmer] Veritabanı başlatılamadı, plugin devre dışı bırakılıyor.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.asyncFarmerRepository    = new AsyncFarmerRepository(farmerRepository, this);
        this.farmerStockRepository    = new FarmerStockRepository(this, farmerRepository.getConnection());
        this.farmerHistoryRepository  = new FarmerHistoryRepository(this, farmerRepository.getConnection());
        this.farmerProductLevelRepository = new FarmerProductLevelRepository(this, farmerRepository.getConnection());
        farmerStockRepository.init();
        farmerHistoryRepository.init();
        farmerProductLevelRepository.init();
        this.farmerMemberRepository = new FarmerMemberRepository(this, farmerRepository.getConnection());
        farmerMemberRepository.init();
        this.farmerSettingsRepository = new FarmerSettingsRepository(this, farmerRepository.getConnection());
        farmerSettingsRepository.init();
        this.farmerProductStateRepository = new FarmerProductStateRepository(this, farmerRepository.getConnection());
        farmerProductStateRepository.init();

        this.productManager = new ProductManager(this);
        productManager.load();

        this.sellService = new SellService(economyService, messageService, productManager,
                farmerStockRepository, farmerHistoryRepository, farmerSettingsRepository,
                farmerProductStateRepository);

        this.claimGuard = new ClaimGuard();

        // ── GUI builder'ları ──────────────────────────────────────────────────
        BuyGui           buyGui           = new BuyGui(configManager, guiConfigManager, economyService);
        ConfirmGui       confirmGui       = new ConfirmGui(configManager, guiConfigManager, economyService);
        FarmerMenuGui    farmerMenuGui    = new FarmerMenuGui(guiConfigManager, productManager,
                farmerStockRepository, farmerProductLevelRepository, farmerSettingsRepository,
                farmerHistoryRepository, farmerMemberRepository, farmerProductStateRepository);
        ProductDetailGui productDetailGui = new ProductDetailGui(guiConfigManager,
                farmerStockRepository, farmerProductLevelRepository, farmerProductStateRepository);
        HistoryGui       historyGui       = new HistoryGui(this, guiConfigManager, farmerHistoryRepository);
        MembersGui       membersGui       = new MembersGui(guiConfigManager, configManager, farmerMemberRepository);
        ControlPanelGui  controlPanelGui  = new ControlPanelGui(guiConfigManager);
        PermissionsGui   permissionsGui   = new PermissionsGui(guiConfigManager, configManager);
        SettingsGui      settingsGui      = new SettingsGui(guiConfigManager, productManager);
        AdminLogGui      adminLogGui      = new AdminLogGui(farmerHistoryRepository);

        PurchaseService purchaseService = new PurchaseService(configManager, messageService,
                economyService, claimGuard, confirmGui, farmerRepository,
                farmerMemberRepository, farmerSettingsRepository);

        // ── Event listener'lar ────────────────────────────────────────────────
        getServer().getPluginManager().registerEvents(
                new BuyGuiListener(guiConfigManager, purchaseService), this);
        getServer().getPluginManager().registerEvents(
                new ConfirmGuiListener(this, guiConfigManager, purchaseService, messageService), this);
        getServer().getPluginManager().registerEvents(
                new RemoveConfirmGuiListener(this, guiConfigManager, messageService, farmerRepository), this);
        getServer().getPluginManager().registerEvents(
                new FarmerMenuGuiListener(this, guiConfigManager, messageService, farmerMenuGui,
                        productDetailGui, historyGui, sellService, confirmGui), this);
        getServer().getPluginManager().registerEvents(
                new ProductDetailGuiListener(this, guiConfigManager, messageService,
                        productDetailGui, farmerMenuGui, sellService, farmerStockRepository), this);
        getServer().getPluginManager().registerEvents(
                new HistoryGuiListener(this, guiConfigManager, historyGui, farmerMenuGui), this);
        getServer().getPluginManager().registerEvents(
                new ControlPanelGuiListener(this, guiConfigManager, messageService,
                        farmerMenuGui, membersGui, permissionsGui, settingsGui), this);
        getServer().getPluginManager().registerEvents(
                new SettingsGuiListener(this, guiConfigManager, configManager, messageService,
                        settingsGui, controlPanelGui, farmerSettingsRepository), this);
        getServer().getPluginManager().registerEvents(
                new MembersGuiListener(this, guiConfigManager, configManager, messageService,
                        membersGui, controlPanelGui, farmerMemberRepository), this);
        getServer().getPluginManager().registerEvents(
                new PermissionsGuiListener(this, guiConfigManager, configManager, messageService,
                        permissionsGui, controlPanelGui), this);
        getServer().getPluginManager().registerEvents(
                new AdminLogGuiListener(adminLogGui), this);
        getServer().getPluginManager().registerEvents(
                new GuiSoundListener(), this);
        getServer().getPluginManager().registerEvents(
                new xyz.geik.wesfarmer.listener.PlayerJoinListener(this, farmerMemberRepository), this);
        // Claim silinme dinleyicisi
        xyz.geik.wesfarmer.listener.ClaimDeleteListener claimDeleteListener =
                new xyz.geik.wesfarmer.listener.ClaimDeleteListener(this, farmerRepository);
        getServer().getPluginManager().registerEvents(claimDeleteListener, this);
        // GriefPrevention ClaimDeletedEvent + ClaimResizeEvent — sadece GP yüklüyse
        if (getServer().getPluginManager().isPluginEnabled("GriefPrevention")) {
            getServer().getPluginManager().registerEvents(
                    new xyz.geik.wesfarmer.listener.GpClaimDeleteListener(
                            claimDeleteListener, farmerRepository), this);
        }
        // SuperiorSkyblock2 IslandDisbandEvent — sadece SSB yüklüyse
        if (getServer().getPluginManager().isPluginEnabled("SuperiorSkyblock2")) {
            getServer().getPluginManager().registerEvents(
                    new xyz.geik.wesfarmer.listener.SsbIslandDisbandListener(
                            claimDeleteListener, farmerRepository), this);
        }

        if (FancyNpcsHook.isAvailable()) {
            getServer().getPluginManager().registerEvents(
                    new FancyNpcsInteractListener(messageService, farmerRepository,
                            farmerMemberRepository, farmerMenuGui), this);
        }

        // ── Komut ─────────────────────────────────────────────────────────────
        WesFarmerCommand commandExecutor = new WesFarmerCommand(this, configManager,
                guiConfigManager, messageService, buyGui, claimGuard, confirmGui,
                purchaseService, farmerMenuGui, adminLogGui);
        PluginCommand command = getCommand("wesfarmer");
        if (command != null) {
            command.setExecutor(commandExecutor);
            command.setTabCompleter(commandExecutor);
        }

        // ── PlaceholderAPI ────────────────────────────────────────────────────
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholderExpansion = new WesFarmerExpansion(this, farmerRepository,
                    farmerStockRepository, farmerHistoryRepository, farmerProductLevelRepository);
            placeholderExpansion.register();
            getLogger().info("PlaceholderAPI expansion kayıtlandı.");
        }

        // ── Startup görevleri ─────────────────────────────────────────────────
        new FarmerRestoreService(this, farmerRepository, configManager, farmerSettingsRepository).runAsync();
        CollectTask.start(this);
        xyz.geik.wesfarmer.task.StockNotificationTask.start(this);

        getLogger().info("WesFarmer etkinleştirildi.");
    }

    @Override
    public void onDisable() {
        if (placeholderExpansion != null) placeholderExpansion.unregister();

        // Cache'leri temizle (memory leak önleme)
        if (farmerSettingsRepository    != null) farmerSettingsRepository.clearCache();
        if (farmerProductLevelRepository != null) farmerProductLevelRepository.clearCache();
        if (farmerProductStateRepository != null) farmerProductStateRepository.clearCache();
        if (farmerMemberRepository       != null) farmerMemberRepository.clearCache();

        if (farmerRepository != null && farmerRepository.isAvailable()) {
            try {
                for (FarmerRecord record : farmerRepository.findAll()) {
                    if (FancyNpcsHook.isAvailable())       FancyNpcsHook.removeNpc(record.npcName());
                    if (FancyHologramsHook.isAvailable() && record.holoName() != null)
                        FancyHologramsHook.removeHologram(record.holoName());
                }
                getLogger().info("[WesFarmer] Tüm NPC ve hologramlar temizlendi.");
            } catch (Exception e) {
                getLogger().warning("[WesFarmer] NPC/hologram temizlenirken hata: " + e.getMessage());
            }
            farmerRepository.close();
        }

        getLogger().info("WesFarmer devre dışı bırakıldı.");
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public ConfigManager getConfigManager()                     { return configManager; }
    public GuiConfigManager getGuiConfigManager()               { return guiConfigManager; }
    public MessageService getMessageService()                   { return messageService; }
    public EconomyService getEconomyService()                   { return economyService; }
    public FarmerRepository getFarmerRepository()               { return farmerRepository; }
    public AsyncFarmerRepository getAsyncFarmerRepository()     { return asyncFarmerRepository; }
    public FarmerStockRepository getFarmerStockRepository()          { return farmerStockRepository; }
    public FarmerHistoryRepository getFarmerHistoryRepository()       { return farmerHistoryRepository; }
    public FarmerProductLevelRepository getFarmerProductLevelRepository() { return farmerProductLevelRepository; }
    public FarmerMemberRepository getFarmerMemberRepository()             { return farmerMemberRepository; }
    public FarmerSettingsRepository getFarmerSettingsRepository()         { return farmerSettingsRepository; }
    public FarmerProductStateRepository getFarmerProductStateRepository() { return farmerProductStateRepository; }
    public ClaimGuard getClaimGuard()                                 { return claimGuard; }
    public ProductManager getProductManager()                         { return productManager; }
    public SellService getSellService()                               { return sellService; }
}
