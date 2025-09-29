
# JetBrains Rider / IntelliJ Code Highlighter Plugin

Lightweight plugin to highlight configured words and patterns inside JetBrains Rider and IntelliJ IDEA.

## Features

- Configurable highlighting rules (plain text or regular expressions).
- Partial/streaming highlighting for large files (100-line chunks).
- Per-task progress overlays and configurable partial application.
- Color, font-style and decoration options per rule.

## Supported IDEs

- JetBrains Rider 2025.2 (development targets Rider local install)
- IntelliJ IDEA 2025.2

## Installation

1. Download the plugin ZIP from Releases or build locally (see below).
2. In IDE: `File → Settings → Plugins` → gear → `Install Plugin from Disk...` → select ZIP.
3. Restart IDE.

## Build from source (Windows)

Use the bundled Gradle wrapper on Windows:

```powershell
.\gradlew.bat clean buildPlugin --no-daemon
```

Resulting ZIP will be in `build\distributions\`.

## Development notes

- The highlighter reads rules from the plugin settings UI. Rules may include an optional "condition/key" field. When set, the plugin first searches for the condition within the first N lines (configurable). If the condition isn't found, the rule is skipped.
- For large files (>500 lines) the document is split into 100-line chunks and processed in parallel. Partial highlights are applied as chunks complete if `applyPartialOnRuleComplete` is enabled.
- Avoid committing IDE local artifacts or crash dumps (`*.hprof`) — they are ignored by `.gitignore`.

## Quick smoke test

1. Open `test_files\TestFile.cs` (or any file) and trigger highlighting.
2. On large files you should see partial highlights and per-task overlays that disappear when tasks complete.

## Advantages & Highlights

Initial public release — CodeDecorator for Rider (minimal, working rule-based text highlighter).

Key features
- Rule-based highlighting by name/word/regex with exclusion filters.
- Support for background/foreground colors, font styles and text decorations (underline / strikethrough).
- Settings UI: enable/disable rules, edit parameters, configure highlight update delay and condition-search window (including whole-file mode).

Performance & reliability
- Chunked file processing with ExecutorService for large files.
- Partial results applied as chunks complete for faster feedback.
- Background tasks cancelled when stale; overlays/tasks updated and hidden safely.
- Defensive logging and exception handling.

Editor behavior
- Respects user edits, including typing, paste, undo/redo; invalidates and removes stale highlighters on change.
- Applies and removes highlights without blocking the UI thread.

Known limitations (this build)
- Visual overlay/progress UI is disabled (no-op stub) — progress UI will be added in future builds.
- Needs additional testing across Rider builds / macOS environments; core implementation uses IntelliJ Platform APIs and is cross-platform.
- Minimal localization and UX polish in this initial release.

## What works

- Rule-based highlighting by plain text or regular expressions with optional exclusion filters.
- Per-rule color, font-style and text decoration (underline / strikethrough) support.
- Settings UI to enable/disable rules, edit rule parameters, and tune performance options (highlight update delay, condition-search window or whole-file mode).
- Chunked processing for large files (document split into chunks and processed in parallel); partial highlights are applied as chunks complete when enabled.
- Responsive behavior around user edits: typing, paste, undo/redo trigger safe invalidation and recalculation of highlights without blocking the UI thread.
- Background tasks are cancellable when an editor is switched or a newer generation of work is started.

## What doesn't work / Known limitations

- Visual overlay / progress UI is disabled in this build (implemented as a no-op stub). Progress indicators and interactive overlays will be re-enabled in a future release.
- Limited cross-version testing: while the code uses IntelliJ Platform APIs and is intended to be cross-platform (Windows/macOS/Linux), it still requires broader testing on Rider 2025.x on macOS and other platforms.
- Minimal localization and UI polish: the settings UI and help text need refinement and localization work.
- Automated tests and CI compatibility checks are not yet available in this repository — unit tests and automated compatibility validation are planned.
- Some advanced editor integrations (overlay widgets, complex drag/drop behavior) are intentionally disabled or stubbed in the initial release.

## 🤝 Development & Collaboration

This plugin was created in collaboration with an AI assistant.  
The human author worked together with AI to accelerate design, implementation, and documentation.  
AI contributed to:

- Code architecture and implementation  
- Advanced regex pattern matching and performance tuning  
- Kotlin and IntelliJ Platform best practices (including Rider-specific APIs)  
- Editor behavior handling (highlighters, background processing, task cancellation)  
- Documentation, examples, and release notes

The combination of human creativity and AI assistance resulted in a robust, feature-rich extension that handles complex decoration scenarios with ease.

## Requirements

- Kotlin 2.x (project configured for Kotlin/JVM)
- JDK 17

## License

MIT License
