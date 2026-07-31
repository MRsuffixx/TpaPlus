package com.mrsuffix.tpapro;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceAuditTest {
    @Test void englishAndTurkishHaveExactlyMatchingStringKeys() {
        Map<String, Object> english = yaml("messages/en_US.yml"), turkish = yaml("messages/tr_TR.yml");
        assertThat(stringKeys(english, "")).containsExactlyInAnyOrderElementsOf(stringKeys(turkish, ""));
        assertThat(stringKeys(english, "")).hasSizeGreaterThan(100);
    }

    @Test void pluginMetadataIdentityCommandsAndRequiredPermissionsAreDeclared() {
        Map<String, Object> plugin = yaml("plugin.yml");
        assertThat(plugin.get("name")).isEqualTo("TpaPro"); assertThat(plugin.get("author")).isEqualTo("MRsuffix");
        assertThat(plugin.get("main")).isEqualTo("com.mrsuffix.tpapro.TpaProPlugin"); assertThat(plugin.get("api-version")).isEqualTo("1.21");
        Map<String, Object> commands = cast(plugin.get("commands"));
        assertThat(commands.keySet()).containsExactlyInAnyOrder("tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel",
                "tpatoggle", "tpautaccept", "tpblock", "tpunblock", "tpblocklist", "tpatrust", "tpalist",
                "tpasettings", "tpback", "tphistory", "tpastats", "tpapro");
        Map<String, Object> permissions = cast(plugin.get("permissions"));
        assertThat(permissions.keySet()).contains("tpapro.use", "tpapro.tpa", "tpapro.tpahere", "tpapro.accept",
                "tpapro.deny", "tpapro.cancel", "tpapro.toggle", "tpapro.autaccept", "tpapro.block", "tpapro.trust",
                "tpapro.list", "tpapro.settings", "tpapro.back", "tpapro.history", "tpapro.stats",
                "tpapro.bypass.cooldown", "tpapro.bypass.warmup", "tpapro.bypass.cost", "tpapro.bypass.combat",
                "tpapro.bypass.world", "tpapro.bypass.region", "tpapro.bypass.safety", "tpapro.bypass.move-cancel",
                "tpapro.bypass.damage-cancel", "tpapro.admin", "tpapro.admin.reload", "tpapro.admin.info",
                "tpapro.admin.debug", "tpapro.admin.inspect", "tpapro.admin.clear-requests",
                "tpapro.admin.reset-cooldown", "tpapro.admin.force-teleport");
        assertThat(stringList(plugin.get("softdepend"))).contains("Vault", "PlaceholderAPI", "WorldGuard", "CombatLogX", "PvPManager");
        assertThat(plugin.toString()).doesNotContain("Velocity", "BungeeCord", "Redis");
    }

    @SuppressWarnings("unchecked") private static Map<String, Object> cast(Object value) { return (Map<String, Object>) value; }
    @SuppressWarnings("unchecked") private static java.util.List<String> stringList(Object value) { return (java.util.List<String>) value; }
    private static Map<String, Object> yaml(String path) {
        try (InputStream input = ResourceAuditTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull(); return new Yaml().load(input);
        } catch (java.io.IOException impossible) { throw new AssertionError(impossible); }
    }
    private static Set<String> stringKeys(Object value, String prefix) {
        Set<String> result = new HashSet<>();
        if (value instanceof Map<?, ?> map) map.forEach((key, child) -> result.addAll(stringKeys(child, prefix.isEmpty() ? key.toString() : prefix + "." + key)));
        else if (value instanceof String) result.add(prefix);
        return result;
    }
}
