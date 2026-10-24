package xyz.geik.wesfarmer.integration.npc;

import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.events.NpcInteractEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import xyz.geik.wesfarmer.config.MessageService;
import xyz.geik.wesfarmer.gui.FarmerMenuGui;
import xyz.geik.wesfarmer.storage.FarmerMemberRepository;
import xyz.geik.wesfarmer.storage.FarmerRecord;
import xyz.geik.wesfarmer.storage.FarmerRepository;
import xyz.geik.wesfarmer.storage.FarmerRole;

/**
 * Çiftçi NPC'sine tıklandığında ana çiftçi menüsünü açar.
 * Sahibi için menü açılır, başkası tıklarsa uyarı mesajı gönderilir.
 */
public final class FancyNpcsInteractListener implements Listener {

    private final MessageService messageService;
    private final FarmerRepository farmerRepository;
    private final FarmerMemberRepository memberRepository;
    private final FarmerMenuGui farmerMenuGui;

    public FancyNpcsInteractListener(MessageService messageService,
                                      FarmerRepository farmerRepository,
                                      FarmerMemberRepository memberRepository,
                                      FarmerMenuGui farmerMenuGui) {
        this.messageService   = messageService;
        this.farmerRepository = farmerRepository;
        this.memberRepository = memberRepository;
        this.farmerMenuGui    = farmerMenuGui;
    }

    @EventHandler(ignoreCancelled = true)
    public void onNpcInteract(NpcInteractEvent event) {
        Npc npc = event.getNpc();
        if (npc == null) return;

        String npcName = npc.getData().getName();
        if (npcName == null || !npcName.startsWith("wesfarmer_")) return;

        if (!(event.getPlayer() instanceof Player player)) return;

        FarmerRecord record = farmerRepository.findByNpcName(npcName);
        if (record == null) return;

        if (record.ownerUuid().equals(player.getUniqueId())
                || memberRepository.hasAccess(record.id(), player.getUniqueId(), FarmerRole.MEMBER)) {
            // Sahip veya üye — ana menüyü aç (sayfa 0)
            player.openInventory(farmerMenuGui.build(player, record, 0));
        } else {
            messageService.send(player, "npc-click-not-owner");
        }
    }
}
