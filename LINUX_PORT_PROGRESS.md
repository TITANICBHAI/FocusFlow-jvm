# FocusFlow Linux Port — Progress Tracker

> Track every task here as it gets completed.
> Format: `[x]` = done, `[-]` = in progress, `[ ]` = not started
> 
> **Reference document:** `LINUX_PORT_PLAN.md` — read that first for full context.
> **Last updated:** June 2026

---

## Phase 0 — Foundations

| # | Task | Status | Notes |
|---|---|---|---|
| 0.1 | Read and understand full codebase | `[x]` | Done — see agent memory |
| 0.2 | Create `LINUX_PORT_PLAN.md` | `[x]` | This document's companion |
| 0.3 | Create `LINUX_PORT_PROGRESS.md` | `[x]` | This file |
| 0.4 | Add `isLinux`, `isWayland`, `isX11`, `hasXdotool` to `WinApiBindings.kt` | `[ ]` | Step 3 in plan |

---

## Phase 1 — Build System

| # | Task | Status | Notes |
|---|---|---|---|
| 1.1 | Add Linux `.deb` config to `build.gradle.kts` | `[ ]` | Step 2a in plan |
| 1.2 | Create/source 512×512 PNG icon | `[ ]` | Needed for `.deb` packaging |
| 1.3 | Create `.github/workflows/build-linux.yml` | `[ ]` | Step 17 in plan |

---

## Phase 2 — Core Enforcement (Highest Priority)

| # | Task | Status | Notes |
|---|---|---|---|
| 2.1 | Add Linux foreground poller to `WinEventHook.kt` | `[ ]` | Step 4b in plan — xdotool based |
| 2.2 | Add `getLinuxForegroundProcess()` to `WinEventHook.kt` | `[ ]` | Step 4b |
| 2.3 | Update `ProcessMonitor.start()` to enable hook on Linux | `[ ]` | Step 4c |
| 2.4 | Remove `if (!isWindows) return` from `ProcessMonitor.tickPoll()` | `[ ]` | Step 4c |
| 2.5 | Add Linux branch to `getForegroundProcessNameAndPid()` | `[ ]` | Step 4d |
| 2.6 | Verify process killing works on Linux (no code change needed) | `[ ]` | Step 5 — test only |

---

## Phase 3 — Nuclear Mode

| # | Task | Status | Notes |
|---|---|---|---|
| 3.1 | Add `linuxEscapeProcesses` set to `NuclearMode.kt` | `[ ]` | Step 6 |
| 3.2 | Add `linuxEscapePathSuffixes` set | `[ ]` | Step 6 |
| 3.3 | Switch `escapeProcesses` to `when { isWindows/isLinux }` | `[ ]` | Step 6 |
| 3.4 | Verify `killAndLog()` works cross-platform | `[ ]` | Uses `killProcessByName()` which already works |

---

## Phase 4 — Website Blocking

| # | Task | Status | Notes |
|---|---|---|---|
| 4.1 | Fix hardcoded `HOSTS_PATH` to be cross-platform | `[ ]` | Step 7 — Change 1 |
| 4.2 | Remove `isWindows` guards from `HostsBlocker` public methods | `[ ]` | Step 7 — Change 2 |
| 4.3 | Add Linux DNS cache flush methods | `[ ]` | Step 7 — Change 3 |
| 4.4 | Fix `canWriteHostsFile()` to work on Linux | `[ ]` | Step 7 — Change 4 |

---

## Phase 5 — Network / Firewall Blocking

| # | Task | Status | Notes |
|---|---|---|---|
| 5.1 | Add `isRunningAsAdmin()` Linux branch (`sudo -n true`) | `[ ]` | Step 8 |
| 5.2 | Add `addRuleLinux()` via iptables | `[ ]` | Step 8 |
| 5.3 | Add `resolveExePathLinux()` | `[ ]` | Step 8 |
| 5.4 | Add `removeRuleLinux()` | `[ ]` | Step 8 |
| 5.5 | Add `syncFromFirewallLinux()` | `[ ]` | Step 8 |

---

## Phase 6 — Kiosk Mode

| # | Task | Status | Notes |
|---|---|---|---|
| 6.1 | Add `hideTaskbarLinux()` via wmctrl/EWMH | `[ ]` | Step 9 |
| 6.2 | Add `showTaskbarLinux()` | `[ ]` | Step 9 |
| 6.3 | Verify fullscreen behaviour uses Compose `WindowPlacement.Fullscreen` (already cross-platform) | `[ ]` | Should work without changes |

---

## Phase 7 — Keyboard Hook

| # | Task | Status | Notes |
|---|---|---|---|
| 7.1 | Add `enableLinux()` Phase 1 (xbindkeys) | `[ ]` | Step 10 |
| 7.2 | Add `disableLinux()` | `[ ]` | Step 10 |
| 7.3 | Update `isActive` to check Linux state | `[ ]` | Step 10 |
| 7.4 | (Future) Phase 2 evdev keyboard grab | `[ ]` | Not in scope for initial release |

---

## Phase 8 — App Discovery

| # | Task | Status | Notes |
|---|---|---|---|
| 8.1 | Add `scanLinux()` to `InstalledAppsScanner.kt` | `[ ]` | Step 11 |
| 8.2 | Add `parseDesktopFile()` parser | `[ ]` | Step 11 |
| 8.3 | Add `extractLinux()` to `AppIconExtractor.kt` | `[ ]` | Step 12 |

---

## Phase 9 — Persistence & Watchdog

| # | Task | Status | Notes |
|---|---|---|---|
| 9.1 | Add `enableLinux()` / `disableLinux()` to `WindowsStartupManager.kt` | `[ ]` | Step 13 — XDG autostart |
| 9.2 | Add `installLinux()` / `uninstallLinux()` to `WatchdogInstaller.kt` | `[ ]` | Step 14 |
| 9.3 | Add systemd user service watchdog | `[ ]` | Step 14 |
| 9.4 | Add cron fallback watchdog | `[ ]` | Step 14 |

---

## Phase 10 — UI Updates

| # | Task | Status | Notes |
|---|---|---|---|
| 10.1 | Update `OsBanner.kt` to show "Linux (beta)" instead of Windows-only banner | `[ ]` | Step 16 |
| 10.2 | Update `BlockDefenseScreen.kt` descriptions for Linux | `[ ]` | Step 16 |
| 10.3 | Update `WindowsSetupScreen.kt` — add Linux setup instructions tab | `[ ]` | Step 16 |
| 10.4 | Update `VpnNetworkScreen.kt` — iptables mention on Linux | `[ ]` | Step 16 |

---

## Phase 11 — Testing & QA

| # | Task | Status | Notes |
|---|---|---|---|
| 11.1 | Test foreground detection on Ubuntu 22.04 X11 | `[ ]` | |
| 11.2 | Test foreground detection on Ubuntu 22.04 Wayland | `[ ]` | |
| 11.3 | Test process killing on Linux | `[ ]` | |
| 11.4 | Test hosts file blocking on Linux | `[ ]` | |
| 11.5 | Test nuclear mode on Linux | `[ ]` | |
| 11.6 | Test kiosk mode fullscreen on Linux | `[ ]` | |
| 11.7 | Test startup persistence on Linux | `[ ]` | |
| 11.8 | Test watchdog relaunch on Linux | `[ ]` | |
| 11.9 | Build and install `.deb` on Ubuntu 22.04 | `[ ]` | |
| 11.10 | Build and install `.deb` on Debian 12 | `[ ]` | |
| 11.11 | Confirm Windows build still works (regression check) | `[ ]` | |

---

## Summary

| Phase | Total Tasks | Done | Remaining |
|---|---|---|---|
| Phase 0 — Foundations | 4 | 3 | 1 |
| Phase 1 — Build System | 3 | 0 | 3 |
| Phase 2 — Core Enforcement | 6 | 0 | 6 |
| Phase 3 — Nuclear Mode | 4 | 0 | 4 |
| Phase 4 — Website Blocking | 4 | 0 | 4 |
| Phase 5 — Network/Firewall | 5 | 0 | 5 |
| Phase 6 — Kiosk Mode | 3 | 0 | 3 |
| Phase 7 — Keyboard Hook | 4 | 0 | 4 |
| Phase 8 — App Discovery | 3 | 0 | 3 |
| Phase 9 — Persistence & Watchdog | 4 | 0 | 4 |
| Phase 10 — UI Updates | 4 | 0 | 4 |
| Phase 11 — Testing & QA | 11 | 0 | 11 |
| **TOTAL** | **55** | **3** | **52** |

---

## Notes & Decisions Log

| Date | Decision | Reason |
|---|---|---|
| June 2026 | Keep all Windows code intact, add Linux as parallel branches | Zero risk to existing Windows users; same `.jar` builds both platforms |
| June 2026 | Use `xdotool` for X11 foreground detection (Phase 1) | Available on all major distros, no JNA binding required |
| June 2026 | Use `xbindkeys` for keyboard suppression (Phase 1) | xbindkeys is simpler than evdev JNA for initial release |
| June 2026 | Leave `RegistryLockdown` as no-op on Linux | No universal Linux equivalent; NuclearMode + fullscreen provides sufficient enforcement |
| June 2026 | Target Ubuntu 22.04 LTS and Debian 12 as primary Linux targets | Most common desktop Linux distributions |
