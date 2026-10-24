package xyz.geik.wesfarmer.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Thin wrapper around the Vault economy service.
 *
 * Vault is a soft-dependency: {@link #isAvailable()} must be checked before
 * relying on any purchase-related method. When unavailable, callers should
 * disable purchasing rather than silently pretending it succeeded.
 */
public final class EconomyService {

    @Nullable
    private Economy economy;

    public void setup() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            economy = null;
            return;
        }
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        economy = (provider == null) ? null : provider.getProvider();
    }

    public boolean isAvailable() {
        return economy != null;
    }

    public boolean has(OfflinePlayer player, double amount) {
        return isAvailable() && economy.has(player, amount);
    }

    public EconomyResponse withdraw(OfflinePlayer player, double amount) {
        if (!isAvailable()) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Ekonomi eklentisi bulunamadi.");
        }
        return economy.withdrawPlayer(player, amount);
    }

    public EconomyResponse deposit(OfflinePlayer player, double amount) {
        if (!isAvailable()) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Ekonomi eklentisi bulunamadi.");
        }
        return economy.depositPlayer(player, amount);
    }

    public String format(double amount) {
        return isAvailable() ? economy.format(amount) : String.valueOf(amount);
    }
}
