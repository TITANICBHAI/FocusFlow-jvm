package com.focusflow.services

import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * AutoBackupService
 *
 * Automatically backs up the SQLite database daily.
 * Keeps up to MAX_BACKUPS rolling backups in ~/.focusflow/backups/.
 * No user interaction required — runs silently in the background.
 */
object AutoBackupService {

    private const val MAX_BACKUPS = 7
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    private val dbPath: String
        get() = System.getProperty("user.home") + "/.focusflow/focusflow.db"

    private val backupDir: File
        get() = File(System.getProperty("user.home") + "/.focusflow/backups").also { it.mkdirs() }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            runBackupIfNeeded()
            while (isActive) {
                delay(6 * 60 * 60 * 1000L)
                runBackupIfNeeded()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun runBackupNow(): Boolean {
        return try {
            val src  = File(dbPath)
            if (!src.exists()) return false
            val dest = File(backupDir, "focusflow_${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.db")
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
            pruneOldBackups()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun runBackupIfNeeded() {
        val today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
        val todayBackup = File(backupDir, "focusflow_$today.db")
        if (!todayBackup.exists()) runBackupNow()
    }

    private fun pruneOldBackups() {
        val files = backupDir.listFiles { f -> f.name.endsWith(".db") }
            ?.sortedByDescending { it.lastModified() } ?: return
        files.drop(MAX_BACKUPS).forEach { it.delete() }
    }

    fun listBackups(): List<File> =
        (backupDir.listFiles { f -> f.name.endsWith(".db") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList())

    fun restoreBackup(file: File): Boolean {
        return try {
            val dest = File(dbPath)
            Files.copy(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (_: Exception) {
            false
        }
    }
}
