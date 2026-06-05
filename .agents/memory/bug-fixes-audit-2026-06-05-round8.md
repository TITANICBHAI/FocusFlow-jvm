---
name: Bug fixes audit round 8 — comprehensive diagnostic
description: Functional bugs found and fixed across FocusScreen.kt and TasksScreen.kt in the full-codebase diagnostic audit.
---

## Files audited this round (complete)
FocusScreen.kt (all 1104 lines), ProcessMonitor.kt (all 718 lines), TasksScreen.kt (lines 1–470),
StatsScreen.kt (header), SettingsScreen.kt (lines 1–280), DashboardScreen.kt (lines 1–338),
SessionPin.kt, GlobalPin.kt — all confirmed clean except the three bugs below.

## Bug 1 — FocusScreen: session not linked to task (FIXED)
**Where:** `FocusSessionService.start()` called at three sites in FocusScreen.kt:
- Button onClick (non-PIN path, line ~311)
- Button onClick (PIN path, stored in `pendingStartMins/Apps` then consumed in PinRevealDialog confirm)
- Ctrl+Enter shortcut (line ~162)

**Root cause:** `tid` parameter (nullable String, defaults to null) was never passed at any call site.
Sessions were saved with `taskId = null` in the DB even when started from a preloaded task card or
a quick-pick chip — breaking task–session correlation in stats.

**Fix:**
- Added `var selectedTaskId by remember { mutableStateOf(preloadTask?.id) }` state var
- Added `var pendingStartTaskId by remember { mutableStateOf<String?>(null) }` for PIN flow
- `LaunchedEffect(preloadTask)` now also sets `selectedTaskId = it.id`
- customTaskName `onValueChange` clears `selectedTaskId = null` (user typed a custom name — no task link)
- Quick-pick chip `onClick` sets `selectedTaskId = task.id`
- All three start sites now pass `tid = selectedTaskId` (or `tid = pendingStartTaskId` in PIN confirm)

## Bug 2 — TasksScreen: misleading "deleted" snackbar (FIXED)
**Where:** `deleteWithUndo()` inner function in TasksScreen.kt

**Root cause:** Snackbar was shown BEFORE the deletion, while the task remained visible on screen.
The conditional was also inverted in spirit — it deleted when `result != ActionPerformed` but never
removed the item optimistically, so the UI stayed inconsistent.

**Fix:** `tasks = tasks.filter { it.id != task.id }` runs immediately (optimistic removal), then:
- If `ActionPerformed` (Undo clicked): `reload()` restores from DB
- Else (snackbar expired): `Database.deleteTask(id)` + `reload()`

## Bug 3 — FocusScreen: Keyword Blocker count hardcoded 0 (FIXED)
**Where:** `StandaloneBlockPanel` composable, `EnforcementRow` for "Keyword Blocker"

**Root cause:** Count argument was the literal `0`, always showing "None configured".

**Fix:**
- Added `var keywordCount by remember { mutableStateOf(0) }` state var
- `reload()` now loads `Database.getBlockedKeywords().size` into `keywordCount`
- `StandaloneBlockPanel` gained a `keywordCount: Int` parameter
- `EnforcementRow` now passes `keywordCount` with proper pluralisation

## Files confirmed clean this round (no action needed)
- ProcessMonitor.kt: `tryAcquireCooldown`, `checkProcess`, `launcherSweep`, `enforceBlock` — all correct
- SessionPin.kt / GlobalPin.kt: SHA-256 hash logic, verify() null/blank passthrough — correct
- SettingsScreen.kt: alwaysOn toggle SessionPin guard, Pomodoro save, chime styles — correct
- DashboardScreen.kt: focusScore formula, streak ring, What's New auto-dismiss — correct
- FocusScreen.kt (active session): pause/resume/end buttons, distraction counter, session notes — correct
- PomodoroCycleIndicator: dot position and cycle label math — correct

## Intentional patterns (do NOT change)
- `distractionCount` IS incremented via the `+` button (line ~619) — not a bug
- `preloadTask?.focusBlockedApps` used for `extraApps` even when user picks a quick-pick task;
  quick-pick only sets name/duration/selectedTaskId, not blockedApps — by design
- `StandaloneBlockPanel` still reads `alwaysOn` from FocusScreen state, not ProcessMonitor directly — by design
