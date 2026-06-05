---
name: Bug fixes audit round 6 — v1.0.9 final
description: Full production code audit of all service files; 8 @Volatile fixes applied; complete clean/confirmed list.
---

## Rule
`job`/`schedulerJob`/`monitorJob`/`timerJob` fields in all scheduler services MUST be `@Volatile`.

**Why:** `doShutdown` in Main.kt (line 168) spins up a dedicated `"FocusFlow-Shutdown"` daemon
thread. All `start()` calls happen on the Compose application thread. The JVM memory model
requires `@Volatile` (or synchronisation) to guarantee the shutdown thread sees the live Job
reference written by the startup thread. Without it, `stop()` can call `null?.cancel()` (no-op)
and leave enforcement loops running after teardown.

**How to apply:** Any new service that holds a scheduler `Job` MUST declare it `@Volatile`.
Pattern already in place for all other cross-thread fields in these files (allowances, trackingDate,
latestReport, activeScheduleNames, monitoredDomains, lastClearedDate).

## Fixed in v1.0.9 (round 6)
- `DailyAllowanceTracker.job`
- `AutoBackupService.job`
- `RecurringTaskService.job`
- `TaskAlarmService.schedulerJob`
- `WeeklyReportService.schedulerJob`
- `BlockScheduleService.schedulerJob`
- `HostsBlocker.monitorJob`
- `FocusSessionService.timerJob`

## Confirmed clean (with reasoning)
- **ProcessMonitor** — all enforcement flags @Volatile; ConcurrentHashMap.compute() for atomic
  cooldown TOCTOU guard.
- **DailyAllowanceTracker** (logic) — wall-clock lastTickMs correction prevents drift; midnight
  flush-before-clear prevents data loss; usageSeconds/blockedToday synchronized correctly.
- **BlockScheduleService** (logic) — overnight windows: tail correctly attributed to prevDayValue.
- **AutoBackupService** (safety) — uses `VACUUM INTO` (atomic SQLite snapshot), not raw file copy.
- **SessionPin / GlobalPin** — fail-open on null is intentional + documented (lines 39–52 of
  SessionPin.kt); callers always check isSet() first; both getSetting() calls use the same path so
  both fail or both succeed together.
- **FocusSessionService** (start/end) — @Synchronized on start() and end() covers the race;
  withContext(Dispatchers.Main) makes timer's end() sequential with UI pause().
- **RecurringTaskService** (logic) — @Synchronized generateForToday() has no suspension points;
  dedup key prevents duplicates.
- **TaskAlarmService** (logic) — date-change check on every 30 s tick closes midnight-window race;
  persistLock prevents concurrent alarm writes from stomping each other.
- **WeeklyReportService** (scheduler) — wall-clock Duration.between() on every loop iteration;
  correct after hibernate.
- **NuclearMode / KillSwitchService** — awaitCleanup() called before exit; no dup-hook issue.
- **HostsBlocker** (logic) — writeLock serializes all hosts file read-modify-write; @Volatile
  monitoredDomains correct.

## Intentional patterns — do NOT change
- `SessionPin.verify()` / `GlobalPin.verify()` return `true` on null stored value — intentional
  fail-safe with explicit comment; callers gate on isSet() first.
- Empty `catch` blocks in enforcement cleanup (NetworkBlocker.removeAllRules, etc.) — async
  fire-and-forget; state is already cleared before the async call.
- Redundant `RegistryLockdown.disable()` calls in teardown sequences — defense-in-depth.
- Daemon threads for telemetry (CrashReporter, ResourceMonitorService) — intentional; never block
  JVM exit.
