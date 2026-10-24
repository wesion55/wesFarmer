package xyz.geik.wesfarmer.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import xyz.geik.wesfarmer.WesFarmer;
import xyz.geik.wesfarmer.config.ConfigManager;
import xyz.geik.wesfarmer.config.GuiConfigManager;
import xyz.geik.wesfarmer.config.MessageService;
import xyz.geik.wesfarmer.storage.FarmerMember;
import xyz.geik.wesfarmer.storage.FarmerMemberRepository;
import xyz.geik.wesfarmer.storage.FarmerRecord;
import xyz.geik.wesfarmer.storage.FarmerRole;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all clicks and chat input for the members management GUI.
 *
 * <p>Actions:
 * <ul>
 *   <li><b>Content slot (0-44), left-click:</b> cycle role MEMBER ↔ LEADER (LEADER+ only)</li>
 *   <li><b>Content slot (0-44), right-click:</b> remove member (LEADER+ only, cannot remove owner)</li>
 *   <li><b>Add button:</b> prompt chat input for a player name, add as MEMBER</li>
 *   <li><b>Back button:</b> return to control panel</li>
 *   <li><b>Prev / Next:</b> page navigation</li>
 * </ul>
 */
public final class MembersGuiListener implements Listener {

    /** UUID → farmerId for players currently waiting to type a member name in chat. */
    private static final Map<UUID, Long> PENDING_ADD = new ConcurrentHashMap<>();

    private final WesFarmer plugin;
    private final GuiConfigManager guiConfigManager;
    private final ConfigManager configManager;
    private final MessageService messageService;
    private final MembersGui membersGui;
    private final ControlPanelGui controlPanelGui;
    private final FarmerMemberRepository memberRepository;

    public MembersGuiListener(WesFarmer plugin,
                               GuiConfigManager guiConfigManager,
                               ConfigManager configManager,
                               MessageService messageService,
                               MembersGui membersGui,
                               ControlPanelGui controlPanelGui,
                               FarmerMemberRepository memberRepository) {
        this.plugin            = plugin;
        this.guiConfigManager  = guiConfigManager;
        this.configManager     = configManager;
        this.messageService    = messageService;
        this.membersGui        = membersGui;
        this.controlPanelGui   = controlPanelGui;
        this.memberRepository  = memberRepository;
    }

    // ── Inventory click ───────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MembersGuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        GuiConfigManager.MembersMenuSettings s = guiConfigManager.getMembersMenuSettings();
        FarmerRecord record  = holder.getRecord();
        int          slot    = event.getRawSlot();
        boolean      isRight = event.getClick().name().contains("RIGHT");

        // ── Navigasyon butonları ─────────────────────────────────────────────
        if (slot == s.backSlot()) {
            SoundHelper.playClick(player);
            player.openInventory(controlPanelGui.build(player, record));
            return;
        }

        if (slot == s.prevSlot()) {
            int newPage = holder.getPage() - 1;
            if (newPage < 0) return;
            SoundHelper.playClick(player);
            player.openInventory(membersGui.build(player, record, newPage));
            return;
        }

        if (slot == s.nextSlot()) {
            SoundHelper.playClick(player);
            player.openInventory(membersGui.build(player, record, holder.getPage() + 1));
            return;
        }

        // ── Üye ekle ─────────────────────────────────────────────────────────
        if (slot == s.addSlot()) {
            if (!memberRepository.hasAccess(record.id(), player.getUniqueId(), FarmerRole.LEADER)) {
                messageService.send(player, "no-permission");
                return;
            }
            // Limit kontrolü
            int currentCount = memberRepository.countMembers(record.id());
            int maxMembers   = configManager.getSettings().maxMembers();
            if (maxMembers > 0 && currentCount >= maxMembers + 1) { // +1 for owner
                messageService.send(player, "member-limit-reached");
                return;
            }
            player.closeInventory();
            PENDING_ADD.put(player.getUniqueId(), record.id());
            messageService.send(player, "member-add-prompt");
            return;
        }

        // ── İçerik alanı (0-44): üye slotları ────────────────────────────────
        if (slot < 0 || slot >= 45) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) return;

        // Sadece LEADER+ yönetebilir
        if (!memberRepository.hasAccess(record.id(), player.getUniqueId(), FarmerRole.LEADER)) {
            messageService.send(player, "no-permission");
            return;
        }

        // Hangi üyeye tıklandığını bul
        List<FarmerMember> members = memberRepository.findByFarmer(record.id());
        int index = holder.getPage() * 45 + slot;
        if (index >= members.size()) return;
        FarmerMember target = members.get(index);

        // Sahibi değiştirme / çıkarma yasak
        if (target.role() == FarmerRole.OWNER) {
            messageService.send(player, "member-cannot-modify-owner");
            return;
        }

        // LEADER başka bir LEADER'ı yönetemez — sadece OWNER yönetebilir
        if (target.role() == FarmerRole.LEADER) {
            FarmerRole actorRole = memberRepository.getRole(record.id(), player.getUniqueId());
            if (actorRole != FarmerRole.OWNER) {
                messageService.send(player, "no-permission");
                return;
            }
        }

        if (isRight) {
            // Sağ tık → üyeyi kaldır
            memberRepository.remove(record.id(), target.uuid());
            messageService.send(player, "member-removed",
                    MessageService.Placeholder.of("%player%", target.name()));
        } else {
            // Sol tık → MEMBER ↔ LEADER döngüsü
            FarmerRole next = (target.role() == FarmerRole.MEMBER) ? FarmerRole.LEADER : FarmerRole.MEMBER;
            memberRepository.updateRole(record.id(), target.uuid(), next);
            String newRoleName = configManager.getSettings().roleDisplayName(next);
            messageService.send(player, "member-role-changed",
                    MessageService.Placeholder.of("%player%", target.name()),
                    MessageService.Placeholder.of("%role%", newRoleName));
        }

        SoundHelper.playClick(player);
        player.openInventory(membersGui.build(player, record, holder.getPage()));
    }

    // ── Chat: üye adı girişi ──────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Long farmerId = PENDING_ADD.remove(player.getUniqueId());
        if (farmerId == null) return;

        event.setCancelled(true);

        String input = PlainTextComponentSerializer.plainText()
                .serialize(event.message()).trim();

        if (input.equalsIgnoreCase("iptal") || input.equalsIgnoreCase("cancel")) {
            messageService.send(player, "member-add-cancelled");
            return;
        }

        // Main thread'e geç — Bukkit API ve DB
        final long fid = farmerId;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Oyuncuyu isimle bul (online veya offline)
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(input);

            if (!target.hasPlayedBefore() && !target.isOnline()) {
                messageService.send(player, "admin-player-not-found",
                        MessageService.Placeholder.of("%player%", input));
                return;
            }

            UUID targetUuid = target.getUniqueId();
            String targetName = target.getName() != null ? target.getName() : input;

            // Zaten üye mi?
            if (memberRepository.find(fid, targetUuid).isPresent()) {
                messageService.send(player, "member-already-member",
                        MessageService.Placeholder.of("%player%", targetName));
                return;
            }

            // Limit tekrar kontrol (race condition önlemi)
            int currentCount = memberRepository.countMembers(fid);
            int maxMembers   = configManager.getSettings().maxMembers();
            if (maxMembers > 0 && currentCount >= maxMembers + 1) {
                messageService.send(player, "member-limit-reached");
                return;
            }

            memberRepository.save(new FarmerMember(fid, targetUuid, targetName, FarmerRole.MEMBER));
            messageService.send(player, "member-added",
                    MessageService.Placeholder.of("%player%", targetName));

            // Ekleyene GUI'yi yeniden aç
            FarmerRecord record = plugin.getFarmerRepository().findById(fid);
            if (record != null) {
                player.openInventory(membersGui.build(player, record, 0));
            }
        });
    }

    /** Cancel pending add if the player logs out or closes the inventory. */
    public static void cancelPending(UUID uuid) {
        PENDING_ADD.remove(uuid);
    }
}
