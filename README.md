# CachedDupeScanner

CachedDupeScanner is an **Android-first** duplicate file scanner. It scans very large directories quickly, persists metadata and hashes in a **Room-backed cache**, and dramatically accelerates subsequent scans by reusing cached results.

## Screenshots
<img width="100" alt="Screenshot_20260204_153212" src="https://github.com/user-attachments/assets/5a4dda4b-02af-4fc1-9546-58fbd8ca7a45" />
<img width="100" alt="Screenshot_20260204_153310" src="https://github.com/user-attachments/assets/366568dc-cf8c-4c58-ab4c-fff2a018ef2f" />
<img width="100" alt="Screenshot_20260204_153318" src="https://github.com/user-attachments/assets/262fb3e5-5e74-4b0f-a190-525ce9e39ac2" />
<img width="100" alt="Screenshot_20260204_153327" src="https://github.com/user-attachments/assets/64662850-743c-4b89-8e6c-3c95acba6ec3" />
<img width="100" alt="Screenshot_20260204_153343" src="https://github.com/user-attachments/assets/23970da5-27e7-40b4-a318-d2a7cb0a067d" />
<img width="100" alt="Screenshot_20260204_153354" src="https://github.com/user-attachments/assets/4fef6179-0738-453f-91c0-000cb8e18b29" />
<img width="100" alt="Screenshot_20260204_153447" src="https://github.com/user-attachments/assets/99a18a0b-04bf-408f-8d0e-e71cbe622544" />
<img width="100" alt="Screenshot_20260204_153452" src="https://github.com/user-attachments/assets/d15621e9-5165-4b56-a1d1-a0f463a8c0a6" />
<img width="100" alt="Screenshot_20260204_153508" src="https://github.com/user-attachments/assets/7797694c-fd2d-420a-8944-3164e8e79f0a" />
<img width="100" alt="Screenshot_20260204_153541" src="https://github.com/user-attachments/assets/cdc0c813-8406-4491-b1c1-8bbaa554ffdb" />
<img width="100" alt="Screenshot_20260204_153555" src="https://github.com/user-attachments/assets/15971c27-0f4e-4ff9-8b6d-b3e99a7b0513" />
<img width="100" alt="Screenshot_20260204_153600" src="https://github.com/user-attachments/assets/92c8e80a-eebf-4a31-9ed8-aad07cc27b2c" />

## Key features

- **Incremental scans**: unchanged files are not re-hashed; cache is reused.
- **Deferred hashing**: SHA-256 is computed only when size collisions exist.
- **Persistent cache**: metadata stored in scan-cache.db (Room/SQLite).
- **Target management**: save multiple scan targets; run per-target or batch scans.
- **Duplicate grouping**: results grouped by hash with sorting/filtering.
- **Trash flow**: move to .CachedDupeScanner/trashbin with restore/permanent delete.
- **Scan reports**: timings, phase durations, hash candidate counts.
- **Export**: JSON/CSV utilities for results.
- **DB maintenance**: purge missing files, re-hash stale or missing entries.

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

For detailed DB access flows, see [docs/db-access.md](docs/db-access.md).

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
