package com.mrsuffix.tpapro.economy;

import java.util.UUID;

public interface EconomyGateway {
    boolean available();
    double balance(UUID playerId);
    Transaction withdraw(UUID playerId, double amount);
    Transaction deposit(UUID playerId, double amount);
    String format(double amount);

    record Transaction(boolean success, double balance, String error) { }
}
