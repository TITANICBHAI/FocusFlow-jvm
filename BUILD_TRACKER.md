# FocusFlow JVM — Build Tracker

> Updated live as work progresses. Pushed to repo on every milestone.
> Last updated: 2026-05-02

---

## Project Goal

Replace the Electron/web PC app with a real Kotlin/JVM desktop app using **Compose Multiplatform for Desktop**. This app has actual Win32 enforcement (process monitor, firewall rules) — not just stored settings.

---

## Architecture

```
focusflow-jvm/
├── build.gradle.kts              Gradle build (Compose Desktop + JNA + SQLite)
├── settings.gradle.kts           Project name
├── gradle/wrapper/               Gradle wrapper (8.6)
├── src/main/kotlin/com/focusflow/
│   ├── Main.kt                   Entry point — launches Compose window
│   ├── App.kt                    Root composable, navigation state, overlay wiring
│   ├── ui/
│   │   ├── theme/Theme.kt        Material 3 dark colour scheme, typography
│   │   ├── screens/
│   │   │   ├── DashboardScreen.kt    Today overview, streak, quick start
│   │   │   ├── TasksScreen.kt        Full task CRUD, recurring tasks, add dialog
│   │   │   ├── FocusScreen.kt        Active session timer + controls + attempt counter
│   │   │   ├── StatsScreen.kt        Streaks, session history, temptation log chart
│   │   │   └── SettingsScreen.kt     Blocking config, PIN, enforcement toggle
│   │   └── components/
│   │       ├── TaskCard.kt           Priority-coloured card, start/complete/delete actions
│   │       ├── BlockOverlay.kt       Full-screen animated block overlay composable
│   │       └── SideNav.kt            Left sidebar navigation
│   ├── data/
│   │   ├── Database.kt           SQLite via org.xerial:sqlite-jdbc (same schema as web app)
│   │   ├── models/Models.kt      Data classes (Task, FocusSession, BlockRule, etc.)
│   │   ├── TaskRepository.kt     (inline in Database.kt)
│   │   └── SettingsRepository.kt (inline in Database.kt)
│   ├── enforcement/              ← THE REAL ENGINE
│   │   ├── WinApiBindings.kt     JNA: GetForegroundWindow + getForegroundProcessName()
│   │   ├── ProcessMonitor.kt     500ms coroutine polling loop — kills blocked apps
│   │   ├── AppBlocker.kt         Callback bridge: fires BlockOverlay in Compose UI
│   │   └── NetworkBlocker.kt     netsh advfirewall + PowerShell New-NetFirewallRule
│   └── services/
│       ├── FocusSessionService.kt    Session state machine + coroutine countdown timer
│       ├── TemptationLogger.kt       In-session attempt log (ported from Android)
│       └── SessionPin.kt             SHA-256 PIN gate (ported from Android, zero Android APIs)
├── .github/workflows/
│   └── build-windows.yml         GitHub Actions → Windows EXE + MSI → GitHub Release
└── BUILD_TRACKER.md              This file
```

---

## Enforcement Stack

| Capability | Mechanism | Notes |
|---|---|---|
| Foreground window detection | `GetForegroundWindow()` via JNA → `ProcessHandle.info().command()` | Polls every 500ms |
| Kill blocked app | `ProcessHandle.destroyForcibly()` (JVM 9+) | Instant |
| Kill fallback | `taskkill /F /IM processname.exe` via `ProcessBuilder` | Catches permission-denied cases |
| Network block per-app | `New-NetFirewallRule` via PowerShell | Requires admin |
| Block overlay | Compose always-on-top animated composable | Shows 4s then auto-dismisses |
| Boot persistence | Windows Registry `HKCU\Run` (planned) | Not yet implemented |

---

## Technology Stack

| Layer | Choice | Version |
|---|---|---|
| Language | Kotlin/JVM | 1.9.22 |
| UI | Compose Multiplatform Desktop | 1.6.1 |
| Build | Gradle (Kotlin DSL) | 8.6 |
| Native interop | JNA + jna-platform | 5.14.0 |
| Database | org.xerial:sqlite-jdbc | 3.45.1.0 |
| Async | kotlinx.coroutines-swing | 1.7.3 |
| Packaging | jpackage (via Compose Desktop plugin) | bundled JRE |
| CI/CD | GitHub Actions `windows-latest` | build-windows.yml |

---

## Progress Tracker

- [x] Architecture planned and documented
- [x] BUILD_TRACKER.md written
- [x] Gradle build files (build.gradle.kts, settings.gradle.kts, wrapper)
- [x] GitHub Actions workflow (build-windows.yml)
- [x] Data layer (Database.kt, Models.kt — SQLite, same schema as web app)
- [x] Enforcement layer (WinApiBindings.kt, ProcessMonitor.kt, AppBlocker.kt, NetworkBlocker.kt)
- [x] Services (FocusSessionService.kt, TemptationLogger.kt, SessionPin.kt)
- [x] UI Theme (Material 3 dark purple)
- [x] UI Screens (Dashboard, Tasks, Focus, Stats, Settings — all functional)
- [x] UI Components (BlockOverlay, TaskCard, SideNav)
- [x] Main entry point (Main.kt, App.kt)
- [x] GitHub repo created: https://github.com/TITANICBHAI/FocusFlow-jvm
- [x] All 27 files pushed to repo
- [x] **GitHub Actions build-windows job: ✅ SUCCESS**
- [x] **EXE artifact built and uploaded: 108,670,635 bytes**
- [x] **MSI artifact built and uploaded: 108,412,109 bytes**
- [x] Release job: fix pushed (tag creation + softprops/action-gh-release)
- [ ] GitHub Release with EXE attached (in progress after fix)

---

## Download EXE Right Now

Go to: **https://github.com/TITANICBHAI/FocusFlow-jvm/actions**

Click the latest run → **Artifacts** → `FocusFlow-Windows-EXE` (108 MB zip containing the `.exe`)

Or wait for the release job fix to create a proper GitHub Release with the EXE attached.

---

## File Count & Lines of Code

- **27 files** pushed to GitHub
- **~2,500 lines** of Kotlin source code
- **8 source modules** (data, enforcement, services, ui/screens, ui/components, ui/theme, App, Main)

---

## What this app CAN do (real enforcement)

| Feature | Android version | This JVM version |
|---|---|---|
| Real app blocking | AccService (instant) | JNA process kill (500ms poll) |
| Network blocking | VPN null-routing | Windows Firewall via netsh (admin required) |
| Block overlay | WindowManager over blocked app | Compose overlay inside our own window |
| Session PIN | Kotlin, tamper-proof | SHA-256 in JVM (same algorithm) |
| Temptation log | Yes | Yes (same logic ported) |
| Weekly report | AlarmManager | Planned (Java Timer) |
| Boot persistence | BroadcastReceiver | Planned (Registry Run key) |
| Sound aversion | Yes | Planned (Java AudioClip) |

## Hard ceiling (JVM cannot do)

- **Kernel-level blocking** (Cold Turkey level) requires a Windows kernel driver — not possible in JVM
- **Instant block** requires Windows event hook (`SetWinEventHook`) — doable via JNA but not yet implemented (uses polling instead)
- **Tamper-proof process** — a user with admin rights can kill our JVM process (unlike a kernel driver)

---

## Build Instructions (local)

```bash
# Requires JDK 17+
# Run the app (cross-platform UI, enforcement is Windows-only)
gradle run

# Build Windows EXE (only works on Windows, or via GitHub Actions)
gradle packageExe

# Build Windows MSI
gradle packageMsi
```
