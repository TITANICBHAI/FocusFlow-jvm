# FocusFlow — Focus Launcher: How Kiosk Mode Works

> **Two audiences, one document.**  
> The first half explains kiosk mode in plain English — what it does and why.  
> The second half is the full technical reference for how every piece is implemented.

---

## Part 1 — Plain English

### What is Focus Launcher?

Focus Launcher is FocusFlow's kiosk mode for Windows. When you enter it, your entire desktop environment is locked down to only the applications you chose. Everything else — your taskbar, system shortcuts, Task Manager, and all other apps — becomes inaccessible until the session ends.

The design principle is simple: **environmental change is more effective than willpower**. By physically removing access to distracting apps, Focus Launcher eliminates the moment-of-weakness decision entirely. You can't open Reddit if the process is killed the instant it starts.

### What Happens When You Enter

1. Your Windows taskbar disappears — Start button, system tray, clock, everything
2. Win+D, Win+Tab, Task Manager, command prompts, and all system escape routes are disabled
3. A full-screen overlay appears with only your chosen app tiles
4. Any app not on your list is killed the moment it gains focus
5. You get one 5-minute break per calendar day (requires PIN if one is set)
6. Exiting requires a click — or your PIN if you've enabled Hard Lock

### What You Cannot Do

- Switch to blocked apps (they're killed instantly)
- Open Task Manager or Process Explorer to force-quit FocusFlow
- Open a terminal and kill FocusFlow manually
- Use Win+D to peek at the desktop
- Install tools that could bypass the block (winget, msiexec are blocked)

### What Is Always Safe

FocusFlow never touches the processes that run your keyboard, mouse, audio, security, and display system. Killing those would lock you out of your computer entirely. A carefully-maintained safe list (see Part 2) ensures peripheral drivers, input method editors, Windows Defender, and accessibility tools are never affected.

### The Safety Nets

- **5-minute break:** Once per day, you can pause everything and use your computer normally
- **Kill Switch:** A separate 5-minute emergency budget that pauses all FocusFlow enforcement
- **Hard Lock off by default:** You can always exit without a PIN unless you explicitly turn Hard Lock on
- **Triple crash recovery:** If FocusFlow crashes or is killed, your taskbar is restored automatically on the next launch — three independent recovery paths ensure this

---

## Part 2 — Technical Reference

### Architecture Overview

Focus Launcher is composed of five cooperating services and one full-screen UI overlay:

```
┌─────────────────────────────────────────────────────────────────┐
│                    FocusLauncherOverlay (UI)                     │
│  Full-screen Compose overlay, zIndex 200                        │
│  App grid · Timer chip · Hard lock toggle · Break / Exit        │
└────────────────────────┬────────────────────────────────────────┘
                         │ reads / calls
┌────────────────────────▼────────────────────────────────────────┐
│                  FocusLauncherService (object)                   │
│  StateFlows: isActive, isHardLocked, sessionApps,               │
│              breakActive, breakRemainingSeconds,                  │
│              sessionEndMs, sessionStartMs, canTakeBreak          │
│  Coordinates: enter() · exit() · startBreak() · endBreak()      │
│  Crash recovery: JVM shutdown hook + loadFromDb()               │
└───┬────────────────┬───────────────┬────────────────────────────┘
    │                │               │
    ▼                ▼               ▼
ProcessMonitor   NuclearMode    KillSwitchService
(inverse         (escape-route  (5-min daily
 allowlist)       blocking)      emergency budget)
```

---

### Session Lifecycle

```
User clicks "Enter Focus Launcher"
         │
         ▼
FocusLauncherService.enter(apps, durationMinutes)
  1. _isActive          = true
  2. _sessionApps       = apps
  3. _sessionStartMs    = System.currentTimeMillis()
  4. _sessionEndMs      = now + duration * 60_000  (or 0 if no limit)
  5. breakSecondsAccumulated = 0
  6. Database.setSetting("launcher_crash_guard", "true")
  7. ProcessMonitor.launcherAllowedProcesses = apps.processNames.toSet()
  8. NuclearMode.enable()
  9. hideTaskbar()             ← Win32 ShowWindow(Shell_TrayWnd, SW_HIDE)
 10. startSessionTimer()       ← coroutine on Dispatchers.IO
         │
         ▼
  [SESSION ACTIVE — enforcement loops running at 500ms / 2s]
         │
    ┌────┴────────────────────────────┐
    │                                 │
    ▼                                 ▼
 Normal exit                      Break (once/day)
 (or timer fires)                      │
    │                         FocusLauncherService.startBreak()
    │                           - _breakActive = true
    │                           - sessionTimerJob?.cancel()
    │                           - NuclearMode.disable()
    │                           - showTaskbar()
    │                           - breakCountdown coroutine starts
    │                                 │
    │                         FocusLauncherService.endBreak()
    │                           - breakSecondsAccumulated += used
    │                           - _sessionEndMs += used * 1_000L
    │                           - NuclearMode.enable()
    │                           - hideTaskbar()
    │                           - Resume sessionTimerJob
    │
    ▼
FocusLauncherService.exit()
  - All StateFlows reset to idle
  - Database.setSetting("launcher_crash_guard", "false")
  - ProcessMonitor.launcherAllowedProcesses = emptySet()
  - NuclearMode.disable() (if active)
  - showTaskbar()           ← Win32 ShowWindow(Shell_TrayWnd, SW_SHOW)
```

---

### Enforcement Layer 1 — ProcessMonitor (Inverse Allowlist)

`ProcessMonitor` is the core enforcement engine. It normally operates as a **blocklist** (kill anything in the blocked set). In launcher mode, it switches to an **inverse allowlist** (kill anything NOT in the allowed set).

**Injection point:**
```kotlin
// FocusLauncherService.enter():
ProcessMonitor.launcherAllowedProcesses = apps.map { it.processName.lowercase() }.toSet()

// FocusLauncherService.exit():
ProcessMonitor.launcherAllowedProcesses = emptySet()
```

**Detection — dual path:**

| Path | Mechanism | Latency | Use |
|------|-----------|---------|-----|
| WinEventHook | `EVENT_SYSTEM_FOREGROUND` Win32 event | ~0 ms | Primary |
| Poll | `getForegroundProcessNameAndPid()` | 2 s (hook active) / 750 ms (fallback) | Safety net |

The hook fires the instant a window gains focus — before the user sees the app's UI. The poll catches apps that were already in the foreground when the session started, and covers hook-registration failures.

**Per-event enforcement logic (`checkProcess`):**

```
foreground process detected
         │
         ├── in launcherSafeProcesses?  →  skip (system-critical)
         │
         ├── in launcherAllowedProcesses?  →  skip (user chose this app)
         │
         └── else: tryAcquireCooldown(processName, now)
                    │
                    ├── cooldown active (< 800ms since last kill)?  →  skip
                    │
                    └── kill:
                         - pid > 0: killProcessByPid(pid)   ← exact window
                         - pid = 0: killProcessByName(name)  ← all instances
```

The 800ms cooldown uses `ConcurrentHashMap.compute()` — an atomic read-compare-write operation that prevents the hook and poll from both killing the same process within the cooldown window.

**UWP frame host resolution:**  
UWP apps (Microsoft Store apps) run inside `ApplicationFrameHost.exe`. When this process is detected as foreground, `ProcessMonitor` resolves the actual hosted child process by scanning the window tree, then checks that child against the allowlist.

**`launcherSafeProcesses` — what is protected and why:**

| Category | Examples | Why protected |
|----------|----------|---------------|
| Kernel / session | `csrss.exe`, `winlogon.exe`, `lsass.exe` | Kill = BSOD or immediate logoff |
| Desktop compositor | `dwm.exe` | Kill = black screen |
| Input framework | `ctfmon.exe`, `tabtip.exe` | Kill = total keyboard/mouse lockout |
| Synaptics/Elan touchpad | `syntpenh.exe`, `etdctrl.exe` | Kill = touchpad stops responding |
| Logitech (SetPoint / G HUB / Options) | `lghub.exe`, `logioptions.exe` | Kill = mouse/keyboard stops responding |
| Razer Synapse | `rzsynapse.exe`, `razer.exe` | Kill = peripheral input loss |
| SteelSeries GG | `steelseriesgg.exe`, `ggdrive.exe` | Kill = peripheral input loss |
| Corsair iCUE | `icue.exe`, `corsairservice.exe` | Kill = keyboard macro keys / lighting stop |
| ASUS Armoury Crate | `armourycrate.exe` | Kill = keyboard shortcut keys stop |
| UWP / Shell infrastructure | `RuntimeBroker.exe`, `ApplicationFrameHost.exe` | Kill = all UWP apps close instantly |
| Audio | `audiodg.exe`, `audioendpointbuilder.exe` | Kill = all system sound stops |
| Security | `msmpeng.exe`, `smartscreen.exe` | Kill = Defender protection drops |
| Accessibility | `narrator.exe`, `atbroker.exe` | Kill = all AT tools break |

---

### Enforcement Layer 2 — NuclearMode (Escape Route Blocking)

NuclearMode runs a three-layer enforcement stack targeting processes that could be used to escape the kiosk or kill FocusFlow itself.

**Target processes:**
```
Task managers:    taskmgr.exe, procexp.exe, procexp64.exe, processhacker.exe
Terminals:        cmd.exe, powershell.exe, pwsh.exe, wt.exe, bash.exe, wsl.exe
Registry tools:   regedit.exe, msconfig.exe, regedt32.exe
Admin tools:      mmc.exe, eventvwr.exe, wmic.exe, wscript.exe, cscript.exe
Installers:       winget.exe, msiexec.exe
```

**Layer 1 — Detect (single scan):**
```kotlin
ProcessBuilder("tasklist", "/FO", "CSV", "/NH").start()
// Parse CSV in-memory, filter against escapeProcesses set
// O(n) scan — one process spawn per tick
```
The original approach spawned 40+ individual `taskkill` processes per tick (~8,000/min).  
The current approach: 1 `tasklist` call + 1 `taskkill` call if needed.

**Layer 2 — Kill (batch):**
```kotlin
val args = mutableListOf("taskkill", "/F")
found.forEach { exe -> args += "/IM"; args += exe }
ProcessBuilder(args).start()   // fire-and-forget
```
All detected escape processes are terminated in a single `taskkill` invocation.

**Layer 3 — Firewall (pre-emptive block):**
```
netsh advfirewall firewall add rule
  name="FocusFlow-Block-{exe}"
  dir=out action=block program="{path}"
```
Outbound-deny rules are added for every escape process when NuclearMode enables. Even if the user renames `taskmgr.exe`, the firewall blocks the original binary. Rules are removed cleanly on `disable()`.  
Requires admin privileges — degrades gracefully to kill-only if not elevated.

**Enforcement loop:**
```kotlin
while (isActive) {
    if (!KillSwitchService.isActive.value) {
        val found = getRunningEscapeProcesses()
        killAndLog(found)
    }
    delay(500)
}
```
Tick rate: 500ms. Skips when KillSwitch is active.

**Escape attempt telemetry:**  
Each detected process is counted in `escapeCounts: ConcurrentHashMap<String, Int>`. Every 5th hit, the count is persisted to DB (`escape_attempt_{name}`). The Stats screen surfaces "attempted to escape N times" data. Session total is written to `nuclear_last_session_attempts` on `disable()`.

---

### Enforcement Layer 3 — Taskbar Control (Win32)

```kotlin
// Hide taskbar (enter)
val u32 = User32Extra.INSTANCE
val taskbar   = u32.FindWindowW("Shell_TrayWnd", null)
val secondary = u32.FindWindowW("Shell_SecondaryTrayWnd", null)
u32.ShowWindow(taskbar,   SW_HIDE)   // SW_HIDE = 0
u32.ShowWindow(secondary, SW_HIDE)

// Show taskbar (exit / crash recovery)
u32.ShowWindow(taskbar,   SW_SHOW)   // SW_SHOW = 5
u32.ShowWindow(secondary, SW_SHOW)
```

`Shell_TrayWnd` = primary taskbar.  
`Shell_SecondaryTrayWnd` = secondary taskbar on multi-monitor setups.

`SW_SHOW` on an already-visible taskbar is a no-op at the Win32 level — so `showTaskbar()` is called unconditionally at startup (crash recovery path) without risk of visual glitches.

---

### Break System

#### Focus Launcher Break (once per calendar day)

```
User clicks "Take a Break" → PinGateDialog → FocusLauncherService.startBreak()
  _breakActive = true
  _breakRemainingSeconds = 300
  Database.setSetting("launcher_break_used_date", LocalDate.now())
  sessionTimerJob?.cancel()         ← pause session countdown
  ProcessMonitor.launcherAllowedProcesses = emptySet()
  NuclearMode.disable()
  showTaskbar()
  _canTakeBreak = false             ← immediate UI update
  
  breakJob = coroutine(500 → 0, 1s ticks)

[Break active — normal desktop access]

breakJob completes (or user clicks "End Break Early")
  → FocusLauncherService.endBreak()
      breakUsed = BREAK_SECONDS - remainingSeconds
      breakSecondsAccumulated += breakUsed
      _sessionEndMs += breakUsed * 1_000L    ← extend by ACTUAL time used
      NuclearMode.enable()
      hideTaskbar()
      Resume sessionTimerJob
```

**Why `sessionEndMs` is extended in `endBreak()` not `startBreak()`:**  
If the break is ended after 2 minutes, extending by the full 5 minutes at start would grant 3 unearned minutes. By extending at end by the actual seconds elapsed, the extension exactly matches the break duration.

**`elapsedSeconds()` calculation:**
```kotlin
val raw = (System.currentTimeMillis() - sessionStartMs) / 1000L
return maxOf(0L, raw - breakSecondsAccumulated)
```
Break time is subtracted from wall-clock elapsed so the session timer shows only actual focus time.

#### KillSwitch Emergency Break (global, 300s daily)

```kotlin
KillSwitchService.activate()
  ProcessMonitor.killSwitchActive = true     ← all enforcement pauses
  FocusLauncherService.onKillSwitchActivated()
    → ProcessMonitor.launcherAllowedProcesses = emptySet()
    → NuclearMode.disable()
    → showTaskbar()
  
  countdown coroutine: 300 → 0, saves to DB every 30s or when ≤ 10s

KillSwitchService.deactivate() (or budget exhausted)
  FocusLauncherService.onKillSwitchDeactivated()
    → ProcessMonitor.launcherAllowedProcesses = sessionApps (restored)
    → NuclearMode.enable()
    → hideTaskbar()
  ProcessMonitor.killSwitchActive = false
```

Budget persisted to: `killswitch_remaining_today` + `killswitch_reset_date`.  
Resets when the calendar date changes (checked on first access after midnight).

---

### Hard Lock

When enabled, the Exit button and "Disable Hard Lock" action both require PIN entry via `PinGateDialog`. Hard lock state is persisted to `launcher_hard_locked` in DB and cleared on both normal exit and startup crash recovery.

```
isHardLocked = true
  → Exit clicked → PinGateDialog ("Exit Focus Launcher")
      → onSuccess: scope.launch(Dispatchers.IO) { FocusLauncherService.exit() }
  
  → "Unlock Session" clicked → PinGateDialog ("Disable Hard Lock")
      → onSuccess: scope.launch(Dispatchers.IO) { FocusLauncherService.toggleHardLock() }
```

`toggleHardLock()` dispatches to `Dispatchers.IO` because it calls `Database.setSetting()`.

---

### Triple Crash Recovery System

Three independent recovery paths ensure the taskbar is always restored:

#### Path 1 — JVM Shutdown Hook
```kotlin
// Registered in FocusLauncherService.init {}
Runtime.getRuntime().addShutdownHook(Thread {
    try { showTaskbar() } catch (_: Throwable) {}
})
```
Runs on any JVM exit (normal, OOM, SIGTERM, OutOfMemoryError).  
**Cannot run on SIGKILL** — no process runs on SIGKILL. Covered by Path 2.

#### Path 2 — Startup Restore (`loadFromDb()`)
```kotlin
// Called unconditionally at application startup
showTaskbar()                                      // always safe (no-op if visible)
ProcessMonitor.launcherAllowedProcesses = emptySet()
```
If FocusFlow was SIGKILLed mid-session and the taskbar is still hidden, the next launch restores it before any other initialization.  
Note: **This is unconditional** — not gated on `launcher_crash_guard`. If the DB was corrupted and recreated, the guard key would be missing even though the taskbar is still hidden from the previous session. Checking the guard would silently skip the restore.

#### Path 3 — Global Crash Handler (`emergencyRestoreWindows()`)
```kotlin
fun emergencyRestoreWindows() {
    ProcessMonitor.launcherAllowedProcesses = emptySet()
    showTaskbar()
}
```
Pure Win32 calls — no DB access, no coroutines, no state changes that could throw. Safe to call from any thread at any time, including during an uncaught exception handler. Registered in the app's global `Thread.setDefaultUncaughtExceptionHandler`.

---

### Data Persistence Reference

| DB Key | Type | Purpose |
|--------|------|---------|
| `launcher_crash_guard` | `"true"` / `"false"` | Set while session active; startup restore reads this |
| `launcher_hard_locked` | `"true"` / `"false"` | Persists hard lock state |
| `launcher_break_used_date` | `"YYYY-MM-DD"` | Prevents second break on same day |
| `launcher_selected_apps` | `"app1.exe,app2.exe,…"` | Last-used app selection; pre-fills setup screen |
| `killswitch_remaining_today` | `"0"–"300"` | Remaining KillSwitch seconds |
| `killswitch_reset_date` | `"YYYY-MM-DD"` | When KillSwitch budget was last reset |
| `nuclear_mode` | `"true"` / `"false"` | NuclearMode active state (cleared on startup) |
| `escape_attempt_{name}` | integer string | Per-process escape attempt count |
| `nuclear_last_session_attempts` | integer string | Total escape attempts last session |

---

### Key Design Decisions

**Inverse allowlist vs. blocklist**  
A blocklist requires knowing every app a user might try to open — impossible to maintain completely. An inverse allowlist (kill everything not explicitly allowed) is both simpler and more complete. The challenge is the `launcherSafeProcesses` list, which must be precisely curated to avoid breaking input, display, and security subsystems.

**WinEventHook + polling instead of polling only**  
The WinEventHook fires on `EVENT_SYSTEM_FOREGROUND` — the instant any window gains focus, before it renders a single frame. A blocked app is killed before the user sees it. Polling alone has a 750ms–2s detection gap during which the app briefly appears. The hook provides instant-kill UX; the poll covers hook failures and apps already foreground at session start.

**NuclearMode as a separate service from ProcessMonitor**  
NuclearMode's escape-route list is intentionally broad (it blocks `cmd.exe` even during normal use, not just launcher mode). Separating it lets ProcessMonitor handle the app-specific allowlist while NuclearMode independently watches for jailbreak tools — two independent loops with different cadences and target sets.

**Peripheral driver safe list**  
During development testing, killing Logitech G HUB, Razer Synapse, or Corsair iCUE processes caused the corresponding mouse/keyboard to stop sending input events — effectively locking the user inside an inaccessible kiosk. The safe list is cross-referenced against Microsoft Learn (WDK docs), Windows Internals, and live process-tree analysis on 15+ hardware configurations.

**Batch `tasklist` scan instead of individual process checks**  
The original NuclearMode implementation spawned 40+ individual `taskkill` processes per 500ms tick — approximately 8,000 process spawns per minute, detectable as CPU spikes in Task Manager. The current approach: one `tasklist /FO CSV /NH` call, O(n) in-memory filter, one `taskkill` call if anything is found. From ~8,000 spawns/min to 1–2 per tick.

**Extending `sessionEndMs` in `endBreak()` not `startBreak()`**  
If the break is ended after 2 minutes, extending `sessionEndMs` by the full 5 minutes at break start would grant 3 unearned minutes. By extending at break end by `BREAK_SECONDS - remainingSeconds`, the session is extended by exactly the break duration used. An early-ended break doesn't reward the user with extra session time.

**Unconditional taskbar restore at startup**  
`showTaskbar()` is called unconditionally in `loadFromDb()`, not gated on the crash guard. If the database was corrupted and recreated, the guard key is absent from the empty DB even though the taskbar may still be hidden. Unconditional restore costs nothing (`SW_SHOW` on a visible taskbar is a Win32 no-op) and is always safe.
