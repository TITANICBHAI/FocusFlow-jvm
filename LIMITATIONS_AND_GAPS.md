# FocusFlow — Limitations & Known Gaps

_Last updated: May 2026 · Version 1.0.3_

---

## 1. Platform Limitations (Windows-Only Features)

FocusFlow is built for **Windows 10/11**. On Linux or macOS the UI renders but enforcement is silently skipped — no blocking occurs.

| Feature | Windows | Linux/macOS | Notes |
|---------|---------|-------------|-------|
| Process killing (app blocking) | ✅ Full | ❌ Skipped | JNA/Win32 `TerminateProcess` |
| WinEventHook foreground detection | ✅ Full | ❌ Skipped | `SetWinEventHook` Win32 API |
| Network blocking (netsh) | ✅ Full | ❌ Skipped | Requires Windows netsh + admin |
| Focus Launcher (kiosk/taskbar) | ✅ Full | ❌ Skipped | `ShowWindow` on Shell_TrayWnd |
| Nuclear Mode | ✅ Full | ❌ Skipped | Win32 process enumeration |
| System tray icon | ✅ Full | ⚠️ Partial | AWT tray works on most Linux DEs |
| Auto-start (registry) | ✅ Full | ❌ N/A | `HKCU\...\Run` registry key |
| Website blocking (hosts file) | ✅ Full | ❌ Skipped | `C:\Windows\System32\drivers\etc\hosts` |
| Sound aversion | ✅ Full | ⚠️ Partial | javax.sound may vary on Linux |
| Windows notifications | ✅ Full | ❌ Skipped | AWT SystemTray balloon |

**Impact**: Non-Windows users see a fully functional UI but zero enforcement. An OsBanner component warns non-Windows users that enforcement is inactive.

---

## 2. Enforcement Gaps

### 2a. Browser-Level Website Blocking
FocusFlow kills entire browser processes when the browser is on the blocklist. The hosts-file blocker blocks specific domains for all browsers at the OS level. However:
- A user on an unlisted browser (e.g. a portable browser or one not in the blocklist) bypasses process blocking.
- The hosts-file blocker requires admin privilege to write — if not running as admin, it fails silently.
- A user who manually edits `hosts` back or flushes DNS while FocusFlow is not watching can bypass it.

### 2b. Admin Privilege Not Enforced at Startup
Nuclear Mode and network blocking require administrator privileges. If the app runs as a standard user:
- `netsh` commands fail silently.
- Some high-privilege process kills may be denied by Windows.
- Hosts-file edits are denied.

**WindowsSetupScreen** shows a setup wizard that prompts the user to re-launch as admin when these features are enabled. This is a prompt, not an automatic elevation.

### 2c. Process Name Exact Match Only
Block rules match by exact process name (e.g. `chrome.exe`). Renamed processes, portable apps, or apps launched from unusual paths can bypass blocking. Keyword blocking partially mitigates this for apps with identifiable window titles.

### 2d. VPN / Proxy Bypass
The `NetworkBlocker` uses `netsh advfirewall` outbound rules. A VPN or proxy app running on the same machine can route traffic around these rules.

### 2e. Kernel-Level Enforcement Ceiling
FocusFlow cannot reach kernel-level blocking (Cold Turkey / Windows parental controls level). A user with admin rights can kill the FocusFlow JVM process itself. This is a fundamental JVM ceiling — kernel-level blocking requires a Windows kernel driver.

---

## 3. Focus Launcher Gaps

| Gap | Details |
|-----|---------|
| Kiosk is per-session only | The Focus Launcher kiosk state does not persist across reboots — only per-session |
| Taskbar auto-hide users | If the user has "auto-hide taskbar" enabled in Windows, the hide/show cycle may cause a visible flicker on session end |
| Multi-monitor | Taskbar hide/show only targets the primary taskbar handle; secondary-monitor taskbars on some multi-monitor configs may not be affected |
| UWP app kills during launcher | The 55-process whitelist is comprehensive but does not cover every possible OEM driver variant — exotic hardware drivers may need to be added manually |

---

## 4. Data & Backup

| Gap | Details |
|-----|---------|
| No cloud sync | Sessions and tasks exist only on the local machine |
| No import from other apps | Cannot import from Todoist, Notion, or other tools |
| Manual CSV export only | No scheduled automatic export to external storage |
| No cross-device profile | Focus streaks and history are not portable across machines |

**Automatic local backup is implemented**: `AutoBackupService` creates rolling backups in `%USERPROFILE%\.focusflow\backups\` automatically. Data loss from a single corrupted write is protected.

---

## 5. UX / Accessibility Gaps

| Gap | Details |
|-----|---------|
| No keyboard shortcuts | Every action requires mouse click — no `Ctrl+N` for new task, etc. |
| Dark mode only | App is always dark; no light theme option |
| No font size setting | No accessibility scaling for low-vision users |
| Colour as priority indicator | High/medium/low priority uses colour — colour-blind users rely on the text label |
| Limited screen reader support | Compose Desktop has partial accessibility tree support on Windows |
| Minimum window size | At very small window sizes, layout may overflow without horizontal scroll |

---

## 6. What the App Cannot Do

- Block apps at kernel level (requires a Windows kernel driver — not possible in JVM)
- Block websites inside a running browser tab (only kills entire browser processes or blocks at hosts level)
- Run enforcement on macOS or Linux
- Sync data across multiple devices
- Import tasks from external tools
- Send email or calendar reminders
- Block apps running as SYSTEM or inside a sandboxed/VM environment
- Operate headlessly (requires a graphical Windows desktop session)
- Track focus sessions across multiple Windows user accounts on the same machine
