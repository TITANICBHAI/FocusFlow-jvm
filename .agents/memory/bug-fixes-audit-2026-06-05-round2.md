---
name: Bug fixes audit round 2 (2026-06-05)
description: 7 confirmed bugs fixed across 7 files in the second diagnostic audit round.
---

## Fixed bugs

### BlockScheduleService.kt — Overnight schedule drops enforcement at midnight (CRITICAL)
A schedule like "Monday 22:00–02:00" (start > end) stopped blocking at midnight because
the outer `dayOfWeek !in schedule.daysOfWeek` check used the current day (now Tuesday).
**Fix:** Removed the outer day check. In the `isOvernight` branch, the head (start→23:59)
is owned by today and the tail (00:00→end) is owned by yesterday (`prevDayValue`). Both
branches check day membership independently.

### NuclearMode.kt — enable() raced against prior disable()'s cleanup thread (HIGH)
`disable()` spawns a background Thread to remove firewall rules. If `enable()` was called
before that thread finished, `applyFirewallLock()` added rules and then the cleanup thread
removed them — leaving nuclear mode with zero firewall coverage.
**Fix:** In `enable()`, after the CAS, `join(3_000)` the cleanup thread if present before
adding any rules. Note: interrupt() is not used because the thread may be blocked in
`Process.waitFor()` for netsh, which is interrupt-immune.

### GlobalKeyboardHook.kt — win32ThreadId race causes dangling hook thread (HIGH)
A rapid enable→disable sequence could call disable() before the pump thread had called
`GetCurrentThreadId()`. `PostThreadMessageW` was then called with tid=0 (skipped), the
pump thread stayed blocked in `GetMessage` indefinitely, surviving past `join(1500)` and
leaving an active WH_KEYBOARD_LL hook after the session ended.
**Fix:** 200ms spin-wait in `disable()` polling for `win32ThreadId != 0`.

### ProcessMonitor.kt — sessionActive / alwaysOnEnabled missing @Volatile (MEDIUM)
Both are written by the UI/service threads and read by the monitor coroutine on
Dispatchers.IO. Without @Volatile the JVM may cache them in a register and the monitor
thread never sees updates, leaving enforcement running after a session ends.
**Fix:** Added `@Volatile` to both declarations.

### FocusLauncherService.kt — exit() vs onKillSwitchDeactivated() race (MEDIUM)
`onKillSwitchDeactivated` checked `_isActive.value` once at the top. If `exit()` ran
concurrently (CAS → isActive=false) between the check and the body, it would proceed to
call `NuclearMode.enable()`, `RegistryLockdown.enable()`, and `hideTaskbar()` during
session teardown — re-engaging enforcement that was just torn down.
**Fix:** Added a second `if (!_isActive.value)` check (with `launcherAllowedProcesses`
rollback) just before the dangerous calls, narrowing the race window to near-zero.

### AutoBackupService.kt — restoreBackup safety snapshot used Files.copy on WAL DB (MEDIUM)
The live DB runs in WAL mode. `Files.copy` captures only the `.db` file, omitting the
`-wal` and `-shm` files. The resulting safety snapshot was missing uncommitted
transactions and would produce a corrupted rollback target if restore failed.
**Fix:** Replaced `Files.copy` with `Database.vacuumInto()` which produces a single,
WAL-free, fully-consistent snapshot even while the DB is actively written to.

### RegistryLockdown.kt — shutdownHookRegistered plain @Volatile, not atomic CAS (LOW)
Two concurrent `enable()` calls could both read `shutdownHookRegistered == false` and
both register duplicate JVM shutdown hooks, causing `disable()` to run twice on exit.
**Fix:** Changed to `AtomicBoolean` with `compareAndSet(false, true)` in
`registerShutdownHook()`.

## Remaining known issues (not yet fixed)
- NetworkBlocker.kt: resolveExePath computed without computeIfAbsent → multiple threads
  can redundantly run expensive PowerShell resolution. Low impact (result is identical).
- TaskAlarmService.kt: firedToday string grows unbounded across one day. Minor.
- KillSwitchService.kt: midnight loadFromDb race on LocalDate.now(). Very narrow.
