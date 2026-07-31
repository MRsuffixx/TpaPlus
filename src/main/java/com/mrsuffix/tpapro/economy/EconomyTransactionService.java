package com.mrsuffix.tpapro.economy;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class EconomyTransactionService {
    public enum Status { CHARGED, ALREADY_CHARGED, BYPASSED, UNAVAILABLE, INSUFFICIENT, FAILED, REFUNDED, NOT_CHARGED }
    public record Result(Status status, double amount, double balance, String error) {
        public boolean success() { return status == Status.CHARGED || status == Status.ALREADY_CHARGED || status == Status.BYPASSED || status == Status.REFUNDED; }
    }
    private final EconomyGateway gateway;
    private final Map<UUID, State> transactions = new ConcurrentHashMap<>();

    public EconomyTransactionService(EconomyGateway gateway) { this.gateway = Objects.requireNonNull(gateway, "gateway"); }

    public Result chargeOnce(UUID transactionId, UUID payer, double amount, boolean bypass) {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(payer, "payer");
        if (!Double.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Invalid amount");
        if (bypass || amount == 0) return new Result(Status.BYPASSED, 0, gateway.available() ? gateway.balance(payer) : 0, null);
        AtomicReference<Result> outcome = new AtomicReference<>();
        transactions.compute(transactionId, (ignored, existing) -> {
            if (existing != null) {
                outcome.set(new Result(Status.ALREADY_CHARGED, existing.amount, gateway.balance(existing.payer), null));
                return existing;
            }
            if (!gateway.available()) {
                outcome.set(new Result(Status.UNAVAILABLE, amount, 0, "unavailable"));
                return null;
            }
            double balance = gateway.balance(payer);
            if (!Double.isFinite(balance) || balance < amount) {
                outcome.set(new Result(Status.INSUFFICIENT, amount, balance, "insufficient"));
                return null;
            }
            EconomyGateway.Transaction result = gateway.withdraw(payer, amount);
            if (!result.success()) {
                outcome.set(new Result(Status.FAILED, amount, result.balance(), result.error()));
                return null;
            }
            outcome.set(new Result(Status.CHARGED, amount, result.balance(), null));
            return new State(amount, payer);
        });
        return outcome.get();
    }

    public Result refundOnce(UUID transactionId) {
        Objects.requireNonNull(transactionId, "transactionId");
        AtomicReference<Result> outcome = new AtomicReference<>(new Result(Status.NOT_CHARGED, 0, 0, null));
        transactions.computeIfPresent(transactionId, (ignored, state) -> {
            EconomyGateway.Transaction result = gateway.deposit(state.payer, state.amount);
            if (!result.success()) {
                outcome.set(new Result(Status.FAILED, state.amount, result.balance(), result.error()));
                return state;
            }
            outcome.set(new Result(Status.REFUNDED, state.amount, result.balance(), null));
            return null;
        });
        return outcome.get();
    }

    public boolean charged(UUID transactionId) { return transactions.containsKey(transactionId); }
    public void forget(UUID transactionId) { transactions.remove(transactionId); }
    public int trackedTransactions() { return transactions.size(); }
    private record State(double amount, UUID payer) { }
}
