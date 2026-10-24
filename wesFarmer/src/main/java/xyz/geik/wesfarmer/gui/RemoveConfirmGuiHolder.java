package xyz.geik.wesfarmer.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Holder for the farmer remove-confirmation panel.
 * Carries the NPC name so the listener knows which farmer to delete.
 */
public final class RemoveConfirmGuiHolder implements InventoryHolder {

    private final String npcName;
    private Inventory inventory;

    public RemoveConfirmGuiHolder(String npcName) {
        this.npcName = npcName;
    }

    public String getNpcName() {
        return npcName;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
