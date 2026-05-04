package com.focusflow.enforcement

import com.focusflow.data.Database
import com.focusflow.services.SoundAversion
import com.focusflow.services.SystemTrayManager
import kotlinx.coroutines.*
import java.awt.TrayIcon

/**
 * NuclearMode
 *
 * Maximum enforcement: kills any process on the "escape routes" list every 300ms.
 * This prevents users from opening Task Manager, PowerShell, regedit, etc. to
 * circumvent the app blocker.
 *
 * Kills via taskkill /F /IM on Windows (avoids "destroy of current process not allowed"
 * from ProcessHandle.destroyForcibly() and handles elevated processes better).
 * Own PID is always excluded regardless.
 */
object NuclearMode {

    /** Processes that could be used to escape focus enforcement. */
    private val escapeProcesses = setOf(
        // Task management / process viewers
        "taskmgr.exe", "procexp.exe", "procexp64.exe", "procmon.exe", "procmon64.exe",
        "processhacker.exe", "processhacker2.exe", "systemexplorer.exe",
        "perfmon.exe", "resmon.exe",                 // Resource/perf monitor
        // Registry / config editors
        "regedit.exe", "regedt32.exe", "msconfig.exe", "gpedit.msc", "compmgmt.msc",
        // Shells / terminals
        "cmd.exe", "powershell.exe", "powershell_ise.exe", "pwsh.exe",
        "wt.exe",               // Windows Terminal
        "mintty.exe",           // Git Bash / Cygwin terminal
        "conemu64.exe", "conemu.exe", "cmder.exe",
        "bash.exe", "zsh.exe", "sh.exe",             // WSL shells
        "ubuntu.exe", "debian.exe", "kali.exe",      // WSL distros
        "wsl.exe", "wslhost.exe",
        // MMC snap-ins / admin tools
        "mmc.exe", "eventvwr.exe", "diskmgmt.msc", "services.msc",
        "lusrmgr.msc", "secpol.msc",
        // Script engines (can be used to kill processes)
        "wscript.exe", "cscript.exe", "mshta.exe",
        // WMI / remote execution
        "wmic.exe", "winrm.exe",
        // Installer/package managers (can add/remove apps to bypass)
        "winget.exe", "msiexec.exe"
    )

    private val ownPid: Long = ProcessHandle.current().pid()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null

    @Volatile var isActive: Boolean = false
        private set

    fun enable() {
        if (isActive) return
        isActive = true
        Database.setSetting("nuclear_mode", "true")

        monitorJob = scope.launch {
            while (isActive) {
                killEscapeProcesses()
                delay(300)
            }
        }

        SystemTrayManager.showNotification(
            "Nuclear Mode ON",
            "All escape routes are blocked. Stay focused.",
            TrayIcon.MessageType.WARNING
        )
        SystemTrayManager.updateTooltip("FocusFlow — NUCLEAR MODE ACTIVE")
        SoundAversion.playBlockAlert()
    }

    fun disable() {
        isActive = false
        monitorJob?.cancel()
        monitorJob = null
        Database.setSetting("nuclear_mode", "false")
        SystemTrayManager.updateTooltip("FocusFlow — Ready")
        SystemTrayManager.showNotification(
            "Nuclear Mode OFF",
            "Normal operation resumed.",
            TrayIcon.MessageType.INFO
        )
    }

    private fun killEscapeProcesses() {
        if (isWindows) {
            // On Windows, use taskkill for each escape process name — more reliable than
            // ProcessHandle.destroyForcibly() which throws on own PID and can't kill
            // elevated processes from a non-elevated context.
            escapeProcesses.forEach { exeName ->
                try {
                    ProcessBuilder("taskkill", "/F", "/IM", exeName)
                        .redirectErrorStream(true)
                        .start()
                        .waitFor()
                } catch (_: Exception) {}
            }
        } else {
            // Non-Windows fallback: ProcessHandle (own PID always excluded)
            try {
                ProcessHandle.allProcesses()
                    .filter { ph ->
                        ph.pid() != ownPid && ph.info().command().isPresent
                    }
                    .forEach { ph ->
                        val exe = java.io.File(ph.info().command().get()).name.lowercase()
                        if (exe in escapeProcesses) {
                            runCatching { ph.destroyForcibly() }
                        }
                    }
            } catch (_: Exception) {}
        }
    }

    fun loadFromDb() {
        isActive = Database.getSetting("nuclear_mode") == "true"
        if (isActive) {
            isActive = false
            enable()
        }
    }
}
