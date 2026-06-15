# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project adheres to Semantic Versioning.

## [Unreleased]

### Added

- Similarity settings management for video/image duplicate candidates, including exact-thumbnail clustering, duration-only video clustering, duration-neighbor video lists, and per-setting clear controls.
- Similarity settings now provide separate flows for setting type selection, custom creation controls, setting management, and similarity group member browsing.
- Similarity settings identity now treats different method parameters as separate settings, so thumbnail sizes such as 2x2 and 3x3 maintain independent results.
- Similarity result previews with exact-hash reduction tiles, per-file member thumbnails, compact member previews, duration labels, tappable video cards, retained sort and preview menu selections, duration-neighbor sort direction controls, cluster list sort options, lazy result browsing, and task progress notifications.
- Similarity cluster detail video members now provide an optional timeline video preview from the detail overflow menu, using the configured video preview cache, width snap, line count, and frame size.
- Video timeline preview menus now provide optional duration and resolution labels in Files and similarity cluster details without forcing timeline frames on.
- Duplicate group detail views now support long-press member selection and selected-file deletion.

### Changed

- Similarity setting screens now explain that there is no separate Update/Rebuild action and that scan completion generates enabled settings from the scan cache.
- Similarity settings management now separates setting creation, maintenance, per-setting management, and group browsing into clearer flows with readable summaries and confirmation dialogs for clearing generated similarity data.
- Similarity settings screens now restore the previous template-style flow, detailed cluster and duration-neighbor explanations, exact-thumbnail reduction previews, and richer result card summaries while keeping the maintained settings terminology.
- Similarity cluster detail member cards now restore the previous full-width media card layout with exact byte counts, selection styling, and separated video metadata/timeline preview rows.
- Similarity settings group browsing now restores member previews, thumbnail-backed group cards, paged group-detail members, and file detail actions.
- Similarity setting management pages now keep clear/delete controls separate from the group browsing screen while scan completion generates similarity results.
- Similarity settings no longer expose manual run/rebuild controls; enabled settings are generated from scan-cache data after scans.
- Similarity cluster detail member browsing now shares the result member sort control and automatically loads additional members near the end of the list.
- Similarity setting group lists now restore cluster sort controls for file count and total size order.
- Similarity cluster detail screens in maintained settings now restore long-press member selection, selected-file deletion, and video preview menu options.
- Duration-neighbor similarity cluster details now restore duration-order controls and display known member durations.
- Bulk delete execution now shows shared task progress and notifications while deleting files.
- Similarity data now uses settings-based sidecar storage joined to the active file cache, so deleted files drop out of similarity results while restored files can reappear after maintenance.
- Video preview settings now describe that timeline preview memory, size, lines, and width snapping apply to both files and similarity cluster details.
- Duplicate group detail screens now share one member sort control across legacy results, DB results, and similarity exact clusters.
- File detail dialogs now show the cached file hash when available and an explicit no-hash state otherwise.

### Fixed

- Enabling a paused similarity setting now stays lightweight; paused settings catch up during the next scan-cache generation.
- Similarity cluster detail top-right video preview, duration, and resolution menu selections now stay enabled when returning to the screen.
- Similarity settings no longer auto-create default rows when opened, and individual settings can now be deleted with their generated data.
- Top-right checkbox menu items now stay open after toggling preview and path display options.
- Similarity setting parameters are now normalized before identity/storage matching, so equivalent custom values reuse the same setting rows.
- Duration-neighbor similarity generation now builds connected neighbor clusters with stable normalized range keys.
- Similarity cluster member loading now stays under SQLite binding limits for large clusters, preventing member previews and detail screens from falling back to unavailable.
- Similarity cluster detail long-press select-all now keeps lazy not-loaded member handling while paging members.
- Lazy result and similarity detail selection now share one selection state contract, while eager-only detail content is explicitly separated from paged detail screens.
- Simple and result-detail screens now use result-style lazy side scrollbars instead of standalone scroll-state scrollbars.
- Background DB, trash, bulk-delete, and similarity work now use the app-owned task runtime and foreground service, so UI lifecycle changes no longer cancel tracked tasks or reset active task monitoring.
- Bulk-delete execution now keeps paging stable while successful deletes refresh duplicate groups, preventing later groups from being skipped.
- Bulk-delete previews now report full candidate group and file totals while keeping preview samples bounded.
- Bulk-delete execution now rescans the current snapshot, filter, and command so capped preview samples do not limit eligible deletions.
- Results DB filters now evaluate member-dependent filters page-by-page instead of materializing every member at once.
- Duration-neighbor similarity result lists now lazy-load larger member pages and prefetch earlier near the end of the visible list.
- App screens now handle screen size, orientation, layout, and keyboard-hidden configuration changes without recreating and crashing active screens.
- Scan work now runs on an app-owned scope with a foreground service so active scans continue when the UI lifecycle changes or the app stays in the background.
- Background scans no longer stop immediately when scan work starts before the UI job state is assigned.
- Scans and duplicate-group rebuilds now repair same-size cache entries with missing hashes, so interrupted deferred hashing no longer leaves duplicate groups incomplete.
- Duplicate-group rebuild progress now reports the missing-hash repair stage instead of staying on the preparing state.

### Performance

- Similarity cluster detail member thumbnails now compose through lazy list items, so thumbnail and optional video preview loading starts from visible members instead of the whole loaded page.
- Similarity cluster detail screens now load members page-by-page instead of materializing entire large clusters at once.
- Scan cancellation, filtered duplicate results, bulk-delete previews, Empty Trash, scan reports, and duplicate-only database maintenance now page or stream large data sets instead of loading them eagerly.

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
