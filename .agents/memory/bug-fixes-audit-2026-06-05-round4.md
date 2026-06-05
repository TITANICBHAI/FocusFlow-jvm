---
name: Bug fixes audit round 4 (2026-06-05)
description: Two surgical fixes in DailyAllowanceTracker and FloatingBlockOverlay, plus architecture-clean verdicts on 8 unaudited services.
---

## Bugs fixed (commit 3f267fb)

### 1. DailyAllowanceTracker — usage drift from hard-coded "+10L" accumulation
- **File**: `services/DailyAllowanceTracker.kt`
- **Bug**: Every tick added exactly 10 seconds to the foreground-process usage counter regardless of how long tick() actually took. On Windows, `ProcessHandle.allProcesses()` takes 100–500 ms per call. Over a 60-minute session this causes the allowance to drain ~54 seconds short — a user with a 30-minute limit effectively gets ~31 minutes before being blocked.
- **Fix**: Introduced `lastTickMs` (wall-clock millisecond timestamp). Updated at the END of every `tick()` call (so the delta includes tick execution time). In the foreground-process accumulation block, replaced `+10L` with `((nowMs - lastTickMs) / 1000L).coerceAtLeast(1L)`.
- **Why `coerceAtLeast(1L)`**: On the very first tick after `start()`, `lastTickMs` was set at start time so delta is ~0 ms. We floor at 1 s so the first tick always credits at least one second of actual use.
- **Initialised**: `lastTickMs = System.currentTimeMillis()` immediately before launching the job coroutine in `start()`.

### 2. FloatingBlockOverlay — `dismissJob` not @Volatile
- **File**: `enforcement/FloatingBlockOverlay.kt`
- **Bug**: `dismissJob` was a plain `var Job?`. `show()` is called from the enforcement thread (ProcessMonitor/WinEventHook hot path); the auto-dismiss coroutine fires `hide()` from Dispatchers.IO. Without @Volatile, the enforcement-thread write of a new Job reference may not be flushed to the IO thread's cache. Result: the IO thread cancels a stale dismiss job, and the new dismiss timer runs past its intended window (overlay stays visible indefinitely until the next block event).
- **Fix**: Added `@Volatile` to `dismissJob`. The cancel+reassign race that @Volatile doesn't fully cover is benign (double-cancel is idempotent; a lost reference just runs an extra hide() after dismissSeconds — harmless).

## Architecture clean verdicts (no bugs found)

- **FocusLauncherService.kt**: State machine transitions are CAS-guarded throughout (enter/exit/startBreak/endBreak all use compareAndSet). Kill-switch integration double-checks `_isActive` after re-enabling enforcement. Registry cleanup is redundant across startup janitor + shutdown hook + loadFromDb. Clean.
- **AppBlocker.kt / FloatingBlockOverlay.kt**: `overlayJob` in AppBlocker is only accessed inside `scope.launch(Dispatchers.Main)` — Main is single-threaded, no race. FloatingBlockOverlay window is always accessed via `SwingUtilities.invokeLater` — EDT-safe.
- **RegistryLockdown.kt**: Shutdown hook is CAS-guarded against double-registration. `disable()` is idempotent. Startup janitor in Main.kt provides belt-and-suspenders recovery for SIGKILL/power-loss dirty state. Clean.
- **InstalledAppsScanner.kt**: Registry scans guarded with try-catch; synchronized install lock prevents redundant scans. Clean.
- **BlockScheduleService.kt**: Loop executes `tick()` BEFORE `delay(60_000)` — no blind window at startup (explorer incorrectly claimed otherwise; verified by direct code read). Overnight schedule tail handled correctly via prevDayValue. Clean.
- **TaskAlarmService.kt**: Uses `LocalDate` for midnight detection (not narrow time window) — reliable. Firing window is `diffSeconds in -60..0` which is safe with a 30 s poll. Clean.
- **NotificationService.kt**: Thin wrapper; correct. OS-level Do-Not-Disturb suppression is a Windows platform constraint, not a code bug.
- **TemptationLogger.kt**: Uses `CopyOnWriteArrayList` for in-memory log — thread-safe for concurrent writes from enforcement + reads from UI. Clean.

## Items deliberately NOT fixed

- **DailyAllowanceTracker 10-second escape window**: A foreground process used in < 10 s bursts avoids the quota. This is fundamental to polling-based enforcement — would require continuous OS-level hooks to fix. Accepted limitation documented in code.
- **DailyAllowanceTracker nested synchronized locks**: Locks `blockedToday` and `usageSeconds` are only ever held by one thread at a time (single IO-thread coroutine for tick, `start()` completes before job launches). No real deadlock risk.
- **BreakEnforcer 1 s delay drift**: BreakEnforcer uses delay(1000) for its countdown. Small drift (~seconds over a 15-minute break) is acceptable for the UX use case.
