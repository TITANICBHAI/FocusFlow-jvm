package com.focusflow.recovery

import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef.HWND
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
}

// ── Data model ────────────────────────────────────────────────────────────────

data class RecoveryStep(val name: String, val description: String)

enum class StepStatus { PENDING, RUNNING, SUCCESS, FAILED, SKIPPED }

data class StepResult(
    val step: RecoveryStep,
    val status: StepStatus,
    val detail: String = ""
)

// ── Engine ────────────────────────────────────────────────────────────────────

object RecoveryEngine {

    private val isWindows = System.getProperty("os.name").lowercase().contains("windows")

    private val dbPath: String
        get() = "${System.getProperty("user.home")}/.focusflow/focusflow.db"
            .replace("/", File.separator)

    private const val HOSTS_PATH   = "C:\\Windows\\System32\\drivers\\etc\\hosts"
    private const val HOSTS_MARKER = "# FocusFlow"
    private const val RULE_PREFIX  = "FocusFlow_Block_"

    private const val SW_SHOW = 5

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

    suspend fun runStep(index: Int, onProgress: (StepResult) -> Unit) {
        val step = steps[index]
        onProgress(StepResult(step, StepStatus.RUNNING))
        val result = withContext(Dispatchers.IO) {
            runCatching {
                when (index) {
                    0 -> restoreTaskbar(step)
                    1 -> clearDatabaseFlags(step)
                    2 -> removeFirewallRules(step)
                    3 -> cleanHostsFile(step)
                    4 -> flushDns(step)
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
            var acted = false

            val primary = u32.FindWindowW("Shell_TrayWnd", null)
            if (primary != null) { u32.ShowWindow(primary, SW_SHOW); acted = true }

            val secondary = u32.FindWindowW("Shell_SecondaryTrayWnd", null)
            if (secondary != null) { u32.ShowWindow(secondary, SW_SHOW); acted = true }

            val detail = if (acted) "Taskbar restored successfully" else "Taskbar was already visible"
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

    // ── Step 3: Firewall rules ────────────────────────────────────────────────

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

    // ── Step 4: Hosts file ────────────────────────────────────────────────────

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
