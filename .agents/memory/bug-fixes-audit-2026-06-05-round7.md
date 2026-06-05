---
name: Bug fixes audit round 7 — v1.0.9 enforcement core
description: Full audit of enforcement/session core (KillSwitchService, FocusLauncherService, NuclearMode, BreakEnforcer, ProcessMonitor, App.kt, Database.kt, TemptationLogger, FocusInsightsService); 5 @Volatile fixes applied.
---

## Rule (same as round 6)
`Job?` fields in singleton `object` services MUST be `@Volatile` whenever written on one thread
and read (or cancelled) on a different thread — especially the shutdown thread.

**How to apply:** Any new service that holds a scheduler/countdown `Job` MUST declare it
`@Volatile`. The shutdown thread, IO coroutines, and AWT tray callbacks all constitute
different threads for JVM memory-model purposes.

## Fixed in v1.0.9 (round 7)
- `NuclearMode.monitorJob` — written by `enable()` (UI/main/IO threads), read/cancelled by
  `disable()` called from shutdown thread, startBreak(), onKillSwitchActivated(). Without
  @Volatile, shutdown thread could see stale null → escape-process kill loop leaks after teardown.
- `KillSwitchService.countdownJob` — written by `activate()` (AWT tray thread), read/cancelled by
  `deactivate()` on IO thread and shutdown thread. Without @Volatile → leaked countdown timer.
- `FocusLauncherService.breakJob` — written by `startBreak()` (UI thread), read/cancelled by
  `endBreak()` (IO coroutine) and `exit()` (shutdown thread + sessionTimerJob coroutine).
- `FocusLauncherService.sessionTimerJob` — written by `startSessionTimer()` (enter on UI thread;
  endBreak() on IO coroutine), read/cancelled by `startBreak()` (UI) and `exit()` (shutdown thread).
- `BreakEnforcer.breakJob` — written by `startBreakCountdown()` called via withContext(Main),
  read/cancelled by `reset()` called from FocusSessionService.end() on IO coroutine.

## Confirmed clean (round 7 sweep)
- **App.kt** — all DB calls via withContext(IO); navigation state is Compose remember (UI thread only). Clean.
- **TemptationLogger** — CopyOnWriteArrayList; no cross-thread mutable state. Clean.
- **FocusInsightsService** — pure stateless compute(); called on IO thread by callers. Clean.
- **Database.kt (complete)** — all 60+ CRUD methods @Synchronized; insertSession uses INSERT OR
  REPLACE (idempotent); updateDailyFocusMinutes recalculates from scratch (double-insert safe);
  all multi-statement mutations wrapped in autoCommit=false/rollback/finally. Clean.
- **KillSwitchService** (logic) — compareAndSet double-guard on activate()/deactivate() prevents
  duplicate countdown launch and double teardown. Clean after @Volatile fix.
- **FocusLauncherService** (logic) — compareAndSet re-entrancy guards on enter(), exit(), startBreak(),
  endBreak() prevent all double-teardown and double-enable races. Clean after @Volatile fix.
- **NuclearMode** (logic) — CAS guard on enable()/disable(); cleanupThread join() before re-enable
  prevents firewall rule race. Clean after @Volatile fix. `while (isActive)` in coroutine uses
  coroutine-scoped isActive (correct — NOT NuclearMode.isActive).
- **BreakEnforcer** (logic) — AtomicBoolean breakCompletionFired CAS ensures onBreakComplete fires
  exactly once per cycle across skipBreak()/countdown races. Clean after @Volatile fix.
- **ProcessMonitor** — dispose() calls scope.cancel() which propagates to monitorJob and cacheJob
  via scope membership; explicit cacheJob?.cancel() in dispose() is a safe redundant no-op.
  @Volatile not needed for monitorJob/cacheJob because scope.cancel() is the definitive shutdown path.

## Intentional patterns — do NOT change
All patterns from round 6 still apply (see bug-fixes-audit-2026-06-05-round6.md).
