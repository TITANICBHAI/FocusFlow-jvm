package com.focusflow.enforcement

import com.focusflow.data.Database
import com.focusflow.data.models.BlockRule
import com.focusflow.data.models.NetworkCutoffRule
import com.focusflow.data.models.NetworkRuleMode
import com.focusflow.services.KeywordMatchLogger
import com.focusflow.services.SoundAversion
import com.focusflow.services.TemptationLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * ProcessMonitor
 *
 * Dual-mode enforcement engine:
 *   1. WinEventHook (instant) — EVENT_SYSTEM_FOREGROUND fires on every foreground change.
 *      Zero delay between app switch and kill.
 *   2. Polling fallback (2 s when hook active, 750 ms when not) — catches apps already in
 *      the foreground when a session starts, and covers hook-registration failures.
 *
 * Concurrency model:
 *   Both the hook callback and the poll can fire for the same foreground window within
 *   milliseconds of each other. The cooldown check uses ConcurrentHashMap.compute() so
 *   the read-check-write is a single atomic operation — prevents double-kills.
 *
 * Block sources (union evaluated on every event):
 *   - alwaysOnEnabled / sessionActive    — all block_rules with enabled=1
 *   - sessionExtraBlockedProcesses       — per-task apps from the active task
 *   - scheduleBlockedProcesses           — injected by BlockScheduleService
 *   - standaloneBlockedProcesses         — injected by StandaloneBlockService
 *   - dailyAllowanceBlockedProcesses     — injected by DailyAllowanceTracker
 *
 * DB caching:
 *   All DB reads are cached in-memory (TTL = CACHE_TTL_MS). The enforcement hot-path
 *   never touches the database. Cache is eagerly refreshed on start() so the very first
 *   WinEventHook callback already has populated data.
 */
object ProcessMonitor {

    // ── Tuning constants ──────────────────────────────────────────────────────
    /** How long to wait before re-blocking the same process. 800 ms prevents instant
     *  re-spawn loops while still feeling near-instant to the user. */
    private const val COOLDOWN_MS  = 800L

    /** Poll interval when WinEventHook is active — slower since the hook handles
     *  real-time detection and the poll is only a safety net. */
    private const val POLL_HOOK_MS = 2_000L

    /** Poll interval when WinEventHook is NOT registered (fallback mode). */
    private const val POLL_FALLBACK_MS = 750L

    /** How often the in-memory block-rule cache is refreshed from SQLite. */
    private const val CACHE_TTL_MS = 2_000L

    private val scope      = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null
    private var cacheJob:   Job? = null

    private val _blockedAttempts = MutableStateFlow(0)
    val blockedAttempts: StateFlow<Int> = _blockedAttempts

    private val _lastBlockedApp = MutableStateFlow<String?>(null)
    val lastBlockedApp: StateFlow<String?> = _lastBlockedApp

    /**
     * Per-process-key cooldown timestamps.
     * The map itself is ConcurrentHashMap, but the check-and-set uses .compute()
     * to make the entire read-compare-write sequence atomic, preventing duplicate
     * kills when the hook and poll both fire within the cooldown window.
     */
    private val cooldowns = ConcurrentHashMap<String, Long>()

    var sessionActive:   Boolean = false
    var alwaysOnEnabled: Boolean = false

    /**
     * Set to true by KillSwitchService while the daily emergency break is active.
     * When true, ALL enforcement is bypassed — no process kills, no network blocks.
     */
    @Volatile var killSwitchActive: Boolean = false

    /**
     * Shells / terminals that are always killed whenever any enforcement is active,
     * without requiring Nuclear Mode. These are escape-route tools that should never
     * be accessible during a focus session or always-on block.
     */
    private val systemShells = setOf(
        "cmd.exe", "powershell.exe", "powershell_ise.exe", "pwsh.exe",
        "wt.exe", "mintty.exe", "conemu64.exe", "conemu.exe", "cmder.exe",
        "bash.exe", "zsh.exe", "sh.exe"
    )

    /** Injected by BlockScheduleService — processes blocked by schedule right now. */
    @Volatile var scheduleBlockedProcesses: Set<String> = emptySet()

    /** Injected by StandaloneBlockService — processes blocked by timed block. */
    @Volatile var standaloneBlockedProcesses: Set<String> = emptySet()

    /** Injected by DailyAllowanceTracker — processes whose daily cap is exceeded. */
    @Volatile var dailyAllowanceBlockedProcesses: Set<String> = emptySet()

    /**
     * Injected by FocusSessionService — per-task extra blocked apps from the
     * active task's focusBlockedApps list. Cleared when the session ends.
     */
    @Volatile var sessionExtraBlockedProcesses: Set<String> = emptySet()

    /**
     * Injected by FocusLauncherService — when non-empty, the launcher kiosk
     * mode is active. Any foreground process NOT in this set (and not a known-safe
     * system process) will be killed. This is inverse of normal blocking.
     */
    @Volatile var launcherAllowedProcesses: Set<String> = emptySet()

    /**
     * System processes that are always safe in launcher mode — never kill these.
     *
     * Covers four categories:
     *   1. Core Windows system processes (killing these = BSOD or unrecoverable state)
     *   2. Input stack: keyboard, mouse, touchpad, touchscreen, stylus, on-screen keyboard
     *      (killing any of these breaks input until reboot)
     *   3. Shell infrastructure that routes focus/activation events
     *   4. Security, audio, and driver hosting processes
     */
    private val launcherSafeProcesses = setOf(
        // ── FocusFlow itself ──────────────────────────────────────────────────
        "focusflow.exe", "java.exe", "javaw.exe",

        // ── Core Windows processes — NEVER touch these ────────────────────────
        "explorer.exe",       // Shell; taskbar, desktop, file dialogs
        "dwm.exe",            // Desktop Window Manager; GPU compositing
        "winlogon.exe",       // Logon/session management
        "csrss.exe",          // Client/Server Runtime; Win32 subsystem
        "wininit.exe",        // Windows initialisation
        "services.exe",       // Service control manager
        "lsass.exe",          // Local Security Authority
        "svchost.exe",        // Service host (hundreds of system services)
        "smss.exe",           // Session Manager
        "fontdrvhost.exe",    // Font driver host
        "spoolsv.exe",        // Print spooler
        "conhost.exe",        // Console host (needed by many system processes)
        "dllhost.exe",        // COM+ DLL host
        "taskhostw.exe",      // Task host
        "sihost.exe",         // Shell infrastructure host — required for input routing

        // ── Input framework — keyboard, mouse, touchpad, touchscreen, stylus ──
        "ctfmon.exe",              // Text input framework — ALL keyboard input flows through this
        "tabtip.exe",              // Touch keyboard & handwriting panel (Surface, tablets)
        "textinputhost.exe",       // Modern touch/virtual keyboard (Windows 10/11)
        "osk.exe",                 // On-screen keyboard (accessibility + touchscreen)
        "inputmethod.exe",         // Input method host
        "inputpersonalization.exe",// Input personalisation (handwriting recognition training)
        "rdpinput.exe",            // Remote Desktop input
        "tabletinputservice.exe",  // Tablet PC input service
        "wisptis.exe",             // Windows Ink Services Platform (stylus/pen)
        "wudfhost.exe",            // Windows User-mode Driver Framework — HID/USB input drivers
        "hidinput.exe",            // HID input aggregator (some OEM drivers)
        "touchpointeditor.exe",    // Touchpad calibration (Synaptics/Elan/Precision)
        "syntp.exe", "syntpenh.exe", "syntphelper.exe",  // Synaptics touchpad
        "elantech.exe", "etdctrl.exe", "etdgesture.exe", // Elan touchpad
        "itype.exe", "ipoint.exe",                        // Microsoft IntelliMouse/keyboard
        "setpoint.exe", "khalmnpr.exe",                    // Logitech SetPoint
        "razer.exe", "razercentralservice.exe",           // Razer HID
        "lghub.exe",                                      // Logitech G HUB
        "steelseries.exe", "ggdrive.exe",                 // SteelSeries

        // ── UWP & shell activation infrastructure ─────────────────────────────
        "runtimebroker.exe",           // UWP process broker; handles permissions & activation
        "applicationframehost.exe",    // UWP frame host (Netflix, Calculator, etc.)
        "shellexperiencehost.exe",     // Action Centre, Quick Settings, notification toasts
        "startmenuexperiencehost.exe", // Start menu host
        "searchhost.exe",              // Search host
        "searchapp.exe",               // Search app (older Win 10)
        "lockapp.exe",                 // Lock screen
        "logonui.exe",                 // Logon UI
        "credentialuibroker.exe",      // Credential dialogs (UAC prompts)
        "consent.exe",                 // UAC consent dialog
        "dashost.exe",                 // Device Association Framework
        "settingsynchost.exe",         // Settings sync
        "usoclient.exe",               // Update session orchestrator

        // ── Audio — must remain running for any in-app sound ──────────────────
        "audiodg.exe",                // Audio Device Graph (all sound routes through this)
        "audioendpointbuilder.exe",   // Audio endpoint builder service

        // ── Security — killing these breaks Windows Update, Defender, UAC ─────
        "msmpeng.exe",                // Windows Defender antivirus engine
        "securityhealthsystray.exe",  // Windows Security tray icon
        "smartscreen.exe",            // Windows SmartScreen
        "msseces.exe",                // Microsoft Security Essentials (older Windows)

        // ── Accessibility — screen readers, magnifier ─────────────────────────
        "narrator.exe",               // Windows Narrator screen reader
        "magnify.exe",                // Screen magnifier
        "utilman.exe"                 // Utility Manager (Ease of Access shortcut)
    )

    /** True when at least one enabled keyword-mode NetworkCutoffRule exists. */
    @Volatile var networkCutoffKeywordEnabled: Boolean = false

    // ── In-memory caches for hot-path DB reads ────────────────────────────────

    @Volatile private var cachedEnabledProcesses: Set<String>             = emptySet()
    @Volatile private var cachedKeywordEnabled:   Boolean                 = false
    @Volatile private var cachedKeywords:         List<String>            = emptyList()
    @Volatile private var cachedNetCutoffRules:   List<NetworkCutoffRule> = emptyList()
    @Volatile private var cachedBlockRules:       List<BlockRule>         = emptyList()
    @Volatile private var cacheLastRefreshMs:     Long                    = 0L

    private suspend fun refreshCaches() {
        val now = System.currentTimeMillis()
        if (now - cacheLastRefreshMs < CACHE_TTL_MS) return
        cacheLastRefreshMs = now

        cachedEnabledProcesses  = Database.getEnabledBlockProcesses()
        cachedKeywordEnabled    = Database.isKeywordBlockerEnabled()
        cachedKeywords          = Database.getBlockedKeywords()
        cachedNetCutoffRules    = Database.getEnabledNetworkCutoffRules()
        cachedBlockRules        = Database.getBlockRules()
        networkCutoffKeywordEnabled =
            cachedNetCutoffRules.any { it.mode == NetworkRuleMode.KEYWORD && it.enabled }

        // Retry firewall rules that couldn't be applied earlier because the
        // target process wasn't running at block time. Now that processes may
        // have started, the path can be resolved and the rule applied.
        if (NetworkBlocker.pendingRuleCount() > 0) {
            NetworkBlocker.retryPendingRules()
        }
    }

    /**
     * Force an immediate cache refresh on the next poll tick.
     * Call this after any DB write that affects enforcement.
     */
    fun invalidateCaches() {
        cacheLastRefreshMs = 0L
    }

    /**
     * Atomic cooldown gate. Returns true if this key has not fired within COOLDOWN_MS
     * AND atomically records the current timestamp so concurrent callers cannot both pass.
     *
     * ConcurrentHashMap.compute() guarantees the lambda runs under the key's lock,
     * making the read-compare-write a single uninterruptible operation.
     */
    private fun tryAcquireCooldown(key: String, now: Long): Boolean {
        var acquired = false
        cooldowns.compute(key) { _, lastHit ->
            if (lastHit == null || now - lastHit >= COOLDOWN_MS) {
                acquired = true
                now
            } else {
                lastHit  // keep existing timestamp — this caller loses the race
            }
        }
        return acquired
    }

    /**
     * Called from WinEventHook callback for instant (zero-delay) enforcement.
     * [pid] is the exact OS PID of the foreground window — used for targeted
     * per-window kills (e.g. one Chrome window) instead of all-instances-by-name.
     */
    fun onForegroundChanged(processName: String, pid: Long = 0L) {
        if (!isAnyEnforcementActive()) return
        scope.launch { checkProcess(processName, pid) }
    }

    private fun isAnyEnforcementActive(): Boolean {
        if (killSwitchActive) return false
        return sessionActive || alwaysOnEnabled ||
            scheduleBlockedProcesses.isNotEmpty() ||
            standaloneBlockedProcesses.isNotEmpty() ||
            dailyAllowanceBlockedProcesses.isNotEmpty() ||
            launcherAllowedProcesses.isNotEmpty() ||
            cachedKeywordEnabled ||
            VpnBlocker.isEnabled ||
            networkCutoffKeywordEnabled
    }

    fun start() {
        if (monitorJob?.isActive == true) return

        // Eagerly refresh caches before the hook fires its first callback.
        // This prevents the narrow window on session-start where cachedEnabledProcesses
        // is empty and a switch to a blocked app goes undetected.
        invalidateCaches()
        scope.launch(Dispatchers.IO) {
            try { refreshCaches() } catch (_: Exception) {}
        }

        // Start background cache refresh loop
        if (cacheJob?.isActive != true) {
            cacheJob = scope.launch {
                while (isActive) {
                    try { refreshCaches() } catch (_: Exception) {}
                    delay(CACHE_TTL_MS)
                }
            }
        }

        if (isWindows) {
            WinEventHook.start { pName, pid -> onForegroundChanged(pName, pid) }
        }

        monitorJob = scope.launch {
            while (isActive) {
                if (isAnyEnforcementActive()) tickPoll()
                // Use a slower poll rate when the hook is active — the hook handles
                // instant detection, the poll is only a safety net (e.g., already-
                // foreground apps at session start, hook delivery gaps).
                val pollInterval = if (WinEventHook.isActive) POLL_HOOK_MS else POLL_FALLBACK_MS
                delay(pollInterval)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        WinEventHook.stop()
    }

    private suspend fun tickPoll() {
        if (!isWindows) return
        val (processName, pid) = getForegroundProcessNameAndPid() ?: return
        checkProcess(processName, pid)
    }

    /**
     * UWP apps (Netflix, Calculator, Windows Store apps) are hosted inside
     * ApplicationFrameHost.exe. Resolve to the actual child process.
     */
    private val uwpFrameHost = "applicationframehost.exe"

    private val systemFrameProcesses = setOf(
        "applicationframehost.exe",
        "shellexperiencehost.exe",
        "startmenuexperiencehost.exe",
        "searchhost.exe",
        "searchapp.exe"
    )

    private suspend fun checkProcess(processName: String, pid: Long = 0L) {
        val lower = processName.lowercase()
        val now   = System.currentTimeMillis()

        // Refresh caches if stale (non-blocking if still fresh)
        refreshCaches()

        // ── Build blocked set once ────────────────────────────────────────────
        val blocked = buildSet<String> {
            if (alwaysOnEnabled || sessionActive) addAll(cachedEnabledProcesses)
            addAll(scheduleBlockedProcesses)
            addAll(standaloneBlockedProcesses)
            addAll(dailyAllowanceBlockedProcesses)
            if (sessionActive) addAll(sessionExtraBlockedProcesses)
            // Shells/terminals are always killed when any enforcement is active —
            // no nuclear mode required. taskmgr.exe is intentionally excluded.
            addAll(systemShells)
        }

        // ── UWP frame host resolution ─────────────────────────────────────────
        val resolvedName = if (lower == uwpFrameHost || lower in systemFrameProcesses) {
            resolveUwpHostedProcess(blocked) ?: return
        } else {
            processName
        }
        val resolvedLower = resolvedName.lowercase()

        // ── Launcher kiosk mode — inverse block (kill anything not allowed) ───
        val launcherAllowed = launcherAllowedProcesses
        if (launcherAllowed.isNotEmpty()) {
            if (resolvedLower !in launcherSafeProcesses && resolvedLower !in launcherAllowed) {
                if (tryAcquireCooldown("launcher:$resolvedLower", now)) {
                    if (pid > 0L) killProcessByPid(pid)
                    else killProcessByName(resolvedName)
                    _blockedAttempts.update { it + 1 }
                }
            }
            return
        }

        // ── 0. VPN process blocking ───────────────────────────────────────────
        if (VpnBlocker.isVpnProcess(resolvedLower)) {
            if (tryAcquireCooldown("vpn:$resolvedLower", now)) {
                enforceBlock(resolvedName)
            }
            return
        }

        // ── 1. Process-name blocking ──────────────────────────────────────────
        if (blocked.any { resolvedLower == it.lowercase() }) {
            if (tryAcquireCooldown(resolvedLower, now)) {
                enforceBlock(resolvedName, pid)
            }
            return
        }

        // ── 2a. Network cutoff keyword rules (cuts network, does NOT kill) ────
        if (networkCutoffKeywordEnabled) {
            val netRules = cachedNetCutoffRules.filter { it.mode == NetworkRuleMode.KEYWORD }
            if (netRules.isNotEmpty()) {
                val netTitle = getForegroundWindowTitle() ?: ""
                val netTitleLower = netTitle.lowercase()
                netRules.forEach { rule ->
                    val processMatches = rule.targetProcess == null ||
                        rule.targetProcess.lowercase() == resolvedLower
                    if (!processMatches) return@forEach
                    if (netTitleLower.contains(rule.pattern.lowercase())) {
                        val netKey = "net:${rule.id}:$resolvedLower"
                        if (tryAcquireCooldown(netKey, now)) {
                            val cutTarget = rule.targetProcess ?: resolvedName
                            NetworkBlocker.addRule(cutTarget)
                        }
                    }
                }
            }
        }

        // ── 2b. Keyword blocking (kills process) ─────────────────────────────
        if (!cachedKeywordEnabled) return
        val keywords = cachedKeywords
        if (keywords.isEmpty()) return

        val title = getForegroundWindowTitle() ?: return
        val titleLower = title.lowercase()
        val matchedKeyword = keywords.firstOrNull { kw -> titleLower.contains(kw.lowercase()) }
            ?: return

        if (!tryAcquireCooldown("kw:$resolvedLower", now)) return

        // Kill by PID when available — closes only the specific browser window.
        if (pid > 0L) killProcessByPid(pid) else killProcessByName(resolvedName)
        SoundAversion.playBlockAlert()

        val displayName = resolvedName.removeSuffix(".exe").replaceFirstChar { it.uppercase() }
        TemptationLogger.log(resolvedName, displayName)
        Database.logTemptation(resolvedName, displayName)
        KeywordMatchLogger.record(displayName, matchedKeyword, title)

        _blockedAttempts.update { it + 1 }
        _lastBlockedApp.value = displayName

        withContext(Dispatchers.Main) {
            AppBlocker.showOverlay(displayName)
        }
    }

    private fun resolveUwpHostedProcess(blocked: Set<String>): String? {
        return try {
            if (blocked.isEmpty()) return null
            ProcessHandle.allProcesses()
                .filter { ph -> ph.info().command().isPresent }
                .map { ph ->
                    ph.info().command().get()
                        .substringAfterLast('\\')
                        .substringAfterLast('/')
                        .lowercase()
                }
                .filter { exe -> blocked.any { b -> exe == b.lowercase() } }
                .findFirst()
                .orElse(null)
        } catch (_: Exception) { null }
    }

    /**
     * Shared kill + log + notify path for process-name block triggers.
     * Kills by PID when available (targeted), falls back to name-based kill.
     */
    private suspend fun enforceBlock(processName: String, pid: Long = 0L) {
        if (pid > 0L) killProcessByPid(pid) else killProcessByName(processName)
        SoundAversion.playBlockAlert()

        val displayName = processName.removeSuffix(".exe").replaceFirstChar { it.uppercase() }
        TemptationLogger.log(processName, displayName)
        Database.logTemptation(processName, displayName)

        _blockedAttempts.update { it + 1 }
        _lastBlockedApp.value = displayName

        withContext(Dispatchers.Main) {
            AppBlocker.showOverlay(displayName)
        }

        val rule = cachedBlockRules.find { it.processName.equals(processName, ignoreCase = true) }
        if (rule?.blockNetwork == true) {
            NetworkBlocker.addRule(processName)
        }
    }

    fun dispose() {
        WinEventHook.stop()
        cacheJob?.cancel()
        scope.cancel()
    }
}
