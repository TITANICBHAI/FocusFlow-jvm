package com.focusflow.enforcement

import com.focusflow.data.Database
import com.focusflow.services.SoundAversion
import com.focusflow.services.TemptationLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 *   the blocked-keyword list (Database.getBlockedKeywords()). A keyword match kills the
 *   foreground process and logs a temptation. Keyword checking uses GetWindowTextW via JNA.
 */
object ProcessMonitor {

    private const val POLL_MS     = 500L
    private const val COOLDOWN_MS = 3000L

    private val scope      = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null

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
        Database.isKeywordBlockerEnabled()

    fun start() {
        if (monitorJob?.isActive == true) return

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
     * We cache the last-known UWP process name to avoid scanning on every poll tick.
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

        // ── UWP frame host resolution ──────────────────────────────────────────────────
        // ApplicationFrameHost.exe hosts UWP apps. Rather than blocking the frame host
        // itself (which would kill the Windows shell), resolve the actual hosted app by
        // checking what non-system processes changed foreground most recently.
        val resolvedName = if (lower == uwpFrameHost || lower in systemFrameProcesses) {
            resolveUwpHostedProcess() ?: return
        } else {
            processName
        }
        val resolvedLower = resolvedName.lowercase()

        // ── 1. Process-name blocking (app list, schedules, standalone, allowances) ──────
        val blocked = buildSet<String> {
            if (alwaysOnEnabled || sessionActive) addAll(Database.getEnabledBlockProcesses())
            addAll(scheduleBlockedProcesses)
            addAll(standaloneBlockedProcesses)
            addAll(dailyAllowanceBlockedProcesses)
        }

        if (blocked.any { resolvedLower == it.lowercase() }) {
            val lastHit = cooldowns[resolvedLower] ?: 0L
            if (now - lastHit >= COOLDOWN_MS) {
                cooldowns[resolvedLower] = now
                enforceBlock(resolvedName)
            }
            return  // Already handling this process — skip keyword check
        }

        // ── 2. Keyword blocking (foreground window title) ────────────────────────────────
        if (!Database.isKeywordBlockerEnabled()) return
        val keywords = Database.getBlockedKeywords()
        if (keywords.isEmpty()) return

        val title = getForegroundWindowTitle() ?: return
        val titleLower = title.lowercase()
        val matchedKeyword = keywords.firstOrNull { kw -> titleLower.contains(kw.lowercase()) }
            ?: return

        // Cooldown key for keyword hits: use resolved process name
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

        _blockedAttempts.value++
        _lastBlockedApp.value = displayName

        withContext(Dispatchers.Main) {
            AppBlocker.showOverlay(displayName)
        }
    }

    /**
     * When ApplicationFrameHost.exe (Windows UWP frame host) is in the foreground,
     * return the last known non-system foreground process so we can check it against
     * the block list. UWP apps run inside ApplicationFrameHost, not as Win32 windows.
     *
     * Strategy: track the most recent non-system foreground process in a @Volatile var
     * and return it here. Falls back to null (skip) if nothing valid is available.
     */
    @Volatile private var lastNonSystemForeground: String? = null

    private fun resolveUwpHostedProcess(): String? {
        // Try to find a UWP app in the running process list that might be blocked
        // by scanning active processes and matching against the block list.
        return try {
            val blocked = buildSet<String> {
                if (alwaysOnEnabled || sessionActive) addAll(Database.getEnabledBlockProcesses())
                addAll(scheduleBlockedProcesses)
                addAll(standaloneBlockedProcesses)
                addAll(dailyAllowanceBlockedProcesses)
            }
            if (blocked.isEmpty()) return null

            // Return first blocked process that is currently running
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

        _blockedAttempts.value++
        _lastBlockedApp.value = displayName

        withContext(Dispatchers.Main) {
            AppBlocker.showOverlay(displayName)
        }

        val rule = Database.getBlockRules().find { it.processName.equals(processName, ignoreCase = true) }
        if (rule?.blockNetwork == true) {
            NetworkBlocker.addRule(processName)
        }
    }

    fun dispose() {
        WinEventHook.stop()
        scope.cancel()
    }
}
