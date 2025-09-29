(# Plugin status and developer notes

Date: 2025-09-27

## Current status

- Code compiles and `./gradlew buildPlugin` produces a plugin distribution (last verified locally).
- Implemented chunked processing for large files (100-line chunks) with prioritized visible-chunk-first ordering.
- Per-rule "condition/key" search implemented: rules with non-empty condition are first checked (search limited to a configurable number of lines). Only rules whose condition is found participate in the main pattern search.
- Per-task overlays and per-block overlays are shown and hidden when the corresponding task completes. Fixed an earlier bug where overlays were not hidden due to identifier mismatch.

## Known issues

- Runtime validation required: please test in Rider to confirm overlays hide reliably in edge cases and partial application behaves as expected.
- Rules with complex regex for comment-matching may be slow; consider precompiling or limiting scope.

## Reproducer (manual)

1. Build plugin: `./gradlew.bat clean buildPlugin`
2. Install plugin from disk in Rider.
3. Open a large file (~2000+ lines) and trigger highlight.
4. Observe per-task overlays and partial highlights as chunks complete.

## Next steps

- Add settings to expose chunk size and concurrency.
- Add logging/telemetry for task lifecycle to ease debugging of overlay lifecycle issues.
- Add tests for rule condition detection and chunked processing logic (unit tests with sample texts).

## Maintainer notes

- When changing background processing, ensure that task ids used for overlays are stable and unique per-submission so overlays can be hidden reliably regardless of completion order.
- Avoid committing local `.intellijPlatform` or `*.hprof` files — they are heavy and already removed from repo history.

