---
name: Bug fixes audit round 5
description: Eight bugs fixed across StandaloneBlockService, SystemTrayManager, EnforcementLog, ReportsScreen, FocusLauncherScreen on 2026-06-05.
---

## Bugs fixed

### StandaloneBlockService — @Volatile on watchJob
`watchJob` was a plain `var` written by a scope.launch coroutine (IO thread) and read/cancelled by `stop()` from the UI thread. Without `@Volatile` the UI thread could see a stale null and fail to cancel the watcher.

**Rule:** Any `Job?` field written on one thread and read on another needs `@Volatile`.

### StandaloneBlockService — non-atomic addTime() / addApps()
Both methods did a classic read-modify-write: `val cur = _block.value; _block.value = cur.copy(...)`. Two rapid UI calls could both read the same snapshot, each compute their update, and the second write silently discard the first's change.

**Fix:** `_block.update { current -> current?.copy(...) }`. Capture the computed value inside the lambda for the subsequent DB write.

**Rule:** Any read-modify-write on a MutableStateFlow must use `.update {}`.

### SystemTrayManager — @Volatile on trayIcon and killSwitchItem
Both fields are initialised inside `EventQueue.invokeLater` (AWT EDT) but read from IO and Main threads. Without `@Volatile`, threads calling `showNotification` / `updateTooltip` / `updateKillSwitchItem` see a stale null and silently drop the call.

### SystemTrayManager — duplicate install guard
`install()` had no guard. A second call would add a second ghost tray icon; the first icon's reference would be overwritten making it impossible to remove. Guard added inside `invokeLater` with `if (trayIcon != null) return@invokeLater` (safe because the EDT is single-threaded, so check and set are atomic from its perspective).

### EnforcementLog — unbounded file growth
`appendText` with no size limit. A flapping enforcement condition can write hundreds of lines/minute and fill the disk. Added a 512 KB ceiling with rotation: read file, find first newline past the midpoint, write back only the newer half, then append the new line.

### ReportsScreen — 4 LazyColumn items() calls missing key=
Without a key, Compose uses positional identity. When the list updates (new session added, sorted differently), Compose reuses the wrong composable state, causing scroll-position loss and potentially wrong item rendering.

Fixed keys:
- `daySessions` → `key = { it.id }`
- `byDay` → `key = { (date, _) -> date.toString() }`
- `grouped` (temptation by app) → `key = { (displayName, _) -> displayName }`
- `temptLog.take(30)` → `key = { "${it.timestamp}|${it.processName}" }`

### FocusLauncherScreen — 2 LazyColumn items() calls missing key=
`availableApps` and `searchResults` both use `processName` as a stable unique key. Without it, checkbox state could attach to the wrong app row after list re-ordering.

## Items intentionally NOT fixed (false positives)
- App.kt AppBlocker callbacks: set in LaunchedEffect(Unit) and never cleared — intentional. App() is never recreated while the process is alive; system-tray hide/show does not restart the composition on Compose Desktop.
- while(true)+delay() in startWatcher: delay() is cancellation-aware; this is correct cooperative cancellation, not a leak.
- GlobalPin.isNullOrBlank fail-open: intentional design — no PIN configured means no gate.
