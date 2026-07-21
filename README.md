# CachedDupeScanner

CachedDupeScanner is an **Android duplicate file scanner**. It scans very large directories, persists metadata and hashes in a **Room-backed cache**, and accelerates subsequent scans by reusing unchanged results.

## Screenshots
<img width="160" alt="CachedDupeScanner Android interface screenshot 1" src="https://github.com/user-attachments/assets/e580c048-3c51-4356-a23c-88369d99d852" />
<img width="160" alt="CachedDupeScanner Android interface screenshot 2" src="https://github.com/user-attachments/assets/2f5a4f87-a5a4-4963-af77-bf5a61379d65" />
<img width="160" alt="CachedDupeScanner Android interface screenshot 3" src="https://github.com/user-attachments/assets/2595b492-c63b-422d-b24d-bb2055c8f48a" />
<img width="160" alt="CachedDupeScanner Android interface screenshot 4" src="https://github.com/user-attachments/assets/f6f92c3c-5ca2-4fdd-adde-de8d156307b0" />
<img width="160" alt="CachedDupeScanner Android interface screenshot 5" src="https://github.com/user-attachments/assets/0702e197-e71e-4bf5-8f04-7c5cdbbc1db9" />
<img width="160" alt="CachedDupeScanner Android interface screenshot 6" src="https://github.com/user-attachments/assets/acbfd65d-9d2a-4c9b-93e5-1548b58fff0d" />
<img width="160" alt="CachedDupeScanner Android interface screenshot 7" src="https://github.com/user-attachments/assets/5436087b-b117-43fe-915d-b62c2339659c" />
<img width="160" alt="CachedDupeScanner Android interface screenshot 8" src="https://github.com/user-attachments/assets/b41c374d-1f53-4dee-b04a-d124b8e56db3" />
<img width="160" alt="CachedDupeScanner Android interface screenshot 9" src="https://github.com/user-attachments/assets/1f5d904a-20c9-48e0-ab3d-2060b683b566" />
<img width="160" alt="CachedDupeScanner Android interface screenshot 10" src="https://github.com/user-attachments/assets/5a20d12b-7a55-4bf8-8304-f1ae85e1cc00" />
<img width="160" alt="CachedDupeScanner Android interface screenshot 11" src="https://github.com/user-attachments/assets/33dad9a5-377f-4ebc-a83e-094bc22f7d8e" />
<img width="160" alt="CachedDupeScanner Android interface screenshot 12" src="https://github.com/user-attachments/assets/9783eeb7-8ea3-4b4f-a5ec-ef7110a2883d" />
<img width="160" alt="CachedDupeScanner Android interface screenshot 13" src="https://github.com/user-attachments/assets/8808ceec-982e-4bba-a23c-65cc439f36e3" />

## Key features

- **Incremental scans**: unchanged files are not re-hashed; cache is reused.
- **Deferred hashing**: SHA-256 is computed only when size collisions exist.
- **Persistent cache**: metadata stored in scan-cache.db (Room/SQLite), with stable numeric file identities, indexed 32-byte SHA-256 storage shared by derived data, and user-approved blocking upgrades before the app opens its data screens.
- **Target management**: save multiple scan targets; run per-target or batch scans.
- **Duplicate grouping**: database-backed result browsing with infinite scrolling for large datasets.
- **Trash flow**: move files to .CachedDupeScanner/trashbin with restore and permanent-delete controls. The bin is excluded from scans by default.
- **Manage duplicates**: group-detail views with multi-select, select-all, and specific delete tracking.
- **Scan reports**: timings, phase durations, hash candidate counts.
- **App-wide task monitoring**: in-app banners and a draggable bubble follow long-running work across screens, while Android notifications expose active work outside the app. Task surfaces show processing speed, elapsed time, and estimated remaining time.
- **Background execution**: an app-owned task runtime, foreground data-sync service, and partial WakeLocks keep supported work active across UI lifecycle changes and device sleep.
- **Performance controls**: separate configurable 1-32 worker limits for scan hashing and similarity feature calculation, optional memory usage overlay, shared RAM thumbnail retention, and configurable thumbnail/timeline preview sizing for heavy workloads.
- **Rich media previews**: Timeline video preview mode with a dedicated RAM cache policy, width snapping, and multi-line frame rows.
- **Smart filters**: Saved filters, per-rule any/all member matching, same-folder and same-size group rules, similarity same-resolution and average-duration rules, and modified-time rules that persist across sessions.
- **Advanced bulk delete**: Full-candidate previews support keeping exactly one, at least one, or an exact custom count of matching or non-matching files by text rule, or keeping the oldest, newest, shortest-duration, or longest-duration file, with modified-time fallback for equal video durations.
- **DB maintenance**: purge missing files, re-hash stale or missing entries, rebuild duplicate groups, and scope maintenance to all cached files, detected duplicate-result groups, or generated similarity groups. Actionable via notification-backed execution.
- **Similarity**: configure and browse named video/image similarity clustering generated from scan-cache data after scans; exact reduced thumbnails are grouped by indexed 32-byte SHA-256 values, while numeric file-ID joins, bounded parallel media feature extraction, persistent sort options, and progress-tracked Update/Rebuild actions keep large result sets practical.

## How scanning works

For filesystem scans:

1. **Collect eligible files**: `FileWalker` gathers metadata while applying configured exclusions.
2. **Select candidates**: non-zero files become hash candidates when their size collides in the current scan or persisted cache.
3. **Check the cache**: `CacheStore` classifies candidate metadata as FRESH, STALE, or MISS.
4. **Hash only when needed**: uncached, stale, or missing-hash candidates are processed through the configured bounded SHA-256 worker pool.
5. **Persist results**: file metadata is cached and duplicate groups are derived from matching hashes.

## Cache policy (current)

The cache is designed to **delay hashing as long as possible**.

1. **Record eligible metadata**: path, size, and modified time are stored for files that pass scan exclusions and cache settings; zero-size cache entries are skipped by default.
2. **Hash collisions during scans**: regular scans hash non-zero files only when their size collides with another current or cached entry.
3. **Detect changes and repair on demand**: size or modified-time changes mark entries as stale, while explicit DB maintenance can repair stale or missing hashes.

## Architecture overview

```
Compose UI
  -> App task coordinator
      -> Foreground service / notifications / partial WakeLocks
      -> Scan engine
          -> File walker
          -> Bounded SHA-256 workers
      -> Similarity / DB maintenance / Trash operations
  -> Room cache and derived result tables
```

Key design points:

- **Deferred hashing** to minimize CPU and I/O
- **Bounded workers and chunked writes** for large datasets
- **Stable numeric file identities** for derived-table joins, with normalized paths retained for unique lookup and display

## Data model summary

Room database (scan-cache.db) core tables:

- **cached_files**: path, size, mtime, hash
- **scan_reports**: scan summary (durations, counts, targets)
- **trash_entries**: trash records (origin/trashed path, size, timestamps)
- **dupe_groups**: materialized snapshot of duplicate groups for fast paginated browsing
- **similarity_settings / similarity_setting_files / method-specific feature tables / similarity_clusters / similarity_cluster_members**: configured similarity methods, file state, compact method-specific feature storage including binary thumbnail hashes, and sidecar member links for similarity-based duplicate candidates

## Package map

- **core**: `FileMetadata`, `ScanResult`, duplicate analysis
- **engine**: `IncrementalScanner`, `FileWalker`, hashing
- **cache**: Room entities/DAO, cache lookup/upsert
- **storage**: settings, targets, reports, Trash, and Similarity repositories
- **tasks / notifications**: app-owned task state, foreground execution, and Android notifications
- **export**: JSON/CSV result serializers used as a developer utility; they are not currently exposed in the app UI
- **ui**: Compose screens and components

## Main screens

- Dashboard (entry point)
- Permission (file access)
- Targets (scan targets)
- Scan Command (run scans + progress)
- Results / Files (duplicates and file list)
- Trash (restore/permanent delete)
- DB Management (cleanup/rehash)
- Similarity (managed similarity clustering and results)
- Reports (scan reports)
- Settings / About

## Permissions and data handling

- Filesystem scans can require broad storage access so the app can enumerate, hash, move, restore, and delete user-selected files.
- Scanning, hashing, cache maintenance, and Similarity processing run locally. The app does not declare the Android Internet permission.
- Metadata, hashes, reports, Trash records, and Similarity results are stored in the local `scan-cache.db` database.
- Normal deletion moves files to `.CachedDupeScanner/trashbin`; permanent deletion from Trash cannot be undone.
- Long-running work uses a foreground data-sync service and may display Android notifications.

## Quickstart

### Requirements

- Android Studio or a command-line Android SDK installation with SDK Platform 36
- JDK 17 or newer to launch Gradle; the build selects a JetBrains JDK 21 daemon and Kotlin toolchain
- An Android API 24 or newer device or emulator for installation and instrumented tests
- Network access on the first build for Gradle dependencies and toolchain provisioning

### Build

```bash
./gradlew assembleDebug
```

### Tests

```bash
./gradlew test
```

### Instrumented tests

Connect an API 24 or newer device or start an emulator before running:

```bash
./gradlew connectedAndroidTest
```

### Release automation

For signed APK automation, see [docs/android-apk-release.md](docs/android-apk-release.md).

## Development workflow

Project rules and the agent guide are in [AGENTS.md](AGENTS.md).

## License

See [LICENSE](LICENSE).
