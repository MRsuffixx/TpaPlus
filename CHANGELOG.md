# Changelog

All notable changes follow semantic versioning.

## [1.0.0] - 2026-08-01

### Security

- Added safe-destination validation to administrative force teleports unless the caller has the explicit safety-bypass permission.
- Added fail-closed WorldGuard flag revalidation and regression coverage for GUI request ownership and MiniMessage placeholder isolation.

### Fixed

- Fixed `REPLACE` with `ON_REQUEST` economy charging so the superseded transaction is refunded and all terminal request paths settle transaction state exactly once.
- Fixed unbounded economy transaction tracking and safely handle Vault provider failures without retaining failed transactions.
- Fixed cooldown persistence loss during shutdown by tracking pending writes and draining accepted SQL work before closing HikariCP.
- Fixed settings and locale state diverging from the database by serializing per-player writes and rolling back failed updates.
- Fixed confirmation-token capacity failures, stale request tokens, raw UUID output from `%tpapro_last_target%`, invalid stored-locale observability, and mutual-trust limit enforcement.
- Fixed configured Bukkit sound aliases so they resolve to registry keys and invalid playback failures are logged once.

### Changed

- Added configurable teleport-history retention with a 90-day default and asynchronous scheduled pruning.
- Upgraded configuration metadata to version 2 and rotate the three newest migration backups per file.
- Made cooldown snapshots consistent with concurrent mutations and restricted request-clearing operations to pending requests.

### Added

- Added regression tests for coordinator economy replacement, administrative safety, confirmation capacity/replay, settings races, shutdown write tracking, configuration backup rotation, history retention, GUI authorization, and localization safety.

## [1.0.0-SNAPSHOT] - 2026-07-31

- Initial TpaPro development release.
- Added atomic TPA/TPA_HERE request lifecycle, multiple pending requests, expiration, cooldowns, and permission groups.
- Added warmups, cancellation listeners, bounded safety search, trap analysis, back locations, history, and statistics.
- Added privacy settings, trusted players, per-player auto-accept, block lists, English/Turkish localization, sounds, and menus.
- Added asynchronous SQLite/MySQL/MariaDB/PostgreSQL persistence.
- Added optional Vault, PlaceholderAPI, WorldGuard, CombatLogX, and PvPManager integrations.
- Added administration commands, public Bukkit API/events, Gradle Java 21 build, and automated core/SQLite tests.
