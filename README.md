# CachedDupeScanner

CachedDupeScanner is an **Android-first** duplicate file scanner. It scans very large directories quickly, persists metadata and hashes in a **Room-backed cache**, and dramatically accelerates subsequent scans by reusing cached results.

## Screenshots
<img width="100" src="https://github.com/user-attachments/assets/e580c048-3c51-4356-a23c-88369d99d852" />
<img width="100" src="https://github.com/user-attachments/assets/2f5a4f87-a5a4-4963-af77-bf5a61379d65" />
<img width="100" src="https://github.com/user-attachments/assets/2595b492-c63b-422d-b24d-bb2055c8f48a" />
<img width="100" src="https://github.com/user-attachments/assets/f6f92c3c-5ca2-4fdd-adde-de8d156307b0" />
<img width="100" src="https://github.com/user-attachments/assets/0702e197-e71e-4bf5-8f04-7c5cdbbc1db9" />
<img width="100" src="https://github.com/user-attachments/assets/acbfd65d-9d2a-4c9b-93e5-1548b58fff0d" />
<img width="100" src="https://github.com/user-attachments/assets/5436087b-b117-43fe-915d-b62c2339659c" />
<img width="100" src="https://github.com/user-attachments/assets/b41c374d-1f53-4dee-b04a-d124b8e56db3" />
<img width="100" src="https://github.com/user-attachments/assets/1f5d904a-20c9-48e0-ab3d-2060b683b566" />
<img width="100" src="https://github.com/user-attachments/assets/5a20d12b-7a55-4bf8-8304-f1ae85e1cc00" />
<img width="100" src="https://github.com/user-attachments/assets/33dad9a5-377f-4ebc-a83e-094bc22f7d8e" />
<img width="100" src="https://github.com/user-attachments/assets/9783eeb7-8ea3-4b4f-a5ec-ef7110a2883d" />
<img width="100" src="https://github.com/user-attachments/assets/8808ceec-982e-4bba-a23c-65cc439f36e3" />

## Key features

- **Incremental scans**: unchanged files are not re-hashed; cache is reused.
- **Deferred hashing**: SHA-256 is computed only when size collisions exist.
- **Persistent cache**: metadata stored in scan-cache.db (Room/SQLite).
- **Target management**: save multiple scan targets; run per-target or batch scans.
- **Duplicate grouping**: database-backed result browsing with infinite scrolling for large datasets.
- **Trash flow**: move to .CachedDupeScanner/trashbin with restore/permanent delete.
- **Manage duplicates**: group-detail views with multi-select, select-all, and specific delete tracking.
- **Scan reports**: timings, phase durations, hash candidate counts.
- **Export**: JSON/CSV utilities for results.
- **DB maintenance**: purge missing files, re-hash stale or missing entries, and rebuild duplicate groups. Actionable via notification-backed execution for long-running tasks.

## How scanning works

1. **Collect files**: `FileWalker` gathers file metadata.
2. **Select candidates**: only size-collision files become hash candidates.
3. **Cache lookup**: `CacheStore` checks FRESH/STALE/MISS state.
4. **Hash only when needed**: compute SHA-256 for uncached or stale candidates.
5. **Group results**: duplicates are grouped by hash and persisted.

## Cache policy (current)

The cache is designed to **delay hashing as long as possible**.

1. **Always record size/mtime**: path, size, and modified time are stored for all files.
2. **Hash only on collisions**: hashing occurs only if a size collision exists.
3. **Change detection**: size/mtime changes mark entries as stale, re-hashed on demand.

## Architecture overview

```
UI (Compose)
  -> Scan Orchestrator
      -> File Walker
          -> Hashing (SHA-256)
      -> Cache Store (Room/SQLite)
  -> Results / Reports / Trash
```

Key design points:

- **Deferred hashing** to minimize CPU/I/O
- **Chunked writes** for large datasets
- **Normalized paths** for stable identifiers

## Data model summary

Room database (scan-cache.db) core tables:

- **cached_files**: path, size, mtime, hash
- **scan_reports**: scan summary (durations, counts, targets)
- **trash_entries**: trash records (origin/trashed path, size, timestamps)
- **dupe_groups**: materialized snapshot of duplicate groups for fast paginated browsing


## Module map

- **core**: `FileMetadata`, `ScanResult`, duplicate analysis
- **engine**: `IncrementalScanner`, `FileWalker`, hashing
- **cache**: Room entities/DAO, cache lookup/upsert
- **storage**: settings/targets/reports/trash
- **export**: JSON/CSV exports
- **ui**: Compose screens and components

## Main screens

- Dashboard (entry point)
- Permission (file access)
- Targets (scan targets)
- Scan Command (run scans + progress)
- Results / Files (duplicates and file list)
- Trash (restore/permanent delete)
- DB Management (cleanup/rehash)
- Reports (scan reports)
- Settings / About

## Quickstart

### Requirements

- Android Studio (latest stable)
- JDK 17+ (Android toolchain)

### Build

```bash
./gradlew assembleDebug
```

### Tests

```bash
./gradlew test
```

### Instrumented tests

```bash
./gradlew connectedAndroidTest
```

### Release automation

For signed APK automation, see [docs/android-apk-release.md](docs/android-apk-release.md).

## Development workflow

Project rules and the agent guide are in [AGENTS.md](AGENTS.md).

## License

See [LICENSE](LICENSE).
