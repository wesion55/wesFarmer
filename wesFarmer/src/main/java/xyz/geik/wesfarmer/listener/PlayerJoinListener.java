package xyz.geik.wesfarmer.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import xyz.geik.wesfarmer.WesFarmer;
import xyz.geik.wesfarmer.storage.FarmerMemberRepository;

/**
 * Oyuncu giriş yaptığında üye isim kaydını günceller.
 * Ad değiştiren oyuncular üye GUI'sinde doğru isimle görünür.
 */
public final class PlayerJoinListener implements Listener {

    private final WesFarmer plugin;
    private final FarmerMemberRepository memberRepository;

    public PlayerJoinListener(WesFarmer plugin, FarmerMemberRepository memberRepository) {
        this.plugin           = plugin;
        this.memberRepository = memberRepository;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> memberRepository.updateName(player.getUniqueId(), player.getName()));
    }
}
