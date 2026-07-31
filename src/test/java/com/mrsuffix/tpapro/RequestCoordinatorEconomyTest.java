package com.mrsuffix.tpapro;

import com.mrsuffix.tpapro.config.ConfigManager;
import com.mrsuffix.tpapro.config.ConfigurationBundle.ChargeMode;
import com.mrsuffix.tpapro.config.ConfigurationBundle.FriendsFallback;
import com.mrsuffix.tpapro.cooldown.CooldownService;
import com.mrsuffix.tpapro.economy.EconomyGateway;
import com.mrsuffix.tpapro.economy.EconomyTransactionService;
import com.mrsuffix.tpapro.history.StatisticsService;
import com.mrsuffix.tpapro.integration.combat.CombatService;
import com.mrsuffix.tpapro.integration.friends.FriendsIntegration;
import com.mrsuffix.tpapro.integration.worldguard.RegionIntegration;
import com.mrsuffix.tpapro.locale.LocaleManager;
import com.mrsuffix.tpapro.locale.SoundService;
import com.mrsuffix.tpapro.permission.PermissionGroupResolver;
import com.mrsuffix.tpapro.permission.Permission;
import com.mrsuffix.tpapro.permission.PermissionService;
import com.mrsuffix.tpapro.request.DuplicateBehavior;
import com.mrsuffix.tpapro.request.RequestCoordinator;
import com.mrsuffix.tpapro.request.RequestOutcome;
import com.mrsuffix.tpapro.request.RequestRegistry;
import com.mrsuffix.tpapro.request.RequestType;
import com.mrsuffix.tpapro.restriction.RestrictionRegistry;
import com.mrsuffix.tpapro.restriction.WorldRestrictionService;
import com.mrsuffix.tpapro.safety.ConfirmationTokenService;
import com.mrsuffix.tpapro.safety.TrapRiskAnalyzer;
import com.mrsuffix.tpapro.settings.PlayerSettings;
import com.mrsuffix.tpapro.settings.RequestPolicyEvaluator;
import com.mrsuffix.tpapro.teleport.TeleportService;
import com.mrsuffix.tpapro.user.UserProfile;
import com.mrsuffix.tpapro.user.UserService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class RequestCoordinatorEconomyTest {
    @Test void replaceRefundsSupersededOnRequestChargeAndRetainsOnlyTheReplacement() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-01T00:00:00Z"));
        UUID senderId = UUID.randomUUID(), targetId = UUID.randomUUID();
        Player sender = mock(Player.class), target = mock(Player.class); World world = mock(World.class);
        when(sender.getUniqueId()).thenReturn(senderId); when(target.getUniqueId()).thenReturn(targetId);
        when(sender.getName()).thenReturn("Sender"); when(target.getName()).thenReturn("Target");
        when(sender.getWorld()).thenReturn(world); when(target.getWorld()).thenReturn(world);

        ConfigManager configs = mock(ConfigManager.class, RETURNS_DEEP_STUBS);
        when(configs.get().main().requests().duplicateBehavior()).thenReturn(DuplicateBehavior.REPLACE);
        when(configs.get().main().requests().expirationSeconds()).thenReturn(60);
        when(configs.get().main().requests().maxOutgoingPerSender()).thenReturn(3);
        when(configs.get().main().requests().maxPendingPerTarget()).thenReturn(5);
        when(configs.get().main().permissionGroups().get(anyString())).thenReturn(List.of());
        when(configs.get().main().trusted().friendsFallback()).thenReturn(FriendsFallback.TRUSTED);
        when(configs.get().integrations().economy().enabled()).thenReturn(true);
        when(configs.get().integrations().economy().chargeMode()).thenReturn(ChargeMode.ON_REQUEST);
        when(configs.get().integrations().economy().refundOnFailure()).thenReturn(true);
        when(configs.get().integrations().economy().cost(RequestType.TPA)).thenReturn(25.0);

        UserService users = mock(UserService.class); when(users.loaded(any())).thenReturn(true);
        when(users.get(targetId)).thenReturn(new UserProfile(PlayerSettings.defaults("en_US"), Set.of(), Set.of(), Set.of(), null));
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.has(any(), any())).thenAnswer(invocation -> invocation.getArgument(1) != Permission.BYPASS_COST);
        WorldRestrictionService worlds = mock(WorldRestrictionService.class);
        when(worlds.check(any(), any(), any(Boolean.class))).thenReturn(new WorldRestrictionService.Result(true, "allowed"));
        RegionIntegration regions = mock(RegionIntegration.class); when(regions.available()).thenReturn(false);
        CombatService combat = mock(CombatService.class); FriendsIntegration friends = mock(FriendsIntegration.class);
        FakeEconomy gateway = new FakeEconomy(); EconomyTransactionService economy = new EconomyTransactionService(gateway);
        RequestRegistry registry = new RequestRegistry(clock);
        LocaleManager locales = mock(LocaleManager.class);
        when(locales.prefixed(any(), anyString(), any())).thenReturn(Component.empty());
        when(locales.component(any(), anyString(), any())).thenReturn(Component.empty());
        RequestCoordinator coordinator = new RequestCoordinator(configs, registry, new CooldownService(clock), permissions,
                new PermissionGroupResolver(), users, new RequestPolicyEvaluator(), worlds, regions, combat,
                new RestrictionRegistry(), economy, mock(TrapRiskAnalyzer.class), new ConfirmationTokenService(clock),
                mock(TeleportService.class), locales, mock(SoundService.class),
                mock(StatisticsService.class), friends, ignored -> { });

        PluginManager pluginManager = mock(PluginManager.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(senderId)).thenReturn(sender);
            bukkit.when(() -> Bukkit.getPlayer(targetId)).thenReturn(target);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            RequestOutcome first = coordinator.send(senderId, targetId, RequestType.TPA);
            RequestOutcome replacement = coordinator.send(senderId, targetId, RequestType.TPA);
            assertThat(first.success()).isTrue();
            assertThat(replacement.replaced()).isTrue();
            assertThat(replacement.supersededRequest()).isSameAs(first.request());
        }
        assertThat(gateway.withdrawals).isEqualTo(2);
        assertThat(gateway.deposits).isEqualTo(1);
        assertThat(gateway.balance).isEqualTo(75);
        assertThat(economy.trackedTransactions()).isEqualTo(1);
    }

    private static final class FakeEconomy implements EconomyGateway {
        private double balance = 100; private int withdrawals; private int deposits;
        @Override public boolean available() { return true; }
        @Override public double balance(UUID playerId) { return balance; }
        @Override public Transaction withdraw(UUID playerId, double amount) { withdrawals++; balance -= amount; return new Transaction(true, balance, ""); }
        @Override public Transaction deposit(UUID playerId, double amount) { deposits++; balance += amount; return new Transaction(true, balance, ""); }
        @Override public String format(double amount) { return Double.toString(amount); }
    }
}
