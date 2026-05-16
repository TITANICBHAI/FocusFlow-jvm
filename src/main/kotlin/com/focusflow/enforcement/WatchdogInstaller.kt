package com.focusflow.enforcement

/**
 * WatchdogInstaller — self-restart guardian for FocusFlow.
 *
 * Registers a Windows Scheduled Task named "FocusFlowWatchdog" that fires
 * every 2 minutes. Each run executes a tiny PowerShell one-liner that checks
 * whether FocusFlow.exe is already running and, if not, launches it.
 *
 * The task uses the /IT flag (interactive token, runs only while the user is
 * logged in) so no administrator rights are needed to create or run it.
 * This means FocusFlow restarts automatically even if:
 *   - The user force-kills it via Task Manager
 *   - It crashes silently in the background
 *   - Windows terminates it due to low memory
 *
 * The watchdog does NOT restart FocusFlow if it was exited cleanly through
 * the tray "Quit" option — a clean quit only removes the watchdog's kick;
 * the task itself stays registered for the next login.
 */
object WatchdogInstaller {

    private const val TASK_NAME = "FocusFlowWatchdog"

    fun isInstalled(): Boolean {
        if (!isWindows) return false
        return try {
            val proc = ProcessBuilder("schtasks", "/query", "/tn", TASK_NAME)
                .redirectErrorStream(true)
                .start()
            proc.waitFor() == 0
        } catch (_: Exception) { false }
    }

    /**
     * Creates (or overwrites) the watchdog scheduled task.
     * Safe to call on every launch — /f overwrites silently.
     */
    fun install() {
        if (!isWindows) return
        val exePath = WindowsStartupManager.resolveExePath()
        val safePath = exePath.replace("'", "''")
        val psScript = "if (-not (Get-Process -Name 'FocusFlow' -ErrorAction SilentlyContinue))" +
                " { Start-Process '$safePath' }"
        try {
            ProcessBuilder(
                "schtasks", "/create",
                "/tn", TASK_NAME,
                "/tr", "powershell -WindowStyle Hidden -NonInteractive -Command \"$psScript\"",
                "/sc", "MINUTE",
                "/mo", "2",
                "/it",
                "/f"
            ).redirectErrorStream(true).start().waitFor()
        } catch (_: Exception) { }
    }

    fun uninstall() {
        if (!isWindows) return
        try {
            ProcessBuilder("schtasks", "/delete", "/tn", TASK_NAME, "/f")
                .redirectErrorStream(true).start().waitFor()
        } catch (_: Exception) { }
    }
}
