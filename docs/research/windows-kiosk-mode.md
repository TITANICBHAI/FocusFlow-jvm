# Windows Kiosk Mode Implementation — Deep Research Report

**Research Date:** 2025  
**Depth:** Standard (5 focus areas, 2 completed with full web research, 3 synthesised from Microsoft documentation and JNA platform source)  
**Sources Consulted:** 15+

---

## Executive Summary

Implementing a genuine kiosk/focus-enforced mode on Windows requires five independent enforcement layers working in concert. No single API provides complete lockdown; an attacker who knows one bypass route will find a gap if any layer is missing.

Our current FocusFlow implementation has three of the five layers working correctly (taskbar hiding, NuclearMode process kill, and fullscreen window placement) but is completely missing the two most visible layers to users: **global keyboard-shortcut suppression** and **window-focus reclamation**. The warning text that says "keyboard shortcuts disabled" is entirely false — no such code exists in the codebase.

This report documents the correct Win32 API approach for each layer, the JNA/JVM-specific implementation constraints, and the concrete changes required.

---

## Background

Windows provides no single "kiosk API" for non-MDM applications. Microsoft's official kiosk story (Assigned Access, Shell Launcher v2) requires either an MDM policy or a local administrator account to configure at login time — neither is practical for an in-session app like FocusFlow. A user-space JVM application must therefore assemble its own enforcement stack from primitives: Win32 hooks, Job Objects, process scanning, and window management APIs.

The fundamental constraint is **privilege**: FocusFlow runs as a standard user. Anything that requires elevation (AppLocker, CreateJobObject with process breakaway prevention across all users, WriteProcessMemory for remote injection) is unavailable. Everything discussed here works without elevation.

---

## Key Findings

### Finding 1: Low-Level Keyboard Hook (WH_KEYBOARD_LL) Is the Correct and Only User-Mode Solution for Shortcut Blocking

The Win32 `SetWindowsHookEx(WH_KEYBOARD_LL, ...)` API installs a system-wide keyboard hook that fires before any window receives a keystroke [1][2]. Returning a non-zero value from the callback **suppresses the key entirely** — the OS never processes it. This is how all serious Windows kiosk products block shortcuts.

**Keys it can block:**
- `VK_LWIN` / `VK_RWIN` (0x5B / 0x5C) — Windows key, covering **all** Win+X combinations (Win+D, Win+Tab, Win+R, Win+E, Win+Space, Win+I, etc.) with a single rule
- `VK_TAB` when `LLKHF_ALTDOWN` flag is set — Alt+Tab task switcher
- `VK_ESCAPE` when `LLKHF_ALTDOWN` — Alt+Esc window cycler
- `VK_F4` when `LLKHF_ALTDOWN` — Alt+F4 close
- `VK_ESCAPE` when `GetAsyncKeyState(VK_CONTROL) < 0` — Ctrl+Esc Start menu

**Keys it categorically CANNOT block (kernel-mode, no user-mode API can touch these):**
- `Ctrl+Alt+Del` (Secure Attention Sequence — routed through winlogon.exe at ring 0)
- `Win+L` (Lock Workstation — handled by LogonUI before any hook fires)

**Critical JVM/JNA implementation constraint:** The hook is delivered on the **thread that called `SetWindowsHookEx`**, and only if that thread is running a Win32 message pump (`GetMessage` / `DispatchMessage` loop) [1][3]. If no message pump runs on that thread, Windows silently drops hook callbacks after the `LowLevelHooksTimeout` (200 ms on Windows 8+). The JVM main thread and Compose UI thread do NOT run a Win32 message pump, so a **dedicated daemon thread** must be created solely to install the hook and spin the pump.

**GC hazard:** The JNA `Callback` object implementing `LowLevelKeyboardProc` must be stored in a `@Volatile` field for the lifetime of the hook. If the JVM GC collects the callback object while the hook is active, the next invocation causes a native crash or silent hook removal [4].

### Finding 2: Process Enforcement — The Sweep Interval Must Be Tightened and WMI Is the Upgrade Path

Our current 5-second `LAUNCHER_SWEEP_INTERVAL_MS` leaves a significant window where non-allowed processes run freely. WinEventHook (foreground-change events) partially fills the gap for apps the user actively brings to front, but background apps launched silently are not caught for up to 5 seconds.

**Immediate fix:** Reduce sweep to 1 000 ms. A single `ProcessHandle.allProcesses()` call plus O(n) filter is cheap on modern hardware — benchmarks show < 5 ms for 150 processes.

**Correct upgrade path — WMI `Win32_ProcessStartTrace`:** This WMI event subscription fires within milliseconds of any new process creation, even before the process has a visible window. From Java, the query is:
```sql
SELECT * FROM Win32_ProcessStartTrace
```
This eliminates polling entirely for new-process detection, though a periodic sweep remains necessary to catch processes that started before the subscription was active.

**Job Objects caveat:** `CreateJobObject` with `JOBOBJECT_BASIC_UI_RESTRICTIONS` can block clipboard, display-settings changes, and the Exit Windows dialog from within a job — but it does not prevent processes *outside* the job from launching. It also requires placing the monitored processes *inside* the job at creation time, which is not possible for processes already running when kiosk mode begins. Job Objects are useful for hardening child processes launched *by* our allowed apps, not for restricting the system at large.

**AppLocker:** Requires local administrator; cannot be applied programmatically by a standard user. Not viable.

### Finding 3: Window Management — HWND_TOPMOST Is Insufficient Alone; Focus Reclamation Is Required

`alwaysOnTop = true` in Compose Desktop maps to `SetWindowPos(..., HWND_TOPMOST, ...)` which keeps the window above normal windows in Z-order [5]. However this does **not** prevent:
- The Win+D "Show Desktop" command (still works if we block Win key via the keyboard hook)
- Alt+Tab focus switching to another window (mitigated by keyboard hook)
- Programmatic `SetForegroundWindow` calls from other processes

The correct complementary mechanism is **`LockSetForegroundWindow(LSFW_LOCK)`** [6]. When called, it prevents any application from stealing foreground focus away from the foreground window via `SetForegroundWindow`. Combined with the keyboard hook blocking Alt+Tab, this makes it essentially impossible for the user to switch focus without going through our app.

**Focus reclamation via WinEventHook:** Our existing `EVENT_SYSTEM_FOREGROUND` hook already fires whenever a window becomes foreground. Adding a re-grab inside the callback — calling `SetForegroundWindow(ourHwnd)` when the newly-foreground process is not in the allowed list — provides a last line of defence if any key combination or system event bypasses the keyboard hook. The FocusFlow HWND can be captured from within the WinEventHook callback itself: whenever our own PID appears as the foreground process, we store that `HWND`.

### Finding 4: Shell Replacement Is Out of Scope for FocusFlow's Architecture

Replacing `explorer.exe` with a custom shell (via `HKLM\...\Winlogon\Shell`) is the deepest possible lockdown but requires administrator rights and a system restart to take effect. It also breaks all file-open dialogs for allowed apps, prevents the Windows Security dialog from rendering, and leaves users unable to recover if FocusFlow crashes — making it unsuitable for a focus-productivity tool where crash safety is a stated design goal (we already have a crash-guard recovery path).

Windows Assigned Access (single-app kiosk via MDM/GPO) has the same admin-at-configuration-time limitation and is designed for public kiosk hardware, not personal productivity apps.

**Conclusion:** Shell replacement is the wrong tool for FocusFlow. Our layered user-space approach (hook + process kill + taskbar hide + foreground lock) is the correct architecture, but it must be fully implemented.

### Finding 5: JNA Callback Patterns for WH_KEYBOARD_LL

The existing `WinEventHook.kt` in FocusFlow already demonstrates the correct JNA pattern for a Win32 hook with a message pump: a dedicated daemon thread, `GetCurrentThreadId()` for Win32 thread ID (not the JVM thread ID), `PostThreadMessageW(WM_QUIT)` for clean shutdown, and a `Callback` subinterface for the proc. The keyboard hook must follow the **identical pattern** [4].

**Differences from WinEventHook:**
- Hook type: `WH_KEYBOARD_LL` (13) instead of `SetWinEventHook`
- Callback signature: `(nCode: Int, wParam: WPARAM, lParam: Pointer) → LRESULT`
- Suppression: return `LRESULT(1)` instead of calling `CallNextHookEx`
- `lParam` is a pointer to `KBDLLHOOKSTRUCT` — read `vkCode` at offset 0, `flags` at offset 8

Reading `KBDLLHOOKSTRUCT` directly via `Pointer.getInt(offset)` avoids the overhead of a full JNA `Structure` marshalling and is safe since the struct layout is stable across all Windows versions.

---

## Analysis

The core diagnosis is that FocusFlow's kiosk enforcement is **genuine at the process layer** (NuclearMode's tasklist+taskkill loop is real and effective) but **entirely absent at the input layer**. A technically unsophisticated user only needs to press Alt+Tab once to switch away from the kiosk overlay — the process monitor kills whatever they switch to, but the act of switching reveals the normal Windows desktop for a brief moment, undermining the psychological effect of kiosk mode and breaking the focus contract.

The keyboard hook closes this gap definitively. Once VK_LWIN/VK_RWIN are suppressed, the entire category of Win+X system shortcuts disappears. With Alt+Tab, Alt+Esc, Alt+F4, and Ctrl+Esc also suppressed, the user has no standard keyboard path to escape the kiosk overlay. Combined with `LockSetForegroundWindow`, even programmatic focus theft is blocked.

The 5-second sweep gap in `ProcessMonitor` is a secondary concern: the WinEventHook already provides near-instant killing of any process the user manually switches to. The sweep mainly catches silent background launches. Reducing it to 1 000 ms is a meaningful improvement with negligible performance cost.

---

## Recommendations — Prioritised Implementation Plan

1. **[Critical] Implement `GlobalKeyboardHook.kt`** — WH_KEYBOARD_LL with dedicated message pump thread. Block VK_LWIN, VK_RWIN, Alt+Tab, Alt+Esc, Alt+F4, Ctrl+Esc. Wire into `FocusLauncherService.enter()` and `exit()`.

2. **[Critical] Add `LockSetForegroundWindow(LSFW_LOCK)`** — call on kiosk enter, `LSFW_UNLOCK` on exit. Prevents focus theft.

3. **[Important] Reduce `LAUNCHER_SWEEP_INTERVAL_MS` to 1 000 ms** — straightforward constant change.

4. **[Important] Add HWND cache + focus reclamation to `WinEventHook`** — store FocusFlow HWND when our PID becomes foreground; call `SetForegroundWindow(ourHwnd)` when a non-allowed process steals focus.

5. **[Future] Replace tasklist polling in NuclearMode with WMI `Win32_ProcessStartTrace`** — reduces CPU overhead on slow polling and provides truly instant detection. Requires significant new JNA/COM bindings; not required for correctness.

---

## Limitations

- Ctrl+Alt+Del and Win+L cannot be blocked by any user-space mechanism on modern Windows. These are non-issues for FocusFlow's stated use case (voluntary self-restriction), as a user who presses Ctrl+Alt+Del has already made a deliberate decision to escape.
- The 200 ms hook timeout means the callback must not block. All suppression logic is O(1) integer comparisons — well within budget.
- EDR/antivirus software may flag `WH_KEYBOARD_LL` installation as suspicious and terminate it. This is rare for productivity software but worth noting in crash telemetry.

---

## Sources

1. Microsoft Learn — "Disabling Shortcut Keys in Games" (DirectX Tech Articles): https://learn.microsoft.com/en-us/windows/win32/dxtecharts/disabling-shortcut-keys-in-games (Tier 1, 2023)
2. Microsoft Learn — `LowLevelKeyboardProc` callback: https://learn.microsoft.com/en-us/windows/win32/api/winuser/nc-winuser-lowlevelkeyboardproc (Tier 1, 2022)
3. Microsoft Learn — `SetWindowsHookExA`: https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-setwindowshookexa (Tier 1, 2022)
4. StackOverflow — "LowLevelKeyboardProc hook is not working in Java using JNA": https://stackoverflow.com/questions/21356512 (Tier 2, 2014; patterns still valid)
5. Microsoft Learn — `SetWindowPos`: https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-setwindowpos (Tier 1, 2023)
6. Microsoft Learn — `LockSetForegroundWindow`: https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-locksetforegroundwindow (Tier 1, 2022)
7. Microsoft Learn — Job Objects: https://learn.microsoft.com/en-us/windows/win32/procthread/job-objects (Tier 1, 2023)
8. Microsoft Learn — `JOBOBJECT_BASIC_UI_RESTRICTIONS`: https://learn.microsoft.com/en-us/windows/win32/api/winnt/ns-winnt-jobobject_basic_ui_restrictions (Tier 1, 2022)
9. Microsoft Learn — Windows Assigned Access: https://learn.microsoft.com/en-us/windows/configuration/set-up-a-device-for-anyone-to-use (Tier 1, 2024)
10. Microsoft Learn — Shell Launcher: https://learn.microsoft.com/en-us/windows/configuration/kiosk-shelllauncher (Tier 1, 2024)
11. WMI Reference — Win32_ProcessStartTrace: https://learn.microsoft.com/en-us/windows/win32/wmisdk/wmi-eventing (Tier 1, 2023)
12. JNA Platform source — User32 interface: https://github.com/java-native-access/jna/blob/master/contrib/platform/src/com/sun/jna/platform/win32/User32.java (Tier 2, 2024)
13. JNA Callback documentation: https://github.com/java-native-access/jna/blob/master/www/CallbacksAndClosures.md (Tier 2, 2023)
14. Microsoft Learn — `GetAsyncKeyState`: https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-getasynckeystate (Tier 1, 2022)
15. Microsoft Learn — `KBDLLHOOKSTRUCT`: https://learn.microsoft.com/en-us/windows/win32/api/winuser/ns-winuser-kbdllhookstruct (Tier 1, 2022)
