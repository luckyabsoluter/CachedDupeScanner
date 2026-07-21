# Agent Guide

This document is the operational guide for AI agents working on CachedDupeScanner. Follow it exactly.

## Mission

Build a high-performance duplicate file scanner with a persistent cache that speeds up subsequent scans. Every change must be test-driven and focused on the requested scope.

## Ground Rules

- Do not skip tests.
- Do not make unrelated refactors.
- Keep commits small and scoped.
- If a requirement is unclear, inspect the codebase before asking questions.
- Optimize large-scale operations with bounded batching/chunking and avoid unnecessary global rebuilds in runtime paths.
- For large mutations, apply transaction boundaries that keep source data and derived data consistent per batch, so interruptions still leave committed batches in a valid state.
- Keep expensive external I/O outside DB transactions when possible to reduce lock duration.
- Run Gradle through `mise x -- ./gradlew ...` without prefixing `ANDROID_HOME`; rely on the repo/local SDK configuration unless Gradle cannot discover the SDK.

## Required Workflow

1. Identify the smallest next deliverable.
2. Implement only that deliverable.
3. Add or update tests.
4. Run tests and confirm they pass.
5. Update CHANGELOG.md to reflect the changes.
6. Commit with a Conventional Commit message.

## Definitions

- **Cache**: persisted metadata and hash results used to avoid reprocessing unchanged files.
- **Incremental scan**: scanning that reuses cache and re-hashes only new or modified files.
- **Tiered hashing**: size and mtime checks before partial and full hashes.

## Testing Checklist

- Unit tests cover new logic.
- Regression tests added for any bug fixes.
- Performance-sensitive changes include benchmark smoke tests when applicable.

## Commit Checklist

- Single purpose per commit.
- Tests included for new behaviors.
- CHANGELOG.md updated to reflect the changes.

## Scope Boundaries

Agents must not:

- Introduce new architecture without an explicit plan.
- Add external services or analytics.
- Remove or rename public APIs without a plan.

## Suggested Commit Prefixes

- `feat(scope): ...`
- `fix(scope): ...`
- `test(scope): ...`
- `docs(scope): ...`
- ~~`chore(scope): ...`~~ Do not use chore
- `config(scope): ...`
- `refactor(scope): ...`
- `style(scope): ...` Should result in identical machine code. (Use `design` for UI changes)
- `design(scope): ...` User Interface (UI) and visual changes.
- `perf(scope): ...`
- `ci(scope): ...`
- `build(scope): ...`
