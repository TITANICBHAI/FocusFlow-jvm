package com.focusflow.enforcement

import com.focusflow.services.ResourceMonitorService
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

    /**
     * Writes the HKCU Run entry so FocusFlow launches at login.
     * No admin rights required — HKCU is always accessible to the current user.
     * Returns true if the entry was successfully written and verified.
     */
    fun enable(): Boolean {
        if (!isWindows) return false
        ResourceMonitorService.sendModeEvent(
            title       = "🚀 Launch at Login Enabled",
            description = "User enabled FocusFlow to run automatically at Windows login.",
            color       = 3447003 // blue
        )
        val exePath = resolveExePath()
        // A bare filename (no directory) would be useless in the Run key — Windows
        // won't find it at login. Bail early so the UI can report failure.
        if (!File(exePath).isAbsolute) return false

        val written = try {
            Advapi32Util.registrySetStringValue(
                WinReg.HKEY_CURRENT_USER, RUN_KEY, APP_NAME, "\"$exePath\""
            )
            true
        } catch (_: Exception) {
            // Fallback: reg.exe CLI. Use waitFor() so we know if it succeeded.
            try {
                ProcessBuilder(
                    "reg", "add", "HKCU\\$RUN_KEY",
                    "/v", APP_NAME, "/t", "REG_SZ", "/d", "\"$exePath\"", "/f"
                ).start().waitFor() == 0
            } catch (_: Exception) { false }
        }
        // Verify the key actually landed — guards against silent write failures.
        return written && isEnabled()
    }

    /**
     * Removes the HKCU Run entry.
     * No admin rights required — HKCU is always accessible to the current user.
     * Returns true if the entry is confirmed absent after the call.
     */
    fun disable(): Boolean {
        if (!isWindows) return true   // not applicable on this platform
        ResourceMonitorService.sendModeEvent(
            title       = "🚫 Launch at Login Disabled",
            description = "User disabled FocusFlow from running automatically at Windows login.",
            color       = 15844367 // yellow
        )
        if (!isEnabled()) return true  // already absent — nothing to do
        val deleted = try {
            Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, APP_NAME)
            true
        } catch (_: Exception) {
            // Fallback: reg.exe CLI.
            try {
                ProcessBuilder(
                    "reg", "delete", "HKCU\\$RUN_KEY", "/v", APP_NAME, "/f"
                ).start().waitFor() == 0
            } catch (_: Exception) { false }
        }
        // Verify the key is actually gone.
        return deleted && !isEnabled()
    }

    /**
     * Exposed so the onboarding relaunch-as-admin button can find the exe path.
     */
    internal fun resolveExePath(): String {
        val processCmd = ProcessHandle.current().info().command().orElse("")

        // Case 1: already running as FocusFlow.exe (rare — JVM usually shows java.exe)
        if (processCmd.endsWith("FocusFlow.exe", ignoreCase = true) && File(processCmd).exists())
            return processCmd

        // Case 2: Compose Desktop distributable layout
        //   <install>/app/runtime/bin/java.exe  →  go up 4 levels → <install>/FocusFlow.exe
        if (processCmd.isNotBlank()) {
            var dir: File? = File(processCmd)
            repeat(4) { dir = dir?.parentFile }
            val candidate = dir?.let { File(it, "FocusFlow.exe") }
            if (candidate?.exists() == true) return candidate.absolutePath
        }

        // Case 3: working directory is the install root
        val fromUserDir = File(System.getProperty("user.dir", ""), "FocusFlow.exe")
        if (fromUserDir.exists()) return fromUserDir.absolutePath

        // Case 4: one level up from working directory
        val fromParent = File(System.getProperty("user.dir", "")).parentFile
            ?.let { File(it, "FocusFlow.exe") }
        if (fromParent?.exists() == true) return fromParent.absolutePath

        // Fallback
        return "FocusFlow.exe"
    }
}
