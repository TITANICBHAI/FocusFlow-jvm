package com.focusflow.recovery

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Writes a plain-text recovery log to the user's Desktop (fallback: TEMP).
 *
 * Filename: FocusFlow-Recovery-<yyyyMMdd-HHmmss>.log
 *
 * Usage:
 *   RecoveryLogger.start(isAdmin, focusFlowWasRunning)
 *   RecoveryLogger.logStep(number, step, result)   // called per step
 *   val path = RecoveryLogger.finish(anyFailed)     // returns file path, "" on error
 */
object RecoveryLogger {

    private val sessionStamp: String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))

    private val logFile: File by lazy {
        val desktop = File(System.getProperty("user.home"), "Desktop")
        val dir = if (desktop.exists() && desktop.canWrite()) desktop
                  else File(System.getProperty("java.io.tmpdir"))
        File(dir, "FocusFlow-Recovery-$sessionStamp.log")
    }

    private val entries = mutableListOf<String>()

    private fun sep(char: Char = '=') = char.toString().repeat(64)

    fun start(isAdmin: Boolean, focusFlowWasRunning: Boolean) {
        entries.clear()
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        entries += sep()
        entries += "  FocusFlow Emergency Recovery Tool  v1.0.4"
        entries += "  Run started : $now"
        entries += sep('-')
        entries += "  OS          : ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})"
        entries += "  Java        : ${System.getProperty("java.version")}"
        entries += "  Admin       : $isAdmin"
        entries += "  FF running  : $focusFlowWasRunning"
        entries += sep()
        entries += ""
    }

    fun logStep(number: Int, step: RecoveryStep, result: StepResult) {
        val time   = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
        val status = result.status.name.padEnd(8)
        val detail = if (result.detail.isNotBlank()) "  →  ${result.detail}" else ""
        entries += "[$time]  STEP $number  [$status]  ${step.name}$detail"
    }

    /**
     * Writes all collected entries to disk.
     * @return Absolute path of the log file, or empty string if writing failed.
     */
    fun finish(anyFailed: Boolean): String {
        entries += ""
        entries += sep('-')
        entries += if (anyFailed)
            "  RESULT  : PARTIAL — one or more steps failed (check FAILED lines above)"
        else
            "  RESULT  : SUCCESS — all steps completed"
        entries += sep()

        return try {
            logFile.parentFile?.mkdirs()
            logFile.writeText(entries.joinToString(System.lineSeparator()))
            logFile.absolutePath
        } catch (_: Exception) {
            ""
        }
    }
}
