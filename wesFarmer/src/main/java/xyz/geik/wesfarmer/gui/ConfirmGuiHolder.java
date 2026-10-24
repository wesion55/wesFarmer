package xyz.geik.wesfarmer.gui;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holder for every confirmation panel (purchase onayı, admin removeall onayı, vb.).
 *
 * <p>İki mod:
 * <ul>
 *   <li><b>Purchase modu</b> — {@link #getPurchaseLocation()} ile konum taşır,
 *       {@link #getAction()} {@code null} döner.
 *       {@link ConfirmGuiListener} bu modu kullanır.</li>
 *   <li><b>Action modu</b> — {@link #getAction()} ile herhangi bir {@link Runnable}
 *       taşır, {@link #getPurchaseLocation()} {@code null} döner.
 *       Admin komutları (removeall vb.) bu modu kullanır.</li>
 * </ul>
 */
public final class ConfirmGuiHolder implements InventoryHolder {

    @Nullable private final Location purchaseLocation;
    @Nullable private final Runnable action;
    @Nullable private final String   cancelMessageKey;
    private Inventory inventory;

    /** Purchase onayı için — konum taşır. */
    public ConfirmGuiHolder(Location purchaseLocation) {
        this.purchaseLocation = purchaseLocation;
        this.action           = null;
        this.cancelMessageKey = null;
    }

    /** Generic aksiyon onayı için — herhangi bir Runnable taşır. */
    public ConfirmGuiHolder(Runnable action, @Nullable String cancelMessageKey) {
        this.purchaseLocation = null;
        this.action           = action;
        this.cancelMessageKey = cancelMessageKey;
    }

    @Nullable public Location getPurchaseLocation() { return purchaseLocation; }
    @Nullable public Runnable getAction()           { return action; }
    /** İptal edildiğinde gönderilecek mesaj anahtarı. Null ise "action-cancelled" kullanılır. */
    @Nullable public String   getCancelMessageKey() { return cancelMessageKey; }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
