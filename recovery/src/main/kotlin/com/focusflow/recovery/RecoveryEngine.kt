package com.focusflow.recovery

import com.sun.jna.Native
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.io.File
import java.sql.DriverManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── Minimal JNA binding (no dependency on main FocusFlow module) ──────────────

interface User32Recovery : StdCallLibrary {
    companion object {
        val INSTANCE: User32Recovery by lazy {
            Native.load("user32", User32Recovery::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }
    fun FindWindowW(lpClassName: String?, lpWindowName: String?): HWND?
    fun ShowWindow(hWnd: HWND, nCmdShow: Int): Boolean
    /** Release a foreground-lock set by LockSetForegroundWindow(LSFW_LOCK).
     *  Call with LSFW_UNLOCK (= 2) to restore normal window-activation behaviour.
     *  Safe to call even when no lock is active — it is a no-op in that case. */
    fun LockSetForegroundWindow(uLockCode: Int): Boolean
}

// ── Data model ────────────────────────────────────────────────────────────────

data class RecoveryStep(val name: String, val description: String)

enum class StepStatus { PENDING, RUNNING, SUCCESS, FAILED, SKIPPED }

data class StepResult(
    val step: RecoveryStep,
    val status: StepStatus,
    val detail: String = ""
)

/**
 * Snapshot of FocusFlow's current enforcement state as read from disk.
 * Used by the UI to show which locks are actually active before running recovery.
 *
 * Field values:
 *   -1  = could not determine (DB missing, PS failed, no read access)
 *    0  = checked and nothing found / flag is false
 *   >0  = items found / flag is true (mapped to 1)
 */
data class EnforcementState(
    val dbFound: Boolean       = false,
    val crashGuard: Boolean    = false,  // launcher_crash_guard == "true"
    val hardLocked: Boolean    = false,  // launcher_hard_locked == "true"
    val nuclearMode: Boolean   = false,  // nuclear_mode == "true"
    val killSwitchCapped: Boolean = false, // killswitch_remaining_today < 300 (i.e. used today)
    val registryPolicies: Int  = -1,     // -1=unknown, 0=none, n=values present
    val firewallRuleCount: Int = -1,     // -1=unknown, 0=none, n=count
    val hostsEntryCount: Int   = -1      // -1=unknown, 0=none, n=count
) {
    /** True if at least one issue was positively detected. */
    val hasAnyIssue: Boolean get() =
        crashGuard || hardLocked || nuclearMode || killSwitchCapped ||
        registryPolicies > 0 || firewallRuleCount > 0 || hostsEntryCount > 0

    /** Human-readable count of confirmed issues (unknown entries excluded). */
    val confirmedIssueCount: Int get() = listOf(
        crashGuard, hardLocked, nuclearMode, killSwitchCapped,
        registryPolicies > 0, firewallRuleCount > 0, hostsEntryCount > 0
    ).count { it }
}

// ── Engine ────────────────────────────────────────────────────────────────────

object RecoveryEngine {

    private val isWindows = System.getProperty("os.name").lowercase().contains("windows")

    private val dbPath: String
        get() = "${System.getProperty("user.home")}/.focusflow/focusflow.db"
            .replace("/", File.separator)

    private const val HOSTS_PATH   = "C:\\Windows\\System32\\drivers\\etc\\hosts"
    private const val HOSTS_MARKER = "# FocusFlow"
    private const val RULE_PREFIX  = "FocusFlow_Block_"

    private const val SW_SHOW    = 5
    private const val LSFW_UNLOCK = 2

    val steps = listOf(
        RecoveryStep(
            "Restore Taskbar",
            "Show the Windows Taskbar and any secondary taskbars hidden by Focus Launcher"
        ),
        RecoveryStep(
            "Clear Enforcement Flags",
            "Reset launcher_crash_guard, launcher_hard_locked, nuclear_mode, and kill-switch state in the FocusFlow database"
        ),
        RecoveryStep(
            "Restore Registry Policies",
            "Remove DisableTaskMgr, NoLogOff, and HideFastUserSwitching registry values applied during kiosk sessions"
        ),
        RecoveryStep(
            "Remove Firewall Rules",
            "Delete all FocusFlow_Block_* outbound-deny rules from Windows Firewall"
        ),
        RecoveryStep(
            "Clean Hosts File",
            "Remove all FocusFlow website-block entries from C:\\Windows\\System32\\drivers\\etc\\hosts"
        ),
        RecoveryStep(
            "Flush DNS Cache",
            "Run ipconfig /flushdns so unblocked sites resolve immediately"
        )
    )

    // ── Current-state detection (called at startup, before running recovery) ────

    /**
     * Reads the database, firewall, and hosts file to determine what is currently
     * locked. Never throws — returns -1/false for any field that couldn't be read.
     * Runs on Dispatchers.IO so the UI stays responsive.
     */
    suspend fun detectCurrentState(): EnforcementState = withContext(Dispatchers.IO) {
        // ── 1. Database flags ────────────────────────────────────────────────
        val dbFile = File(dbPath)
        var crashGuard     = false
        var hardLocked     = false
        var nuclearMode    = false
        var killSwitchUsed = false
        var dbFound        = false

        if (dbFile.exists()) {
            dbFound = true
            runCatching {
                Class.forName("org.sqlite.JDBC")
                DriverManager.getConnection("jdbc:sqlite:$dbPath").use { conn ->
                    val stmt = conn.createStatement()
                    val rs   = stmt.executeQuery("SELECT key, value FROM settings")
                    while (rs.next()) {
                        when (rs.getString("key")) {
                            "launcher_crash_guard"       -> crashGuard     = rs.getString("value") == "true"
                            "launcher_hard_locked"       -> hardLocked     = rs.getString("value") == "true"
                            "nuclear_mode"               -> nuclearMode    = rs.getString("value") == "true"
                            "killswitch_remaining_today" -> {
                                val remaining = rs.getString("value").toIntOrNull() ?: 300
                                killSwitchUsed = remaining < 300
                            }
                        }
                    }
                }
            }
        }

        // ── 2. Registry lockdown policies ────────────────────────────────────
        val regPolicyCount = if (!isWindows) -1 else runCatching {
            val hkcuSystem   = "Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\System"
            val hkcuExplorer = "Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer"
            val hklmSystem   = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System"
            listOf(
                WinReg.HKEY_CURRENT_USER  to (hkcuSystem   to "DisableTaskMgr"),
                WinReg.HKEY_CURRENT_USER  to (hkcuExplorer to "NoLogOff"),
                WinReg.HKEY_LOCAL_MACHINE to (hklmSystem   to "HideFastUserSwitching")
            ).count { (root, pathName) ->
                try { Advapi32Util.registryValueExists(root, pathName.first, pathName.second) }
                catch (_: Throwable) { false }
            }
        }.getOrDefault(-1)

        // ── 3. Firewall rules (fast PS query, no removal) ────────────────────
        val fwCount = if (!isWindows) -1 else runCatching {
            val script = "(Get-NetFirewallRule -ErrorAction SilentlyContinue | " +
                "Where-Object { \$_.DisplayName -like '${RULE_PREFIX}*' } | " +
                "Measure-Object).Count"
            val proc = ProcessBuilder(
                "powershell", "-NonInteractive", "-NoProfile",
                "-ExecutionPolicy", "Bypass", "-Command", script
            ).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
            out.lines().lastOrNull { it.trim().toIntOrNull() != null }
               ?.trim()?.toIntOrNull() ?: 0
        }.getOrDefault(-1)

        // ── 3. Hosts file entries ────────────────────────────────────────────
        val hostsCount = if (!isWindows) -1 else runCatching {
            File(HOSTS_PATH).readLines().count { it.contains(HOSTS_MARKER) }
        }.getOrDefault(-1)

        EnforcementState(
            dbFound           = dbFound,
            crashGuard        = crashGuard,
            hardLocked        = hardLocked,
            nuclearMode       = nuclearMode,
            killSwitchCapped  = killSwitchUsed,
            registryPolicies  = regPolicyCount,
            firewallRuleCount = fwCount,
            hostsEntryCount   = hostsCount
        )
    }

    suspend fun runStep(index: Int, onProgress: (StepResult) -> Unit) {
        val step = steps[index]
        onProgress(StepResult(step, StepStatus.RUNNING))
        val result = withContext(Dispatchers.IO) {
            runCatching {
                when (index) {
                    0 -> restoreTaskbar(step)
                    1 -> clearDatabaseFlags(step)
                    2 -> restoreRegistryPolicies(step)
                    3 -> removeFirewallRules(step)
                    4 -> cleanHostsFile(step)
                    5 -> flushDns(step)
                    else -> StepResult(step, StepStatus.SKIPPED)
                }
            }.getOrElse { e ->
                StepResult(step, StepStatus.FAILED, e.message ?: "Unexpected error")
            }
        }
        onProgress(result)
    }

    // ── Step 1: Taskbar ──────────────────────────────────────────────────────

    private fun restoreTaskbar(step: RecoveryStep): StepResult {
        if (!isWindows) return StepResult(step, StepStatus.SKIPPED, "Not running on Windows")
        return try {
            val u32 = User32Recovery.INSTANCE

            // Release any foreground lock set by FocusFlow's GlobalKeyboardHook layer.
            // LockSetForegroundWindow(LSFW_UNLOCK = 2) is a no-op when no lock is active,
            // so calling it unconditionally is safe even after a clean exit.
            try { u32.LockSetForegroundWindow(LSFW_UNLOCK) } catch (_: Throwable) {}

            var acted = false
            val primary = u32.FindWindowW("Shell_TrayWnd", null)
            if (primary != null) { u32.ShowWindow(primary, SW_SHOW); acted = true }

            val secondary = u32.FindWindowW("Shell_SecondaryTrayWnd", null)
            if (secondary != null) { u32.ShowWindow(secondary, SW_SHOW); acted = true }

            val detail = if (acted) "Taskbar restored and foreground lock released"
                         else "Taskbar already visible; foreground lock released"
            StepResult(step, StepStatus.SUCCESS, detail)
        } catch (e: Exception) {
            StepResult(step, StepStatus.FAILED, "JNA call failed: ${e.message}")
        }
    }

    // ── Step 2: Database flags ────────────────────────────────────────────────

    private fun clearDatabaseFlags(step: RecoveryStep): StepResult {
        val dbFile = File(dbPath)
        if (!dbFile.exists()) {
            return StepResult(step, StepStatus.SKIPPED, "No database found — FocusFlow may not have been run yet")
        }

        return try {
            Class.forName("org.sqlite.JDBC")
            DriverManager.getConnection("jdbc:sqlite:$dbPath").use { conn ->
                conn.autoCommit = false
                val pstmt = conn.prepareStatement(
                    "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)"
                )

                val flags = mapOf(
                    "launcher_crash_guard"       to "false",
                    "launcher_hard_locked"       to "false",
                    "nuclear_mode"               to "false",
                    "killswitch_remaining_today" to "300",
                    "killswitch_reset_date"      to ""
                )

                var written = 0
                flags.forEach { (key, value) ->
                    pstmt.setString(1, key)
                    pstmt.setString(2, value)
                    pstmt.executeUpdate()
                    written++
                }

                conn.commit()
                pstmt.close()
                StepResult(step, StepStatus.SUCCESS, "$written enforcement flag(s) cleared")
            }
        } catch (e: Exception) {
            StepResult(step, StepStatus.FAILED, "Database error: ${e.message}")
        }
    }

    // ── Step 3: Registry policies ─────────────────────────────────────────────

    private fun restoreRegistryPolicies(step: RecoveryStep): StepResult {
        if (!isWindows) return StepResult(step, StepStatus.SKIPPED, "Not running on Windows")

        var cleared = 0
        var failed  = 0

        // HKCU values — no elevation needed
        val hkcuSystem   = "Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\System"
        val hkcuExplorer = "Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer"
        val hklmSystem   = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System"

        data class RegEntry(val root: WinReg.HKEY, val path: String, val name: String)

        val entries = listOf(
            RegEntry(WinReg.HKEY_CURRENT_USER,  hkcuSystem,   "DisableTaskMgr"),
            RegEntry(WinReg.HKEY_CURRENT_USER,  hkcuExplorer, "NoLogOff"),
            RegEntry(WinReg.HKEY_LOCAL_MACHINE, hklmSystem,   "HideFastUserSwitching")
        )

        for (entry in entries) {
            try {
                // Only delete if the value actually exists to avoid a spurious error
                if (Advapi32Util.registryValueExists(entry.root, entry.path, entry.name)) {
                    Advapi32Util.registryDeleteValue(entry.root, entry.path, entry.name)
                }
                cleared++
            } catch (_: Throwable) {
                failed++
            }
        }

        // Tell the shell to re-read its policy cache
        try {
            Native.load("user32", PolicyUser32Recovery::class.java)
                .UpdatePerUserSystemParameters(0, true)
        } catch (_: Throwable) {}

        return if (failed == 0)
            StepResult(step, StepStatus.SUCCESS, "$cleared registry policy value(s) cleared")
        else
            StepResult(step, StepStatus.SUCCESS,
                "$cleared cleared, $failed skipped (HideFastUserSwitching may need admin)")
    }

    private interface PolicyUser32Recovery : StdCallLibrary {
        fun UpdatePerUserSystemParameters(dwFlags: Int, fWinIni: Boolean): Boolean
    }

    // ── Step 4: Firewall rules ────────────────────────────────────────────────

    private fun removeFirewallRules(step: RecoveryStep): StepResult {
        if (!isWindows) return StepResult(step, StepStatus.SKIPPED, "Not running on Windows")

        return try {
            val script = """
                try {
                    ${'$'}rules = Get-NetFirewallRule -ErrorAction SilentlyContinue |
                        Where-Object { ${'$'}_.DisplayName -like '${RULE_PREFIX}*' }
                    ${'$'}count = (${'$'}rules | Measure-Object).Count
                    if (${'$'}count -gt 0) {
                        ${'$'}rules | Remove-NetFirewallRule -ErrorAction SilentlyContinue
                    }
                    Write-Output ${'$'}count
                } catch {
                    Write-Output "0"
                }
            """.trimIndent()

            val proc = ProcessBuilder(
                "powershell", "-NonInteractive", "-NoProfile",
                "-ExecutionPolicy", "Bypass", "-Command", script
            ).redirectErrorStream(true).start()

            val output = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()

            val count = output.lines()
                .lastOrNull { it.trim().toIntOrNull() != null }
                ?.trim()?.toIntOrNull() ?: 0

            StepResult(step, StepStatus.SUCCESS, "$count firewall rule(s) removed")
        } catch (e: Exception) {
            StepResult(step, StepStatus.FAILED, "PowerShell error: ${e.message}")
        }
    }

    // ── Step 5: Hosts file ────────────────────────────────────────────────────

    private fun cleanHostsFile(step: RecoveryStep): StepResult {
        if (!isWindows) return StepResult(step, StepStatus.SKIPPED, "Not running on Windows")

        val hostsFile = File(HOSTS_PATH)
        if (!hostsFile.exists()) {
            return StepResult(step, StepStatus.SKIPPED, "Hosts file not found at $HOSTS_PATH")
        }
        if (!hostsFile.canWrite()) {
            return StepResult(
                step, StepStatus.FAILED,
                "No write access to hosts file — please run this tool as Administrator"
            )
        }

        return try {
            val original = hostsFile.readText()
                .replace("\r\n", "\n")
                .replace("\r", "\n")

            val lines    = original.lines()
            val filtered = lines.filter { !it.contains(HOSTS_MARKER) }
            val removed  = lines.size - filtered.size

            hostsFile.writeText(filtered.joinToString("\n") + "\n")

            val detail = if (removed > 0) "$removed blocked-site line(s) removed"
                         else "No FocusFlow entries found in hosts file"
            StepResult(step, StepStatus.SUCCESS, detail)
        } catch (e: Exception) {
            StepResult(step, StepStatus.FAILED, "File error: ${e.message}")
        }
    }

    // ── Step 5: DNS flush ─────────────────────────────────────────────────────

    private fun flushDns(step: RecoveryStep): StepResult {
        if (!isWindows) return StepResult(step, StepStatus.SKIPPED, "Not running on Windows")
        return try {
            val proc = ProcessBuilder("ipconfig", "/flushdns")
                .redirectErrorStream(true)
                .start()
            proc.waitFor()
            StepResult(step, StepStatus.SUCCESS, "DNS resolver cache flushed")
        } catch (e: Exception) {
            StepResult(step, StepStatus.FAILED, "ipconfig error: ${e.message}")
        }
    }
}
