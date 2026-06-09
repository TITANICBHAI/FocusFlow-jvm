# FocusFlow Linux Port — Progress Tracker

> `[x]` = done | `[-]` = in progress | `[ ]` = not started
>
> **Read the plan first:** `LINUX_PORT_PLAN.md`
> **Section numbers here match section numbers in the plan exactly.**
> **Last updated:** June 2026

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
| 12.1 | Add `scanLinux()` — walks `/usr/share/applications/` and `~/.local/share/applications/` | `InstalledAppsScanner.kt` | `[ ]` |
| 12.2 | Add `parseDesktopFile()` — parses `.desktop` files into `InstalledApp` | `InstalledAppsScanner.kt` | `[ ]` |
| 12.3 | Wrap `scan()` to dispatch to `scanLinux()` | `InstalledAppsScanner.kt` | `[ ]` |
| 12.4 | Add `extractLinux()` — searches `/usr/share/icons/` and `/usr/share/pixmaps/` | `AppIconExtractor.kt` | `[ ]` |
| 12.5 | Wrap `extract()` to dispatch to `extractLinux()` | `AppIconExtractor.kt` | `[ ]` |

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

## Phase 14 — Crash Reporter (parallel with others after Phase 0)

| # | Task | File | Status |
|---|---|---|---|
| 14.1 | Wrap `emergencyRestoreWindows()` call at line ~529 in `if (isWindows)` | `CrashReporter.kt` | `[ ]` |
| 14.2 | Fix `~/Desktop` crash log path to fall back to `~/` if Desktop doesn't exist | `CrashReporter.kt` | `[ ]` |

---

## Phase 15 — System Tray (parallel with others after Phase 0)

| # | Task | File | Status |
|---|---|---|---|
| 15.1 | Ensure `SystemTray.isSupported()` is checked before `SystemTray.getSystemTray()` | `SystemTrayManager.kt` | `[ ]` |

---

## Phase 16 — UI Updates (parallel with others after Phase 0)

| # | Task | File | Status |
|---|---|---|---|
| 16.1 | Add `LINUX_SETUP` to `Screen` enum | `Models.kt` | `[ ]` |
| 16.2 | Make `WINDOWS_SETUP` nav item OS-conditional in `SideNav.kt` | `SideNav.kt` | `[ ]` |
| 16.3 | Create `LinuxSetupScreen.kt` (xdotool install, sudo setup, Wayland limitations) | `LinuxSetupScreen.kt` (new file) | `[ ]` |
| 16.4 | Add `Screen.LINUX_SETUP` routing case in `App.kt` (wherever screen routing happens) | `App.kt` | `[ ]` |
| 16.5 | Update `OsBanner.kt` — show "Linux (beta)" on Linux instead of "Windows only" | `OsBanner.kt` | `[ ]` |
| 16.6 | Update `BlockDefenseScreen.kt` — OS-conditional feature descriptions | `BlockDefenseScreen.kt` | `[ ]` |
| 16.7 | Update `VpnNetworkScreen.kt` — show "iptables rules" instead of "Windows Firewall" on Linux | `VpnNetworkScreen.kt` | `[ ]` |

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
| Phase 2 — Core Enforcement | 7 | 0 | 7 |
| Phase 3 — Keyword Blocking | 3 | 0 | 3 |
| Phase 4 — Nuclear Mode | 7 | 0 | 7 |
| Phase 5 — Launcher Safe List | 3 | 0 | 3 |
| Phase 6 — Hosts File | 5 | 0 | 5 |
| Phase 7 — Network/Firewall | 8 | 0 | 8 |
| Phase 8 — VPN Blocker | 4 | 0 | 4 |
| Phase 9 — Floating Overlay | 1 | 0 | 1 |
| Phase 10 — Kiosk Mode | 5 | 0 | 5 |
| Phase 11 — Keyboard Hook | 5 | 0 | 5 |
| Phase 12 — App Discovery | 5 | 0 | 5 |
| Phase 13 — Startup & Watchdog | 8 | 0 | 8 |
| Phase 14 — Crash Reporter | 2 | 0 | 2 |
| Phase 15 — System Tray | 1 | 0 | 1 |
| Phase 16 — UI Updates | 7 | 0 | 7 |
| Phase 17 — Testing & QA | 20 | 0 | 20 |
| **TOTAL** | **97** | **3** | **94** |

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
