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

    /** True when there is an active block whose window has started and not yet ended. */
    val isActive: Boolean get() {
        val b = _block.value ?: return false
        val now = System.currentTimeMillis()
        val started = b.startMs == null || now >= b.startMs
        return started && b.untilMs > now && b.processNames.isNotEmpty()
    }

    /** True when a block is configured (may be scheduled but not yet started). */
    val isScheduled: Boolean get() {
        val b = _block.value ?: return false
        val now = System.currentTimeMillis()
        return b.untilMs > now && b.processNames.isNotEmpty()
    }

    /** Starts or schedules a standalone block.
     *  @param startMs epoch-ms when enforcement should begin; null = immediately. */
    fun start(processNames: List<String>, durationMs: Long, startMs: Long? = null) {
        val resolvedStart = startMs?.coerceAtLeast(System.currentTimeMillis()) ?: System.currentTimeMillis()
        val untilMs = resolvedStart + durationMs
        val newBlock = StandaloneBlock(
            processNames = processNames,
            untilMs      = untilMs,
            startMs      = if (startMs != null && startMs > System.currentTimeMillis()) startMs else null
        )
        _block.value = newBlock
        Database.setSetting("standalone_block_processes", processNames.joinToString(","))
        Database.setSetting("standalone_block_until",     untilMs.toString())
        Database.setSetting("standalone_block_start",     (newBlock.startMs ?: 0L).toString())

        if (newBlock.startMs == null) {
            // Immediate enforcement
            ProcessMonitor.standaloneBlockedProcesses = processNames.map { it.lowercase() }.toSet()
            SystemTrayManager.showNotification(
                "Block Started",
                "${processNames.size} app(s) blocked for ${durationMs / 60_000}m",
                TrayIcon.MessageType.WARNING
            )
            SystemTrayManager.updateTooltip("FocusFlow — Blocking ${processNames.size} apps")
        } else {
            // Scheduled — enforcement will kick in when startMs is reached
            SystemTrayManager.showNotification(
                "Block Scheduled",
                "${processNames.size} app(s) will be blocked from the scheduled time.",
                TrayIcon.MessageType.INFO
            )
        }
        startWatcher()
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
        if (isActive) {
            ProcessMonitor.standaloneBlockedProcesses = merged.map { it.lowercase() }.toSet()
        }
    }

    fun stop() {
        _block.value = null
        watchJob?.cancel()
        watchJob = null
        ProcessMonitor.standaloneBlockedProcesses = emptySet()
        Database.setSetting("standalone_block_processes", "")
        Database.setSetting("standalone_block_until",     "0")
        Database.setSetting("standalone_block_start",     "0")
        SystemTrayManager.updateTooltip("FocusFlow — Ready")
    }

    fun loadFromDb() {
        val processes  = Database.getSetting("standalone_block_processes") ?: ""
        val until      = Database.getSetting("standalone_block_until")?.toLongOrNull() ?: 0L
        val startEpoch = Database.getSetting("standalone_block_start")?.toLongOrNull() ?: 0L
        val startMs    = if (startEpoch > 0L) startEpoch else null
        val now        = System.currentTimeMillis()
        if (processes.isNotBlank() && until > now) {
            val pList = processes.split(",").filter { it.isNotBlank() }
            _block.value = StandaloneBlock(processNames = pList, untilMs = until, startMs = startMs)
            if (startMs == null || now >= startMs) {
                ProcessMonitor.standaloneBlockedProcesses = pList.map { it.lowercase() }.toSet()
            }
            startWatcher()
        }
    }

    private fun startWatcher() {
        watchJob?.cancel()
        watchJob = scope.launch {
            while (true) {
                delay(5_000)
                val b   = _block.value ?: return@launch
                val now = System.currentTimeMillis()

                // Activate enforcement when scheduled start is reached
                if (b.startMs != null && now >= b.startMs && ProcessMonitor.standaloneBlockedProcesses.isEmpty()) {
                    ProcessMonitor.standaloneBlockedProcesses = b.processNames.map { it.lowercase() }.toSet()
                    SystemTrayManager.showNotification(
                        "Block Started",
                        "${b.processNames.size} app(s) are now blocked.",
                        TrayIcon.MessageType.WARNING
                    )
                    SystemTrayManager.updateTooltip("FocusFlow — Blocking ${b.processNames.size} apps")
                }

                // Expire when end time is reached
                if (now >= b.untilMs) {
                    ProcessMonitor.standaloneBlockedProcesses = emptySet()
                    _block.value = null
                    Database.setSetting("standalone_block_processes", "")
                    Database.setSetting("standalone_block_until",     "0")
                    Database.setSetting("standalone_block_start",     "0")
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

    /** ms until the scheduled block starts, or 0 if already started / no block. */
    fun startsInMs(): Long {
        val b = _block.value ?: return 0L
        val startMs = b.startMs ?: return 0L
        return maxOf(0L, startMs - System.currentTimeMillis())
    }
}
