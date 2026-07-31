package com.mrsuffix.tpapro.integration.placeholderapi;

import com.mrsuffix.tpapro.cooldown.CooldownService;
import com.mrsuffix.tpapro.cooldown.CooldownType;
import com.mrsuffix.tpapro.history.StatisticsService;
import com.mrsuffix.tpapro.request.RequestRegistry;
import com.mrsuffix.tpapro.teleport.TeleportService;
import com.mrsuffix.tpapro.user.UserProfile;
import com.mrsuffix.tpapro.user.UserService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class TpaProExpansion extends PlaceholderExpansion {
    private final Plugin plugin; private final RequestRegistry requests; private final CooldownService cooldowns;
    private final TeleportService teleports; private final UserService users; private final StatisticsService statistics;
    public TpaProExpansion(Plugin plugin, RequestRegistry requests, CooldownService cooldowns,
                           TeleportService teleports, UserService users, StatisticsService statistics) {
        this.plugin = plugin; this.requests = requests; this.cooldowns = cooldowns; this.teleports = teleports;
        this.users = users; this.statistics = statistics;
    }
    @Override public @NotNull String getIdentifier() { return "tpapro"; }
    @Override public @NotNull String getAuthor() { return "MRsuffix"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return ""; UUID id = player.getUniqueId(); UserProfile profile = users.cached(id).orElse(null);
        return switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "enabled" -> profile == null ? "true" : String.valueOf(profile.settings().privacyMode() != com.mrsuffix.tpapro.settings.PrivacyMode.DISABLED);
            case "pending_requests" -> String.valueOf(requests.incoming(id).size());
            case "outgoing_requests" -> String.valueOf(requests.outgoing(id).size());
            case "cooldown" -> String.valueOf(Math.max(cooldowns.remainingSecondsCeiling(id, CooldownType.TPA_SEND), cooldowns.remainingSecondsCeiling(id, CooldownType.TPA_HERE_SEND)));
            case "warmup" -> String.valueOf(teleports.remainingSeconds(id));
            case "last_target" -> statistics.lastTarget(id) == null ? "" : statistics.lastTarget(id).toString();
            case "trusted_count" -> String.valueOf(profile == null ? 0 : profile.trusted().size());
            case "blocked_count" -> String.valueOf(profile == null ? 0 : profile.blocked().size());
            case "auto_accept" -> String.valueOf(profile != null && profile.settings().autoAccept());
            case "privacy_mode" -> profile == null ? "EVERYONE" : profile.settings().privacyMode().name();
            case "successful_teleports" -> String.valueOf(statistics.sessionValue(id, StatisticsService.Metric.TELEPORT_SUCCESS));
            default -> null;
        };
    }
}
