# CachedDupeScanner

CachedDupeScanner is an Android-first project that scans very large file sets for duplicates, persists scan metadata, and reuses that cache to make subsequent scans dramatically faster.

This repository is optimized for AI vibe coding workflows: the README is the single source of truth for milestones, required tests, and commit boundaries. Treat the sections below as the execution plan for agents and contributors.

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

## Project Status

This repository currently contains the Android app shell. The scan engine, cache storage layer, and test suite will be built step-by-step using the milestones defined below.

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

## Architecture (Planned)

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

## Milestones (Agent Roadmap)

Each milestone must be delivered with passing tests and a clean, focused commit. Use the commit messages below as guidance.

### M1 — Core scan model & hashing

Deliverables:

- Data model for file metadata and scan results.
- Hashing utilities with streaming support.

Required tests:

- Hashing produces expected digest for known fixtures.
- Streaming hash matches single-pass hash.
- File metadata normalization is deterministic.

Suggested commit:

```
feat(core): add file metadata model and hashing utils
```

### M2 — Cache store

Deliverables:

- Cache schema (Room/SQLite).
- Upsert and lookup logic.

Required tests:

- Cache insert/update semantics.
- Cache lookup returns correct stale/fresh results.

Suggested commit:

```
feat(cache): add persistent scan cache
```

### M3 — Scan engine

Deliverables:

- File walker that honors ignore rules.
- Incremental scan strategy using the cache.

Required tests:

- Incremental scan avoids re-hashing unchanged files.
- Modified files are re-hashed and updated in cache.

Suggested commit:

```
feat(engine): add incremental duplicate scan engine
```

### M4 — UI results and export

Deliverables:

- Compose UI for scan progress and results.
- Export results to JSON/CSV.

Required tests:

- UI state reducer handles progress, success, and error.
- Exporter produces stable output.

Suggested commit:

```
feat(ui): add scan results view and export
```

### M5 — Performance & regression tests

Deliverables:

- Benchmark scaffolding.
- Regression tests for cache behavior.

Required tests:

- Benchmark smoke test runs within time limits.
- Regression cases for previously fixed issues.

Suggested commit:

```
test(perf): add benchmark and regression tests
```

## License

See [LICENSE](LICENSE).