package xyz.geik.wesfarmer.integration.hologram;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;

import java.util.List;
import java.util.Locale;

/**
 * Soft-dependency hook for FancyHolograms. {@link #isAvailable()} must be
 * checked by the caller before any of the other methods are used.
 */
public final class FancyHologramsHook {

    private FancyHologramsHook() {}

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("FancyHolograms");
    }

    /** FancyHolograms'de bu isimde hologram kayıtlı mı? */
    public static boolean hologramExists(String name) {
        try {
            return FancyHologramsPlugin.get().getHologramManager().getHologram(name).isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * @param background "transparent", hex "#RRGGBB" / "#AARRGGBB", ya da
     *                   FancyHolograms'in destekledigi renk adi (white, black …)
     * @param textShadow true ise hologram metninde golge aktif olur
     */
    public static void createHologram(String name, Location location, List<String> lines,
                                       String background, boolean textShadow) {
        try {
            TextHologramData data = new TextHologramData(name, location);
            data.setText(lines);
            data.setBackground(parseBackground(background));
            data.setTextShadow(textShadow);

            HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
            Hologram hologram = manager.create(data);
            manager.addHologram(hologram);
        } catch (Exception exception) {
            Bukkit.getLogger().warning("[WesFarmer] FancyHolograms hologrami '" + name + "' olusturulamadi: " + exception.getMessage());
        }
    }

    public static void removeHologram(String name) {
        try {
            HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
            manager.getHologram(name).ifPresent(manager::removeHologram);
        } catch (Exception exception) {
            Bukkit.getLogger().warning("[WesFarmer] FancyHolograms hologrami '" + name + "' silinemedi: " + exception.getMessage());
        }
    }

    /**
     * Hologramı tüm oyunculardan gizler — removeHologram ile kaldırır,
     * tekrar göstermek için {@link #createHologram} çağrılmalıdır.
     * (FancyHolograms API'sinde per-hologram visibility toggle yoktur.)
     */
    public static void hideHologram(String name) {
        removeHologram(name);
    }

    /**
     * Var olan bir hologramın text satirlarini, arka planini ve golgesini gunceller.
     * Hologram bulunamazsa sessizce gecer.
     */
    public static void updateHologramLines(String name, List<String> lines,
                                            String background, boolean textShadow) {
        try {
            HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
            manager.getHologram(name).ifPresent(hologram -> {
                if (hologram.getData() instanceof TextHologramData textData) {
                    textData.setText(lines);
                    textData.setBackground(parseBackground(background));
                    textData.setTextShadow(textShadow);
                    hologram.forceUpdate();
                }
            });
        } catch (Exception exception) {
            Bukkit.getLogger().warning("[WesFarmer] FancyHolograms hologrami '" + name + "' guncellenemedi: " + exception.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Config string'ini {@link Color}'a cevirir.
     * <ul>
     *   <li>"transparent" → {@link Hologram#TRANSPARENT}</li>
     *   <li>"#RRGGBB"     → solid RGB renk</li>
     *   <li>"#AARRGGBB"   → alpha dahil ARGB renk</li>
     *   <li>Renk adi      → Adventure NamedTextColor uzerinden (white, black…)</li>
     *   <li>Diger / null  → null (FancyHolograms varsayilani kullanir)</li>
     * </ul>
     */
    private static Color parseBackground(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();

        if (v.equalsIgnoreCase("transparent")) {
            return Hologram.TRANSPARENT;
        }

        if (v.startsWith("#")) {
            try {
                String hex = v.substring(1);
                if (hex.length() == 6) {
                    // #RRGGBB: opak renk. Color.fromRGB zaten alpha=255 varsayar,
                    // Color.fromARGB(rgb) kullanmak bu 3 bayti (0xRRGGBB) alpha=0x00
                    // olarak yorumlayip sonradan setAlpha(255) ile duzeltmeye
                    // calisiyordu — dogru sonuca varsa da gereksiz kirilgandi.
                    return Color.fromRGB(Integer.parseInt(hex, 16));
                }
                if (hex.length() == 8) {
                    // #AARRGGBB: alpha dahil, dogrudan ARGB.
                    return Color.fromARGB((int) Long.parseLong(hex, 16));
                }
            } catch (NumberFormatException ignored) {}
        }

        // Renk adi (white, dark_red …)
        NamedTextColor named = NamedTextColor.NAMES.value(v.toLowerCase(Locale.ROOT).replace(' ', '_'));
        if (named != null) {
            return Color.fromARGB(named.value() | 0xC8000000);
        }

        Bukkit.getLogger().warning("[WesFarmer] Gecersiz hologram-background degeri: '" + value + "', null kullaniliyor.");
        return null;
    }
}
