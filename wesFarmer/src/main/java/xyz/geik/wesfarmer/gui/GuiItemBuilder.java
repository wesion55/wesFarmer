package xyz.geik.wesfarmer.gui;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tüm GUI builder sınıflarının paylaştığı ItemStack üretici.
 *
 * <p>{@code <!italic>} tag'i her display-name ve lore satırına otomatik
 * eklenir — YAML dosyalarına tek tek yazmak gerekmez, Minecraft'ın
 * default italic efekti plugin genelinde kaldırılır.
 */
public final class GuiItemBuilder {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Başa otomatik eklenen italic-kapatma tag'i. */
    private static final String NO_ITALIC = "<!italic>";

    private GuiItemBuilder() {}

    public static ItemStack build(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MM.deserialize(NO_ITALIC + name));
            meta.lore(lore.stream()
                    .map(line -> MM.deserialize(NO_ITALIC + line))
                    .collect(Collectors.toList()));
            item.setItemMeta(meta);
        }
        return item;
    }
}
