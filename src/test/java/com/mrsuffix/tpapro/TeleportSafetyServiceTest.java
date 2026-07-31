package com.mrsuffix.tpapro;

import com.mrsuffix.tpapro.config.ConfigManager;
import com.mrsuffix.tpapro.config.ConfigurationBundle.Safety;
import com.mrsuffix.tpapro.cooldown.CooldownService;
import com.mrsuffix.tpapro.economy.EconomyTransactionService;
import com.mrsuffix.tpapro.economy.NoEconomyGateway;
import com.mrsuffix.tpapro.history.HistoryService;
import com.mrsuffix.tpapro.history.StatisticsService;
import com.mrsuffix.tpapro.integration.combat.CombatService;
import com.mrsuffix.tpapro.integration.worldguard.RegionIntegration;
import com.mrsuffix.tpapro.locale.LocaleManager;
import com.mrsuffix.tpapro.locale.SoundService;
import com.mrsuffix.tpapro.permission.PermissionService;
import com.mrsuffix.tpapro.request.RequestRegistry;
import com.mrsuffix.tpapro.restriction.WorldRestrictionService;
import com.mrsuffix.tpapro.safety.SafeTeleportService;
import com.mrsuffix.tpapro.safety.TrapRiskAnalyzer;
import com.mrsuffix.tpapro.scheduler.SchedulerAdapter;
import com.mrsuffix.tpapro.teleport.TeleportService;
import com.mrsuffix.tpapro.user.UserService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeleportSafetyServiceTest {
    @Test void forceTeleportRefusesUnsafeDestinationWithoutSafetyBypass() {
        SafeTeleportService safety = mock(SafeTeleportService.class);
        Player traveler = mock(Player.class), target = mock(Player.class); UUID id = UUID.randomUUID();
        Location destination = mock(Location.class);
        when(traveler.getUniqueId()).thenReturn(id); when(target.getLocation()).thenReturn(destination);
        when(safety.find(id, destination, false)).thenReturn(new SafeTeleportService.Result(false, null, "lava", 1));
        TeleportService service = new TeleportService(mock(ConfigManager.class), mock(LocaleManager.class),
                mock(SoundService.class), mock(PermissionService.class), mock(SchedulerAdapter.class),
                new MutableClock(Instant.EPOCH), new RequestRegistry(new MutableClock(Instant.EPOCH)), safety,
                mock(TrapRiskAnalyzer.class), mock(WorldRestrictionService.class), mock(RegionIntegration.class),
                mock(CombatService.class), new EconomyTransactionService(new NoEconomyGateway()),
                mock(UserService.class), mock(HistoryService.class), mock(StatisticsService.class),
                new CooldownService(new MutableClock(Instant.EPOCH)), ignored -> { });
        TeleportService.StartResult result = service.force(traveler, target, false);
        assertThat(result.success()).isFalse(); assertThat(result.reason()).isEqualTo("lava");
        verify(safety).find(id, destination, false);
    }

    @Test void safeLocationFindRejectsUnloadedDestinationWithoutReadingBlocks() {
        ConfigManager configs = mock(ConfigManager.class, RETURNS_DEEP_STUBS);
        Safety rules = new Safety(true, false, 0, 0, 16, true, true, true, true,
                true, true, true, true, true, true, false, 3);
        when(configs.get().main().safety()).thenReturn(rules);
        World world = mock(World.class); when(world.isChunkLoaded(0, 0)).thenReturn(false);
        Location location = new Location(world, 0.5, 64, 0.5);
        PluginManager pluginManager = mock(PluginManager.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            SafeTeleportService service = new SafeTeleportService(configs);
            assertThat(service.find(UUID.randomUUID(), location, false).reason()).isEqualTo("chunk-unloaded");
            verify(world, org.mockito.Mockito.never()).getBlockAt(anyInt(), anyInt(), anyInt());
        }
    }
}
