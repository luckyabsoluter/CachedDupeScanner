# CachedDupeScanner

CachedDupeScanner is an **Android-first** duplicate file scanner. It scans very large directories quickly, persists metadata and hashes in a **Room-backed cache**, and dramatically accelerates subsequent scans by reusing cached results.

## Screenshots
<img width="100" src="https://github.com/user-attachments/assets/e580c048-3c51-4356-a23c-88369d99d852" />
<img width="100" src="https://github.com/user-attachments/assets/1eafa9b1-6de7-49f4-9416-ba556be9893a" />
<img width="100" src="https://github.com/user-attachments/assets/2595b492-c63b-422d-b24d-bb2055c8f48a" />
<img width="100" src="https://github.com/user-attachments/assets/c8105f4e-f16b-40e0-86e5-5660ece2a8fd" />
<img width="100" src="https://github.com/user-attachments/assets/969a62d7-47e0-4b38-8922-dfe407e6f508" />
<img width="100" src="https://github.com/user-attachments/assets/07f34db9-ca7d-4c06-8830-ac55964b09b4" />
<img width="100" src="https://github.com/user-attachments/assets/fb076a13-605e-4c29-a2d9-af9d9d16ea33" />
<img width="100" src="https://github.com/user-attachments/assets/d7f90d51-15af-4e53-9093-abe9971e373b" />
<img width="100" src="https://github.com/user-attachments/assets/164d7c80-520c-4418-b9cc-42d31df34637" />
<img width="100" src="https://github.com/user-attachments/assets/ab779d92-3887-49cd-ba74-6b7694f317ef" />
<img width="100" src="https://github.com/user-attachments/assets/9783eeb7-8ea3-4b4f-a5ec-ef7110a2883d" />
<img width="100" src="https://github.com/user-attachments/assets/8808ceec-982e-4bba-a23c-65cc439f36e3" />

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
