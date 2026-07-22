# Threat Model — FocusFlow Desktop

## Project Overview

FocusFlow is a Windows-only desktop productivity app built with Kotlin + Compose Multiplatform. It enforces focus sessions by blocking apps and websites, killing escape processes, modifying the Windows Firewall, editing the system hosts file, and writing to the HKLM registry. Users can configure PIN gates (SHA-256 hashed) to prevent themselves from bypassing enforcement during active sessions. The app runs entirely locally — no server, no network API, no user accounts. Data is stored in a local SQLite database. The primary threat model concerns a single-user local environment where the user is also the adversary (self-control software).

## Assets

- **PIN hashes** — SHA-256 digests stored in the local SQLite DB and in memory. Compromise allows a user to bypass session PIN gates, Nuclear Mode disablement, or break access. No remote value; the threat is local circumvention.
- **Local SQLite database** — focus sessions, habits, tasks, daily usage, block schedules, settings. No PII beyond what the user themselves typed. Loss or corruption degrades the app's blocking and reporting features.
- **Windows Firewall rules** — written by NetworkBlocker via `netsh advfirewall` / PowerShell `New-NetFirewallRule`. An attacker (or the user) who can write firewall rules can unblock blocked apps or block arbitrary traffic.
- **System hosts file** — `C:\Windows\System32\drivers\etc\hosts`. HostsBlocker appends domain-block entries. Tampering allows bypassing website blocking.
- **HKLM registry entries** — RegistryLockdown writes to `HKLM\SOFTWARE\Policies\...` to enforce system policy. Corrupt entries could lock system features persistently after the app is removed.
- **Watchdog process** — WatchdogInstaller installs a helper process. If replaced or hijacked, it could be used to evade enforcement or run arbitrary code at startup.
- **Telemetry / crash data** — CrashReporter and ResourceMonitorService previously sent anonymous telemetry via webhooks (removed in audit). If re-introduced, any PII leaking into crash payloads becomes an asset.

## Trust Boundaries

- **User process ↔ OS kernel** — FocusFlow requires elevated (admin) privileges for firewall, hosts file, and registry writes. Everything it does to the OS is trusted by Windows.
- **User ↔ FocusFlow enforcement** — the app is explicitly designed to enforce rules *against the user's in-session impulses*. The PIN system is the enforcement boundary; the user is both the operator and the adversary.
- **FocusFlow process ↔ local SQLite** — no authentication, no encryption. Anything with local filesystem access can read/modify the database.
- **FocusFlow process ↔ external executables** — ProcessMonitor, NetworkBlocker, and NuclearMode shell out to `tasklist`, `taskkill`, `netsh`, and `powershell`. Arguments are constructed from internal data (not user-supplied strings), but injection is possible if process names from the DB reach these commands unsanitized.
- **Watchdog ↔ main app** — separate process; trust established by filesystem path. No cryptographic verification.

## Scan Anchors

**Production entry points:** `src/main/kotlin/com/focusflow/Main.kt`, `src/main/kotlin/com/focusflow/App.kt`

**Highest-risk code areas:**
- `enforcement/` — NuclearMode, ProcessMonitor, NetworkBlocker, RegistryLockdown, WatchdogInstaller
- `services/` — SessionPin, GlobalPin, NuclearModePin, HostsBlocker, FocusLauncherService
- `data/Database.kt`, `data/BlockingDao.kt`, `data/SessionDao.kt`

**Public / authenticated surfaces:** all enforcement is local; PIN gates (`SessionPin`, `GlobalPin`, `NuclearModePin`) are the only auth layer.

**Dev-only:** `artifacts/mockup-sandbox/` — Vite dev server used for UI preview only; not shipped.

## Threat Categories

### Spoofing

FocusFlow has no network identity — no logins, no tokens, no server-side auth. The relevant spoofing threat is **PIN bypass**: an attacker (or the user circumventing their own session) presents a PIN without knowing the real one.

All three PIN services (SessionPin, GlobalPin, NuclearModePin) hash with SHA-256 and compare against the stored digest. SHA-256 is not suitable for password storage (no salt, no iteration); however, since the PIN is user-chosen and the database is local, the realistic threat is the user simply reading the hash from the SQLite file and brute-forcing a short PIN offline. **Required guarantee:** enforce a minimum PIN length of ≥ 6 digits in the UI; consider adding a per-attempt delay after failed PIN checks to slow local brute-force.

### Tampering

- **Database tampering:** The SQLite file is writable by any process running as the same user. A determined user could edit their block schedule, allowances, or session records directly. FocusFlow cannot fully prevent this; it is acceptable for self-control software (the user is only defeating themselves). **Required guarantee:** do not rely on database integrity for security-critical decisions (e.g., Nuclear Mode state should come from an in-memory flag, not solely the DB).
- **Shell injection via process names:** `killAndLog()` in NuclearMode and `killProcessByName()` in WinApiBindings pass process names to `taskkill /F /IM <name>`. If a process name originates from user input (block list, allowance list), a crafted name could inject additional `taskkill` arguments. **Required guarantee:** validate that all process names passed to shell commands match `^[a-zA-Z0-9_\-\.]+\.exe$` before constructing command arguments.
- **Hosts file integrity:** HostsBlocker writes domain entries. If the app crashes mid-write, the hosts file could be left in a corrupt state. **Required guarantee:** write to a temp file and atomically rename, or validate the hosts file on startup.

### Repudiation

FocusFlow writes an `EnforcementLog` and persists escape attempt counts to the DB. Since all data is local and the user controls the machine, repudiation of their own actions is inherent. No multi-party audit requirement exists. **No action required** beyond keeping the existing enforcement log.

### Information Disclosure

- **Webhook telemetry:** Telemetry via Discord webhooks was previously hardcoded and has been removed. If re-introduced, any crash payload that includes session task names, habit names, or notes would constitute PII disclosure. **Required guarantee:** telemetry payloads must be reviewed for PII before any webhook/remote logging is re-enabled; user opt-in consent must be obtained.
- **PIN hash in plaintext DB:** SHA-256 digests are stored in the SQLite file without encryption. An attacker with local filesystem access can read them. Since the database is on the user's own machine, this is accepted risk, but **required guarantee:** the app must never log PIN values or hashes to the EnforcementLog, crash reports, or any telemetry channel.
- **Error messages:** Exception swallowing with `catch (_: Exception) {}` is prevalent. This prevents leaking internals but also hides errors from the user. Acceptable for enforcement loops; not acceptable for user-visible flows.

### Denial of Service

- **NuclearMode enforcement loop saturation:** NuclearMode polls every 500 ms. If `tasklist` hangs (e.g., antivirus blocking it), the loop blocks on `proc.waitFor()` without a timeout. This can stall the enforcement coroutine. **Required guarantee:** add a timeout to `proc.waitFor()` in `getRunningEscapeProcesses()` (e.g., 2 000 ms) and kill the process on timeout.
- **Hosts file size growth:** HostsBlocker appends entries without deduplication. Long-running use with many domains could grow the hosts file substantially. **Required guarantee:** deduplicate entries on write; remove all FocusFlow entries on uninstall.
- **Database connection exhaustion:** `Database` is a singleton with a single `Connection`. All DAO calls are `synchronized(this)`. Heavy concurrent IO (multiple services flushing simultaneously) serializes correctly but could create latency spikes. Acceptable for current scale.

### Elevation of Privilege

- **Admin requirement:** FocusFlow requests admin elevation at launch for firewall/registry/hosts access. This is by design. The risk is that a compromised FocusFlow binary (e.g., via a supply-chain attack on a dependency) would run with admin privileges. **Required guarantee:** verify installer signatures; keep the dependency tree minimal and pinned.
- **Watchdog process:** WatchdogInstaller registers a watchdog that relaunches FocusFlow. If the watchdog binary path is writable by a non-admin process, an attacker can replace it with arbitrary code that runs at the next watchdog invocation (potentially with elevated privileges). **Required guarantee:** the watchdog binary and its parent directory must be installed to a path writable only by administrators (e.g., `%ProgramFiles%`), not `%APPDATA%` or `%TEMP%`.
- **Registry lockdown persistence:** RegistryLockdown writes to HKLM. If these entries are not cleaned up on uninstall, they persist and can affect system behavior after the app is removed. **Required guarantee:** the uninstaller must remove all HKLM entries written by RegistryLockdown; store the key list to enable reliable cleanup.
