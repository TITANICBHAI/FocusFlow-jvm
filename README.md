# FocusFlow JVM

A real-enforcement productivity & focus app for Windows, built with **Kotlin + Compose Multiplatform Desktop**.

Unlike the Electron version, this app **actually blocks apps** — it kills blocked processes using Win32 APIs via JNA and can add Windows Firewall rules to cut network access.

---

## Download

Go to [Actions](../../actions) → latest build → **Artifacts** → `FocusFlow-Windows-EXE` or `FocusFlow-Windows-MSI`.

Every push to `main` automatically builds a Windows EXE via GitHub Actions.

---

## Features

| Feature | Status | How |
|---|---|---|
| Task scheduling | ✅ | SQLite |
| Focus session timer | ✅ | Coroutine countdown |
| **App blocking** | ✅ Real | JNA `GetForegroundWindow` + `ProcessHandle.destroyForcibly()` |
| **Network blocking** | ✅ Real (admin required) | `netsh advfirewall` + PowerShell `New-NetFirewallRule` |
| **Block overlay** | ✅ Real | Always-on-top Compose window |
| **Session PIN** | ✅ Real | SHA-256, same algorithm as Android version |
| Temptation log | ✅ | Logs every block attempt, weekly summary |
| Always-on enforcement | ✅ | Monitor runs 24/7 when enabled |
| Streak tracking | ✅ | SQLite daily completions |
| Stats & charts | ✅ | Session history, temptation stats |
| Dark mode | ✅ | Material 3 dark colour scheme |
| Recurring tasks | ✅ | Daily / weekday / weekly / monthly |

---

## Enforcement Details

**Process Monitor** polls `GetForegroundWindow()` every **500ms** (vs Android's event-driven instant response). When a blocked app is detected:

1. `ProcessHandle.of(pid).destroyForcibly()` — JVM 9+ process kill
2. Fallback: `taskkill /F /IM processname.exe` via `ProcessBuilder`
3. Block overlay window appears with motivational message
4. Block attempt logged to SQLite temptation log
5. If "block network" is enabled for this app: `New-NetFirewallRule` via PowerShell

**Limitation vs Android:** The Android app uses `AccessibilityService` which fires instantly on window events. This JVM app polls every 500ms. A user could potentially open and close a blocked app within 500ms. Cold Turkey-level hardening would require a Windows kernel driver (not possible in JVM).

---

## Build Locally

Requires JDK 17+. On Windows:

```bash
./gradlew run          # Run the app
./gradlew packageExe   # Build standalone EXE (~150MB with embedded JRE)
./gradlew packageMsi   # Build MSI installer
```

On Linux/Mac (cross-compile is not supported by jpackage — use GitHub Actions for Windows EXE):

```bash
./gradlew run          # Run (UI works cross-platform, enforcement is Windows-only)
```

---

## Tech Stack

- **Kotlin 1.9** + **Compose Multiplatform Desktop 1.6**
- **JNA 5.14** — Win32 API bindings (no C/C++ required)
- **org.xerial:sqlite-jdbc 3.45** — SQLite database
- **kotlinx.coroutines 1.7** — async monitor loop, UI state
- **Gradle 8.6** — build system
- **jpackage** (via Compose Desktop plugin) — native Windows EXE/MSI

---

## Project Structure

```
src/main/kotlin/com/focusflow/
├── Main.kt                     Entry point
├── App.kt                      Root composable, navigation
├── ui/
│   ├── theme/Theme.kt          Material 3 dark theme
│   ├── screens/                Dashboard, Tasks, Focus, Stats, Settings
│   └── components/             SideNav, TaskCard, BlockOverlay
├── data/
│   ├── Database.kt             SQLite via sqlite-jdbc
│   └── models/Models.kt        Data classes
├── enforcement/
│   ├── WinApiBindings.kt       JNA Win32 bindings
│   ├── ProcessMonitor.kt       500ms polling loop
│   ├── AppBlocker.kt           Kill + overlay
│   └── NetworkBlocker.kt       netsh firewall rules
└── services/
    ├── FocusSessionService.kt  Session state machine
    ├── TemptationLogger.kt     Block attempt logging
    └── SessionPin.kt           SHA-256 PIN (ported from Android)
```
