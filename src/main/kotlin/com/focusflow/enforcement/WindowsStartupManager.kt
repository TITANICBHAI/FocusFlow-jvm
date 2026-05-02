package com.focusflow.enforcement

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File

/**
 * WindowsStartupManager
 *
 * Adds / removes a HKCU\Software\Microsoft\Windows\CurrentVersion\Run registry
 * entry so FocusFlow JVM launches automatically on Windows login.
 *
 * Uses JNA Advapi32Util for registry access — no admin rights required for HKCU.
 */
object WindowsStartupManager {

    private const val RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val APP_NAME = "FocusFlow"

    fun isEnabled(): Boolean {
        if (!isWindows) return false
        return try {
            Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, APP_NAME)
        } catch (_: Exception) {
            false
        }
    }

    fun enable() {
        if (!isWindows) return
        val exePath = resolveExePath()
        try {
            Advapi32Util.registrySetStringValue(
                WinReg.HKEY_CURRENT_USER, RUN_KEY, APP_NAME, "\"$exePath\""
            )
        } catch (_: Exception) {
            // Fallback: reg.exe CLI
            ProcessBuilder(
                "reg", "add", "HKCU\\$RUN_KEY",
                "/v", APP_NAME, "/t", "REG_SZ", "/d", "\"$exePath\"", "/f"
            ).start()
        }
    }

    fun disable() {
        if (!isWindows) return
        try {
            if (isEnabled()) {
                Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, APP_NAME)
            }
        } catch (_: Exception) {
            ProcessBuilder(
                "reg", "delete", "HKCU\\$RUN_KEY", "/v", APP_NAME, "/f"
            ).start()
        }
    }

    private fun resolveExePath(): String {
        val processCmd = ProcessHandle.current().info().command().orElse("")
        if (processCmd.endsWith(".exe", ignoreCase = true)) return processCmd
        val appDir = File(System.getProperty("user.dir"))
        val candidate = File(appDir, "FocusFlow.exe")
        return if (candidate.exists()) candidate.absolutePath
        else File(appDir.parentFile, "FocusFlow.exe").absolutePath
    }
}
