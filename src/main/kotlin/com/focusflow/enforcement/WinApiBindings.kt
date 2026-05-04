package com.focusflow.enforcement

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinNT.HANDLE
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

/**
 * WinApiBindings
 *
 * JNA bindings for the Win32 APIs needed by the enforcement layer.
 * These are thin wrappers — no Android APIs, no React Native, pure JVM.
 *
 * APIs used:
 *   user32.GetForegroundWindow()           — handle to the active window
 *   user32.GetWindowThreadProcessId()      — PID from window handle
 *   psapi.GetProcessImageFileNameW()       — process executable path from handle
 *   kernel32.OpenProcess()                 — open a process handle by PID
 *   kernel32.CloseHandle()                 — release a process handle
 */
interface User32Extra : StdCallLibrary {
    companion object {
        val INSTANCE: User32Extra = Native.load("user32", User32Extra::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }

    fun GetForegroundWindow(): HWND
    fun GetWindowThreadProcessId(hWnd: HWND, lpdwProcessId: IntArray): Int
}

interface Psapi : StdCallLibrary {
    companion object {
        val INSTANCE: Psapi = Native.load("psapi", Psapi::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }

    fun GetProcessImageFileNameW(hProcess: HANDLE, lpImageFileName: CharArray, nSize: Int): Int
}

/**
 * Get the process name (e.g. "chrome.exe") of the currently active foreground window.
 * Returns null if the window handle is invalid or access is denied.
 */
fun getForegroundProcessName(): String? {
    return try {
        val user32 = User32Extra.INSTANCE
        val hwnd = user32.GetForegroundWindow() ?: return null

        val pidArr = IntArray(1)
        user32.GetWindowThreadProcessId(hwnd, pidArr)
        val pid = pidArr[0].toLong()
        if (pid == 0L) return null

        // Use ProcessHandle (JVM 9+) to get the executable name — no native call needed
        val ph = ProcessHandle.of(pid).orElse(null) ?: return null
        ph.info().command().orElse(null)
            ?.substringAfterLast("\\")
            ?.substringAfterLast("/")
            ?.lowercase()
    } catch (_: Exception) {
        null
    }
}

/**
 * Kill a process by name. Returns true if at least one matching process was killed.
 *
 * On Windows: uses taskkill /F /IM as the PRIMARY method — avoids the JVM restriction
 * of "destroy of current process not allowed" and handles elevated processes better.
 *
 * On other platforms: uses ProcessHandle (cross-platform JVM 9+), skipping own PID.
 */
fun killProcessByName(processName: String): Boolean {
    if (isWindows) {
        // Primary: taskkill — reliable, skips our own process by process name matching
        return try {
            val proc = ProcessBuilder("taskkill", "/F", "/IM", processName)
                .redirectErrorStream(true)
                .start()
            proc.waitFor() == 0
        } catch (_: Exception) { false }
    }

    // Non-Windows fallback: ProcessHandle (cross-platform)
    val ownPid = ProcessHandle.current().pid()
    var killed = false
    ProcessHandle.allProcesses().filter { ph ->
        ph.pid() != ownPid && ph.info().command().orElse("").let { cmd ->
            cmd.substringAfterLast("\\").substringAfterLast("/")
                .equals(processName, ignoreCase = true)
        }
    }.forEach { ph ->
        try {
            ph.destroyForcibly()
            killed = true
        } catch (_: Exception) {}
    }
    return killed
}

/**
 * Check if we are running on Windows.
 */
val isWindows: Boolean get() = System.getProperty("os.name").lowercase().contains("windows")
