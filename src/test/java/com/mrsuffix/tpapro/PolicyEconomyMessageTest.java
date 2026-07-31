package com.mrsuffix.tpapro;

import com.mrsuffix.tpapro.economy.EconomyGateway;
import com.mrsuffix.tpapro.economy.EconomyTransactionService;
import com.mrsuffix.tpapro.locale.MessageCatalog;
import com.mrsuffix.tpapro.settings.PlayerSettings;
import com.mrsuffix.tpapro.settings.PrivacyMode;
import com.mrsuffix.tpapro.settings.RequestPolicyEvaluator;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyEconomyMessageTest {
    @Test void blockListAlwaysOverridesPrivacy() {
        RequestPolicyEvaluator evaluator = new RequestPolicyEvaluator();
        assertThat(evaluator.evaluate(PlayerSettings.defaults("en_US"), true, true, true, true, true, true))
                .isEqualTo(RequestPolicyEvaluator.Decision.BLOCKED);
    }
    @Test void everyPrivacyModeHasExplicitBehavior() {
        RequestPolicyEvaluator evaluator = new RequestPolicyEvaluator();
        assertThat(evaluator.evaluate(settings(PrivacyMode.EVERYONE), false, false, false, false, false, false)).isEqualTo(RequestPolicyEvaluator.Decision.ALLOW);
        assertThat(evaluator.evaluate(settings(PrivacyMode.DISABLED), false, true, true, true, true, true)).isEqualTo(RequestPolicyEvaluator.Decision.DISABLED);
        assertThat(evaluator.evaluate(settings(PrivacyMode.TRUSTED_ONLY), false, false, false, false, false, true)).isEqualTo(RequestPolicyEvaluator.Decision.NOT_TRUSTED);
        assertThat(evaluator.evaluate(settings(PrivacyMode.TRUSTED_ONLY), false, true, false, false, false, true)).isEqualTo(RequestPolicyEvaluator.Decision.ALLOW);
        assertThat(evaluator.evaluate(settings(PrivacyMode.SAME_WORLD_ONLY), false, false, false, false, false, false)).isEqualTo(RequestPolicyEvaluator.Decision.DIFFERENT_WORLD);
        assertThat(evaluator.evaluate(settings(PrivacyMode.FRIENDS_ONLY), false, true, false, false, true, true)).isEqualTo(RequestPolicyEvaluator.Decision.ALLOW);
    }
    @Test void economyChargesAndRefundsExactlyOnce() {
        FakeEconomy gateway = new FakeEconomy(); EconomyTransactionService service = new EconomyTransactionService(gateway);
        UUID transaction = UUID.randomUUID(), payer = UUID.randomUUID();
        assertThat(service.chargeOnce(transaction, payer, 25, false).status()).isEqualTo(EconomyTransactionService.Status.CHARGED);
        assertThat(service.chargeOnce(transaction, payer, 25, false).status()).isEqualTo(EconomyTransactionService.Status.ALREADY_CHARGED);
        assertThat(gateway.withdrawals).isEqualTo(1); assertThat(service.refundOnce(transaction).status()).isEqualTo(EconomyTransactionService.Status.REFUNDED);
        assertThat(service.refundOnce(transaction).status()).isEqualTo(EconomyTransactionService.Status.NOT_CHARGED); assertThat(gateway.deposits).isEqualTo(1);
    }
    @Test void economyRejectsInsufficientBalanceWithoutTransaction() {
        FakeEconomy gateway = new FakeEconomy(); EconomyTransactionService service = new EconomyTransactionService(gateway);
        assertThat(service.chargeOnce(UUID.randomUUID(), UUID.randomUUID(), 101, false).status()).isEqualTo(EconomyTransactionService.Status.INSUFFICIENT);
        assertThat(gateway.withdrawals).isZero();
    }
    @Test void messageCatalogFallsBackByLocaleAndKeyAndNeverReturnsNull() {
        MessageCatalog catalog = new MessageCatalog("en_US", Map.of("en_US", Map.of("hello", "Hello"), "tr_TR", Map.of("other", "Diğer")));
        assertThat(catalog.get("tr_TR", "hello")).isEqualTo("Hello");
        assertThat(catalog.get("unknown", "hello")).isEqualTo("Hello");
        assertThat(catalog.get("tr_TR", "missing")).contains("Missing message");
    }
    private static PlayerSettings settings(PrivacyMode mode) { return PlayerSettings.defaults("en_US").withPrivacy(mode); }
    private static final class FakeEconomy implements EconomyGateway {
        private double balance = 100; private int withdrawals; private int deposits;
        @Override public boolean available() { return true; } @Override public double balance(UUID playerId) { return balance; }
        @Override public Transaction withdraw(UUID playerId, double amount) { withdrawals++; balance -= amount; return new Transaction(true, balance, ""); }
        @Override public Transaction deposit(UUID playerId, double amount) { deposits++; balance += amount; return new Transaction(true, balance, ""); }
        @Override public String format(double amount) { return String.valueOf(amount); }
    }
}
