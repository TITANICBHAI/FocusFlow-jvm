---
name: Bug fixes audit 2026-06-04
description: 7 confirmed bugs fixed across 5 files in the June 2026 diagnostic audit round.
---

## Fixed bugs

### BreakEnforcer.kt — double-fire of onBreakComplete
`startBreakCountdown()` was calling `breakCompletionFired.set(false)` BEFORE `breakJob?.cancel()`.
The old job, if it had already passed its `delay()` and was about to call the CAS, could win
the CAS on the freshly-reset flag and fire `onBreakComplete` a second time.
**Fix:** cancel old job first, then reset the flag.

### HostsBlocker.kt — non-atomic read-modify-write
`blockDomain`, `unblockDomain`, and `unblockAll` each read the hosts file, mutate a StringBuilder,
then write it back. Concurrent calls would each read the "same" file, build independent versions,
and the last writer would silently overwrite the others — leaving domains unblocked.
**Fix:** added `private val writeLock = Any()` and wrapped each read-modify-write in `synchronized(writeLock)`.
The nslookup verify is done outside the lock (it's slow, pure read).

### Database.kt — connection leak in tryOpenAndMigrate
`val conn = ds.connection` was inside the try block; the catch block had no reference to it.
If a PRAGMA call or `migrate()` threw, the JDBC connection was silently leaked.
**Fix:** hoisted to `var localConn: java.sql.Connection? = null`, close in catch via `try { localConn?.close() } catch`.

### Database.kt — completeTask not atomic
`UPDATE tasks … completed=1` and `recordDailyCompletion()` were two separate statements with no
transaction. A crash between them leaves the task marked complete but the streak counter stale.
**Fix:** wrapped in explicit `autoCommit=false / commit / rollback` transaction.

### Database.kt — insertSession not atomic
Session `INSERT OR REPLACE` and `updateDailyFocusMinutes()` were two separate DB operations.
A crash between them leaves the session saved but daily focus totals wrong.
**Fix:** wrapped in explicit transaction.

### Database.kt — deleteHabit not atomic
`DELETE FROM habits` and `DELETE FROM habit_entries` were two separate statements.
A crash between them leaves orphaned habit_entries rows that can never be cleaned.
**Fix:** wrapped in explicit transaction.

### WeeklyReportService.kt — onReportReady lambda memory leak
`onReportReady` is a `var` on a singleton. If a Compose-scoped component sets it and then
navigates away, the singleton holds a strong reference to the lambda's closure indefinitely.
**Fix:** added `onReportReady = null` to `stopScheduler()`.

### FocusSessionService.dispose() — dirty enforcement state on app close
`dispose()` called `scope.cancel()` directly. If a session was active, ProcessMonitor.sessionActive
was never cleared and NetworkBlocker rules were never removed, dirtying state on next launch.
**Fix:** call `end(completed = false)` before `scope.cancel()` when `_state.value.isActive`.

## What was NOT fixed (known, lower priority)
- NuclearMode enable/disable race: if enable() is called while the cleanup thread from a prior
  disable() is still removing firewall rules, the new rules could be removed by the old thread.
  Requires cancelling/awaiting the cleanup thread in enable(). Not yet applied (complex, low frequency).
- FocusLauncherService KillSwitch race with exit(): onKillSwitchDeactivated checks _isActive before
  calling RegistryLockdown.enable(); concurrent exit() could set isActive=false mid-check.
  Very narrow race, not yet applied.
