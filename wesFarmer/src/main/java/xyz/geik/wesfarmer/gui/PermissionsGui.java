package xyz.geik.wesfarmer.gui;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import xyz.geik.wesfarmer.config.ConfigManager;
import xyz.geik.wesfarmer.config.GuiConfigManager;
import xyz.geik.wesfarmer.storage.FarmerPermission;
import xyz.geik.wesfarmer.storage.FarmerRecord;
import xyz.geik.wesfarmer.storage.FarmerRole;

import java.util.ArrayList;
import java.util.List;

/**
 * İzinler paneli GUI'si (36 slot / 9×4).
 *
 * <p>Slot düzeni:
 * <pre>
 *  Satır 1-4 (0-26):
 *    Sol grup  → sütun 0: izin ikonu  (SELL, SELL_ALL, TAKE, UPGRADE)
 *    Sağ grup  → sütun 4: izin ikonu  (UPGRADE_ALL, COLLECT_TOGGLE, COLLECT_TOGGLE_ALL, VIEW_MEMBERS)
 *  Satır 5 (27-35): filler × 4 | geri=31 | filler × 4
 * </pre>
 *
 * <p>Sol tık → minimum rol yükselt (MEMBER→LEADER→OWNER)
 * <p>Sağ tık → minimum rol düşür  (OWNER→LEADER→MEMBER)
 */
public final class PermissionsGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GuiConfigManager guiConfigManager;
    private final ConfigManager    configManager;

    public PermissionsGui(GuiConfigManager guiConfigManager, ConfigManager configManager) {
        this.guiConfigManager = guiConfigManager;
        this.configManager    = configManager;
    }

    public Inventory build(Player player, FarmerRecord record) {
        GuiConfigManager.PermissionsMenuSettings s = guiConfigManager.getPermissionsMenuSettings();
        ConfigManager.Settings cfg = configManager.getSettings();

        PermissionsGuiHolder holder = new PermissionsGuiHolder(record);
        Inventory inv = Bukkit.createInventory(holder, 36,
                MM.deserialize("<!italic>" + s.title()));
        holder.setInventory(inv);

        // Filler sadece geri dön satırı (27-35)
        ItemStack filler = GuiItemBuilder.build(Material.BLACK_STAINED_GLASS_PANE, "", List.of());
        for (int i = 27; i < 36; i++) inv.setItem(i, filler);

        // Tüm 9 izni 2. satırda yan yana (slot 9-17)
        FarmerPermission[] allPerms = {
            FarmerPermission.SELL,
            FarmerPermission.SELL_ALL,
            FarmerPermission.TAKE,
            FarmerPermission.UPGRADE,
            FarmerPermission.UPGRADE_ALL,
            FarmerPermission.COLLECT_TOGGLE,
            FarmerPermission.COLLECT_TOGGLE_ALL,
            FarmerPermission.VIEW_MEMBERS,
            FarmerPermission.SELL_TOGGLE
        };

        for (int i = 0; i < allPerms.length; i++) {
            inv.setItem(9 + i, buildPermItem(allPerms[i], cfg));
        }

        // Geri (slot 31 — 4. satır ortası, col 4 ancak bu 27+4=31 sol grubun dışında)
        inv.setItem(s.backSlot(),
                GuiItemBuilder.build(s.backMat(), s.backName(), s.backLore()));

        return inv;
    }

    // ── Item builder ─────────────────────────────────────────────────────────

    private ItemStack buildPermItem(FarmerPermission perm, ConfigManager.Settings cfg) {
        FarmerRole current   = cfg.requiredRole(perm);
        String     color     = roleColor(current);
        String     colorEnd  = color.replace("<", "</");
        String     roleName  = cfg.roleDisplayName(current);

        // Rol göstergesi: önceki ve sonraki
        FarmerRole prev = prevRole(current);
        FarmerRole next = nextRole(current);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<#D09019>Gereken Rol:</#D09019>");
        lore.add(" " + color + "<bold>▶ " + roleName + "</bold>" + colorEnd);
        lore.add("");
        if (prev != null) {
            lore.add("<gray>● <white><b>ѕᴀğ ᴛıᴋ</b></white> → " + roleColor(prev) + cfg.roleDisplayName(prev) + roleColor(prev).replace("<", "</") + "</gray>");
        }
        if (next != null) {
            lore.add("<gray>● <white><b>ѕᴏʟ ᴛıᴋ</b></white> → " + roleColor(next) + cfg.roleDisplayName(next) + roleColor(next).replace("<", "</") + "</gray>");
        }

        String name = permDisplayName(perm);
        return GuiItemBuilder.build(permIcon(perm), name, lore);
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    /**
     * Tıklanan slot'tan izin ve yön çözümler.
     * Sol grup: col 0, satır 0-3 → LEFT_ORDER
     * Sağ grup: col 4, satır 0-3 → RIGHT_ORDER
     * Slot 31 (back) buraya düşmez çünkü 27. satırdan itibaren filler var.
     */
    public static PermClick resolveClick(int slot) {
        if (slot < 9 || slot > 17) return null;
        FarmerPermission[] allPerms = {
            FarmerPermission.SELL,
            FarmerPermission.SELL_ALL,
            FarmerPermission.TAKE,
            FarmerPermission.UPGRADE,
            FarmerPermission.UPGRADE_ALL,
            FarmerPermission.COLLECT_TOGGLE,
            FarmerPermission.COLLECT_TOGGLE_ALL,
            FarmerPermission.VIEW_MEMBERS,
            FarmerPermission.SELL_TOGGLE
        };
        int index = slot - 9;
        return new PermClick(allPerms[index]);
    }

    public record PermClick(FarmerPermission permission) {}

    /** Bir sonraki (daha yüksek) rol, yoksa null. */
    public static FarmerRole nextRole(FarmerRole role) {
        return switch (role) {
            case MEMBER -> FarmerRole.LEADER;
            case LEADER -> FarmerRole.OWNER;
            case OWNER  -> null;
        };
    }

    /** Bir önceki (daha düşük) rol, yoksa null. */
    public static FarmerRole prevRole(FarmerRole role) {
        return switch (role) {
            case OWNER  -> FarmerRole.LEADER;
            case LEADER -> FarmerRole.MEMBER;
            case MEMBER -> null;
        };
    }

    private static String roleColor(FarmerRole role) {
        return switch (role) {
            case MEMBER -> "<#38bdf8>";
            case LEADER -> "<#f97316>";
            case OWNER  -> "<#facc15>";
        };
    }

    private static Material permIcon(FarmerPermission perm) {
        return switch (perm) {
            case SELL               -> Material.EMERALD;
            case SELL_ALL           -> Material.EMERALD_BLOCK;
            case TAKE               -> Material.CHEST;
            case UPGRADE            -> Material.ANVIL;
            case UPGRADE_ALL        -> Material.BEACON;
            case COLLECT_TOGGLE     -> Material.HOPPER;
            case COLLECT_TOGGLE_ALL -> Material.DROPPER;
            case VIEW_MEMBERS       -> Material.PLAYER_HEAD;
            case SELL_TOGGLE        -> Material.REDSTONE;
        };
    }

    private static String permDisplayName(FarmerPermission perm) {
        return switch (perm) {
            case SELL               -> "<#4ade80>♦ Ürün Sat</#4ade80>";
            case SELL_ALL           -> "<#4ade80>♦ Tümünü Sat</#4ade80>";
            case TAKE               -> "<#38bdf8>♦ Envantere Al</#38bdf8>";
            case UPGRADE            -> "<#f97316>♦ Seviye Yükselt</#f97316>";
            case UPGRADE_ALL        -> "<#f97316>♦ Toplu Yükselt</#f97316>";
            case COLLECT_TOGGLE     -> "<#a78bfa>♦ Toplama Aç/Kapat</#a78bfa>";
            case COLLECT_TOGGLE_ALL -> "<#a78bfa>♦ Toplama Toplu Aç/Kapat</#a78bfa>";
            case VIEW_MEMBERS       -> "<#facc15>♦ Üyeleri Görüntüle</#facc15>";
            case SELL_TOGGLE        -> "<#4ade80>♦ Satış Durumu Değiştir</#4ade80>";
        };
    }
}
