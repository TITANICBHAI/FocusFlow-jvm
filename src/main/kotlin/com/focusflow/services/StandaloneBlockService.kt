package com.focusflow.services

import com.focusflow.data.Database
import com.focusflow.data.models.StandaloneBlock
import com.focusflow.enforcement.ProcessMonitor
import com.focusflow.enforcement.NetworkBlocker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.TrayIcon

object StandaloneBlockService {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var watchJob: Job? = null

    private val _block = MutableStateFlow<StandaloneBlock?>(null)
    val block: StateFlow<StandaloneBlock?> = _block

    val isActive: Boolean get() {
        val b = _block.value ?: return false
        return b.untilMs > System.currentTimeMillis() && b.processNames.isNotEmpty()
    }

    fun start(processNames: List<String>, durationMs: Long) {
        val untilMs = System.currentTimeMillis() + durationMs
        val newBlock = StandaloneBlock(processNames = processNames, untilMs = untilMs)
        _block.value = newBlock
        Database.setSetting("standalone_block_processes", processNames.joinToString(","))
        Database.setSetting("standalone_block_until", untilMs.toString())
        ProcessMonitor.standaloneBlockedProcesses = processNames.map { it.lowercase() }.toSet()
        startWatcher()
        SystemTrayManager.showNotification(
            "Block Started",
            "${processNames.size} app(s) blocked for ${durationMs / 60_000}m",
            TrayIcon.MessageType.WARNING
        )
        SystemTrayManager.updateTooltip("FocusFlow — Blocking ${processNames.size} apps")
    }

    fun addTime(extraMs: Long) {
        val current = _block.value ?: return
        val newUntil = maxOf(current.untilMs, System.currentTimeMillis()) + extraMs
        _block.value = current.copy(untilMs = newUntil)
        Database.setSetting("standalone_block_until", newUntil.toString())
    }

    fun addApps(moreProcesses: List<String>) {
        val current = _block.value ?: return
        val merged = (current.processNames + moreProcesses).distinct()
        _block.value = current.copy(processNames = merged)
        Database.setSetting("standalone_block_processes", merged.joinToString(","))
        ProcessMonitor.standaloneBlockedProcesses = merged.map { it.lowercase() }.toSet()
    }

    fun stop() {
        _block.value = null
        watchJob?.cancel()
        watchJob = null
        ProcessMonitor.standaloneBlockedProcesses = emptySet()
        Database.setSetting("standalone_block_processes", "")
        Database.setSetting("standalone_block_until",     "0")
        SystemTrayManager.updateTooltip("FocusFlow — Ready")
    }

    fun loadFromDb() {
        val processes = Database.getSetting("standalone_block_processes") ?: ""
        val until     = Database.getSetting("standalone_block_until")?.toLongOrNull() ?: 0L
        if (processes.isNotBlank() && until > System.currentTimeMillis()) {
            val pList = processes.split(",").filter { it.isNotBlank() }
            _block.value = StandaloneBlock(processNames = pList, untilMs = until)
            ProcessMonitor.standaloneBlockedProcesses = pList.map { it.lowercase() }.toSet()
            startWatcher()
        }
    }

    private fun startWatcher() {
        watchJob?.cancel()
        watchJob = scope.launch {
            while (isActive) {
                delay(10_000)
                val b = _block.value ?: return@launch
                if (System.currentTimeMillis() >= b.untilMs) {
                    ProcessMonitor.standaloneBlockedProcesses = emptySet()
                    _block.value = null
                    SystemTrayManager.showNotification(
                        "Block Ended",
                        "Standalone block expired.",
                        TrayIcon.MessageType.INFO
                    )
                    SystemTrayManager.updateTooltip("FocusFlow — Ready")
                    return@launch
                }
            }
        }
    }

    fun remainingMs(): Long {
        val b = _block.value ?: return 0L
        return maxOf(0L, b.untilMs - System.currentTimeMillis())
    }
}
