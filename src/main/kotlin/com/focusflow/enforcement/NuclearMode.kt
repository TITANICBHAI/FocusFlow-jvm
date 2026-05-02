package com.focusflow.enforcement

import com.focusflow.data.Database
import com.focusflow.services.SoundAversion
import com.focusflow.services.SystemTrayManager
import kotlinx.coroutines.*
import java.awt.TrayIcon

object NuclearMode {

    private val escapeProcesses = setOf(
        "taskmgr.exe", "regedit.exe", "regedit32.exe",
        "procexp.exe", "procexp64.exe", "procmon.exe", "procmon64.exe",
        "msconfig.exe", "gpedit.msc", "compmgmt.msc",
        "cmd.exe", "powershell.exe", "powershell_ise.exe", "pwsh.exe",
        "mmc.exe", "eventvwr.exe"
    )

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
        SoundAversion.playAversion()
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
        try {
            ProcessHandle.allProcesses()
                .filter { it.info().command().isPresent }
                .forEach { ph ->
                    val exe = java.io.File(ph.info().command().get()).name.lowercase()
                    if (exe in escapeProcesses) {
                        ph.destroyForcibly()
                    }
                }
        } catch (_: Exception) {}
    }

    fun loadFromDb() {
        isActive = Database.getSetting("nuclear_mode") == "true"
        if (isActive) {
            isActive = false
            enable()
        }
    }
}
