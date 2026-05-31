---
name: UI thread DB freeze patterns
description: Rules for safe Database access in Compose UI — the mistakes we found and fixed, and the pattern to follow going forward.
---

## The Rule
Every `Database.*` call in a `@Composable` or its callbacks MUST be on a background thread:
- Reads in `LaunchedEffect` → wrap with `withContext(Dispatchers.IO)`
- Writes in button callbacks → use `scope.launch(Dispatchers.IO) { ... }` or `scope.launch { withContext(Dispatchers.IO) { ... } }`
- Never call `Database.*` directly in a composable body, `remember {}`, or `onCheckedChange` / `onClick` without a coroutine.

## Fixed Issues (confirmed, do not re-introduce)
1. `BlockOverlay.kt` — bare `Database.getSetting()` in composable body → moved to `LaunchedEffect` + `withContext(IO)`
2. `FocusScreen.kt` — `Database.getSetting("pomodoro_mode")` in `remember { mutableStateOf(...) }` → moved to `LaunchedEffect(Unit)`
3. `FocusScreen.kt` — three `Database.setSetting()` calls in `onCheckedChange`/`onClick` → wrapped in `scope.launch(Dispatchers.IO)`
4. `FocusLauncherScreen.kt` — `Database.getSetting("launcher_selected_apps")` in `LaunchedEffect` but outside the `withContext(IO)` block → moved inside
5. `OsBanner.relaunchAsAdmin()` — `Thread.sleep(600)` on calling thread → moved to daemon thread
6. `FocusSessionService.start()/end()` — `Database.insertSession()` blocking session thread → moved to `scope.launch(Dispatchers.IO)`
7. `Database.migrate()` — no transaction → wrapped in autoCommit=false / commit / rollback

## Screens audited and confirmed safe
ActiveScreen, BlockDefenseScreen, HabitsScreen, KeywordBlockerScreen, ProfileScreen,
SettingsScreen, DailyNotesScreen, StatsScreen, VpnNetworkScreen, AppBlockerScreen,
OnboardingScreen — all use `withContext(Dispatchers.IO)` or `scope.launch(Dispatchers.IO)`.

**Why:** SQLite on desktop is synchronous/blocking. Any DB call on the Compose UI thread (Dispatchers.Main / swing EDT) directly freezes the frame clock, causing visible hangs.
**How to apply:** Before adding any new `Database.*` call in UI code, always check if you are on the composition thread. If you are, use the coroutine patterns above.
