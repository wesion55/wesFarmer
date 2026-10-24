package xyz.geik.wesfarmer.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads every player-facing message from the standalone {@code messages.yml}
 * and sends it as a proper Adventure {@link Component} — no hardcoded
 * strings, no legacy {@code org.bukkit.ChatColor}.
 *
 * The prefix is MiniMessage (it needs a gradient + bold + small caps), while
 * every message body underneath {@code messages:} is plain '&' legacy colour
 * code. This mirrors the plugin's design spec: MiniMessage only where a
 * gradient/hex effect is required, legacy everywhere else in chat.
 */
public final class MessageService {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final Map<String, String> messages = new HashMap<>();
    private Component prefix = Component.empty();

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        messages.clear();

        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        prefix = MINI_MESSAGE.deserialize(config.getString("prefix", ""));

        loadSection(config.getConfigurationSection("messages"), messages);
    }

    private void loadSection(ConfigurationSection section, Map<String, String> target) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            target.put(key, section.getString(key, ""));
        }
    }

    public void send(CommandSender receiver, String key, Placeholder... placeholders) {
        receiver.sendMessage(get(key, placeholders));
    }

    public Component get(String key, Placeholder... placeholders) {
        return prefix.append(LEGACY.deserialize(applyPlaceholders(messages.getOrDefault(key, key), placeholders)));
    }

    private String applyPlaceholders(String raw, Placeholder... placeholders) {
        String result = raw;
        for (Placeholder placeholder : placeholders) {
            result = result.replace(placeholder.token(), placeholder.value());
        }
        return result;
    }

    public record Placeholder(String token, String value) {
        public static Placeholder of(String token, String value) {
            return new Placeholder(token, value);
        }
    }
}
