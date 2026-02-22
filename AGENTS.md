# Agent Guide

This document is the operational guide for AI agents working on CachedDupeScanner. Follow it exactly.

## Mission

Build a high-performance duplicate file scanner with a persistent cache that speeds up subsequent scans. Every change must be test-driven and milestone-aligned.

## Ground Rules

- Do not skip tests.
- Do not make unrelated refactors.
- Keep commits small and scoped.
- Use the milestones in README as the roadmap.
- If a requirement is unclear, inspect the codebase before asking questions.
- Optimize large-scale operations with bounded batching/chunking and avoid unnecessary global rebuilds in runtime paths.
- For large mutations, apply transaction boundaries that keep source data and derived data consistent per batch, so interruptions still leave committed batches in a valid state.
- Keep expensive external I/O outside DB transactions when possible to reduce lock duration.

## Required Workflow

1. Read README milestones.
2. Identify the smallest next deliverable.
3. Implement only that deliverable.
4. Add or update tests.
5. Run tests and confirm they pass.
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
- README milestones updated if scope changes.

## Scope Boundaries

Agents must not:

- Introduce new architecture without a milestone update.
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
