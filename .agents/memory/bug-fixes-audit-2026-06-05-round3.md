---
name: Bug fixes audit round 3 (2026-06-05)
description: Five surgical fixes applied in Round 3 of the full-spectrum diagnostic audit.
---

## Bugs fixed (commit c26cdac)

### 1. NetworkBlocker — duplicate PowerShell on cache miss
- **File**: `enforcement/NetworkBlocker.kt`
- **Bug**: Two concurrent `addRule("x.exe")` calls both saw the cache miss and both spawned PowerShell for the same baseName.
- **Fix**: Split into `resolveExePath()` (fast path + `synchronized(resolvedPaths)` double-check) and `resolveExePathUncached()` (does the actual PowerShell + dir walk). Null results are never stored so `retryPendingRules()` can retry once the process is running.
- **Why `computeIfAbsent` was rejected**: `resolvedPaths` is `ConcurrentHashMap<String, String>` (non-nullable value). Kotlin rejects a `computeIfAbsent` lambda that returns null. Used explicit `synchronized` double-check instead.

### 2. RecurringTaskService — duplicate tasks on concurrent generateForToday()
- **File**: `services/RecurringTaskService.kt`
- **Bug**: Two threads (startup coroutine + UI refresh button) both read `existing` as empty before either DB insert committed, both passed the dedup-key check, and both inserted the same template — producing duplicate tasks for the day.
- **Fix**: `@Synchronized` on `generateForToday()`.

### 3. BackupService — CSV newline injection
- **File**: `services/BackupService.kt`
- **Bug**: `replace("\"","'")` was the only CSV escaping applied. A task name or description containing `\n` would start a new row in Excel/Google Sheets, misaligning all subsequent columns.
- **Fix**: Local `String.csvField()` extension strips `\r` and replaces `\n` with space before wrapping in quotes. Applied to both `exportToCsv()` and `exportTasksToCsv()`.

### 4. Main.kt — FocusSessionService.dispose() never called at shutdown
- **File**: `Main.kt`
- **Bug**: `doShutdown` called `FocusSessionService.end()` but never `dispose()`. The internal `CoroutineScope` + timer coroutine stayed live until JVM exit.
- **Fix**: Added `FocusSessionService.dispose()` call immediately after `end()` in the shutdown sequence.

### 5. SessionPin — blank-bypass analysis (no code change needed)
- **File**: `services/SessionPin.kt`
- **Conclusion**: The `isNullOrBlank() → return true` in `verify()` is safe because callers always call `isSet()` first. `isSet()` uses the same `getSetting(KEY)?.isNotBlank() == true` check; if the hash is missing/blank, `isSet()` returns false and the caller never shows the PIN dialog — so `verify()` is never invoked with a stale empty value. Added detailed explanatory comment in the code.

## Items deliberately NOT fixed

- **Main.kt shutdown thread daemon flag**: Intentional. The Compose window's non-daemon AWT thread keeps the JVM alive until `exitApplication()` is called at the END of the shutdown thread. The daemon flag prevents a hung service from freezing the OS indefinitely.
- **FocusLauncherService double RegistryLockdown.disable()**: Cosmetic taskbar flicker on startup. Both calls are idempotent; fixing requires invasive cross-file changes.
- **SoundAversion line.drain() in IO pool**: Intentional design — audio draining must block until the buffer empties.
- **BackupService FileDialog on UI thread**: The `FileDialog.isVisible = true` call MUST run on the AWT EDT (it's an OS-native dialog). The call IS blocking by design — user must pick a file before the app continues. Not a bug.
