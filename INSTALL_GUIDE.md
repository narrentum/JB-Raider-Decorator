# Plugin installation and test guide

This file explains how to install and quickly verify the plugin locally on Windows.

## Quick install (Windows)

1. Build the plugin with Gradle (use the bundled wrapper on Windows):

```powershell
.\gradlew.bat clean buildPlugin --no-daemon
```

2. The produced ZIP will be in `build\distributions\` (example: `Decorator for Raider-1.3.44.zip`).

3. In Rider/IDEA: `File → Settings → Plugins` → gear icon → `Install Plugin from Disk...` → select the ZIP.

4. Restart the IDE when prompted.

## Quick smoke test

1. Open `test_files\TestFile.cs` (or any project file) in the IDE.
2. Trigger the plugin highlight action (or edit the file to trigger throttled highlighting).
3. Expected:
   - Configured rules are applied. On large files the plugin processes document in 100-line chunks and applies partial highlights as chunks complete.
   - Per-task overlays show progress and disappear when a task or chunk finishes.

## PowerShell snippet (example variables)

```powershell
$zip = Join-Path $PSScriptRoot 'build\distributions\Decorator for Raider-1.3.44.zip'
Write-Host "Plugin: $zip"
```

## Notes and troubleshooting

- If highlights are not visible, open `Help → Show Log in Explorer` and check idea logs for exceptions from `DirectHighlighterComponent`.
- Avoid committing local IDE artifacts or crash dumps (`*.hprof`) to the repository. Use `.gitignore` (already included).
- The plugin uses the local Rider platform when building (see `build.gradle.kts` local path during development).

---
Generated from previous `INSTALL_GUIDE.txt` and updated to Markdown for easier reading.
