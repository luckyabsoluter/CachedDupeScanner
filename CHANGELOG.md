# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project adheres to Semantic Versioning.

## [Unreleased]

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
