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
