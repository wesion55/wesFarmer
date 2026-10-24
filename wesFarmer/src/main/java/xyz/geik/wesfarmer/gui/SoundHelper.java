package xyz.geik.wesfarmer.gui;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Tüm GUI'lerde kullanılan ses yardımcısı.
 * - Menü açılışında sandık sesi
 * - Butona tıklayınca UI click sesi
 */
public final class SoundHelper {

    private SoundHelper() {}

    /** Menü açılışında çalar — sandık açma sesi. */
    public static void playOpen(Player player) {
        player.playSound(player.getLocation(),
                Sound.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
    }

    /** Her butona tıklamada çalar — UI click sesi. */
    public static void playClick(Player player) {
        player.playSound(player.getLocation(),
                Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
    }

    /** İptal / red işlemlerinde çalar. */
    public static void playCancel(Player player) {
        player.playSound(player.getLocation(),
                Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
    }

    /** Başarılı işlemlerde çalar. */
    public static void playSuccess(Player player) {
        player.playSound(player.getLocation(),
                Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
    }
}
