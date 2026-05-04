# FocusFlow JVM — by TBTechs

A real-enforcement productivity & focus app for Windows, built with Kotlin + Compose Multiplatform Desktop.

## Architecture

- **Language**: Kotlin 1.9.22
- **UI**: Compose Multiplatform Desktop 1.6.1 (Material 3)
- **Database**: SQLite via org.xerial:sqlite-jdbc
- **Native Interop**: JNA 5.14 (Win32 APIs — Windows-only enforcement)
- **Async**: kotlinx.coroutines-swing
- **Build**: Gradle 8.14.2 (Kotlin DSL)

## Theme Variables

All UI colours come from `com.focusflow.ui.theme.*`:
`Purple80`, `Purple60`, `PurpleGrey`, `Surface`, `Surface2`, `Surface3`,
`OnSurface`, `OnSurface2`, `Success`, `Warning`, `Error`

**Never use**: `Primary`, `OnSurfaceVariant` — these don't exist in our theme.

## Project Structure

```
src/main/kotlin/com/focusflow/
├── Main.kt                          Entry point; wires all services + tray
├── App.kt                           Root composable; onboarding check + nav
├── ui/
│   ├── theme/Theme.kt               Material 3 dark theme
│   ├── screens/
│   │   ├── DashboardScreen.kt
│   │   ├── TasksScreen.kt
│   │   ├── FocusScreen.kt
│   │   ├── StatsScreen.kt
│   │   ├── SettingsScreen.kt        Includes Privacy & Permissions section
│   │   ├── HabitsScreen.kt
│   │   ├── ReportsScreen.kt
│   │   ├── DailyNotesScreen.kt
│   │   ├── ProfileScreen.kt
│   │   └── PrivacyPermissionsScreen.kt  Privacy Policy / EULA / Permissions dialog
│   └── components/
│       ├── SideNav.kt
│       ├── TaskCard.kt
│       ├── BlockOverlay.kt
│       ├── AppLogo.kt
│       ├── EmptyStateCard.kt
│       ├── ScrollUtils.kt
│       ├── OsBanner.kt              Non-Windows warning + admin privilege detection
│       └── OnboardingScreen.kt      6-step first-run wizard (onboarding_complete flag)
├── data/
│   ├── Database.kt                  SQLite via sqlite-jdbc
│   └── models/Models.kt             Data classes
├── enforcement/                     Windows-only enforcement engine
│   ├── WinApiBindings.kt            JNA Win32 bindings
│   ├── ProcessMonitor.kt            Dual-mode: WinEventHook + 500ms polling
│   ├── AppBlocker.kt                Kill + overlay bridge
│   ├── NetworkBlocker.kt            netsh advfirewall rules
│   ├── NuclearMode.kt               Nuclear blocking mode
│   ├── WinEventHook.kt              Instant foreground event detection
│   └── InstalledAppsScanner.kt      Scan installed apps
└── services/
    ├── FocusSessionService.kt       Session state machine
    ├── TemptationLogger.kt          Block attempt logging
    ├── SessionPin.kt                SHA-256 PIN gate
    ├── SoundAversion.kt             Sound alerts on block
    ├── SystemTrayManager.kt         System tray integration
    ├── NotificationService.kt       Windows notifications
    ├── TaskAlarmService.kt          Task scheduling alarms
    ├── RecurringTaskService.kt      Auto-generate recurring tasks
    ├── BlockScheduleService.kt      Time-window blocking
    ├── StandaloneBlockService.kt    Timed standalone block
    ├── DailyAllowanceTracker.kt     Per-app usage caps
    ├── WeeklyReportService.kt       Weekly focus report
    ├── BreakEnforcer.kt             Break enforcement
    ├── FocusInsightsService.kt      Focus analytics
    ├── BackupService.kt             Database backup (legacy)
    ├── AutoBackupService.kt         Daily rolling backup (7 copies) — wired into Main.kt
    ├── HostsBlocker.kt              Windows hosts-file domain blocker + DNS flush
    └── PrivacyPolicyService.kt      Privacy Policy, EULA, permissions manifest text
```

## Replit Environment Setup

### Java / Gradle
- **Java**: GraalVM CE 19 (Java 19)
  - Path: `/nix/store/c8hr2f0b0dm685yx1dkp6bw24bpx495n-graalvm19-ce-22.3.1`
- **Gradle**: System Gradle 8.14.2 (installed via Nix)
- **gradle.properties**: Configures toolchain to use GraalVM Java 19

### Key env vars (set in workflow command)
```bash
export JAVA_HOME=/nix/store/c8hr2f0b0dm685yx1dkp6bw24bpx495n-graalvm19-ce-22.3.1
export PATH=$JAVA_HOME/bin:$PATH
```

### Workflow
- **Name**: Start application
- **Type**: VNC (desktop GUI app)
- **Command**: `gradle run --no-daemon` (with JAVA_HOME set)

## Platform Notes

- **UI**: Cross-platform — Compose Desktop renders on Linux/Mac/Windows
- **Enforcement**: Windows-only — JNA calls to Win32 APIs are no-ops on Linux
- **Packaging**: Windows EXE/MSI via GitHub Actions (`windows-latest`); MSIX built manually in CI
- **Database**: SQLite at `~/.focusflow/focusflow.db`

## Building

```bash
# Run the app (UI works cross-platform)
export JAVA_HOME=/nix/store/c8hr2f0b0dm685yx1dkp6bw24bpx495n-graalvm19-ce-22.3.1
gradle run --no-daemon

# Build Windows EXE/MSI (requires Windows — use GitHub Actions)
# See .github/workflows/build-windows.yml
```

## CI/CD

GitHub Actions at `.github/workflows/build-windows.yml`:
- Runs on `windows-latest`
- Produces EXE + MSI (native distribution) + MSIX (built via MakeAppx.exe) artifacts
- Auto-creates a GitHub Release on every push to `main`

## Pushing to GitHub

Run from the Replit **Shell** tab (the platform auto-commits; this just pushes):

```bash
bash push_to_github.sh
```

Requires `GITHUB_PERSONAL_ACCESS_TOKEN` Replit Secret (already set).
After push, watch CI at: https://github.com/TITANICBHAI/FocusFlow-jvm/actions
