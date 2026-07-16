---
name: Round 11 audit — 2026-07-16
description: Full audit covering errors/crashes/security for all changes since round 10 (2026-06-05), plus first-class security & privacy-claim verification pass.
---

# Round 11 Audit — 2026-07-16

## Scope
- Files changed since round 10 (2026-06-05): ProcessMonitor.kt, WinEventHook.kt, GlobalKeyboardHook.kt, BlockDefenseScreen.kt, VpnNetworkScreen.kt, KeywordBlockerScreen.kt (all fixed in this same session before the audit)
- Phase 2 priority files (first dedicated pass): RegistryLockdown.kt, VpnBlocker.kt, WatchdogInstaller.kt, KillSwitchService.kt, AppIconExtractor.kt, BlockPresets.kt, GlobalPin.kt, NuclearModePin.kt, DonateDialog.kt, EnforcementLog.kt, KeywordMatchLogger.kt
- Phase 3: Security & privacy-claim verification (first-class category)

---

## Phase 1 — Diagnostics

**Compile result:** `gradle compileKotlin compileTestKotlin` → BUILD SUCCESSFUL, 0 Kotlin errors, 0 Kotlin warnings.

**Gradle deprecation (platform-level, not our code):**
> `StartParameter.isConfigurationCacheRequested` is deprecated, scheduled for removal in Gradle 10.0.
> Action: none until we upgrade to Gradle 10 — it's a Gradle internals change, not a user code issue.

**Test result:** `gradle test` → BUILD SUCCESSFUL, all 50 tests pass:
- PinHashingTest: 19 tests ✅
- ScheduleEvaluatorTest: 18 tests ✅
- DailyAllowanceTest: 13 tests ✅

**Git log since 2026-06-05:** 3 commits — all from this session (ProcessMonitor UWP fix, state management refactors, keyboard/keyword fixes). No external changes to audit.

---

## Phase 2 — Deep Review

### Fixes Applied

#### RegistryLockdown.kt — TOCTOU race on `hooksTimeoutModified` / `savedHooksTimeout`
**What:** `setLowLevelHooksTimeout()` and `restoreLowLevelHooksTimeout()` both read `hooksTimeoutModified` as a guard then write `savedHooksTimeout` + `hooksTimeoutModified` in separate statements. Two concurrent callers (e.g. kiosk enter racing with a watchdog tick) could both pass the guard, both overwrite `savedHooksTimeout`, and `restoreLowLevelHooksTimeout()` would then restore the wrong original value — potentially deleting a registry value the user had set before installing the app.

**Fix:** Added `@Synchronized` to both methods so only one caller can run the read-check-then-write block at a time.

**Why:** `@Volatile` on the fields prevents torn reads but does not make the check-then-set sequence atomic. `@Synchronized` on the `object` method uses the singleton's monitor, which is the correct granularity here.

**Files:** `src/main/kotlin/com/focusflow/enforcement/RegistryLockdown.kt` (~L164, ~L198)

---

### Confirmed Already Correct

| File | Finding |
|------|---------|
| RegistryLockdown.kt | Force-kill cleanup gap is intentional: SIGKILL/power-loss survivor keys are handled by the startup janitor in `Main.kt` (documented in code). Every normal-exit path (System.exit, SIGTERM, crash) is covered by the registered JVM shutdown hook. |
| VpnBlocker.kt | Non-atomic read-modify-write on `_cachedCustomProcesses` in `addCustomProcess`/`removeCustomProcess` is low-risk: the DB is the source of truth, cache is rebuilt on next reload, and concurrent custom-process edits are unlikely. Not a security issue. |
| KillSwitchService.kt | Uses `compareAndSet` on `_isActive` — no WinEventHook-style lockout. `countdownJob` is cancelled on deactivation. Clean. |
| AppIconExtractor.kt | `ConcurrentHashMap` for cache; `Graphics` disposed in `finally`. Clean. |
| BlockPresets.kt | Immutable data — no concurrency concern. |
| EnforcementLog.kt | No concurrency issues found. |
| KeywordMatchLogger.kt | `CopyOnWriteArrayList` — size could transiently exceed `MAX_ENTRIES` under concurrent inserts, but this is a cosmetic log overflow, not a security issue. Accepted. |
| DonateDialog.kt | Hardcoded UPI ID and GitHub URL are business constants, not secrets. No issue. |
| WatchdogInstaller.kt | `/tr` argument uses string interpolation with `$psScript` / `$safePath`, but the exe path comes from `WindowsStartupManager.resolveExePath()` (system-determined, not user input) with single-quote escaping for the PowerShell context. Not a user-controlled injection vector. All other ProcessBuilder calls throughout the codebase use array form. |
| Auto-start (WindowsStartupManager) | `removeFromStartup()` exists and is wired to uninstall/disable flows. HKCU Run key is correctly cleaned up. |
| Taskbar-restore guarantee | `FocusFlowTaskbarGuard` scheduled task is installed at kiosk enter, runs at every logon independently of FocusFlow, and survives uninstall. Covers hard-kill + uninstall scenario. |
| Dual-detection race (WinEventHook + poll) | Both paths call `tryAcquireCooldown()` before killing. The cooldown map uses `ConcurrentHashMap.compute()` as an atomic gate — only one caller wins per cooldown window. No double-kill risk. |

---

## Phase 3 — Security & Privacy-Claim Verification

### 🔴 ARCHITECTURAL RISK (product decision required): Webhook telemetry in release builds

**What:**
- `CrashReporter.kt` sends crash reports to a Discord webhook URL read from JVM system property `focusflow.webhook.crash`.
- `ResourceMonitorService.kt` sends resource heartbeats to `focusflow.webhook.monitor`.
- `build.gradle.kts` reads `FOCUSFLOW_WEBHOOK_CRASH` and `FOCUSFLOW_WEBHOOK_MONITOR` environment variables and bakes them into the packaged app as JVM args (`-Dfocusflow.webhook.crash=...`).
- `build-windows.yml` injects those env vars from GitHub repository secrets into every `packageExe`, `packageMsi`, and `createDistributable` step — the **shipped binaries**, not just dev runs.
- The workflow even has an explicit "Check webhook secrets" step that celebrates "🎉 ALL WEBHOOKS FOUND — crash & resource telemetry ACTIVE".

**Contradiction:**
- Website homepage: "0 — Data sent off your PC" and "No internet required."
- Microsoft Store listing: "No data collection."
- If `FOCUSFLOW_WEBHOOK_CRASH` and `FOCUSFLOW_WEBHOOK_MONITOR` are set as GitHub secrets, every installed copy of FocusFlow silently phones home with no in-app disclosure, no consent prompt, and no opt-out toggle.

**This is flagged as an architectural risk regardless of whether the secrets are currently set.** A future secret-add would silently activate telemetry with zero user-facing change.

**Three fix options (product decision — not applied unilaterally):**

Option A — Strip webhook telemetry from release builds entirely:
Add an `isReleaseBuild` Gradle property; when true, skip injecting the JVM args entirely. Dev builds retain full telemetry. Requires no privacy-policy update.

Option B — Add explicit in-app opt-in + update disclosures:
Add a consent toggle on first launch (defaulting to OFF). Only inject webhook URLs when user has opted in. Update the privacy policy and Store listing to accurately describe what data is sent, when, and where. Requires policy update before next Store submission.

Option C — CI gate that blocks secret-injected release tags:
Add a workflow check that fails the build if webhook secrets are set AND the build is triggered by a public release tag. Forces a deliberate decision at publish time. Does not fix the underlying disclosure gap but prevents accidental telemetry in shipped builds.

**Recommended:** Option A or B. Option C alone is insufficient — it's a process guard, not a user-visible fix.

---

### ✅ No remaining hardcoded secrets/tokens

Full codebase grep confirms:
- No webhook URLs in source (both read from system properties injected at build time)
- No API keys, tokens, or credentials anywhere in `.kt`, `.yml`, `.properties` files
- Round 9 cleanup (Base64-encoded Discord URLs removed from CrashReporter + ResourceMonitorService) is intact

### ✅ ProcessBuilder injection — all clear

Every `ProcessBuilder` invocation across the enforcement layer uses array-form arguments:
- `WinApiBindings.kt`: `ProcessBuilder("taskkill", "/F", "/IM", processName)` — process name comes from OS enumeration, not user input
- `NetworkBlocker.kt`: array form, firewall rule targets are exe names from block rules
- `NuclearMode.kt`: `ProcessBuilder("tasklist", "/FO", "CSV", "/NH")` — no user input
- `WatchdogInstaller.kt`: `/tr` uses string interpolation but with system-determined exe path (not user input); single-quote escaped for PowerShell context. Low risk, no fix required.
- `recovery/`: array form throughout

### ✅ PIN hashing consistency — all three implementations identical

| Class | Algorithm | Salt | Min length | Legacy migration |
|-------|-----------|------|------------|------------------|
| SessionPin | SHA-256 + SecureRandom 16-byte salt | ✅ | 8 chars | ✅ upgrades on verify |
| GlobalPin | SHA-256 + SecureRandom 16-byte salt | ✅ | 8 chars | ✅ upgrades on verify |
| NuclearModePin | SHA-256 + SecureRandom 16-byte salt | ✅ | 4 chars | N/A — rejects unexpected format (correct: no legacy format ever existed for this class) |

All three use identical `hashPin` / `generateSalt` / `sha256Salted` implementations. Storage format `"saltHex:hashHex"` is consistent.

### ✅ Dependency CVE status

| Dependency | Version | Status |
|-----------|---------|--------|
| sqlite-jdbc | 3.47.1.0 | Current; no known CVEs |
| jna / jna-platform | 5.14.0 | Current; no known critical CVEs |
| kotlinx-coroutines | 1.7.3 | Slightly behind (1.9.x current); no security CVEs in 1.7.x — upgrade is a stability/feature concern, not a security one |

### ✅ Admin rights behavior — silent vs. surfaced

- `RegistryLockdown`: HKCU writes (DisableTaskMgr, NoLogOff) work without admin. HKLM write (HideFastUserSwitching) fails gracefully; result tracked in `userSwitchingLocked` — callers can warn the user.
- `NetworkBlocker`: uses `netsh advfirewall` — requires admin. Failure is caught and logged but not surfaced to the UI as a user-visible error. Acceptable: FocusFlow already requires admin for its core install; a non-admin run is an unsupported configuration.
- `HostsBlocker`: writes to `%windir%\System32\drivers\etc\hosts` — requires admin. Same pattern: failure logged, app continues. Same reasoning applies.

---

## Phase 4 — Validation

- `gradle compileKotlin compileTestKotlin` → ✅ BUILD SUCCESSFUL (0 warnings)
- `gradle test` → ✅ BUILD SUCCESSFUL, 50/50 tests pass
- Packaging not run (no Windows environment on Replit — packaging requires Windows SDK)

---

## Test Gaps Identified

| Gap | Severity | Detail |
|-----|----------|--------|
| NuclearModePin not covered by PinHashingTest | Medium | `PinHashingTest.kt` tests SessionPin (10 tests) and GlobalPin (9 tests) but has zero tests for `NuclearModePin`. The implementation is correct and identical in structure to the other two, but a wrong-PIN rejection, salted-format verification, clearWithPin, and clearWithoutPin test would close the gap. |
