# FocusFlow Linux Port — Progress Tracker

> `[x]` = done | `[-]` = in progress | `[ ]` = not started
>
> **Read the plan first:** `LINUX_PORT_PLAN.md`
> **Section numbers here match section numbers in the plan exactly.**
> **Last updated:** June 2026 — v3 recheck: 129 total tasks (32 new vs v2)

---

## ⚠️ Mandatory First Step

Phase 0 (Section 1 — OS Detection Utilities) **must be completed before any other phase.**
Every other section depends on `isLinux`, `isX11`, `isWayland`, and `hasXdotool` existing.

---

## Phase 0 — Foundations

| # | Task | File | Status |
|---|---|---|---|
| 0.1 | Codebase analysis complete | — | `[x]` |
| 0.2 | `LINUX_PORT_PLAN.md` created | — | `[x]` |
| 0.3 | `LINUX_PORT_PROGRESS.md` created | — | `[x]` |
| 0.4 | Add `isLinux`, `isMac`, `isWayland`, `isX11`, `hasXdotool` to `WinApiBindings.kt` | `WinApiBindings.kt` | `[ ]` |

---

## Phase 1 — Build System (can run any time, no code dependencies)

| # | Task | File | Status |
|---|---|---|---|
| 1.1 | Add `linux { }` block to `nativeDistributions` | `build.gradle.kts` | `[ ]` |
| 1.2 | Source or create 512×512 PNG icon | `src/main/resources/focusflow_512.png` | `[ ]` |
| 1.3 | Create GitHub Actions Linux build workflow | `.github/workflows/build-linux.yml` | `[ ]` |

---

## Phase 2 — Core Enforcement ← Start here after Phase 0

| # | Task | File | Status |
|---|---|---|---|
| 2.1 | Add `startLinuxPoller()` and `stopLinuxPoller()` | `WinEventHook.kt` | `[ ]` |
| 2.2 | Add `getLinuxForegroundProcess()`, `getX11ForegroundProcess()`, `getWaylandForegroundProcessFallback()` | `WinEventHook.kt` | `[ ]` |
| 2.3 | Rename existing `start()`/`stop()` to `startWindows()`/`stopWindows()`, add dispatching `start()`/`stop()` | `WinEventHook.kt` | `[ ]` |
| 2.4 | Update `isActive` property to include Linux branch | `WinEventHook.kt` | `[ ]` |
| 2.5 | Change `if (isWindows)` to `if (isWindows \|\| isLinux)` in `ProcessMonitor.start()` | `ProcessMonitor.kt` | `[ ]` |
| 2.6 | Change `if (!isWindows) return` to `if (!isWindows && !isLinux) return` in `ProcessMonitor.tickPoll()` | `ProcessMonitor.kt` | `[ ]` |
| 2.7 | Add Linux branch to `getForegroundProcessNameAndPid()` | `WinApiBindings.kt` | `[ ]` |
| 2.8 | Add `if (!isWindows) return null` defensive guard to `getForegroundProcessName()` at line ~81 | `WinApiBindings.kt` | `[ ]` |
| 2.9 | Add `if (!isWindows) return null` defensive guard to `getForegroundProcessNameAndPid()` function body (call site is guarded, function itself is not) | `WinApiBindings.kt` | `[ ]` |

---

## Phase 3 — Keyword Blocking (parallel with others after Phase 0)

| # | Task | File | Status |
|---|---|---|---|
| 3.1 | Rename existing `getForegroundWindowTitle()` to `getForegroundWindowTitleWindows()` | `WinApiBindings.kt` | `[ ]` |
| 3.2 | Add `getLinuxWindowTitle()` via `xdotool getactivewindow getwindowname` | `WinApiBindings.kt` | `[ ]` |
| 3.3 | Add dispatching `getForegroundWindowTitle()` | `WinApiBindings.kt` | `[ ]` |

---

## Phase 4 — Nuclear Mode (parallel with others after Phase 0)

| # | Task | File | Status |
|---|---|---|---|
| 4.1 | Extract existing `escapeProcesses` set into `windowsEscapeProcesses` constant | `NuclearMode.kt` | `[ ]` |
| 4.2 | Add `linuxEscapeProcesses` set (terminals, monitors, file managers, system config tools) | `NuclearMode.kt` | `[ ]` |
| 4.3 | Change `escapeProcesses` from static `val` to computed `get()` dispatching by OS | `NuclearMode.kt` | `[ ]` |
| 4.4 | Extract existing `knownEscapePathSuffixes` into `windowsEscapePathSuffixes` | `NuclearMode.kt` | `[ ]` |
| 4.5 | Add `linuxEscapePathSuffixes` set | `NuclearMode.kt` | `[ ]` |
| 4.6 | Change `knownEscapePathSuffixes` to computed `get()` dispatching by OS | `NuclearMode.kt` | `[ ]` |
| 4.7 | Wrap `getRunningEscapeProcesses()` — rename Windows path, add Linux (ProcessHandle) path | `NuclearMode.kt` | `[ ]` |

---

## Phase 5 — Launcher Safe Process List ← SAFETY CRITICAL

> **Warning:** Without this, kiosk mode on Linux will attempt to kill `Xorg` or `gnome-shell`,
> crashing the entire display session. Do not test kiosk mode until this is done.

| # | Task | File | Status |
|---|---|---|---|
| 5.1 | Convert `launcherSafeProcesses` from static `val` to computed `get()` | `ProcessMonitor.kt` | `[ ]` |
| 5.2 | Rename existing set to `windowsLauncherSafeProcesses` | `ProcessMonitor.kt` | `[ ]` |
| 5.3 | Add `linuxLauncherSafeProcesses` set (Xorg, gnome-shell, kwin, systemd, dbus-daemon, etc.) | `ProcessMonitor.kt` | `[ ]` |

---

## Phase 5b — Always-Kill Shells (Plan Section 7b)

> Terminals and system monitors are always killed during enforcement on Windows (via `systemShells` set). Without a Linux equivalent, users can freely open a terminal and circumvent enforcement.

| # | Task | File | Status |
|---|---|---|---|
| 5b.1 | Rename existing `systemShells` set to `windowsSystemShells` | `ProcessMonitor.kt` | `[ ]` |
| 5b.2 | Add `linuxSystemShells` set (gnome-terminal, konsole, xterm, bash, zsh, htop, btop, dconf-editor, etc.) | `ProcessMonitor.kt` | `[ ]` |
| 5b.3 | Convert `systemShells` from static `val` to computed `get()` dispatching by OS | `ProcessMonitor.kt` | `[ ]` |

---

## Phase 5c — Process Name Normalization (Plan Section 7c) ← SILENT BUG

> **Without this, a block list built on Windows silently does nothing on Linux.** Every stored name has `.exe`; Linux process names do not. All comparisons silently fail.

| # | Task | File | Status |
|---|---|---|---|
| 5c.1 | Add `normalizeProcessName(name: String): String` utility — strips `.exe` on Linux, identity on Windows | `ProcessMonitor.kt` | `[ ]` |
| 5c.2 | Apply `normalizeProcessName()` to both sides of every blocked-set membership check (systemShells, scheduleBlockedProcesses, standaloneBlockedProcesses, dailyAllowanceBlockedProcesses) | `ProcessMonitor.kt` | `[ ]` |
| 5c.3 | Verify comparison in launcher safe process check also uses normalization | `ProcessMonitor.kt` | `[ ]` |

---

## Phase 5d — Block Presets (Plan Section 8)

| # | Task | File | Status |
|---|---|---|---|
| 5d.1 | Update preset `description` strings that mention Windows-specific apps to be OS-neutral (e.g. "Chrome, Firefox, Edge" → "Chrome, Firefox, Brave, and other browsers") | `BlockPresets.kt` | `[ ]` |
| 5d.2 | Add Linux-native binary names to `browsers` preset (`google-chrome`, `google-chrome-stable`, `chromium`, `chromium-browser`, `brave-browser`) | `BlockPresets.kt` | `[ ]` |
| 5d.3 | Add Linux-native names to `gaming` preset (`steam`, `heroic`, `lutris`) | `BlockPresets.kt` | `[ ]` |

---

## Phase 6 — Hosts File Blocking (parallel with others after Phase 0)

| # | Task | File | Status |
|---|---|---|---|
| 6.1 | Change `HOSTS_PATH` from hardcoded Windows string to OS-dispatching `get()` | `HostsBlocker.kt` | `[ ]` |
| 6.2 | Remove `if (!isWindows) return` guards from all public methods | `HostsBlocker.kt` | `[ ]` |
| 6.3 | Add Linux DNS cache flush (systemd-resolve, resolvectl, nscd) to `flushDnsCache()` | `HostsBlocker.kt` | `[ ]` |
| 6.4 | Remove `if (!isWindows) return false` from `canWriteHostsFile()` | `HostsBlocker.kt` | `[ ]` |
| 6.5 | Rename `BlockResult.NotWindows` to `NotSupported` or add `NoPermission` | `HostsBlocker.kt` | `[ ]` |

---

## Phase 7 — Network / Firewall Blocking (parallel with others after Phase 0)

| # | Task | File | Status |
|---|---|---|---|
| 7.1 | Add `canRunSudoWithoutPassword()` | `NetworkBlocker.kt` | `[ ]` |
| 7.2 | Wrap `isRunningAsAdmin()` — add Linux branch calling `canRunSudoWithoutPassword()` | `NetworkBlocker.kt` | `[ ]` |
| 7.3 | Add `addRuleLinux()` via iptables `--cmd-owner` | `NetworkBlocker.kt` | `[ ]` |
| 7.4 | Wrap `addRule()` to dispatch to `addRuleLinux()` | `NetworkBlocker.kt` | `[ ]` |
| 7.5 | Add `removeRuleLinux()` | `NetworkBlocker.kt` | `[ ]` |
| 7.6 | Wrap `removeRule()` to dispatch to `removeRuleLinux()` | `NetworkBlocker.kt` | `[ ]` |
| 7.7 | Add `syncFromFirewallLinux()` | `NetworkBlocker.kt` | `[ ]` |
| 7.8 | Wrap `syncFromFirewall()` to dispatch to `syncFromFirewallLinux()` | `NetworkBlocker.kt` | `[ ]` |

---

## Phase 8 — VPN Blocker (parallel with others after Phase 0)

| # | Task | File | Status |
|---|---|---|---|
| 8.1 | Rename `KNOWN_VPN_PROCESSES` to `KNOWN_VPN_PROCESSES_WINDOWS` | `VpnBlocker.kt` | `[ ]` |
| 8.2 | Add `KNOWN_VPN_PROCESSES_LINUX` set (no `.exe` suffixes, Linux daemon names) | `VpnBlocker.kt` | `[ ]` |
| 8.3 | Change `KNOWN_VPN_PROCESSES` to computed `get()` dispatching by OS | `VpnBlocker.kt` | `[ ]` |
| 8.4 | Fix `addCustomProcess()` to only append `.exe` on Windows | `VpnBlocker.kt` | `[ ]` |

---

## Phase 9 — Floating Block Overlay (trivial — parallel with others after Phase 0)

| # | Task | File | Status |
|---|---|---|---|
| 9.1 | Change `if (!isWindows) return` to `if (!isWindows && !isLinux) return` on line 44 | `FloatingBlockOverlay.kt` | `[ ]` |

---

## Phase 10 — Kiosk Mode (parallel with others after Phase 0)

| # | Task | File | Status |
|---|---|---|---|
| 10.1 | Rename existing `hideTaskbar()` to `hideTaskbarWindows()` | `FocusLauncherService.kt` | `[ ]` |
| 10.2 | Add `hideTaskbarLinux()` via wmctrl EWMH fullscreen+above | `FocusLauncherService.kt` | `[ ]` |
| 10.3 | Add dispatching `hideTaskbar()` | `FocusLauncherService.kt` | `[ ]` |
| 10.4 | Same pattern for `showTaskbar()` → `showTaskbarWindows()` + `showTaskbarLinux()` | `FocusLauncherService.kt` | `[ ]` |
| 10.5 | Wrap `emergencyRestoreWindows()` body in `if (!isWindows) return` | `FocusLauncherService.kt` | `[ ]` |

---

## Phase 11 — Keyboard Hook (parallel with others after Phase 0)

| # | Task | File | Status |
|---|---|---|---|
| 11.1 | Rename existing `enable()` to `enableWindows()` | `GlobalKeyboardHook.kt` | `[ ]` |
| 11.2 | Add `enableLinux()` — writes xbindkeys config, launches xbindkeys process | `GlobalKeyboardHook.kt` | `[ ]` |
| 11.3 | Add `disableLinux()` — kills xbindkeys, deletes config file | `GlobalKeyboardHook.kt` | `[ ]` |
| 11.4 | Add dispatching `enable()` and `disable()` | `GlobalKeyboardHook.kt` | `[ ]` |
| 11.5 | Update `isActive` to include Linux branch | `GlobalKeyboardHook.kt` | `[ ]` |

---

## Phase 12 — App Discovery (parallel with others after Phase 0)

| # | Task | File | Status |
|---|---|---|---|
| 12.1 | Fix `getRunningApps()` `.endsWith(".exe")` filter — OS-conditional (returns empty on Linux without this) | `InstalledAppsScanner.kt` | `[ ]` |
| 12.2 | Add `linuxCurated` display-name map (no `.exe` keys) and make `curated` dispatch by OS | `InstalledAppsScanner.kt` | `[ ]` |
| 12.3 | Add `linuxSystemIgnore` set and make `systemIgnore` dispatch by OS | `InstalledAppsScanner.kt` | `[ ]` |
| 12.4 | Add `scanLinux()` — walks `/usr/share/applications/` and `~/.local/share/applications/` | `InstalledAppsScanner.kt` | `[ ]` |
| 12.5 | Add `parseDesktopFile()` — parses `.desktop` files into `InstalledApp` | `InstalledAppsScanner.kt` | `[ ]` |
| 12.6 | Wrap `scan()` / `getInstalledApps()` to dispatch to `scanLinux()` | `InstalledAppsScanner.kt` | `[ ]` |
| 12.7 | Add `extractLinux()` — searches `/usr/share/icons/` and `/usr/share/pixmaps/` | `AppIconExtractor.kt` | `[ ]` |
| 12.8 | Wrap `extract()` to dispatch to `extractLinux()` | `AppIconExtractor.kt` | `[ ]` |

---

## Phase 13 — Startup Persistence & Watchdog (parallel with others after Phase 0)

| # | Task | File | Status |
|---|---|---|---|
| 13.1 | Rename existing `enable()` to `enableWindows()` in `WindowsStartupManager` | `WindowsStartupManager.kt` | `[ ]` |
| 13.2 | Add `enableLinux()` — writes `~/.config/autostart/focusflow.desktop` | `WindowsStartupManager.kt` | `[ ]` |
| 13.3 | Add `disableLinux()` — deletes the `.desktop` file | `WindowsStartupManager.kt` | `[ ]` |
| 13.4 | Add `isEnabledLinux()` — checks if file exists | `WindowsStartupManager.kt` | `[ ]` |
| 13.5 | Add dispatching `enable()`, `disable()`, `isEnabled()` | `WindowsStartupManager.kt` | `[ ]` |
| 13.6 | Add `installLinux()` — systemd user service (with cron fallback) | `WatchdogInstaller.kt` | `[ ]` |
| 13.7 | Add `uninstallLinux()` — removes service file + cron entry | `WatchdogInstaller.kt` | `[ ]` |
| 13.8 | Add dispatching `install()`, `uninstall()` | `WatchdogInstaller.kt` | `[ ]` |

---

## Phase 14 — Crash Reporter & Main.kt (Plan Section 20)

| # | Task | File | Status |
|---|---|---|---|
| 14.1 | Wrap `emergencyRestoreWindows()` call at line ~529 in `if (isWindows)` | `CrashReporter.kt` | `[ ]` |
| 14.2 | Fix `~/Desktop` crash log path to fall back to `~/` if Desktop doesn't exist | `CrashReporter.kt` | `[ ]` |
| 14.3 | Wrap `emergencyRestoreWindows()` call at line ~81 in `Main.kt` in `if (isWindows)` | `Main.kt` | `[ ]` |

---

## Phase 15 — System Tray (parallel with others after Phase 0)

| # | Task | File | Status |
|---|---|---|---|
| 15.1 | Ensure `SystemTray.isSupported()` is checked before `SystemTray.getSystemTray()` | `SystemTrayManager.kt` | `[ ]` |

---

## Phase 15b — FocusSessionService .exe Forcing (Plan Section 24a) ← CRITICAL

> **Without this fix, ALL process blocking is broken on Linux for every session.** The service forces `.exe` onto every process name at session start, so `firefox` becomes `firefox.exe` and never matches the running `firefox` process.

| # | Task | File | Status |
|---|---|---|---|
| 15b.1 | Replace `.exe`-forcing `.map { }` at line 92 with OS-conditional normalization using `normalizeProcessName()` (or inline equivalent) | `FocusSessionService.kt` | `[ ]` |

---

## Phase 15c — AppBlockerScreen .exe Forcing (Plan Section 24b) ← CRITICAL

> **Without this, every process a Linux user manually adds is saved with `.exe` and never matched.** Three separate code paths force `.exe` on manual entry.

| # | Task | File | Status |
|---|---|---|---|
| 15c.1 | Fix `addManual()` at line ~256 — OS-conditional `.exe` handling; update validation error message | `AppBlockerScreen.kt` | `[ ]` |
| 15c.2 | Fix inline `.exe` coercion at line ~1244 (first occurrence in session-start manual entry) | `AppBlockerScreen.kt` | `[ ]` |
| 15c.3 | Fix inline `.exe` coercion at line ~1264 (second occurrence in session-start manual entry) | `AppBlockerScreen.kt` | `[ ]` |
| 15c.4 | Add Linux process names to `processColorMap` (lines 63–97) so Linux apps get colored chips | `AppBlockerScreen.kt` | `[ ]` |
| 15c.5 | Make placeholder text OS-conditional (line ~489): `"e.g. discord.exe"` → `"e.g. discord"` on Linux | `AppBlockerScreen.kt` | `[ ]` |

---

## Phase 15d — SettingsScreen Windows-Specific Presets (Plan Section 24c)

| # | Task | File | Status |
|---|---|---|---|
| 15d.1 | Replace quick-add presets map (line ~474) with OS-conditional lists (`.exe` on Windows, no suffix on Linux) | `SettingsScreen.kt` | `[ ]` |
| 15d.2 | Update process monitor status subtitle (lines 149–155) to show "Active — xdotool polling" on Linux instead of "Inactive — only enforced on Windows" | `SettingsScreen.kt` | `[ ]` |
| 15d.3 | Update startup toggle (lines 371–378): OS-conditional label, subtitle, and `enabled` flag; use `settingsStartWithSystem` on Linux | `SettingsScreen.kt` | `[ ]` |

---

## Phase 15e — Translations.kt New String Keys (Plan Section 24d)

| # | Task | File | Status |
|---|---|---|---|
| 15e.1 | Add `navLinuxSetup`, `settingsStartWithSystem`, `settingsFirewallNoteLinux`, `settingsSysTrayDescLinux` to the `Strings` data class | `Translations.kt` | `[ ]` |
| 15e.2 | Add English values for all new fields in the English `Strings(...)` constructor call | `Translations.kt` | `[ ]` |
| 15e.3 | Add translated (or English fallback) values for all new fields in every other language's `Strings(...)` constructor call | `Translations.kt` | `[ ]` |

---

## Phase 15f — DailyAllowanceTracker Normalization (Plan Section 24e)

| # | Task | File | Status |
|---|---|---|---|
| 15f.1 | Apply `normalizeProcessName()` to `proc` before `runningMap.containsKey(proc)` check — fixes silent mismatch when stored names have `.exe` and Linux processes don't | `DailyAllowanceTracker.kt` | `[ ]` |
| 15f.2 | Plug in `getLinuxForegroundProcess()` at line 133 once Section 3 is complete (quality improvement, not a crash fix — the current `else` fallback is acceptable for initial release) | `DailyAllowanceTracker.kt` | `[ ]` |

---

## Phase 15g — OnboardingScreen Windows Row Titles (Plan Section 24f)

| # | Task | File | Status |
|---|---|---|---|
| 15g.1 | Hide "Windows Defender Exclusion" row entirely on Linux with `if (isWindows)` wrapper | `OnboardingScreen.kt` | `[ ]` |
| 15g.2 | Make "Auto-Start" row title OS-conditional: "Auto-Start with Windows" → "Auto-Start at Login" on Linux | `OnboardingScreen.kt` | `[ ]` |
| 15g.3 | Make "Windows Firewall Rules" row title OS-conditional and wrap `onCheckedChange` auto-start body in `if (isWindows)` until Section 17 is complete | `OnboardingScreen.kt` | `[ ]` |
| 15g.4 | Update bottom text "Settings → Windows Setup & Permissions" to "Settings → Linux Setup" on Linux | `OnboardingScreen.kt` | `[ ]` |

---

## Phase 15h — AppStrings.kt, KeywordBlockerScreen, ShareDialog (Plan Sections 24i/j/k)

| # | Task | File | Status |
|---|---|---|---|
| 15h.1 | Rename `settingsStartWithWindows` → `settingsStartWithSystem` in data class field | `AppStrings.kt` | `[ ]` |
| 15h.2 | Rename matching property accessor `settingsStartWithWindows` → `settingsStartWithSystem` | `AppStrings.kt` | `[ ]` |
| 15h.3 | Rename key and update string values for all 7 locales in Translations.kt (coordinated with 15h.1/2 and Phase 15d) | `Translations.kt` | `[ ]` |
| 15h.4 | Make line 125 description text OS-conditional ("…on Windows" → Wayland/X11 note on Linux) | `KeywordBlockerScreen.kt` | `[ ]` |
| 15h.5 | Make line 39 share text OS-conditional ("PC (Windows)" → "PC (Linux)" on Linux) | `ShareDialog.kt` | `[ ]` |

---

## Phase 16 — UI Updates (Plan Section 25)

| # | Task | File | Status |
|---|---|---|---|
| 16.1 | Add `LINUX_SETUP` to `Screen` enum | `Models.kt` | `[ ]` |
| 16.2 | Make `WINDOWS_SETUP` nav item OS-conditional in `SideNav.kt` | `SideNav.kt` | `[ ]` |
| 16.3 | Create `LinuxSetupScreen.kt` (xdotool install, sudo setup, Wayland limitations) | `LinuxSetupScreen.kt` (new file) | `[ ]` |
| 16.4 | Add `Screen.LINUX_SETUP -> LinuxSetupScreen()` to screen router in `App.kt` at **line 206** (directly after `Screen.WINDOWS_SETUP` case) | `App.kt` | `[ ]` |
| 16.5 | Update `OsBanner.kt` — show "Linux (beta)" on Linux instead of "Windows only" | `OsBanner.kt` | `[ ]` |
| 16.6 | Update `BlockDefenseScreen.kt` — OS-conditional feature descriptions | `BlockDefenseScreen.kt` | `[ ]` |
| 16.7 | `VpnNetworkScreen.kt` — make quick-add VPN preset map OS-conditional (lines ~40–57, all `.exe` keys → never match on Linux) | `VpnNetworkScreen.kt` | `[ ]` |
| 16.8 | `VpnNetworkScreen.kt` — fix `.exe` forcing on network rule target process at line ~438 (critical: stores `firefox.exe` in DB on Linux; same pattern as AppBlockerScreen) | `VpnNetworkScreen.kt` | `[ ]` |
| 16.9 | `VpnNetworkScreen.kt` — OS-conditional placeholder text at lines ~242 and ~415 (`"e.g. myvpn.exe"` / `"e.g. chrome.exe"` → use Linux process names on Linux) | `VpnNetworkScreen.kt` | `[ ]` |
| 16.10 | `VpnNetworkScreen.kt` — OS-conditional description strings at lines ~374 and ~376 ("Windows hosts file" → "/etc/hosts"; "Windows Firewall" → "iptables") | `VpnNetworkScreen.kt` | `[ ]` |
| 16.11 | Wrap `showRegistryOrphanDialog` setter and the "Task Manager May Be Disabled" `AlertDialog` in `if (isWindows)` — **App.kt line ~272** — the `confirmButton` contains `ProcessBuilder("powershell", ..., "Start-Process -Verb RunAs")` which crashes on Linux | `App.kt` | `[ ]` |

---

## Phase 16b — Confirmed Cross-Platform (Plan Section 23)

These files were fully reviewed. No changes needed. Check them off once you have read and confirmed them.

| # | Task | File | Status |
|---|---|---|---|
| 16b.1 | Read and confirm `ResourceMonitorService.kt` has zero Windows-specific code | `ResourceMonitorService.kt` | `[ ]` |
| 16b.2 | Read and confirm `StandaloneBlockService.kt` has zero Windows-specific code | `StandaloneBlockService.kt` | `[ ]` |
| 16b.3 | Read and confirm `KillSwitchService.kt` has zero Windows-specific code | `KillSwitchService.kt` | `[ ]` |

---

## Phase 17 — Testing & QA (run last)

| # | Test | Status |
|---|---|---|
| 17.1 | `isLinux` = true, `isWindows` = false on Linux | `[ ]` |
| 17.2 | `isX11` = true on X11 session | `[ ]` |
| 17.3 | `isWayland` = true on Wayland session | `[ ]` |
| 17.4 | `hasXdotool` = true when xdotool installed | `[ ]` |
| 17.5 | Foreground detection fires within 1s of app switch (X11) | `[ ]` |
| 17.6 | Blocked app is killed within 1s of gaining focus | `[ ]` |
| 17.7 | `blockDomain()` writes to `/etc/hosts`; `curl` confirms domain blocked | `[ ]` |
| 17.8 | `gnome-terminal` killed during Nuclear Mode | `[ ]` |
| 17.9 | `Xorg`, `gnome-shell`, `systemd` are **never** killed (safe list test) | `[ ]` |
| 17.10 | `nordvpn` (Linux process, no .exe) detected as VPN process | `[ ]` |
| 17.11 | `addCustomProcess("testvpn")` does not append `.exe` on Linux | `[ ]` |
| 17.12 | Floating block overlay appears over blocked app | `[ ]` |
| 17.13 | Kiosk mode covers taskbar | `[ ]` |
| 17.14 | `~/.config/autostart/focusflow.desktop` created when startup enabled | `[ ]` |
| 17.15 | App relaunches after kill (watchdog test) | `[ ]` |
| 17.16 | App does not crash on Wayland (even if enforcement is degraded) | `[ ]` |
| 17.17 | `./gradlew packageDeb` succeeds without errors | `[ ]` |
| 17.18 | `sudo dpkg -i focusflow_*.deb` installs and launches | `[ ]` |
| 17.19 | **Regression:** Windows build still produces `.exe`/`.msi` without errors | `[ ]` |
| 17.20 | **Regression:** All enforcement features still work on Windows | `[ ]` |

---

## Summary

| Phase | Tasks | Done | Left |
|---|---|---|---|
| Phase 0 — Foundations | 4 | 3 | 1 |
| Phase 1 — Build System | 3 | 0 | 3 |
| Phase 2 — Core Enforcement | 9 | 0 | 9 |
| Phase 3 — Keyword Blocking | 3 | 0 | 3 |
| Phase 4 — Nuclear Mode | 7 | 0 | 7 |
| Phase 5 — Launcher Safe List | 3 | 0 | 3 |
| Phase 5b — Always-Kill Shells | 3 | 0 | 3 |
| Phase 5c — Process Name Normalization | 3 | 0 | 3 |
| Phase 5d — Block Presets | 3 | 0 | 3 |
| Phase 6 — Hosts File | 5 | 0 | 5 |
| Phase 7 — Network/Firewall | 8 | 0 | 8 |
| Phase 8 — VPN Blocker | 4 | 0 | 4 |
| Phase 9 — Floating Overlay | 1 | 0 | 1 |
| Phase 10 — Kiosk Mode | 5 | 0 | 5 |
| Phase 11 — Keyboard Hook | 5 | 0 | 5 |
| Phase 12 — App Discovery | 8 | 0 | 8 |
| Phase 13 — Startup & Watchdog | 8 | 0 | 8 |
| Phase 14 — Crash Reporter & Main.kt | 3 | 0 | 3 |
| Phase 15 — System Tray | 1 | 0 | 1 |
| Phase 15b — FocusSessionService .exe Fix | 1 | 0 | 1 |
| Phase 15c — AppBlockerScreen .exe Fix | 5 | 0 | 5 |
| Phase 15d — SettingsScreen Windows Specifics | 3 | 0 | 3 |
| Phase 15e — Translations New Strings | 3 | 0 | 3 |
| Phase 15f — DailyAllowanceTracker Normalization | 2 | 0 | 2 |
| Phase 15g — OnboardingScreen Row Titles | 4 | 0 | 4 |
| Phase 15h — AppStrings.kt, KeywordBlockerScreen, ShareDialog | 5 | 0 | 5 |
| Phase 16 — UI Updates | 11 | 0 | 11 |
| Phase 16b — Confirmed Cross-Platform | 3 | 0 | 3 |
| Phase 17 — Testing & QA | 20 | 0 | 20 |
| **TOTAL** | **143** | **3** | **140** |

---

## Decisions Log

| Date | Decision | Reason |
|---|---|---|
| June 2026 | Never delete Windows code — wrap with `when { isWindows / isLinux }` | Zero risk to Windows users; same JAR builds both platforms |
| June 2026 | xdotool for X11 foreground detection | CLI-only, no JNA .so dependency required |
| June 2026 | xbindkeys for keyboard suppression Phase 1 | Simpler than evdev JNA for initial release |
| June 2026 | evdev keyboard grab deferred to Phase 2 | Not in scope for initial release |
| June 2026 | Leave `RegistryLockdown` as no-op on Linux | No universal equivalent; NuclearMode + fullscreen is sufficient |
| June 2026 | `launcherSafeProcesses` is safety-critical — must include Xorg, gnome-shell, systemd | Killing these crashes the desktop session immediately |
| June 2026 | `FloatingBlockOverlay` works on Linux unchanged — just remove `isWindows` guard | AWT/Swing is standard Java; cross-platform |
| June 2026 | No recovery tool for Linux | Watchdog uses systemd (auto-stops on crash); no registry state to clean |
| June 2026 | Target Ubuntu 22.04 LTS and Debian 12 as primary Linux targets | Most common desktop Linux distributions |
