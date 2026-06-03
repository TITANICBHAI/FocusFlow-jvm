package com.focusflow.enforcement

import java.io.File
import java.time.LocalDateTime

/**
 * EnforcementLog
 *
 * Lightweight append-only log for enforcement-layer warnings and soft failures.
 * Writes to ~/.focusflow/enforcement.log — separate from crash.log so the two
 * are easy to distinguish (crash.log = fatal / DB, enforcement.log = Win32 soft fails).
 *
 * Rules:
 *  - Never throws. Every write is wrapped in runCatching.
 *  - Never blocks the caller for more than a filesystem append.
 *  - Only called inside existing catch blocks — control flow is never changed.
 */
internal object EnforcementLog {

    private val logFile: File by lazy {
        File(System.getProperty("user.home") + "/.focusflow").also { it.mkdirs() }
            .let { File(it, "enforcement.log") }
    }

    fun warn(tag: String, message: String, cause: Throwable? = null) {
        runCatching {
            val ts = LocalDateTime.now()
            val causeStr = cause?.let { " | ${it.javaClass.simpleName}: ${it.message}" } ?: ""
            logFile.appendText("[$ts] WARN  [$tag] $message$causeStr\n")
        }
    }

    fun info(tag: String, message: String) {
        runCatching {
            val ts = LocalDateTime.now()
            logFile.appendText("[$ts] INFO  [$tag] $message\n")
        }
    }
}
