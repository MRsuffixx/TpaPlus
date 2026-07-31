package com.mrsuffix.tpapro.economy;

import java.util.UUID;

public final class NoEconomyGateway implements EconomyGateway {
    @Override public boolean available() { return false; }
    @Override public double balance(UUID playerId) { return 0; }
    @Override public Transaction withdraw(UUID playerId, double amount) { return new Transaction(false, 0, "unavailable"); }
    @Override public Transaction deposit(UUID playerId, double amount) { return new Transaction(false, 0, "unavailable"); }
    @Override public String format(double amount) { return String.format(java.util.Locale.ROOT, "%.2f", amount); }
}
