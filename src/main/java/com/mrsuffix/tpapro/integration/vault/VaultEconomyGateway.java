package com.mrsuffix.tpapro.integration.vault;

import com.mrsuffix.tpapro.economy.EconomyGateway;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Objects;
import java.util.UUID;

public final class VaultEconomyGateway implements EconomyGateway {
    private final Economy economy;
    public VaultEconomyGateway(Economy economy) { this.economy = Objects.requireNonNull(economy, "economy"); }
    @Override public boolean available() { return Bukkit.getServicesManager().getRegistration(Economy.class) != null; }
    @Override public double balance(UUID playerId) { return economy.getBalance(player(playerId)); }
    @Override public Transaction withdraw(UUID playerId, double amount) { return convert(economy.withdrawPlayer(player(playerId), amount)); }
    @Override public Transaction deposit(UUID playerId, double amount) { return convert(economy.depositPlayer(player(playerId), amount)); }
    @Override public String format(double amount) { return economy.format(amount); }
    private static OfflinePlayer player(UUID id) { return Bukkit.getOfflinePlayer(id); }
    private static Transaction convert(EconomyResponse response) { return new Transaction(response.transactionSuccess(), response.balance, response.errorMessage); }
}
