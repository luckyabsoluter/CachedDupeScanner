# CachedDupeScanner

CachedDupeScanner is an Android-first project that scans very large file sets for duplicates, persists scan metadata, and reuses that cache to make subsequent scans dramatically faster.



## Goals

- **Fast duplicate detection** across massive directories.
- **Persistent cache** to avoid re-reading unchanged files.
- **Deterministic results** with reproducible test suites.
- **Open-source friendly** structure and contribution workflow.

## What is “Cached” here?

Each scan stores file metadata (path, size, timestamp, hashes) and directory state. Later scans compare changes and only re-hash files that are new or modified.

## Cache Policy (Current)

The cache is designed to **delay hashing as long as possible** and only compute hashes when needed.

1. **Always store size first**
	- File path + size + mtime are recorded for every scanned file.
2. **Hash only on collisions**
	- Hash is computed **only when there is a size collision** (same size appears more than once in the candidate set).
3. **Incremental re-hash**
	- If size/mtime changes, the file is treated as stale and re-hashed only when needed by step 2.

This keeps CPU and I/O costs low for large scans while still ensuring reliable duplicate detection.

## Quickstart

### Requirements

- Android Studio (latest stable)
- JDK 17+ (Android toolchain)

### Build

```bash
./gradlew assembleDebug
```

### Release automation

See [docs/android-apk-release.md](docs/android-apk-release.md) for GitHub Actions release workflow setup and keystore/secrets instructions.

### Tests

```bash
./gradlew test
```

### Instrumented Tests

```bash
./gradlew connectedAndroidTest
```

## Architecture

```
UI (Compose)
  -> Scan Orchestrator
	  -> File Walker
		  -> Hashing Engine (tiered: size -> partial hash -> full hash)
	  -> Cache Store (Room/SQLite)
  -> Results Store
```

Key decisions:

- **Tiered hashing** for speed: size and mtime checks before computing hashes.
- **Chunked I/O** to avoid memory spikes.
- **Stable identifiers** for files to track across scans.

## License

See [LICENSE](LICENSE).