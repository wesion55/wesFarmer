package xyz.geik.wesfarmer.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.geik.wesfarmer.WesFarmer;
import xyz.geik.wesfarmer.config.ConfigManager;
import xyz.geik.wesfarmer.config.GuiConfigManager;
import xyz.geik.wesfarmer.config.MessageService;
import xyz.geik.wesfarmer.gui.BuyGui;
import xyz.geik.wesfarmer.gui.ConfirmGui;
import xyz.geik.wesfarmer.gui.FarmerMenuGui;
import xyz.geik.wesfarmer.gui.RemoveConfirmGui;
import xyz.geik.wesfarmer.purchase.PurchaseService;
import xyz.geik.wesfarmer.integration.hologram.FancyHologramsHook;
import xyz.geik.wesfarmer.integration.npc.FancyNpcsHook;
import xyz.geik.wesfarmer.integration.claim.ClaimGuard;
import xyz.geik.wesfarmer.storage.FarmerRecord;
import xyz.geik.wesfarmer.storage.FarmerRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static xyz.geik.wesfarmer.config.MessageService.Placeholder.of;

/**
 * Komut yapısı:
 * <pre>
 *  /çiftçi buy                          — satın alma panelini açar
 *  /çiftçi list                         — kendi çiftçilerini listeler
 *  /çiftçi remove                       — claimdeki çiftçiyi sil (onay GUI'si)
 *  /çiftçi move                         — claimdeki çiftçiyi yeni konuma taşı
 *  /çiftçi admin list    <oyuncu>        — oyuncunun çiftçilerini listeler
 *  /çiftçi admin remove  <oyuncu> <npc> — tek çiftçi siler
 *  /çiftçi admin removeall <oyuncu>     — oyuncunun tüm çiftçilerini siler
 *  /çiftçi admin info    <npc>          — NPC detaylarını gösterir
 *  /çiftçi admin reload                 — config + hologramları yeniler
 * </pre>
 */
public final class WesFarmerCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT_SUBS  = List.of("buy", "list", "remove", "move", "menu", "admin");
    private static final List<String> ADMIN_SUBS = List.of("list", "remove", "removeall", "info", "reload", "setlevel", "log");

    private final WesFarmer plugin;
    private final ConfigManager configManager;
    private final GuiConfigManager guiConfigManager;
    private final MessageService messageService;
    private final BuyGui buyGui;
    private final ClaimGuard claimGuard;
    private final RemoveConfirmGui removeConfirmGui;
    private final ConfirmGui confirmGui;
    private final PurchaseService purchaseService;
    private final FarmerMenuGui farmerMenuGui;
    private final xyz.geik.wesfarmer.gui.AdminLogGui adminLogGui;

    public WesFarmerCommand(WesFarmer plugin, ConfigManager configManager,
                             GuiConfigManager guiConfigManager,
                             MessageService messageService, BuyGui buyGui,
                             ClaimGuard claimGuard, ConfirmGui confirmGui,
                             PurchaseService purchaseService, FarmerMenuGui farmerMenuGui,
                             xyz.geik.wesfarmer.gui.AdminLogGui adminLogGui) {
        this.plugin           = plugin;
        this.configManager    = configManager;
        this.guiConfigManager = guiConfigManager;
        this.messageService   = messageService;
        this.buyGui           = buyGui;
        this.claimGuard       = claimGuard;
        this.removeConfirmGui = new RemoveConfirmGui(guiConfigManager);
        this.confirmGui       = confirmGui;
        this.purchaseService  = purchaseService;
        this.farmerMenuGui    = farmerMenuGui;
        this.adminLogGui      = adminLogGui;
    }

    // -------------------------------------------------------------------------
    // Dispatch
    // -------------------------------------------------------------------------

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            // Argümansız /çiftçi → menu komutunu tetikle
            handleMenu(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "buy"    -> handleBuy(sender);
            case "list"   -> handleList(sender);
            case "remove" -> handleRemove(sender, args);
            case "move"   -> handleMove(sender);
            case "menu"   -> handleMenu(sender);
            case "admin"  -> handleAdmin(sender, args);
            default       -> messageService.send(sender, "usage");
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // /çiftçi buy
    // -------------------------------------------------------------------------

    private void handleBuy(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "player-only");
            return;
        }
        if (!player.hasPermission("wesfarmer.buy")) {
            messageService.send(player, "no-permission");
            return;
        }
        player.openInventory(buyGui.build(player));
    }

    // -------------------------------------------------------------------------
    // /çiftçi list
    // -------------------------------------------------------------------------

    private void handleList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "player-only");
            return;
        }
        if (!player.hasPermission("wesfarmer.list")) {
            messageService.send(player, "no-permission");
            return;
        }

        plugin.getAsyncFarmerRepository().findByOwner(player.getUniqueId())
                .thenAccept(farmers -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (farmers.isEmpty()) {
                        messageService.send(player, "list-empty");
                        return;
                    }
                    messageService.send(player, "list-header",
                            of("%count%", String.valueOf(farmers.size())));
                    for (FarmerRecord f : farmers) {
                        messageService.send(player, "list-entry",
                                of("%npc%",   f.npcName()),
                                of("%world%", f.world()),
                                of("%x%",     String.valueOf((int) f.x())),
                                of("%y%",     String.valueOf((int) f.y())),
                                of("%z%",     String.valueOf((int) f.z())));
                    }
                }));
    }

    // -------------------------------------------------------------------------
    // /çiftçi remove
    // -------------------------------------------------------------------------

    private void handleRemove(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "player-only");
            return;
        }
        if (!player.hasPermission("wesfarmer.remove")) {
            messageService.send(player, "no-permission");
            return;
        }

        // Oyuncunun ayakta durduğu claim'deki farmeri bul (GriefPrevention VEYA
        // WorldGuard — hangisi kuruluysa; eskiden yalnizca GriefPrevention
        // kontrol edildigi icin WorldGuard'da bu komut hep basarisiz oluyordu).
        String claimKey = claimGuard.claimKey(player.getLocation());

        if (claimKey == null) {
            // Claim yoksa (wilderness) — hiçbir şey yapma
            messageService.send(player, "remove-not-in-claim");
            return;
        }

        // DB'de bu claim'e ait, bu oyuncuya ait farmer var mı?
        List<FarmerRecord> farmers = plugin.getFarmerRepository()
                .findByOwner(player.getUniqueId());

        FarmerRecord target = farmers.stream()
                .filter(f -> f.claimKey().equals(claimKey))
                .findFirst()
                .orElse(null);

        if (target == null) {
            messageService.send(player, "remove-no-farmer-in-claim");
            return;
        }

        // Onay GUI'sini aç
        player.openInventory(removeConfirmGui.build(target.npcName()));
    }

    // -------------------------------------------------------------------------
    // /çiftçi menu
    // -------------------------------------------------------------------------

    private void handleMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "player-only");
            return;
        }
        if (!player.hasPermission("wesfarmer.menu")) {
            messageService.send(player, "no-permission");
            return;
        }

        // Oyuncunun bulunduğu claim'i bul
        String claimKey = claimGuard.claimKey(player.getLocation());
        if (claimKey == null) {
            messageService.send(player, "menu-not-in-claim");
            return;
        }

        // O claimdeki çiftçiyi bul
        FarmerRecord target = plugin.getFarmerRepository()
                .findByOwner(player.getUniqueId()).stream()
                .filter(f -> f.claimKey().equals(claimKey))
                .findFirst()
                .orElse(null);

        if (target == null) {
            messageService.send(player, "remove-no-farmer-in-claim");
            return;
        }

        player.openInventory(farmerMenuGui.build(player, target, 0));
    }

    // -------------------------------------------------------------------------
    // /çiftçi move
    // -------------------------------------------------------------------------

    private void handleMove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "player-only");
            return;
        }
        if (!player.hasPermission("wesfarmer.move")) {
            messageService.send(player, "no-permission");
            return;
        }

        String claimKey = claimGuard.claimKey(player.getLocation());
        if (claimKey == null) {
            messageService.send(player, "move-not-in-claim");
            return;
        }

        FarmerRecord target = plugin.getFarmerRepository()
                .findByOwner(player.getUniqueId()).stream()
                .filter(f -> f.claimKey().equals(claimKey))
                .findFirst()
                .orElse(null);

        if (target == null) {
            messageService.send(player, "move-no-farmer-in-claim");
            return;
        }

        purchaseService.moveFarmer(player, target);
    }

    // -------------------------------------------------------------------------
    // /çiftçi admin <sub> [args…]
    // -------------------------------------------------------------------------

    private void handleAdmin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (!sender.hasPermission("wesfarmer.admin")) {
                messageService.send(sender, "no-permission");
                return;
            }
            messageService.send(sender, "admin-usage");
            return;
        }

        String sub  = args[1].toLowerCase();
        String perm = "wesfarmer.admin." + sub;

        if (!sender.hasPermission(perm)) {
            messageService.send(sender, "no-permission");
            return;
        }

        switch (sub) {
            case "list"      -> handleAdminList(sender, args);
            case "remove"    -> handleAdminRemove(sender, args);
            case "removeall" -> handleAdminRemoveAll(sender, args);
            case "info"      -> handleAdminInfo(sender, args);
            case "reload"    -> handleAdminReload(sender);
            case "setlevel"  -> handleAdminSetLevel(sender, args);
            case "log"       -> handleAdminLog(sender, args);
            default          -> messageService.send(sender, "admin-usage");
        }
    }

    // /çiftçi admin list <oyuncu>
    private void handleAdminList(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messageService.send(sender, "admin-usage");
            return;
        }

        String targetName = args[2];

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID targetUuid = resolveUuid(targetName);
            if (targetUuid == null) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        messageService.send(sender, "admin-player-not-found", of("%player%", targetName)));
                return;
            }

            plugin.getAsyncFarmerRepository().findByOwner(targetUuid)
                    .thenAccept(farmers -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (farmers.isEmpty()) {
                            messageService.send(sender, "admin-list-empty", of("%player%", targetName));
                            return;
                        }
                        messageService.send(sender, "admin-list-header",
                                of("%player%", targetName),
                                of("%count%",  String.valueOf(farmers.size())));
                        for (FarmerRecord f : farmers) {
                            messageService.send(sender, "admin-list-entry",
                                    of("%npc%",   f.npcName()),
                                    of("%world%", f.world()),
                                    of("%x%",     String.valueOf((int) f.x())),
                                    of("%y%",     String.valueOf((int) f.y())),
                                    of("%z%",     String.valueOf((int) f.z())));
                        }
                    }));
        });
    }

    // /çiftçi admin remove <oyuncu> <npc_adı>
    private void handleAdminRemove(CommandSender sender, String[] args) {
        if (args.length < 4) {
            messageService.send(sender, "admin-usage");
            return;
        }

        String targetName = args[2];
        String npcName    = args[3];
        FarmerRepository repo   = plugin.getFarmerRepository();
        FarmerRecord     record = repo.findByNpcName(npcName);

        if (record == null) {
            messageService.send(sender, "admin-farmer-not-found", of("%npc%", npcName));
            return;
        }

        // Yazılan oyuncu adı ile NPC'nin gerçek sahibini karşılaştır.
        // Önce çevrimiçi oyunculara bak (cache, hızlı), sonra offline.
        String actualOwnerName = resolvePlayerName(record.ownerUuid());
        if (!targetName.equalsIgnoreCase(actualOwnerName)) {
            messageService.send(sender, "admin-remove-owner-mismatch",
                    of("%npc%",    npcName),
                    of("%actual%", actualOwnerName),
                    of("%given%",  targetName));
            return;
        }

        if (FancyNpcsHook.isAvailable()) FancyNpcsHook.removeNpc(record.npcName());
        if (FancyHologramsHook.isAvailable() && record.holoName() != null) {
            FancyHologramsHook.removeHologram(record.holoName());
        }

        repo.deleteByNpcName(npcName);
        plugin.getFarmerMemberRepository().removeAllByFarmer(record.id());
        plugin.getFarmerSettingsRepository().delete(record.id());
        plugin.getFarmerStockRepository().clearStock(record.id());
        plugin.getFarmerProductLevelRepository().deleteByFarmer(record.id());
        plugin.getFarmerHistoryRepository().deleteByFarmer(record.id());
        plugin.getFarmerProductStateRepository().deleteByFarmer(record.id());
        xyz.geik.wesfarmer.task.CollectTask ct = xyz.geik.wesfarmer.task.CollectTask.getInstance();
        if (ct != null) ct.cacheRemove(record.claimKey());
        messageService.send(sender, "admin-remove-success",
                of("%npc%",    npcName),
                of("%player%", actualOwnerName));
    }

    // /çiftçi admin removeall <oyuncu>
    private void handleAdminRemoveAll(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messageService.send(sender, "admin-usage");
            return;
        }

        if (!(sender instanceof Player admin)) {
            messageService.send(sender, "player-only");
            return;
        }

        String targetName = args[2];

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID targetUuid = resolveUuid(targetName);
            if (targetUuid == null) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        messageService.send(admin, "admin-player-not-found", of("%player%", targetName)));
                return;
            }

            plugin.getAsyncFarmerRepository().findByOwner(targetUuid)
                    .thenAccept(farmers -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (farmers.isEmpty()) {
                            messageService.send(admin, "admin-list-empty", of("%player%", targetName));
                            return;
                        }

                        Runnable deleteAction = () -> {
                            for (FarmerRecord f : farmers) {
                                if (FancyNpcsHook.isAvailable()) FancyNpcsHook.removeNpc(f.npcName());
                                if (FancyHologramsHook.isAvailable() && f.holoName() != null) {
                                    FancyHologramsHook.removeHologram(f.holoName());
                                }
                                plugin.getFarmerMemberRepository().removeAllByFarmer(f.id());
                                plugin.getFarmerSettingsRepository().delete(f.id());
                                plugin.getFarmerStockRepository().clearStock(f.id());
                                plugin.getFarmerProductLevelRepository().deleteByFarmer(f.id());
                                plugin.getFarmerHistoryRepository().deleteByFarmer(f.id());
                                plugin.getFarmerProductStateRepository().deleteByFarmer(f.id());
                                xyz.geik.wesfarmer.task.CollectTask ct2 = xyz.geik.wesfarmer.task.CollectTask.getInstance();
                                if (ct2 != null) ct2.cacheRemove(f.claimKey());
                            }
                            // DB silme async
                            plugin.getAsyncFarmerRepository().deleteAllByOwner(targetUuid)
                                    .thenAccept(deleted -> plugin.getServer().getScheduler().runTask(plugin, () ->
                                            messageService.send(admin, "admin-removeall-success",
                                                    of("%player%", targetName),
                                                    of("%count%",  String.valueOf(deleted)))));
                        };

                        messageService.send(admin, "admin-removeall-confirm-hint",
                                of("%player%", targetName),
                                of("%count%",  String.valueOf(farmers.size())));
                        admin.openInventory(confirmGui.buildAction(deleteAction, "admin-removeall-cancelled"));
                    }));
        });
    }

    // /çiftçi admin info <npc_adı>
    private void handleAdminInfo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messageService.send(sender, "admin-usage");
            return;
        }

        FarmerRecord record = plugin.getFarmerRepository().findByNpcName(args[2]);
        if (record == null) {
            messageService.send(sender, "admin-farmer-not-found", of("%npc%", args[2]));
            return;
        }

        String ownerName = resolvePlayerName(record.ownerUuid());
        messageService.send(sender, "admin-info-owner",    of("%player%", ownerName));
        messageService.send(sender, "admin-info-npc",      of("%npc%",    record.npcName()));
        messageService.send(sender, "admin-info-holo",     of("%holo%",   record.holoName() != null ? record.holoName() : "yok"));
        messageService.send(sender, "admin-info-location",
                of("%world%", record.world()),
                of("%x%", String.valueOf((int) record.x())),
                of("%y%", String.valueOf((int) record.y())),
                of("%z%", String.valueOf((int) record.z())));
        messageService.send(sender, "admin-info-claim", of("%claim%", record.claimKey()));
    }

    // /çiftçi admin reload
    private void handleAdminReload(CommandSender sender) {
        configManager.reload();
        guiConfigManager.reload();
        messageService.load();
        plugin.getProductManager().reload();

        if (FancyHologramsHook.isAvailable()) {
            ConfigManager.Settings settings = configManager.getSettings();

            plugin.getAsyncFarmerRepository().findAll()
                    .thenAccept(allFarmers -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        for (FarmerRecord farmer : allFarmers) {
                            if (farmer.holoName() == null) continue;
                            String resolved = resolvePlayerName(farmer.ownerUuid());
                            List<String> updatedLines = settings.hologramLines().stream()
                                    .map(line -> line.replace("%player%", resolved))
                                    .collect(Collectors.toList());
                            FancyHologramsHook.updateHologramLines(farmer.holoName(), updatedLines,
                                    settings.hologramBackground(), settings.hologramTextShadow());
                        }
                    }));
        }

        messageService.send(sender, "reload-success");
    }

    // /çiftçi admin log <npc_adı>
    private void handleAdminLog(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            messageService.send(sender, "player-only");
            return;
        }
        if (args.length < 3) {
            messageService.send(sender, "admin-usage");
            return;
        }
        String npcName = args[2];
        xyz.geik.wesfarmer.storage.FarmerRecord record =
                plugin.getFarmerRepository().findByNpcName(npcName);
        if (record == null) {
            messageService.send(sender, "admin-farmer-not-found", of("%npc%", npcName));
            return;
        }
        player.openInventory(adminLogGui.build(player, record, 0));
    }

    // /çiftçi admin setlevel <oyuncu> <materyal|all> <seviye>
    @SuppressWarnings("deprecation")
    private void handleAdminSetLevel(CommandSender sender, String[] args) {
        // args: [admin, setlevel, oyuncu, materyal|all, seviye]
        if (args.length < 5) {
            messageService.send(sender, "admin-setlevel-usage");
            return;
        }

        String targetName = args[2];
        String materialArg = args[3].toUpperCase();
        int level;
        try {
            level = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            messageService.send(sender, "take-invalid-number");
            return;
        }

        if (level < 1 || level > xyz.geik.wesfarmer.storage.FarmerProductLevelRepository.MAX_LEVEL) {
            messageService.send(sender, "admin-setlevel-invalid",
                    of("%max%", String.valueOf(xyz.geik.wesfarmer.storage.FarmerProductLevelRepository.MAX_LEVEL)));
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID targetUuid = resolveUuid(targetName);
            if (targetUuid == null) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        messageService.send(sender, "admin-player-not-found", of("%player%", targetName)));
                return;
            }

            var repo = plugin.getFarmerProductLevelRepository();
            List<xyz.geik.wesfarmer.storage.FarmerRecord> farmers =
                    plugin.getFarmerRepository().findByOwner(targetUuid);

            if (farmers.isEmpty()) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        messageService.send(sender, "admin-list-empty", of("%player%", targetName)));
                return;
            }

            final int finalLevel = level;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (materialArg.equals("ALL")) {
                    // Tüm ürünler için set et
                    for (xyz.geik.wesfarmer.storage.FarmerRecord farmer : farmers) {
                        for (var product : plugin.getProductManager().getAll()) {
                            repo.setLevel(farmer.id(), product.material(), finalLevel);
                        }
                    }
                    messageService.send(sender, "admin-setlevel-all-success",
                            of("%player%", targetName),
                            of("%level%",  String.valueOf(finalLevel)));
                } else {
                    org.bukkit.Material mat = org.bukkit.Material.matchMaterial(materialArg);
                    if (mat == null) {
                        messageService.send(sender, "admin-setlevel-invalid-material",
                                of("%material%", materialArg));
                        return;
                    }
                    for (xyz.geik.wesfarmer.storage.FarmerRecord farmer : farmers) {
                        repo.setLevel(farmer.id(), mat, finalLevel);
                    }
                    messageService.send(sender, "admin-setlevel-success",
                            of("%player%",   targetName),
                            of("%material%", materialArg),
                            of("%level%",    String.valueOf(finalLevel)));
                }
            });
        });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return filter(ROOT_SUBS, args[0]);

        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return filter(ADMIN_SUBS, args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            String sub = args[1].toLowerCase();
            if (sub.equals("list") || sub.equals("remove") || sub.equals("removeall") || sub.equals("setlevel")) {
                return plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (sub.equals("info")) {
                return plugin.getFarmerRepository().findAll().stream()
                        .map(FarmerRecord::npcName)
                        .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("admin")
                && args[1].equalsIgnoreCase("setlevel")) {
            List<String> materials = new ArrayList<>();
            materials.add("all");
            plugin.getProductManager().getAll().stream()
                    .map(p -> p.material().name())
                    .filter(n -> n.toLowerCase().startsWith(args[3].toLowerCase()))
                    .forEach(materials::add);
            return materials.stream()
                    .filter(n -> n.toLowerCase().startsWith(args[3].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("admin")
                && args[1].equalsIgnoreCase("setlevel")) {
            return java.util.stream.IntStream.rangeClosed(1,
                    xyz.geik.wesfarmer.storage.FarmerProductLevelRepository.MAX_LEVEL)
                    .mapToObj(String::valueOf)
                    .filter(n -> n.startsWith(args[4]))
                    .collect(Collectors.toList());
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("admin")
                && args[1].equalsIgnoreCase("remove")) {
            return plugin.getFarmerRepository().findAll().stream()
                    .map(FarmerRecord::npcName)
                    .filter(n -> n.toLowerCase().startsWith(args[3].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return List.of();
    }

    private List<String> filter(List<String> source, String partial) {
        return source.stream()
                .filter(s -> s.toLowerCase().startsWith(partial.toLowerCase()))
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Helpers — UUID / isim çözümleme (blocking Mojang çağrısı yapmaz)
    // -------------------------------------------------------------------------

    /**
     * UUID'yi oyuncu adına çevirir.
     * Önce çevrimiçi oyuncular arasında bakar (cache, sıfır gecikme).
     * Bulamazsa Bukkit'in yerel offline cache'ini kullanır — bu hiçbir
     * durumda ağ isteği atmaz; sunucuya hiç girmemiş bir oyuncu için
     * UUID string'i döner.
     */
    private String resolvePlayerName(UUID uuid) {
        // 1. Online cache — en hızlı yol
        Player online = plugin.getServer().getPlayer(uuid);
        if (online != null) return online.getName();

        // 2. Bukkit'in usercache.json'u — hiç ağ isteği atmaz,
        //    sunucuya en az bir kez giriş yapmış oyuncular burada bulunur.
        //    NOT: getOfflinePlayer(UUID) burada güvenlidir; UUID zaten
        //    biliniyor olduğundan Mojang API'si asla çağrılmaz.
        @SuppressWarnings("deprecation")
        String cached = plugin.getServer().getOfflinePlayer(uuid).getName();
        return cached != null ? cached : uuid.toString();
    }

    /**
     * Oyuncu adını UUID'ye çevirir.
     * Önce çevrimiçi oyunculara bakar; yoksa Bukkit usercache'ine bakar.
     * Hiçbir yerde bulunamazsa {@code null} döner — bu noktada çağıran
     * kodu oyuncunun hiç girmediğini varsayabilir.
     *
     * <p>Bu metod async bir task içinden çağrılmak üzere tasarlanmıştır
     * (ağ isteği atmaz, ancak I/O yapabilir).
     */
    @Nullable
    private UUID resolveUuid(String name) {
        // 1. Online cache
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null) return online.getUniqueId();

        // 2. usercache.json — getPlayerExact başarısız oldu, ama cache'e bak.
        //    getOfflinePlayer(String) UNgüvenlidir ama biz bunu async
        //    task içinden çağırıyoruz, bu yüzden ana thread bloklanmaz.
        @SuppressWarnings("deprecation")
        var offline = plugin.getServer().getOfflinePlayer(name);
        // hasPlayedBefore() kontrolü: hiç girmemişse UUID güvenilir değil
        return offline.hasPlayedBefore() ? offline.getUniqueId() : null;
    }
}
