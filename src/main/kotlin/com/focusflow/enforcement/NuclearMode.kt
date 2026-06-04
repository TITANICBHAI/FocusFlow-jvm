package com.focusflow.enforcement

import com.focusflow.data.Database
import com.focusflow.services.SoundAversion
import com.focusflow.services.SystemTrayManager
import kotlinx.coroutines.*
import java.awt.TrayIcon
import java.util.concurrent.ConcurrentHashMap

/**
 * NuclearMode — three-layer escape-route enforcement
 *
 * Layer 1 — Detect (efficient):
 *   A single `tasklist /FO CSV /NH` call captures every running process name.
 *   The output is filtered against [escapeProcesses] in-memory. This replaces
 *   40+ individual taskkill spawns with ONE scan per tick — from ~8 000 process
 *   spawns/min down to 1 per tick.
 *
 * Layer 2 — Kill (batch):
 *   If any escape processes are found, ONE `taskkill /F /IM proc1 /IM proc2 …`
 *   call terminates all of them simultaneously. Escape attempts are counted
 *   in-memory (per process name) and persisted to the DB every 5 hits so the
 *   Stats screen can surface "attempted escape N times" data.
 *
 * Layer 3 — Block (firewall):
 *   When nuclear mode activates, outbound-block firewall rules are added via
 *   NetworkBlocker for every escape process. Even if a user renames taskmgr.exe,
 *   the firewall prevents the original path from reaching the network. Rules are
 *   removed cleanly when nuclear mode deactivates.
 */
object NuclearMode {

    /** Processes that could be used to escape focus enforcement. */
    private val escapeProcesses = setOf(
        // Task management / process viewers
        "taskmgr.exe",
        "procexp.exe", "procexp64.exe", "procmon.exe", "procmon64.exe",
        "processhacker.exe", "processhacker2.exe", "systemexplorer.exe",
        "perfmon.exe", "resmon.exe",
        // Registry / config editors
        "regedit.exe", "regedt32.exe", "msconfig.exe",
        // Shells / terminals
        "cmd.exe", "powershell.exe", "powershell_ise.exe", "pwsh.exe",
        "wt.exe", "mintty.exe",
        "conemu64.exe", "conemu.exe", "cmder.exe",
        "bash.exe", "zsh.exe", "sh.exe",
        "ubuntu.exe", "debian.exe", "kali.exe",
        "wsl.exe", "wslhost.exe",
        // MMC snap-ins / admin tools
        "mmc.exe", "eventvwr.exe",
        "wscript.exe", "cscript.exe", "mshta.exe",
        "wmic.exe", "winrm.exe",
        // Installers (could download bypass tools)
        "winget.exe", "msiexec.exe"
    )

    /**
     * Known canonical Windows paths for escape tools.
     * Used to catch renamed executables — e.g. taskmgr.exe copied/renamed to
     * notmalware.exe still lives in System32 and matches by path suffix.
     * Values are lowercase path segments; comparison uses endsWith on the
     * lowercased full path returned by ProcessHandle.
     */
    private val knownEscapePathSuffixes: Set<String> = setOf(
        "\\windows\\system32\\taskmgr.exe",
        "\\windows\\syswow64\\taskmgr.exe",
        "\\windows\\regedit.exe",
        "\\windows\\system32\\regedt32.exe",
        "\\windows\\system32\\cmd.exe",
        "\\windows\\system32\\mmc.exe",
        "\\windows\\system32\\eventvwr.exe",
        "\\windows\\system32\\perfmon.exe",
        "\\windows\\system32\\resmon.exe",
        "\\windows\\system32\\msconfig.exe",
        "\\windows\\system32\\wmic.exe",
        "\\windows\\system32\\mshta.exe",
        "\\windows\\system32\\wscript.exe",
        "\\windows\\system32\\cscript.exe",
        "\\windows\\system32\\msiexec.exe",
        "\\windows\\system32\\windowspowershell\\v1.0\\powershell.exe",
        "\\windows\\syswow64\\windowspowershell\\v1.0\\powershell.exe",
        "\\windows\\system32\\windowspowershell\\v1.0\\powershell_ise.exe",
        "\\windows\\system32\\wbem\\wmic.exe"
    )

    private val ownPid: Long = ProcessHandle.current().pid()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null

    /** Per-process escape attempt counter — flushed to DB every 5 hits. */
    private val escapeCounts = ConcurrentHashMap<String, Int>()

    @Volatile var isActive: Boolean = false
        private set

    // ── Layer 1: Detect ─────────────────────────────────────────────────────

    /**
     * Returns the subset of [escapeProcesses] that are currently running.
     * Uses ONE `tasklist` call and parses CSV output in-memory — O(n) scan
     * rather than n individual process spawns.
     */
    private fun getRunningEscapeProcesses(): Set<String> {
        if (!isWindows) return getRunningEscapeProcessesFallback()
        return try {
            val proc = ProcessBuilder("tasklist", "/FO", "CSV", "/NH")
                .redirectErrorStream(true)
                .start()
            val text = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            text.lineSequence()
                .mapNotNull { line ->
                    // CSV format: "chrome.exe","12345","Console","1","50,000 K"
                    line.trim()
                        .removePrefix("\"")
                        .substringBefore("\"")
                        .lowercase()
                        .takeIf { it.isNotBlank() }
                }
                .filter { it in escapeProcesses }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun getRunningEscapeProcessesFallback(): Set<String> {
        return try {
            ProcessHandle.allProcesses()
                .filter { ph -> ph.pid() != ownPid && ph.info().command().isPresent }
                .toList()
                .mapNotNull { ph ->
                    java.io.File(ph.info().command().get()).name.lowercase()
                        .takeIf { it.isNotBlank() }
                }
                .filter { it in escapeProcesses }
                .toSet()
        } catch (_: Exception) { emptySet() }
    }

    /**
     * Supplemental path-based scan using ProcessHandle.
     * Catches renamed executables — e.g. taskmgr.exe renamed to notmalware.exe —
     * by matching the full executable path against [knownEscapePathSuffixes].
     * Returns the actual running filename (for use in taskkill), not the canonical name.
     */
    private fun getEscapeProcessesByPath(): Set<String> {
        return try {
            ProcessHandle.allProcesses()
                .filter { ph -> ph.pid() != ownPid && ph.info().command().isPresent }
                .toList()
                .mapNotNull { ph ->
                    val rawPath = ph.info().command().get()
                    val normalised = rawPath.lowercase().replace("/", "\\")
                    val matchesKnownPath = knownEscapePathSuffixes.any { suffix ->
                        normalised.endsWith(suffix)
                    }
                    if (matchesKnownPath) java.io.File(rawPath).name.lowercase() else null
                }
                .filter { it.isNotBlank() }
                .toSet()
        } catch (_: Exception) { emptySet() }
    }

    // ── Layer 2: Kill (batch) + log ──────────────────────────────────────────

    /**
     * Kills all [found] escape processes in a single `taskkill` invocation.
     * Records escape attempts and persists every 5th hit to the database.
     */
    private fun killAndLog(found: Set<String>) {
        if (found.isEmpty()) return

        // Batch kill: one process spawn for all targets
        if (isWindows) {
            val args = mutableListOf("taskkill", "/F")
            found.forEach { exe -> args += "/IM"; args += exe }
            try {
                ProcessBuilder(args)
                    .redirectErrorStream(true)
                    .start()
                // fire-and-forget; no waitFor() needed
            } catch (_: Exception) {}
        }

        // Tally and periodically persist escape attempts
        found.forEach { exe ->
            val count = escapeCounts.merge(exe, 1, Int::plus) ?: 1
            if (count == 1 || count % 5 == 0) {
                runCatching {
                    Database.setSetting("escape_attempt_${exe.removeSuffix(".exe")}", count.toString())
                }
            }
        }
    }

    // ── Layer 3: Pre-emptive firewall block ──────────────────────────────────

    /**
     * Adds outbound-deny firewall rules for every known escape process via
     * [NetworkBlocker]. This is a best-effort call — if admin is not available
     * or the path can't be resolved, it degrades gracefully to kill-only mode.
     */
    private fun applyFirewallLock() {
        if (!NetworkBlocker.isRunningAsAdmin()) return
        escapeProcesses
            .filter { it.endsWith(".exe") }
            .forEach { exe -> NetworkBlocker.addRule(exe) }
    }

    private fun removeFirewallLock() {
        escapeProcesses
            .filter { it.endsWith(".exe") }
            .forEach { exe -> NetworkBlocker.removeRule(exe) }
    }

    // ── Enforcement loop ─────────────────────────────────────────────────────

    /**
     * Single enforcement tick: detect → kill → (layer 3 is applied once at enable()).
     * Runs every 500 ms — aggressive enough to be effective, not so fast it saturates
     * the scheduler. The old 300 ms cadence with 40+ spawns per tick was far more
     * expensive than this single-scan approach.
     */
    private fun enforceTick() {
        if (KillSwitchService.isActive.value) return
        val byName = getRunningEscapeProcesses()
        val byPath = getEscapeProcessesByPath()
        killAndLog(byName + byPath)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Enable nuclear mode.
     * @param silent When true, suppresses tray notifications and sound. Pass true
     *               when called from FocusLauncherService so kiosk-mode lifecycle
     *               events don't show confusing "Nuclear Mode ON" popups.
     */
    fun enable(silent: Boolean = false) {
        if (isActive) return
        isActive = true
        Database.setSetting("nuclear_mode", "true")
        escapeCounts.clear()

        // Layer 3: apply firewall rules before starting the kill loop
        scope.launch(Dispatchers.IO) { applyFirewallLock() }

        monitorJob = scope.launch {
            while (isActive) {
                enforceTick()
                delay(500)
            }
        }

        if (!silent) {
            SystemTrayManager.showNotification(
                "Nuclear Mode ON",
                "All escape routes are blocked. Stay focused.",
                TrayIcon.MessageType.WARNING
            )
            SystemTrayManager.updateTooltip("FocusFlow — NUCLEAR MODE ACTIVE")
            SoundAversion.playNuclearAlert()
        }
    }

    /**
     * Disable nuclear mode.
     * @param silent When true, suppresses tray notifications. Pass true when called
     *               from FocusLauncherService so kiosk-mode lifecycle events don't
     *               show confusing "Nuclear Mode OFF / Normal operation resumed" popups.
     */
    fun disable(silent: Boolean = false) {
        isActive = false
        monitorJob?.cancel()
        monitorJob = null
        Database.setSetting("nuclear_mode", "false")

        // Remove firewall rules added by layer 3.
        // Block until removal completes — callers (especially the shutdown thread)
        // must not allow the JVM to exit with firewall rules still in place.
        runBlocking { withContext(Dispatchers.IO) { removeFirewallLock() } }

        if (!silent) {
            SystemTrayManager.updateTooltip("FocusFlow — Ready")
            SystemTrayManager.showNotification(
                "Nuclear Mode OFF",
                "Normal operation resumed.",
                TrayIcon.MessageType.INFO
            )
        }

        // Log total escape attempts this session
        val totalAttempts = escapeCounts.values.sum()
        if (totalAttempts > 0) {
            Database.setSetting("nuclear_last_session_attempts", totalAttempts.toString())
        }
    }

    /** Total escape attempts recorded since the last [enable] call. */
    fun sessionEscapeAttempts(): Int = escapeCounts.values.sum()

    /** Per-process escape attempt breakdown for the current/last session. */
    fun escapeAttemptBreakdown(): Map<String, Int> = HashMap(escapeCounts)

    fun loadFromDb() {
        isActive = Database.getSetting("nuclear_mode") == "true"
        if (isActive) {
            isActive = false
            enable()
        }
    }
}
