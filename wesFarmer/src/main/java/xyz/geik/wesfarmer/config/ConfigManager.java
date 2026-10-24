package xyz.geik.wesfarmer.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.geik.wesfarmer.storage.FarmerPermission;
import xyz.geik.wesfarmer.storage.FarmerRole;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Loads config.yml once and caches every value in an immutable
 * {@link Settings} record. Callers never touch {@code plugin.getConfig()}
 * directly, so the file is never re-read on the hot path.
 *
 * GUI layout lives in {@code guis/*.yml} (see {@link GuiConfigManager}) and
 * every player-facing string lives in {@code messages.yml} (see
 * {@link MessageService}) — this class only holds plain behaviour settings.
 */
public final class ConfigManager {

    private final JavaPlugin plugin;
    private volatile Settings settings;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        double buyPrice = config.getDouble("settings.buy-price", 1000.0);
        String npcSkin = config.getString("settings.npc-skin", "");
        double hologramOffsetY = config.getDouble("settings.hologram-offset-y", 2.2);
        List<String> hologramLines = config.getStringList("settings.hologram-lines");
        String hologramBackground = config.getString("settings.hologram-background", "transparent");
        boolean hologramTextShadow = config.getBoolean("settings.hologram-text-shadow", false);
        int maxFarmersPerPlayer = config.getInt("settings.max-farmers-per-player", 1);
        List<String> allowedWorlds = config.getStringList("settings.allowed-worlds");

        String roleMemberName = config.getString("settings.roles.member", "Üye");
        String roleLeaderName = config.getString("settings.roles.leader", "Lider");
        String roleOwnerName  = config.getString("settings.roles.owner",  "Sahip");
        int    maxMembers     = config.getInt("settings.max-members", 10);

        String sellAllPermission    = config.getString("settings.sell-all-permission", "");
        String upgradeAllPermission = config.getString("settings.upgrade-all-permission", "");

        // ── İzin → minimum rol eşleşmesi ─────────────────────────────────────
        Map<FarmerPermission, FarmerRole> permMap = new EnumMap<>(FarmerPermission.class);
        for (FarmerPermission perm : FarmerPermission.values()) {
            String path     = "settings.permissions." + perm.configKey();
            String roleName = config.getString(path, defaultRole(perm).name());
            FarmerRole role;
            try {
                role = FarmerRole.valueOf(roleName.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[WesFarmer] Geçersiz rol '" + roleName
                        + "' (" + path + "), varsayılan kullanılıyor: " + defaultRole(perm));
                role = defaultRole(perm);
            }
            permMap.put(perm, role);
        }

        this.settings = new Settings(
                buyPrice, npcSkin, hologramOffsetY, hologramLines,
                hologramBackground, hologramTextShadow, maxFarmersPerPlayer,
                allowedWorlds,
                roleMemberName, roleLeaderName, roleOwnerName, maxMembers,
                sellAllPermission, upgradeAllPermission,
                Map.copyOf(permMap)
        );
    }

    public void reload() {
        load();
    }

    /**
     * Runtime'da yalnızca izin haritasını günceller.
     * Diğer tüm ayarlar değişmeden kalır.
     * Config dosyasına yazmak çağıranın sorumluluğundadır.
     */
    public void applyPermissions(Map<FarmerPermission, FarmerRole> newPermissions) {
        Settings old = this.settings;
        this.settings = new Settings(
                old.buyPrice(), old.npcSkin(), old.hologramOffsetY(), old.hologramLines(),
                old.hologramBackground(), old.hologramTextShadow(), old.maxFarmersPerPlayer(),
                old.allowedWorlds(),
                old.roleMemberName(), old.roleLeaderName(), old.roleOwnerName(),
                old.maxMembers(),
                old.sellAllPermission(), old.upgradeAllPermission(),
                newPermissions
        );
    }

    public Settings getSettings() {
        return settings;
    }

    // ── Varsayılan izin seviyeleri ────────────────────────────────────────────

    /**
     * Her izin için kod içi varsayılan — config'de anahtar yoksa veya
     * değer geçersizse bu değer kullanılır.
     *
     * <p>Tasarım kararı:
     * <ul>
     *   <li>MEMBER → satma, envantere alma (basit okuma/satış işleri)
     *   <li>LEADER → yükseltme, toplama toggle (kaynak değiştiren işler)
     *   <li>OWNER  → üye görüntüleme (hassas)
     * </ul>
     */
    public static FarmerRole defaultRole(FarmerPermission perm) {
        return switch (perm) {
            case SELL, SELL_ALL, TAKE             -> FarmerRole.MEMBER;
            case UPGRADE, UPGRADE_ALL,
                 COLLECT_TOGGLE, COLLECT_TOGGLE_ALL,
                 SELL_TOGGLE                       -> FarmerRole.LEADER;
            case VIEW_MEMBERS                     -> FarmerRole.MEMBER;
        };
    }

    // ── Settings record ───────────────────────────────────────────────────────

    public record Settings(
            double buyPrice,
            String npcSkin,
            double hologramOffsetY,
            List<String> hologramLines,
            String hologramBackground,
            boolean hologramTextShadow,
            int maxFarmersPerPlayer,
            List<String> allowedWorlds,
            String roleMemberName,
            String roleLeaderName,
            String roleOwnerName,
            int maxMembers,
            // Buton permission'ları (boş = herkese açık)
            String sellAllPermission,
            String upgradeAllPermission,
            // İzin → minimum gerekli rol (değiştirilemez kopya)
            Map<FarmerPermission, FarmerRole> permissions
    ) {
        public boolean isWorldAllowed(String worldName) {
            return allowedWorlds.isEmpty() || allowedWorlds.contains(worldName);
        }

        /** Belirtilen permission'a sahip mi? Boş string herkese açık anlamına gelir. */
        public boolean hasButtonPermission(org.bukkit.entity.Player player, String perm) {
            return perm == null || perm.isBlank() || player.hasPermission(perm);
        }

        /** Verilen role ait görünen ismi döndürür. */
        public String roleDisplayName(FarmerRole role) {
            return switch (role) {
                case MEMBER -> roleMemberName;
                case LEADER -> roleLeaderName;
                case OWNER  -> roleOwnerName;
            };
        }

        /**
         * Bu izin için gereken minimum rolü döndürür.
         * Config'de tanımlı değilse kod içi varsayılanı kullanır.
         */
        public FarmerRole requiredRole(FarmerPermission perm) {
            return permissions.getOrDefault(perm, ConfigManager.defaultRole(perm));
        }
    }
}
