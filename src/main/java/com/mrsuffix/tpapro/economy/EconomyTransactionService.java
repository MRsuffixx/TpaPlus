package com.mrsuffix.tpapro.economy;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EconomyTransactionService {
    public enum Status { CHARGED, ALREADY_CHARGED, BYPASSED, UNAVAILABLE, INSUFFICIENT, FAILED, REFUNDED, NOT_CHARGED }
    public record Result(Status status, double amount, double balance, String error) {
        public boolean success() { return status == Status.CHARGED || status == Status.ALREADY_CHARGED || status == Status.BYPASSED || status == Status.REFUNDED; }
    }
    private final EconomyGateway gateway;
    private final Map<UUID, State> transactions = new ConcurrentHashMap<>();

    public EconomyTransactionService(EconomyGateway gateway) { this.gateway = Objects.requireNonNull(gateway, "gateway"); }

    public Result chargeOnce(UUID transactionId, UUID payer, double amount, boolean bypass) {
        if (!Double.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Invalid amount");
        if (bypass || amount == 0) return new Result(Status.BYPASSED, 0, gateway.available() ? gateway.balance(payer) : 0, null);
        State state = transactions.computeIfAbsent(transactionId, ignored -> new State());
        synchronized (state) {
            if (state.charged && !state.refunded) return new Result(Status.ALREADY_CHARGED, state.amount, gateway.balance(payer), null);
            if (!gateway.available()) return new Result(Status.UNAVAILABLE, amount, 0, "unavailable");
            double balance = gateway.balance(payer);
            if (!Double.isFinite(balance) || balance < amount) return new Result(Status.INSUFFICIENT, amount, balance, "insufficient");
            EconomyGateway.Transaction result = gateway.withdraw(payer, amount);
            if (!result.success()) return new Result(Status.FAILED, amount, result.balance(), result.error());
            state.charged = true; state.refunded = false; state.amount = amount; state.payer = payer;
            return new Result(Status.CHARGED, amount, result.balance(), null);
        }
    }

    public Result refundOnce(UUID transactionId) {
        State state = transactions.get(transactionId);
        if (state == null) return new Result(Status.NOT_CHARGED, 0, 0, null);
        synchronized (state) {
            if (!state.charged || state.refunded) return new Result(Status.NOT_CHARGED, state.amount, gateway.balance(state.payer), null);
            EconomyGateway.Transaction result = gateway.deposit(state.payer, state.amount);
            if (!result.success()) return new Result(Status.FAILED, state.amount, result.balance(), result.error());
            state.refunded = true;
            return new Result(Status.REFUNDED, state.amount, result.balance(), null);
        }
    }

    public boolean charged(UUID transactionId) { State state = transactions.get(transactionId); return state != null && state.charged && !state.refunded; }
    public void forget(UUID transactionId) { transactions.remove(transactionId); }
    private static final class State { private boolean charged; private boolean refunded; private double amount; private UUID payer; }
}
