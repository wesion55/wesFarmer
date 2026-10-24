package xyz.geik.wesfarmer.gui;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import xyz.geik.wesfarmer.config.ConfigManager;
import xyz.geik.wesfarmer.config.GuiConfigManager;
import xyz.geik.wesfarmer.storage.FarmerMember;
import xyz.geik.wesfarmer.storage.FarmerMemberRepository;
import xyz.geik.wesfarmer.storage.FarmerRecord;
import xyz.geik.wesfarmer.storage.FarmerRole;

import java.util.ArrayList;
import java.util.List;

/**
 * Çiftçi üye yönetim GUI'si (54 slot).
 *
 * <p>Üye başına 1 slot: sol tıkla rol döngüsü (MEMBER → LEADER → MEMBER),
 * sağ tıkla üyeyi kaldır. Sahip slotu değiştirilemez (sadece bilgi gösterir).
 *
 * <p>Alt satır: önceki | — | — | — | geri | üye ekle | — | — | sonraki
 */
public final class MembersGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    /** Üye listesi için kullanılan slot sayısı (satır 1-5). */
    private static final int CONTENT_SIZE = 45;

    private final GuiConfigManager guiConfigManager;
    private final ConfigManager configManager;
    private final FarmerMemberRepository memberRepository;

    public MembersGui(GuiConfigManager guiConfigManager,
                       ConfigManager configManager,
                       FarmerMemberRepository memberRepository) {
        this.guiConfigManager = guiConfigManager;
        this.configManager    = configManager;
        this.memberRepository = memberRepository;
    }

    public Inventory build(Player player, FarmerRecord record, int page) {
        GuiConfigManager.MembersMenuSettings s = guiConfigManager.getMembersMenuSettings();
        ConfigManager.Settings cfg = configManager.getSettings();

        MembersGuiHolder holder = new MembersGuiHolder(record, page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                MM.deserialize("<!italic>" + s.title()));
        holder.setInventory(inv);

        // Filler sadece 6. satır (45-53)
        ItemStack filler = GuiItemBuilder.build(s.fillerMat(), s.fillerName(), List.of());
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        // ── Üye listesi ──────────────────────────────────────────────────────
        List<FarmerMember> members = memberRepository.findByFarmer(record.id());
        int fromIndex = page * CONTENT_SIZE;
        int toIndex   = Math.min(fromIndex + CONTENT_SIZE, members.size());

        if (fromIndex < members.size()) {
            List<FarmerMember> pageMembers = members.subList(fromIndex, toIndex);
            for (int i = 0; i < pageMembers.size(); i++) {
                inv.setItem(i, buildMemberItem(pageMembers.get(i), s, cfg));
            }
        }

        // ── Alt satır (45-53) ─────────────────────────────────────────────────
        // Önceki sayfa
        if (page > 0) {
            inv.setItem(s.prevSlot(), GuiItemBuilder.build(s.prevMat(), s.prevName(), s.prevLore()));
        }

        // Geri
        inv.setItem(s.backSlot(), GuiItemBuilder.build(s.backMat(), s.backName(), s.backLore()));

        // Üye ekle — sadece LEADER+ görebilir ve limitin altındaysa
        boolean canManage = memberRepository.hasAccess(record.id(), player.getUniqueId(), FarmerRole.LEADER);
        int memberCount   = members.size(); // owner dahil
        int maxMembers    = cfg.maxMembers();
        if (canManage && (maxMembers <= 0 || memberCount < maxMembers + 1)) {
            inv.setItem(s.addSlot(), GuiItemBuilder.build(s.addMat(), s.addName(), s.addLore()));
        }

        // Sonraki sayfa
        boolean hasNext = (page + 1) * CONTENT_SIZE < members.size();
        if (hasNext) {
            inv.setItem(s.nextSlot(), GuiItemBuilder.build(s.nextMat(), s.nextName(), s.nextLore()));
        }

        return inv;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ItemStack buildMemberItem(FarmerMember member,
                                       GuiConfigManager.MembersMenuSettings s,
                                       ConfigManager.Settings cfg) {
        String roleName = cfg.roleDisplayName(member.role());
        String roleColor = switch (member.role()) {
            case OWNER  -> "<#facc15>";
            case LEADER -> "<#f97316>";
            case MEMBER -> "<#38bdf8>";
        };
        String roleColorEnd = roleColor.replace("<", "</");

        // Oyuncu kafası ikonu
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            org.bukkit.OfflinePlayer offlinePlayer =
                    Bukkit.getOfflinePlayer(member.uuid());
            meta.setOwningPlayer(offlinePlayer);
            // Display name MiniMessage
            meta.displayName(MiniMessage.miniMessage().deserialize(
                    "<!italic>" + roleColor + "<bold>" + member.name() + "</bold>" + roleColorEnd));
            // Lore
            java.util.List<net.kyori.adventure.text.Component> loreComponents = new java.util.ArrayList<>();
            loreComponents.add(MiniMessage.miniMessage().deserialize("<!italic>"));
            loreComponents.add(MiniMessage.miniMessage().deserialize("<!italic><#D09019>Bilgiler:</#D09019>"));
            loreComponents.add(MiniMessage.miniMessage().deserialize("<!italic> <white>▸ Rol: " + roleColor + roleName + roleColorEnd + "</white>"));
            if (member.role() != FarmerRole.OWNER) {
                loreComponents.add(MiniMessage.miniMessage().deserialize("<!italic>"));
                loreComponents.add(MiniMessage.miniMessage().deserialize("<!italic><gray>● <white><b>ѕᴏʟ ᴛıᴋ</b></white> → Rol değiştir</gray>"));
                loreComponents.add(MiniMessage.miniMessage().deserialize("<!italic><gray>● <white><b>ѕᴀğ ᴛıᴋ</b></white> → Kaldır</gray>"));
            }
            meta.lore(loreComponents);
            skull.setItemMeta(meta);
        }
        return skull;
    }
}
