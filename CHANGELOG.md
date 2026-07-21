# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project adheres to Semantic Versioning.

## [Unreleased]

## [1.5.0] - 2026-07-21

### Added

- Named video and image similarity management with exact-thumbnail SHA-256 grouping, duration-only groups, duration-neighbor lists, independent parameterized entries, automatic scan-completion generation, and manual Update, Rebuild, and Clear controls.
- Lazy similarity result browsing with group and member sorting, thumbnail and duration summaries, file details, retained preview options, long-press selection, selected-file deletion, and optional timeline previews.
- Similarity group filtering and bulk deletion through the saved Results rule editor and shared command catalog.
- Results and Similarity filters for same-size groups and per-rule `Any member` or `All members` matching, plus Similarity rules for average-duration tolerance and matching media resolution.
- Results and Similarity bulk-delete commands for keeping the shortest or longest video and for requiring exact or minimum matching or non-matching text counts before deleting the opposite set.
- Resumable incremental Similarity result clearing with shared task progress, cancellation, and failure reporting.
- Independent 1-32 worker controls for scan hashing and Similarity feature extraction, including preference import and export support.
- DB maintenance scopes for all cached files, detected duplicate-result groups, and generated Similarity groups.
- Live processing speed, elapsed time, and estimated remaining time across task cards, banners, notifications, filter metadata recalculation, and database upgrades.
- Optional duration and resolution labels in Files and Similarity video preview menus without requiring timeline frames.
- Cached hash status in file details and explicit missing-file state in duplicate and Similarity group details.

### Changed

- Project documentation now reflects current task behavior, cache eligibility, storage permissions, local data handling, and Android/JDK build requirements.
- Database upgrades now require explicit confirmation on a blocking startup screen, report migration progress, and keep data screens unavailable until migration completes.
- Similarity data is maintained alongside the active file cache, so scan completion, Trash restore or deletion, and DB maintenance update persisted Similarity results while active result snapshots remain stable.
- Filter editor clusters now use persistent collapsible headers, target-labeled accordion rows, and dropdown target selection while retaining rule logic, enablement, and open editor state.
- Bulk-delete execution now reports shared task progress and notification outcomes.
- Duplicate and Similarity group details now share member sorting and lazy side-scroll behavior across paged result sources.
- The Android launch splash and blocking database upgrade screen now follow system dark mode.

### Fixed

- App-owned background scans, DB maintenance, Trash, and bulk-delete work now survive UI lifecycle and configuration changes without losing tracked task state, including scans started before UI job assignment.
- Screen size, orientation, layout, and keyboard-hidden changes no longer recreate and crash active app screens.
- Scans and duplicate-group rebuilds now repair same-size cache entries with missing hashes and report that repair stage, preventing interrupted deferred hashing from leaving groups incomplete.
- Bulk-delete previews now include complete candidate totals, and execution uses the same current snapshot, filter, and command without skipping groups as refreshed pages change.
- Scrollbar drags now track the current pointer position and remain pinned to the final item when variable-height estimates change.
- Incomplete task progress bars no longer draw a stop marker at the right edge or round very large counts up to 100 percent.
- Android back navigation no longer emits repeated predictive-back compatibility warnings.
- Top-right checkbox menus now remain open while preview and path display options are toggled.

### Performance

- Duplicate and Similarity grouping, member lookup, repair, and paging now use stable integer file identities and indexed 32-byte SHA-256 values instead of path joins, hexadecimal hash text, or serialized thumbnail payloads.
- Scan hashing and Similarity media extraction now use independently configurable bounded worker pools while progress collection and database writes remain serialized.
- Similarity filtering uses keyset pages, bounded aggregate queries, batched metadata resolution, and reusable duration or resolution metadata instead of repeatedly scanning earlier rows or materializing every member.
- Similarity group and member browsing now uses indexed stored pages, aggregate summaries, lazy member composition, and visible-item thumbnail loading.
- Scan cancellation, filtered duplicate results, bulk-delete previews, Empty Trash, scan reports, and duplicate-only DB maintenance now page or stream large data sets instead of loading them eagerly.
- Trash restore now recalculates only the restored path for generated Similarity entries.

## [1.4.0] - 2026-04-30

### Added

- Fine-grained settings to adjust thumbnail size and video timeline preview frame size (exact % input, no upper cap), applied across files/results/bulk-delete previews.
- Optional video timeline snap mode that expands frames to fill each row width while preserving ratio-driven frame count.
- Configurable video timeline line count to render multiple preview rows per file card.
- DB maintenance option to validate and apply file-maintenance checks only to entries that are currently part of detected duplicate groups.
- Modified-time filter rules for Results and Files filtering.

### Changed

- Configured the Gradle daemon JVM criteria to use JetBrains JDK 21 through Gradle toolchain resolution.
- Moved the Trash item dialog Open action after Restore so Open stays in the same position as other item dialogs.
- Reworked preview size and video preview line controls so edits stay pending until Apply, repeated values are removed, and numeric step controls stay on one compact row.

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
