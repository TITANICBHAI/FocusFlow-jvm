# FocusFlow JVM

A real-enforcement productivity & focus app for Windows, built with Kotlin + Compose Multiplatform Desktop.

## Architecture

- **Language**: Kotlin 1.9.22
- **UI**: Compose Multiplatform Desktop 1.6.1 (Material 3)
- **Database**: SQLite via org.xerial:sqlite-jdbc
- **Native Interop**: JNA 5.14 (Win32 APIs — Windows-only enforcement)
- **Async**: kotlinx.coroutines-swing
- **Build**: Gradle 8.6 (Kotlin DSL)

## Project Structure

```
src/main/kotlin/com/focusflow/
├── Main.kt                     Entry point
├── App.kt                      Root composable, navigation
├── ui/
│   ├── theme/Theme.kt          Material 3 dark theme
│   ├── screens/                Dashboard, Tasks, Focus, Stats, Settings, Habits, Reports, Notes, Profile
│   └── components/             SideNav, TaskCard, BlockOverlay
├── data/
│   ├── Database.kt             SQLite via sqlite-jdbc
│   └── models/Models.kt        Data classes
├── enforcement/                Windows-only enforcement engine
│   ├── WinApiBindings.kt       JNA Win32 bindings
│   ├── ProcessMonitor.kt       Dual-mode: WinEventHook + 500ms polling
│   ├── AppBlocker.kt           Kill + overlay bridge
│   ├── NetworkBlocker.kt       netsh advfirewall rules
│   ├── NuclearMode.kt          Nuclear blocking mode
│   ├── WinEventHook.kt         Instant foreground event detection
│   └── InstalledAppsScanner.kt Scan installed apps
└── services/
    ├── FocusSessionService.kt  Session state machine
    ├── TemptationLogger.kt     Block attempt logging
    ├── SessionPin.kt           SHA-256 PIN gate
    ├── SoundAversion.kt        Sound alerts on block
    ├── SystemTrayManager.kt    System tray integration
    ├── NotificationService.kt  Windows notifications
    ├── TaskAlarmService.kt     Task scheduling alarms
    ├── RecurringTaskService.kt Auto-generate recurring tasks
    ├── BlockScheduleService.kt Time-window blocking
    ├── StandaloneBlockService.kt Timed standalone block
    ├── DailyAllowanceTracker.kt Per-app usage caps
    ├── WeeklyReportService.kt  Weekly focus report
    ├── BreakEnforcer.kt        Break enforcement
    ├── FocusInsightsService.kt Focus analytics
    └── BackupService.kt        Database backup
```

## Replit Environment Setup

### Java / Gradle
- **Java**: Zulu JDK 17.0.12 from Nix store
  - Path: `/nix/store/7h8xkvyyz4sgxm61rj1s64ncml582qyv-zulu-ca-jdk-17.0.12`
- **Gradle**: System Gradle 8.14.2 (installed via Nix)
- **Gradle Wrapper**: `./gradlew` uses Gradle 8.6 (downloaded on first run)
- **gradle.properties**: Configures toolchain to use Zulu JDK 17

### Key env vars (set in workflow command)
```bash
export JAVA_HOME=/nix/store/7h8xkvyyz4sgxm61rj1s64ncml582qyv-zulu-ca-jdk-17.0.12
export PATH=$JAVA_HOME/bin:$PATH
```

### Workflow
- **Name**: Start application
- **Type**: VNC (desktop GUI app)
- **Command**: `gradle run --no-daemon` (with JAVA_HOME set)

## Platform Notes

- **UI**: Cross-platform — Compose Desktop renders on Linux/Mac/Windows
- **Enforcement**: Windows-only — JNA calls to Win32 APIs are no-ops on Linux
- **Packaging**: Windows EXE/MSI only via GitHub Actions (`windows-latest`)
- **Database**: SQLite at `~/.focusflow/focusflow.db`

## Building

```bash
# Run the app (UI works cross-platform)
export JAVA_HOME=/nix/store/7h8xkvyyz4sgxm61rj1s64ncml582qyv-zulu-ca-jdk-17.0.12
gradle run --no-daemon

# Build Windows EXE (use GitHub Actions — requires Windows)
# See .github/workflows/build-windows.yml
```

## CI/CD

GitHub Actions at `.github/workflows/build-windows.yml`:
- Runs on `windows-latest`
- Produces EXE + MSI + MSIX artifacts
- Auto-creates GitHub Release on push to `main`
