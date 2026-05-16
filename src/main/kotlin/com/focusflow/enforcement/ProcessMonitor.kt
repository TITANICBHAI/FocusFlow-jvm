package com.focusflow.enforcement

import com.focusflow.data.Database
import com.focusflow.data.models.BlockRule
import com.focusflow.data.models.NetworkCutoffRule
import com.focusflow.data.models.NetworkRuleMode
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
 *      Zero delay between app switch and kill. Equivalent to Android's AccessibilityService.
 *   2. Polling fallback (500ms) — catches processes that don't own a top-level window
 *      or cases where WinEventHook registration fails.
 *
 * Block sources (union of all sets, evaluated on every event/poll):
 *   - alwaysOnEnabled / sessionActive    — all block_rules with enabled=1
 *   - scheduleBlockedProcesses           — injected by BlockScheduleService (time-window)
 *   - standaloneBlockedProcesses         — injected by StandaloneBlockService (timed block)
 *   - dailyAllowanceBlockedProcesses     — injected by DailyAllowanceTracker (usage cap)
 *
 * Keyword enforcement:
 *   When keywordBlockerEnabled is true, the foreground window title is also checked against
 *   the blocked-keyword list. A keyword match kills the foreground process and logs a
 *   temptation. Keyword checking uses GetWindowTextW via JNA.
 *
 * DB caching:
 *   All enforcement-related DB reads (block rules, keywords, network cutoff rules) are
 *   cached in-memory and refreshed every CACHE_TTL_MS by a background coroutine. This
 *   keeps the 500ms hot path entirely off the database in the steady state.
 */
object ProcessMonitor {

    private const val POLL_MS      = 500L
    private const val COOLDOWN_MS  = 3000L
    private const val CACHE_TTL_MS = 5_000L

    private val scope      = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null
    private var cacheJob:   Job? = null

    private val _blockedAttempts = MutableStateFlow(0)
    val blockedAttempts: StateFlow<Int> = _blockedAttempts

    private val _lastBlockedApp = MutableStateFlow<String?>(null)
    val lastBlockedApp: StateFlow<String?> = _lastBlockedApp

    private val cooldowns = ConcurrentHashMap<String, Long>()

    var sessionActive:   Boolean = false
    var alwaysOnEnabled: Boolean = false

    /** Injected by BlockScheduleService — processes blocked by recurring schedule right now. */
    @Volatile var scheduleBlockedProcesses: Set<String> = emptySet()

    /** Injected by StandaloneBlockService — processes blocked by timed standalone block. */
    @Volatile var standaloneBlockedProcesses: Set<String> = emptySet()

    /** Injected by DailyAllowanceTracker — processes whose daily cap has been exceeded. */
    @Volatile var dailyAllowanceBlockedProcesses: Set<String> = emptySet()

    /**
     * Injected by FocusSessionService — per-task extra blocked apps defined on the
     * active task's focusBlockedApps list. Cleared when the session ends.
     */
    @Volatile var sessionExtraBlockedProcesses: Set<String> = emptySet()

    /** True when at least one enabled keyword-mode NetworkCutoffRule exists.
     *  Kept in sync by the cache refresh; VpnNetworkScreen may also update it immediately. */
    @Volatile var networkCutoffKeywordEnabled: Boolean = false

    // ── In-memory caches for hot-path DB reads ────────────────────────────────
    // Refreshed every CACHE_TTL_MS by cacheJob; never read from DB on the 500ms poll tick.

    @Volatile private var cachedEnabledProcesses: Set<String>             = emptySet()
    @Volatile private var cachedKeywordEnabled:   Boolean                 = false
    @Volatile private var cachedKeywords:         List<String>            = emptyList()
    @Volatile private var cachedNetCutoffRules:   List<NetworkCutoffRule> = emptyList()
    @Volatile private var cachedBlockRules:       List<BlockRule>         = emptyList()
    @Volatile private var cacheLastRefreshMs:     Long                    = 0L

    /** Pull enforcement data from DB into memory. No-op if the cache is still fresh. */
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
    }

    /**
     * Force an immediate cache refresh on the next poll tick.
     * Call this after any DB write that affects enforcement (rule add/remove, keyword change, etc.).
     */
    fun invalidateCaches() {
        cacheLastRefreshMs = 0L
    }

    /** Called from WinEventHook callback for instant (zero-delay) enforcement. */
    fun onForegroundChanged(processName: String) {
        if (!isAnyEnforcementActive()) return
        scope.launch { checkProcess(processName) }
    }

    private fun isAnyEnforcementActive(): Boolean =
        sessionActive || alwaysOnEnabled ||
        scheduleBlockedProcesses.isNotEmpty() ||
        standaloneBlockedProcesses.isNotEmpty() ||
        dailyAllowanceBlockedProcesses.isNotEmpty() ||
        cachedKeywordEnabled ||
        VpnBlocker.isEnabled ||
        networkCutoffKeywordEnabled

    fun start() {
        if (monitorJob?.isActive == true) return

        // Start background cache refresh — fires immediately then every CACHE_TTL_MS
        if (cacheJob?.isActive != true) {
            cacheJob = scope.launch {
                while (isActive) {
                    try { refreshCaches() } catch (_: Exception) {}
                    delay(CACHE_TTL_MS)
                }
            }
        }

        if (isWindows) {
            WinEventHook.start { pName -> onForegroundChanged(pName) }
        }

        monitorJob = scope.launch {
            while (isActive) {
                if (isAnyEnforcementActive()) tickPoll()
                delay(POLL_MS)
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
        val processName = getForegroundProcessName() ?: return
        checkProcess(processName)
    }

    /**
     * UWP apps (Netflix, Calculator, Windows apps from the Store) are hosted inside
     * ApplicationFrameHost.exe. When WinEventHook reports this process as foreground,
     * we must resolve the actual hosted child process by scanning running processes.
     */
    private val uwpFrameHost = "applicationframehost.exe"

    /** Known system frame processes that should be skipped for blocking (they host UWP). */
    private val systemFrameProcesses = setOf(
        "applicationframehost.exe",
        "shellexperiencehost.exe",
        "startmenuexperiencehost.exe",
        "searchhost.exe",
        "searchapp.exe"
    )

    private suspend fun checkProcess(processName: String) {
        val lower = processName.lowercase()
        val now   = System.currentTimeMillis()

        // ── Build blocked set once — reused for UWP resolution AND enforcement ─────────
        val blocked = buildSet<String> {
            if (alwaysOnEnabled || sessionActive) addAll(cachedEnabledProcesses)
            addAll(scheduleBlockedProcesses)
            addAll(standaloneBlockedProcesses)
            addAll(dailyAllowanceBlockedProcesses)
            // Per-task extra blocked apps injected when a focus session is active
            if (sessionActive) addAll(sessionExtraBlockedProcesses)
        }

        // ── UWP frame host resolution ─────────────────────────────────────────────────
        val resolvedName = if (lower == uwpFrameHost || lower in systemFrameProcesses) {
            resolveUwpHostedProcess(blocked) ?: return
        } else {
            processName
        }
        val resolvedLower = resolvedName.lowercase()

        // ── 0. VPN process blocking ───────────────────────────────────────────────────
        if (VpnBlocker.isVpnProcess(resolvedLower)) {
            val vpnKey = "vpn:$resolvedLower"
            val lastHit = cooldowns[vpnKey] ?: 0L
            if (now - lastHit >= COOLDOWN_MS) {
                cooldowns[vpnKey] = now
                enforceBlock(resolvedName)
            }
            return
        }

        // ── 1. Process-name blocking (app list, schedules, standalone, allowances) ─────
        if (blocked.any { resolvedLower == it.lowercase() }) {
            val lastHit = cooldowns[resolvedLower] ?: 0L
            if (now - lastHit >= COOLDOWN_MS) {
                cooldowns[resolvedLower] = now
                enforceBlock(resolvedName)
            }
            return  // Already handling this process — skip keyword check
        }

        // ── 2a. Network cutoff keyword rules (cuts network, does NOT kill) ────────────
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
                        val lastNet = cooldowns[netKey] ?: 0L
                        if (now - lastNet >= COOLDOWN_MS) {
                            cooldowns[netKey] = now
                            val cutTarget = rule.targetProcess ?: resolvedName
                            NetworkBlocker.addRule(cutTarget)
                        }
                    }
                }
            }
        }

        // ── 2b. Keyword blocking (foreground window title — kills process) ────────────
        if (!cachedKeywordEnabled) return
        val keywords = cachedKeywords
        if (keywords.isEmpty()) return

        val title = getForegroundWindowTitle() ?: return
        val titleLower = title.lowercase()
        val matchedKeyword = keywords.firstOrNull { kw -> titleLower.contains(kw.lowercase()) }
            ?: return

        val kwKey = "kw:$resolvedLower"
        val lastKwHit = cooldowns[kwKey] ?: 0L
        if (now - lastKwHit < COOLDOWN_MS) return
        cooldowns[kwKey] = now

        killProcessByName(resolvedName)
        SoundAversion.playBlockAlert()

        val displayName = resolvedName.removeSuffix(".exe").replaceFirstChar { it.uppercase() }
        val reason = "Keyword: \"$matchedKeyword\" in title: \"${title.take(60)}\""
        TemptationLogger.log(resolvedName, "$displayName ($reason)")
        Database.logTemptation(resolvedName, displayName)

        _blockedAttempts.update { it + 1 }
        _lastBlockedApp.value = displayName

        withContext(Dispatchers.Main) {
            AppBlocker.showOverlay(displayName)
        }
    }

    /**
     * When ApplicationFrameHost.exe (Windows UWP frame host) is in the foreground,
     * scan running processes and return the first one that appears in the already-built
     * blocked set. Accepts the set as a parameter to avoid a redundant DB call.
     */
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

    /** Shared kill + log + notify path for process-name block triggers. */
    private suspend fun enforceBlock(processName: String) {
        killProcessByName(processName)
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
