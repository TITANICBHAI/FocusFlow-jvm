# FocusFlow JVM — Build Tracker

> Updated live as work progresses. Pushed to repo on every significant milestone.

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
│   ├── App.kt                    Root composable, navigation state
│   ├── ui/
│   │   ├── theme/Theme.kt        Material 3 colour scheme, typography
│   │   ├── screens/
│   │   │   ├── DashboardScreen.kt    Today overview, streak, quick start
│   │   │   ├── TasksScreen.kt        Full task CRUD, recurring tasks
│   │   │   ├── FocusScreen.kt        Active session timer + controls
│   │   │   ├── StatsScreen.kt        Streaks, session history, charts
│   │   │   └── SettingsScreen.kt     Blocking config, PIN, enforcement
│   │   └── components/
│   │       ├── TaskCard.kt
│   │       ├── TimerDisplay.kt
│   │       ├── BlockOverlay.kt       Always-on-top Compose overlay window
│   │       └── SideNav.kt
│   ├── data/
│   │   ├── Database.kt           SQLite via org.xerial:sqlite-jdbc
│   │   ├── models/Models.kt      Data classes (Task, Session, BlockRule, etc.)
│   │   ├── TaskRepository.kt
│   │   ├── SessionRepository.kt
│   │   └── SettingsRepository.kt
│   ├── enforcement/              ← THE REAL ENGINE (no Android APIs)
│   │   ├── WinApiBindings.kt     JNA bindings: GetForegroundWindow, GetWindowThreadProcessId
│   │   ├── ProcessMonitor.kt     Coroutine polls foreground window every 500ms
│   │   ├── AppBlocker.kt         Kills process + shows BlockOverlay window
│   │   └── NetworkBlocker.kt     netsh advfirewall rules (add/remove)
│   └── services/
│       ├── FocusSessionService.kt    Session timer, state machine
│       ├── TemptationLogger.kt       Ported from Android — logs every block attempt
│       └── SessionPin.kt             SHA-256 PIN (ported from Android, same logic)
├── .github/workflows/
│   └── build-windows.yml         GitHub Actions → Windows EXE + MSI artifact
└── BUILD_TRACKER.md              This file
```

---

## Enforcement Stack (what actually runs)

| Capability | Mechanism | API |
|---|---|---|
| Foreground window detection | `GetForegroundWindow()` → `GetWindowThreadProcessId()` → process name | JNA / user32.dll, psapi.dll |
| Kill blocked app | `ProcessHandle.of(pid).destroyForcibly()` (JVM 9+) | Standard JVM |
| Kill fallback | `taskkill /F /PID <pid>` via ProcessBuilder | Shell |
| Network block per-app | `netsh advfirewall firewall add rule` | Shell → Windows Firewall |
| Block overlay | Compose Desktop always-on-top window, covers full screen | Compose |
| Boot persistence | Windows Registry `HKCU\Run` via JNA advapi32 | JNA |
| CPU stay-alive during session | Thread.sleep loop in monitor coroutine | JVM |

---

## Technology Stack

| Layer | Choice | Why |
|---|---|---|
| Language | Kotlin/JVM 1.9 | JVM stdlib SHA-256, coroutines, type safety |
| UI | Compose Multiplatform Desktop 1.6 | Declarative, same paradigm as Compose Android |
| Build | Gradle 8.6 (Kotlin DSL) | Industry standard for Kotlin |
| Native interop | JNA 5.14 + jna-platform | Calls Win32 without writing any C/C++ |
| Database | org.xerial:sqlite-jdbc 3.45 | Same SQLite, same schema as web app |
| Async | kotlinx.coroutines 1.7 | Non-blocking monitor loop, UI state |
| Packaging | Compose Desktop `packageExe` / `packageMsi` (jpackage) | Native Windows installer |
| CI/CD | GitHub Actions `windows-latest` | Builds real EXE without needing Windows locally |

---

## Progress Tracker

- [x] Architecture planned and documented
- [x] BUILD_TRACKER.md written
- [x] Gradle build files (build.gradle.kts, settings.gradle.kts, wrapper)
- [x] GitHub Actions workflow (build-windows.yml)
- [x] Data layer (Database.kt, Models.kt, repositories)
- [x] Enforcement layer (WinApiBindings.kt, ProcessMonitor.kt, AppBlocker.kt, NetworkBlocker.kt)
- [x] Services (FocusSessionService.kt, TemptationLogger.kt, SessionPin.kt)
- [x] UI Theme
- [x] UI Screens (Dashboard, Tasks, Focus, Stats, Settings)
- [x] UI Components (BlockOverlay, TaskCard, TimerDisplay, SideNav)
- [x] Main entry point
- [x] GitHub repo created (FocusFlow-jvm)
- [x] Code pushed to repo
- [ ] First GitHub Actions build triggered
- [ ] EXE artifact available for download

---

## What this app CAN do (vs Android)

| Feature | Android | This JVM app |
|---|---|---|
| Real app blocking | AccService (instant) | Process kill via JNA + taskkill (500ms poll) |
| Network blocking | VPN null-routing | Windows Firewall rule via netsh |
| Overlay on blocked app | WindowManager overlay | Always-on-top Compose window |
| PIN lock | Native Kotlin, tamper-proof | SHA-256 in JVM, same security as Android |
| Temptation log | Yes | Yes (ported exactly) |
| Boot persistence | BroadcastReceiver | Registry Run key |
| Aversion: sound | Yes | Yes (Java AudioClip) |
| Aversion: vibration | Yes | No (no vibration hardware on PC) |

## What this JVM app CANNOT do vs Cold Turkey

Cold Turkey uses a Windows **kernel driver** — this JVM app does not.  
That means a determined user can:
- Relaunch the killed app before the next poll
- Open Task Manager and kill our monitor process

To reach Cold Turkey-level hardening, a future step would be a separate native Windows service (written in C/Rust) that runs at SYSTEM level and cannot be killed by regular users. The JVM app would talk to it over a named pipe.

---

## Build Instructions (local)

```bash
# Requires JDK 17+
./gradlew run                   # Run app directly
./gradlew packageExe            # Build Windows EXE (run on Windows)
./gradlew packageMsi            # Build Windows MSI installer
```

## GitHub Actions EXE

Every push to `main` triggers `build-windows.yml` on a `windows-latest` runner.
The EXE is uploaded as a workflow artifact named `FocusFlow-Windows-EXE`.
Download from: Actions tab → latest run → Artifacts.
