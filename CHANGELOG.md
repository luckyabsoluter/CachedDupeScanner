# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project adheres to Semantic Versioning.

## [Unreleased]

### Added

- Fine-grained settings to adjust thumbnail size and video timeline preview frame size (exact % input, no upper cap), applied across files/results/bulk-delete previews.
- Optional video timeline snap mode that expands frames to fill each row width while preserving ratio-driven frame count.
- Configurable video timeline line count to render multiple preview rows per file card.
- DB maintenance option to validate and apply file-maintenance checks only to entries that are currently part of detected duplicate groups.
- Modified-time filter rules for Results and Files filtering.

### Changed

- Configured the Gradle daemon JVM criteria to use JetBrains JDK 21 through Gradle toolchain resolution.
- Moved the Trash item dialog Open action after Restore so Open stays in the same position as other item dialogs.

### Fixed

- Preserved the active screen across Android configuration recreations such as rotation or display size changes.

## [1.3.0] - 2026-04-17

### Added

- Timeline video preview mode with a dedicated RAM cache policy.
- Saved filters functionality with a dedicated filter editing screen, persisting definitions across sessions.
- Comprehensive bulk delete command catalog including "keep-oldest" and "keep-newest" commands, and a preview flow with thumbnails.
- Same-folder duplicate group filter rule.

### Changed

- Merged modified-time bulk delete operations into one configurable command.

### Fixed

- Handled partial WakeLocks to ensure tasks keep running smoothly during active operations.
- Improved progress overlay to display raw DB loaded progress, current count, and filtered match counts.
- Reserved space for the sort button in the summary row to prevent layout shifting.

## [1.2.0] - 2026-03-26

### Added

- System-wide floating task banner and draggable task bubble for monitoring long-running operations across scans, DB, and trash.
- Memory usage overlay and RAM thumbnail retention options in settings.
- Default scan exclusion for the app's trash bin.

### Changed

- Unified cancellation handling and linear task progress across all long-running tasks.
- Centralized AppSettingsStore defaults and serialization.
- Simplified scan results card description and aligned task surfaces with shared spacing tokens.

### Fixed

- Stabilized discontinuous navigation using direct lazy scrollbar jumps.
- Constrained task bubble placement reliably after viewport changes.
- Kept task bubble animations, progress states, and notifications synchronized and monotonic.

### Performance

- Remembered thumbnail cache is now shared seamlessly across screens.

## [1.1.0] - 2026-03-22

### Added

- Database-backed duplicate-group browsing for large result sets.
- Infinite scrolling across results, files, and trash views.
- Group-detail delete mode with multi-select, select-all, and its own saved sort order.
- Manual duplicate-group rebuild from DB management.
- Notification-backed execution for long-running DB maintenance tasks.

### Changed

- Result and detail screens now follow a more consistent loading model with clearer indicators and automatic follow-up loading.
- DB management now treats duplicate-group rebuild as a separate workflow from general file maintenance.
- Settings are organized more coherently, and the targets screen no longer exposes sample target creation.
- The skip-zero-size setting is now enabled by default.

### Fixed

- Result browsing now keeps ordering, paging state, previews, sort direction, and scroll position stable through deletes, collapses, and detail transitions.
- File management keeps deleted entries visible with deleted-state highlighting instead of dropping them from view too early.
- DB management restores in-flight task state after screen re-entry and avoids conflicts between scan notifications and DB task notifications.
- Duplicate-group rebuild no longer fails because of cache synchronization conflicts.
- Scan completion keeps the user on the active scan-command screen, and scrollbar dragging remains stable during recomposition.

### Performance

- Result browsing reuses cached group members and remembered previews to reduce repeated loading while navigating large duplicate sets.

## [1.0.0] - 2026-02-04

### Added

- First public Android release of CachedDupeScanner.
- Incremental duplicate scanning with a persistent cache and deferred hashing.
- Scan entry paths for local folders, Storage Access Framework folder picking, and all-files access path scanning where supported.
- Saved scan targets, per-target scans, scan-all execution, scan history, and persisted scan reports.
- Duplicate result browsing with grouping, sorting, group detail views, media thumbnails, file open actions, and path display controls.
- File browser and trash management flows, including restore support for deleted items.
- Settings for zero-size duplicate handling, full-path display, saved sort preferences, and import/export of settings and scan targets.
- DB management tools for cache maintenance, missing-hash handling, cleanup actions, and entry inspection.
- Scan progress notifications and release documentation.
