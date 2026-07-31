<p align="center">
  <img src="img/banner.png" alt="TpaPro Banner" width="100%">
</p>

<p align="center">
  <img src="img/logo.png" alt="TpaPro Logo" width="160">
</p>

<h1 align="center">TpaPro</h1>

<p align="center">
TpaPro is a UUID-based teleport request plugin for modern Paper servers. It provides a race-safe request lifecycle, configurable warmups and cooldowns, safe destination search, privacy controls, trust and block relationships, teleport history, statistics, SQL persistence, optional economy/region/combat integrations, inventory menus, localization, and a public Bukkit API.

Author: **MRsuffix**  
Version: **1.0.0**
Package: `com.mrsuffix.tpapro`

## Requirements and compatibility

- Paper 1.21 or newer
- Java 21 or newer (the plugin is compiled for Java 21)
- No proxy, BungeeCord, Velocity, Redis, or cross-server support
- Folia is detected and reported, but TpaPro 1.0 uses the Paper scheduler and does **not** claim Folia compatibility

Optional soft dependencies are Vault, PlaceholderAPI, WorldGuard, CombatLogX, and PvPManager. Missing optional plugins never prevent startup. Economy actions fail safely when economy is enabled without a Vault provider.

## Features

- `TPA` and `TPA_HERE` requests with immutable identity, automatic expiration, multiple pending requests, sender selection, and configurable `REJECT`, `REPLACE`, or `REFRESH` duplicate behavior
- Atomic state transitions that prevent double acceptance and double teleportation
- Permission-derived warmups, cooldowns, costs, request limits, history sizes, and trusted-player limits
- One shared expiration task and one shared warmup ticker; no scheduler task per request
- Movement, damage, attack, world-change, command, death, quit, and replacement cancellation controls
- Bounded deterministic safe-location search with hazard, border, void, fall, suffocation, and Nether-roof checks
- Configurable trap analysis with UUID/request/destination-bound, expiring confirmation tokens
- Built-in combat tracking plus optional CombatLogX and PvPManager runtime adapters
- World blacklist/whitelist rules, cross-world controls, blocked routes, and a WorldGuard `tpapro-teleport` state flag
- Vault charge stages (`ON_REQUEST`, `ON_ACCEPT`, `ON_SUCCESS`), charge-once semantics, balance checks, and failure refunds
- Persistent privacy settings, notification preferences, locale, one-directional trust, per-player auto-accept, and block lists
- `/tpback`, teleport history, batched statistics, and cached non-blocking PlaceholderAPI values
- Inventory menus with pagination and protected click/drag handling; all features remain usable through commands
- Complete English and Turkish message catalogs using Adventure and MiniMessage
- SQLite by default; MySQL, MariaDB, and PostgreSQL via HikariCP
- Public API through Bukkit's `ServicesManager` and cancellable pre-events plus immutable post-event snapshots

## Installation

1. Stop the Paper server.
2. Copy `TpaPro-1.0.0.jar` into `plugins/`.
3. Start the server. TpaPro copies all configuration and language resources without overwriting existing files.
4. Review `plugins/TpaPro/config.yml`, `storage.yml`, `restrictions.yml`, and `integrations.yml`.
5. Restart after changing storage type/credentials or optional integration availability. Ordinary settings and language content can be reloaded with `/tpapro reload`.

## Commands

| Command | Purpose |
|---|---|
| `/tpa <player>` | Ask to teleport to a player |
| `/tpahere <player>` | Ask a player to teleport to you |
| `/tpaccept [player]` | Accept the only request or one sender's request |
| `/tpdeny [player]` | Deny the only request or one sender's request |
| `/tpcancel [player]` | Cancel an outgoing request |
| `/tpatoggle` | Toggle incoming requests |
| `/tpautaccept [player]` | Toggle global or per-player automatic acceptance |
| `/tpblock`, `/tpunblock`, `/tpblocklist` | Manage blocked players |
| `/tpatrust add`, `remove`, `list` | Manage trusted players |
| `/tpalist` | View pending and sent requests |
| `/tpasettings` | Open settings or use text setting/value arguments |
| `/tpback` | Return to the saved pre-teleport location |
| `/tphistory [page]` | View teleport history |
| `/tpastats [player]` | View statistics; other players require admin inspection permission |
| `/tpapro help` | Show help |
| `/tpapro reload` | Atomically reload validated configuration and languages |
| `/tpapro info` | Show safe runtime diagnostics |
| `/tpapro debug` | Toggle runtime debug logging |
| `/tpapro inspect <player>` | Inspect non-secret player state and counts |
| `/tpapro requests clear <player>` | Invalidate active requests |
| `/tpapro cooldown reset <player>` | Reset teleport cooldowns |
| `/tpapro forceteleport <player> <target>` | Perform an administrative teleport |

Invalid input, ambiguous pending requests, offline players, console-only/player-only mismatches, and permission failures return localized messages without command exceptions.

## Permissions

Player permissions default to `true`:

`tpapro.use`, `tpapro.tpa`, `tpapro.tpahere`, `tpapro.accept`, `tpapro.deny`, `tpapro.cancel`, `tpapro.toggle`, `tpapro.autaccept`, `tpapro.block`, `tpapro.trust`, `tpapro.list`, `tpapro.settings`, `tpapro.back`, `tpapro.history`, `tpapro.stats`.

Bypass permissions default to operators:

`tpapro.bypass.cooldown`, `tpapro.bypass.warmup`, `tpapro.bypass.cost`, `tpapro.bypass.combat`, `tpapro.bypass.world`, `tpapro.bypass.region`, `tpapro.bypass.safety`, `tpapro.bypass.move-cancel`, `tpapro.bypass.damage-cancel`.

Administration permissions default to operators:

`tpapro.admin`, `tpapro.admin.help`, `tpapro.admin.reload`, `tpapro.admin.info`, `tpapro.admin.debug`, `tpapro.admin.inspect`, `tpapro.admin.clear-requests`, `tpapro.admin.reset-cooldown`, `tpapro.admin.force-teleport`.

Values in `config.yml` are selected by permission. For warmups, cooldowns, and costs the lowest matching non-negative value wins. For request/history/trust limits the highest matching value wins.

## Configuration

- `config.yml`: requests, warmups, safety, traps, trust benefits, back/history/statistics, language, and permission groups
- `storage.yml`: database type, connection information, pool settings, shutdown behavior, and cooldown persistence
- `restrictions.yml`: worlds, cross-world routes, combat, and regions
- `integrations.yml`: economy stages/costs and optional plugin toggles
- `sounds.yml`: validated namespaced or Bukkit sound names, volume, and pitch
- `menus.yml`: titles, sizes, materials, and presentation
- `messages/en_US.yml`, `messages/tr_TR.yml`: localized MiniMessage templates

Numeric values are bounded and invalid enum/value input falls back with a server warning. Configuration files carry `config-version: 2`; older files are copied into `plugins/TpaPro/backups/`, migrated to the current version, and rotated to the three newest backups per file. Locale identifiers and SQLite filenames are path-safe validated. Teleport history defaults to a 90-day retention period through `history.retention-days`.

## Database setup

SQLite is ready without configuration and creates `plugins/TpaPro/tpapro.db`. For MySQL, MariaDB, or PostgreSQL, set `type`, host, port, database, username, and password in `storage.yml`, then restart. TpaPro never logs passwords. Schema migrations create unique relationship constraints and indexes for history and relationship lookup. All normal repository work uses a dedicated executor; Bukkit objects are not accessed from database threads.

## Integrations

### Vault

Install Vault and an economy provider, enable `economy.enabled`, select a charge mode, and configure costs. `ON_SUCCESS` is the safe default. Bypass and refund rules are enforced exactly once per transaction ID.

### WorldGuard

TpaPro registers the WorldGuard state flag `tpapro-teleport`. Administrators can deny teleporting in a region with:

```text
/rg flag <region> tpapro-teleport deny
```

Source and destination checks can be toggled in `restrictions.yml`. If flag registration conflicts or WorldGuard cannot initialize, region integration disables safely.

### PlaceholderAPI

Available placeholders include `%tpapro_enabled%`, `%tpapro_pending_requests%`, `%tpapro_outgoing_requests%`, `%tpapro_cooldown%`, `%tpapro_warmup%`, `%tpapro_last_target%`, `%tpapro_trusted_count%`, `%tpapro_blocked_count%`, `%tpapro_auto_accept%`, `%tpapro_privacy_mode%`, and `%tpapro_successful_teleports%`. Resolution uses memory only and never blocks on SQL.

## Localization

The default and fallback locale is `en_US`; `tr_TR` is included. Server defaults are configured under `language`. Players can select a registered locale with `/tpasettings language <locale>` when enabled. Player values are inserted as Adventure components, not parsed as unrestricted MiniMessage.

## Public API

```java
TpaProApi api = Bukkit.getServicesManager().load(TpaProApi.class);
if (api != null) {
    RequestOutcome result = api.sendRequest(senderUuid, targetUuid, RequestType.TPA);
}
```

API request methods and events are server-thread operations. Request snapshots and configuration models exposed by the API are immutable. Custom restrictions return a closeable registration handle. Events include request create/created/accept/accepted/deny/denied/expire/cancel and teleport prepare/start/complete/cancel/safety-check stages.

## Building and development

```powershell
.\gradlew.bat clean test build
```

The Java 21 toolchain, UTF-8 compilation, JUnit 5, reproducible archives, and a dependency-inclusive plugin JAR are configured in Gradle Kotlin DSL. Paper and optional server APIs are `compileOnly` and are not bundled.

Core tests cover lifecycle transitions, duplicate replacement refunds, expiration, ambiguity, cooldown arithmetic, permission values, movement, warmup isolation, administrative safety validation, bounded confirmation tokens, GUI authorization, MiniMessage placeholder isolation, settings persistence races, graceful write draining, backup rotation, history retention, privacy/trust/block logic, economy idempotency, message fallback, configuration validation, reload idempotency, and SQLite uniqueness/persistence.

## Support and credits

When reporting a problem, include `/tpapro info`, sanitized configuration, the server log around the failure, and reproduction steps. Never publish database credentials. TpaPro is designed and credited to **MRsuffix**.
