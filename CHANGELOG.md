# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project adheres to Semantic Versioning.

## [Unreleased]

### Added

- Similarity management for named video/image duplicate candidates, including SHA-256 exact-thumbnail grouping, duration-only video grouping, duration-neighbor video lists, and update, rebuild, and clear controls.
- Similarity now provides separate flows for type selection, custom creation controls, management, and similarity group member browsing.
- Similarity identity now treats different method parameters as separate entries, so thumbnail sizes such as 2x2 and 3x3 maintain independent results.
- Similarity result previews with exact-thumbnail SHA-256 summaries, per-file member thumbnails, compact member previews, duration labels, tappable video cards, retained sort and preview menu selections, duration-neighbor sort direction controls, group list sort options, lazy result browsing, and task progress notifications.
- Similarity group detail video members now provide an optional timeline video preview from the detail overflow menu, using the configured video preview cache, width snap, line count, and frame size.
- Video timeline preview menus now provide optional duration and resolution labels in Files and similarity group details without forcing timeline frames on.
- Duplicate group detail views now support long-press member selection and selected-file deletion.
- Similarity group browsing now supports the saved result filter editor and applies group and paged member rules before groups enter the visible list.
- Result and similarity group filters now support matching groups whose members all have the same byte size.
- Similarity group browsing now provides the shared bulk-delete catalog, filtered previews, and keep-by-text or modified-time commands while retaining deletion highlights only in the active results screen.
- Similarity filters now support matching groups where every video duration stays within a single-value `s` or `ms` tolerance of the exact group average, resolving and caching missing durations in bounded pages when the rule is applied.
- Similarity filters can now match only groups whose media members all have the same width and height, resolving and caching missing dimensions in bounded pages when the rule is applied.
- Results and Similarity bulk delete can now keep the shortest or longest video in each eligible group, using oldest or newest modified time to break equal-duration ties and skipping groups with unreadable durations.
- Results and Similarity text-rule bulk delete can now keep the matching or non-matching side with an exact-one, one-or-more, or exact custom-count requirement before deleting the opposite set.
- Results and Similarity file-name, folder, and modified-time rules now branch to `Any member` or `All members` inside each rule, with existing saved rules retaining `Any member` behavior.
- Similarity result clearing now publishes shared task progress and failure or cancellation outcomes, with a separate resumable incremental clear for recovering large or interrupted result sets in bounded commits.
- Settings now provides a 1-32 scan worker control that persists through preference export and import, bounds concurrent SHA-256 hashing, and applies when each scan starts.
- Settings now provides a separate 1-32 similarity worker control that persists through preference export and import and applies when the next similarity generation starts.

### Changed

- Active task cards, banners, notifications, and blocking database upgrades now show processing speed, elapsed time, and estimated remaining time, with database recovery counts advancing after each committed copy batch.
- Cached files and materialized duplicate groups now store SHA-256 values as 32-byte blobs, with a v22-to-v23 migration that preserves file identities and similarity relationships while rebuilding derived groups.
- Exact-thumbnail similarity features now store canonical reduced-thumbnail SHA-256 values as 32-byte blobs, with a v23-to-v24 migration that hashes existing payloads and replaces raw-pixel cluster keys.
- Database upgrades now require explicit confirmation on a blocking startup screen, keep the main app unavailable until completion, and report the active migration stage.
- Version 21 cache upgrades now build and verify a side-by-side v24 database, preserve readable scan and similarity results when oversized legacy derived tables are corrupt, reconstruct exact-thumbnail features from cluster identities, and replace the original only after integrity and relationship checks pass.
- Scan-cache files and similarity sidecar rows now share stable integer file identities, with an indexed v21-to-v22 migration that preserves valid generated data and removes orphaned relationships.
- Filter editor clusters now provide saveable collapsible headers that retain cluster identity, rule count, logic, enablement, removal, and any open rule editor state.
- Filter editors now present rules inside each cluster as target-labeled accordion rows with distinct tonal borders, expanding one editor at a time while keeping enable and removal controls available.
- Filter rule editors now show only the current target until its selector is opened, with the available targets presented in a dropdown menu.
- Source and manifest text assertions are replaced by runtime tests for merged package metadata, app-owned task survival, foreground-service routing, scan completion ordering, serialized similarity maintenance, and Compose detail interactions.
- Similarity deletion regression coverage now exercises group navigation, detail deletion through Trash, and return-to-list snapshot preservation as one Compose path.
- Duplicate and similarity detail views now check loaded members against the filesystem and mark missing files explicitly.
- Test coverage now removes placeholder, timing-threshold, and prose-snippet checks in favor of settings and Trash cache contract assertions.
- Similarity detail now provides explicit Update and Rebuild actions and explains how they relate to scan-cache generation.
- Similarity management now separates creation, maintenance, entry management, and group browsing into clearer flows with readable summaries and confirmation dialogs for clearing generated similarity data.
- Similarity screens now restore the previous template-style flow, detailed group and duration-neighbor explanations, exact-thumbnail reduction previews, and richer result card summaries while keeping maintained similarity terminology.
- Similarity group detail member cards now restore the previous full-width media card layout with exact byte counts, selection styling, and separated video metadata/timeline preview rows.
- Similarity group browsing now restores member previews, thumbnail-backed group cards, paged group-detail members, and file detail actions.
- Similarity management pages now keep clear/delete controls separate from the group browsing screen while scan completion generates similarity results.
- Similarity keeps scan-completion generation while exposing manual Update/Rebuild controls.
- Similarity group detail member browsing now shares the result member sort control and automatically loads additional members near the end of the list.
- Similarity group lists now restore group sort controls for file count and total size order.
- Similarity group detail screens in maintained entries now restore long-press member selection, selected-file deletion, and video preview menu options.
- Similarity signature explanations now use shared core parsing before the UI formats readable group summaries.
- Similarity group browsing now shows setting rules and parameters in the group header while group cards and details focus on result-specific signatures and spans.
- Duration-neighbor similarity group details now restore duration-order controls and display known member durations.
- Bulk delete execution now shows shared task progress and notifications while deleting files.
- Similarity data now uses sidecar storage joined to the active file cache, so deleted files drop out of similarity results while restored files can reappear after maintenance.
- Video preview settings now describe that timeline preview memory, size, lines, and width snapping apply to both files and similarity group details.
- Duplicate group detail screens now share one member sort control across legacy results, DB results, and similarity exact groups.
- File detail dialogs now show the cached file hash when available and an explicit no-hash state otherwise.

### Fixed

- The Android launch splash and blocking database upgrade screen now follow system dark mode with matching opaque backgrounds and readable foreground colors.
- Task progress bars no longer draw a primary-color stop marker at the right edge while incomplete, and very large incomplete counts remain below 100 percent.
- Scrollbars now map each drag from the current pointer position instead of accumulated deltas, keeping variable-height lazy lists pinned to the final item when thumb estimates change.
- Scan completion now reports automatic similarity generation as part of the scan task instead of staying on the cache-saving status without progress.
- Scan command now uses the shared cache database builder so newly added Room migrations are registered consistently.
- Scan completion now waits for cache persistence and similarity refresh before marking scan tasks complete.
- Scan history recording now uses scanner cache snapshots so duplicate groups and similarity invalidation still see pre-scan cache state.
- Similarity maintenance now reports cancellation before rebuilding groups and serializes automatic and manual generation runs.
- Similarity group detail member sorting now applies at the paged query source instead of sorting only the already loaded subset.
- Similarity result routes now show retryable load errors instead of getting stuck on loading or missing-result states.
- Similarity result lists and result details now use the shared lazy load indicator container.
- Similarity group browsing keeps its loaded parent list and detail-member snapshots in memory across detail back navigation and same-screen re-entry, with deletion changing only the active snapshot colors.
- Trash deletion and DB maintenance now propagate canonical scan-cache mutations to persisted similarity members and groups while the active in-memory result snapshot remains stable.
- Restoring files from Trash now waits for the restored path to rejoin every generated similarity entry, including paused entries, before refreshing cached screens.
- Settings and cancellation tests now isolate persisted preferences and avoid sleep-loop task bodies.
- Similarity group pages now remove or refresh stored group rows when cached files are deleted or changed.
- Similarity group sort changes now rerun after any in-flight page load instead of leaving stale ordering.
- Similarity group browsing now opens from stored group pages instead of blocking initial load on active group aggregation.
- Similarity group lists now preserve scroll position when returning from a group detail screen.
- Similarity Update/Rebuild actions now publish shared task progress again while they process scan-cache candidates.
- Newly created similarity entries now start enabled, including when creation reuses an existing disabled identity.
- Similarity group and member sort selections now persist across screen recreation and app restarts.
- Enabling paused similarity now stays lightweight; paused entries catch up during the next scan-cache generation.
- Similarity group detail top-right video preview, duration, and resolution menu selections now stay enabled when returning to the screen.
- Similarity no longer auto-creates default rows when opened, and individual entries can now be deleted with their generated data.
- Top-right checkbox menu items now stay open after toggling preview and path display options.
- Similarity parameters are now normalized before identity/storage matching, so equivalent custom values reuse the same rows.
- Duration-neighbor similarity generation now builds connected neighbor groups with stable normalized range keys.
- Similarity group member loading now stays under SQLite binding limits for large groups, preventing member previews and detail screens from falling back to unavailable.
- Similarity group detail long-press select-all now keeps lazy not-loaded member handling while paging members.
- Lazy result and similarity detail selection now share one selection state contract, while eager-only detail content is explicitly separated from paged detail screens.
- Simple and result-detail screens now use result-style lazy side scrollbars instead of standalone scroll-state scrollbars.
- Background DB, trash, bulk-delete, and similarity work now use the app-owned task runtime and foreground service, so UI lifecycle changes no longer cancel tracked tasks or reset active task monitoring.
- Bulk-delete execution now keeps paging stable while successful deletes refresh duplicate groups, preventing later groups from being skipped.
- Bulk-delete previews now retain and display every candidate group while reporting full group and file totals.
- Bulk-delete execution now rescans the current snapshot, filter, and command so the displayed preview and full execution use the same candidate scope.
- Results DB filters now evaluate member-dependent filters page-by-page instead of materializing every member at once.
- Duration-neighbor similarity result lists now lazy-load larger member pages and prefetch earlier near the end of the visible list.
- App screens now handle screen size, orientation, layout, and keyboard-hidden configuration changes without recreating and crashing active screens.
- Scan work now runs on an app-owned scope with a foreground service so active scans continue when the UI lifecycle changes or the app stays in the background.
- Background scans no longer stop immediately when scan work starts before the UI job state is assigned.
- Scans and duplicate-group rebuilds now repair same-size cache entries with missing hashes, so interrupted deferred hashing no longer leaves duplicate groups incomplete.
- Duplicate-group rebuild progress now reports the missing-hash repair stage instead of staying on the preparing state.

### Performance

- Duplicate detection, grouping, member lookup, and group paging now compare indexed 32-byte hash blobs instead of 64-character hexadecimal text.
- Similarity feature, member, repair, and result queries now join through integer file IDs instead of normalized path strings, while cache updates preserve IDs through batched inserts and updates.
- Exact-thumbnail grouping now compares indexed 32-byte SHA-256 blobs instead of serialized reduced-pixel text.
- Size-collision hash candidates now run through a bounded worker pool while progress collection and cache writes remain serialized.
- Similarity generation now extracts media signatures, durations, and dimensions through a bounded worker pool while progress collection, database batches, and cluster rebuilding remain serialized.
- Similarity group page queries now use setting-aware sort indexes for file-count and total-size ordering.
- Similarity group browsing now loads stored group rows by page and uses aggregate summaries instead of materializing every group.
- Similarity group detail member thumbnails now compose through lazy list items, so thumbnail and optional video preview loading starts from visible members instead of the whole loaded page.
- Similarity group detail screens now load members page-by-page instead of materializing entire large groups at once.
- Scan cancellation, filtered duplicate results, bulk-delete previews, Empty Trash, scan reports, and duplicate-only database maintenance now page or stream large data sets instead of loading them eagerly.
- Trash restore now recalculates only the restored path instead of scanning every cached file for enabled similarities.

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
