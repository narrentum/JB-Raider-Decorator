
# JetBrains Rider / IntelliJ Code Highlighter Plugin

Lightweight plugin to highlight configured words and patterns inside JetBrains Rider and IntelliJ IDEA.

## Features

- Configurable highlighting rules (plain text or regular expressions).
- Partial/streaming highlighting for large files (100-line chunks).
- Per-task progress overlays and configurable partial application.
- Color, font-style and decoration options per rule.

## Supported IDEs

- JetBrains Rider 2024.3+ (development targets Rider local install)
- IntelliJ IDEA 2024.3+

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

## Requirements

- Kotlin 2.x (project configured for Kotlin/JVM)
- JDK 17

## License

MIT License
