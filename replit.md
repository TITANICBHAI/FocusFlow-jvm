# FocusFlow

A productivity / focus management desktop application built with **Kotlin 1.9 + Compose Multiplatform Desktop**.  
It blocks distracting apps, enforces focus sessions, tracks habits, and generates weekly reports.

## Tech stack

| Layer | Technology |
|---|---|
| UI | Compose Multiplatform Desktop (Material 3) |
| Language | Kotlin 1.9 |
| Build | Gradle 8.6 (Kotlin DSL) |
| Database | SQLite via JDBC (`sqlite-jdbc`) |
| JVM | GraalVM 22.3.1 (Java 19) — see workflow |
| Tests | JUnit Jupiter 5.10.2 |

## Project structure

```
src/main/kotlin/com/focusflow/
├── Main.kt                    — entry point, DB init gate
├── App.kt                     — root Compose app, theme setup
├── data/
│   ├── Database.kt            — connection lifecycle, migrations, vacuum
│   ├── TaskDao.kt             — task CRUD extension functions on Database
│   ├── SessionDao.kt          — session CRUD + daily-stats queries
│   ├── BlockingDao.kt         — block rules, schedules, allowances, usage
│   ├── SettingsDao.kt         — getSetting / setSetting + keyword helpers
│   ├── HabitDao.kt            — habits + entries + streak calc
│   ├── ReportingDao.kt        — streak, temptation log, weekly-report queries
│   ├── models/                — data classes (Task, BlockRule, FocusSession …)
│   └── repository/            — thin object wrappers (BlockingRepository etc.)
├── enforcement/               — AppBlocker, HostsBlocker, NuclearMode, VpnBlocker …
├── services/                  — FocusSessionService, DailyAllowanceTracker, PIN …
├── ui/
│   ├── components/            — shared Composables (EmptyStateCard, overlays …)
│   ├── screens/               — one file per screen
│   └── theme/                 — Color, Typography, Theme
└── i18n/                      — LocalizationManager, string bundles
```

## Running

The workflow `Start application` handles everything:

```
export JAVA_HOME=/nix/store/c8hr2f0b0dm685yx1dkp6bw24bpx495n-graalvm19-ce-22.3.1
export PATH=$JAVA_HOME/bin:$PATH
gradle :run --no-daemon
```

Gradle cold starts take 2–5 minutes. Incremental rebuilds are faster.

## Tests

```
gradle :test --no-daemon
```

Test files live in `src/test/kotlin/com/focusflow/`:
- `ScheduleEvaluatorTest.kt` — 18 tests for `isScheduleActive()` (block schedules)
- `PinHashingTest.kt` — 19 tests for `SessionPin` / `GlobalPin` (salted hashing + migration)
- `DailyAllowanceTest.kt` — 13 tests for `DailyAllowanceTracker` (usage tracking + state isolation)

## Architecture decisions

- **All DB calls must run on `Dispatchers.IO`** — never call DAO functions from the UI thread / Compose context directly.
- **Screens use repositories, not Database directly** — import `com.focusflow.data.*` for DAO extension functions; use `BlockingRepository`, `TaskRepository`, etc. for new screen code.
- **Wildcard import required** — DAO functions are top-level extension functions in `com.focusflow.data`. Files in other packages must `import com.focusflow.data.*` (not just `import com.focusflow.data.Database`).
- **PIN storage** — stored as `saltHex:sha256Hash`; legacy plain hashes are migrated on first successful verify.

## User preferences

- Keep changes minimal and targeted — avoid rewriting working code unnecessarily.
- Always verify builds compile before marking work done.
