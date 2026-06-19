---
name: Bug fixes audit round 9 — health check 50-issue sweep
description: 11 confirmed bugs fixed across enforcement, UI, and security layers. Notes which of the 50 reported issues were genuine vs already correct.
---

## Fixes applied

### FD leaks — enforcement processes
- **WinApiBindings.kt** `killProcessByName` / `killProcessByPid`: replaced `.redirectErrorStream(true).start()` (creates a pipe that GC must close) with `.redirectInput(DISCARD).redirectOutput(DISCARD).redirectError(DISCARD).start()`. No pipe FDs created at all.
- **NetworkBlocker.kt** `runPowerShell()`: same DISCARD redirect fix.
- **NetworkBlocker.kt** `isRunningAsAdmin()`: added `proc.outputStream.close()` immediately after start(); wrapped `inputStream.use {}` so the FD is released as soon as the read completes.
- **NetworkBlocker.kt** `runPowerShellAndRead()`: added `proc.outputStream.close()` + replaced unbounded `.readText()` with a manual 64 KB cap loop inside `inputStream.use { stream.bufferedReader().use { ... } }`.

### Null-safety — thread double-deref
- **WinEventHook.kt** lines 205-206: `hookThread!!.isDaemon = true; hookThread!!.start()` → `val t = hookThread ?: return; t.isDaemon = true; t.start()`. Without the local val, `stop()` could null the field between the two `!!` dereferences.
- **GlobalKeyboardHook.kt** lines 196-197: same pattern → `val t = pumpThread ?: return`.

### Unsafe cast outside try block
- **FloatingBlockOverlay.kt** `paintComponent()`: `g.create() as java.awt.Graphics2D` was OUTSIDE the try/finally block. Changed to `as? java.awt.Graphics2D ?: return`. Prevents ClassCastException or NPE from crashing the paint callback on disposed components.

### Leaked coroutine loop
- **FocusLauncherOverlay.kt**: `while (true)` in LaunchedEffect → `while (isActive)`. Without `isActive`, the 1-second clock ticker keeps running after the LaunchedEffect coroutine is cancelled (e.g. when the overlay leaves composition).

### Stale composable state
- **App.kt**: `focusPreloadTask` was set when navigating to FocusScreen but never cleared. Added `LaunchedEffect(currentScreen) { if (currentScreen != Screen.FOCUS) focusPreloadTask = null }`. Prevents the old task from pre-populating FocusScreen on a subsequent nav from the side bar.

### Security — hardcoded webhook URLs removed from source
- **CrashReporter.kt**: Removed `OBFUSCATED_WEBHOOK` Base64 constant and decoder. `DISCORD_WEBHOOK_URL` now reads from `System.getProperty("focusflow.webhook.crash", "")`. If not set (default for end-user builds), telemetry is silently disabled — both callers (`sendToDiscord`, `reportCritical`) already guard with `takeIf { it.isNotBlank() } ?: return`.
- **ResourceMonitorService.kt**: Same fix — removed `OBFUSCATED_WEBHOOK`, reads from `System.getProperty("focusflow.webhook.monitor", "")`.
- Developer injects URLs at build time via JVM args in build config. Never in source.

## Items analyzed but found already correct (false positives in original health check)
- **AppBlocker.showOverlay()**: `overlayJob` manipulation inside `scope.launch` (Dispatchers.Main) is safe — Main is a sequential single-thread dispatcher; no true race.
- **TaskAlarmService self-restart**: already has `while (isActive)` + all exceptions in `checkAlarms()` wrapped in try/catch. Job only exits on explicit cancel.
- **SessionPin blank bypass**: carefully handled; verify() returns true for null/blank only when isSet() returns false (which the caller checks). Comments explain the invariant.
- **WeeklyReportService malformed dates**: already caught with `try { LocalDate.parse(it) } catch { null }`.
- **KillSwitchService 30s persist gap**: already saves every 30s on lines 81-82.
- **WatchdogInstaller cmd injection**: uses `replace("'", "''")` for PowerShell single-quote escaping.
- **InstalledAppsScanner SecurityException**: caught by `catch (_: Exception) { emptyList() }`.
- **ContactScreen banner never clears**: already has close button `clickable { statusMessage = "" }`.
- **Database recordDailyCompletion formula**: mathematically correct — SQLite evaluates all SET expressions simultaneously against pre-update values.
- **Database migration ordering**: migrateV1..V6 all exist and are called in order inside a single transaction.
- **SideNav per-second recomposition**: uses `collectAsState()` on `FocusSessionService.state` StateFlow; recomposes only when state changes, not on every second.
- **EnforcementLog sync disk write**: only called from IO threads (inside catch blocks); bounded at 512 KB with rotation. Acceptable.
- **WatchdogInstaller missing timeout**: uses `.waitFor()` which blocks but WatchdogInstaller.install() is only called once at startup on the IO thread. Acceptable.

**Why:** Recording which items are genuine vs. pre-fixed prevents wasted re-analysis on future audit passes.

## Injection pattern for webhooks
```
# In build.gradle.kts applicationDefaultJvmArgs or installer wrapper:
-Dfocusflow.webhook.crash=https://discord.com/api/webhooks/...
-Dfocusflow.webhook.monitor=https://discord.com/api/webhooks/...
```
