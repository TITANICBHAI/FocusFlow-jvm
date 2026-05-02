package com.focusflow.enforcement

/**
 * NetworkBlocker
 *
 * Adds and removes Windows Firewall rules via `netsh advfirewall`.
 *
 * JVM equivalent of Android's NetworkBlockerVpnService.
 *
 * Limitation vs Android VPN approach:
 *   - The Android app uses a VPN tunnel that works without admin rights.
 *   - netsh firewall rules on Windows require administrator privileges.
 *   - If the app is not running as admin, these commands will fail silently.
 *   - Future improvement: use a Windows Service running as SYSTEM to apply rules.
 *
 * Usage:
 *   NetworkBlocker.addRule("chrome.exe")    — block outbound for chrome.exe
 *   NetworkBlocker.removeRule("chrome.exe") — unblock
 *   NetworkBlocker.removeAllRules()         — clean up all FocusFlow rules
 */
object NetworkBlocker {

    private const val RULE_PREFIX = "FocusFlow_Block_"
    private val activeRules = mutableSetOf<String>()

    fun addRule(processName: String) {
        if (!isWindows) return
        val ruleName = RULE_PREFIX + processName.replace(".exe", "", ignoreCase = true)
        if (activeRules.contains(processName)) return

        runNetsh(
            "netsh", "advfirewall", "firewall", "add", "rule",
            "name=$ruleName",
            "dir=out",
            "action=block",
            "program=%ProgramFiles%\\...",  // placeholder — real implementation needs full path
            "enable=yes"
        )

        // More reliable: block by process name via PowerShell (Windows 8+)
        runPowerShell("""
            New-NetFirewallRule -DisplayName "$ruleName" -Direction Outbound -Action Block -Program (
                Get-Process -Name '${processName.removeSuffix(".exe")}' |
                Select-Object -First 1 -ExpandProperty Path
            ) -ErrorAction SilentlyContinue
        """.trimIndent())

        activeRules.add(processName)
    }

    fun removeRule(processName: String) {
        if (!isWindows) return
        val ruleName = RULE_PREFIX + processName.replace(".exe", "", ignoreCase = true)
        runNetsh("netsh", "advfirewall", "firewall", "delete", "rule", "name=$ruleName")
        runPowerShell("Remove-NetFirewallRule -DisplayName \"$ruleName\" -ErrorAction SilentlyContinue")
        activeRules.remove(processName)
    }

    fun removeAllRules() {
        activeRules.toList().forEach { removeRule(it) }
    }

    private fun runNetsh(vararg args: String) {
        try {
            ProcessBuilder(*args).redirectErrorStream(true).start().waitFor()
        } catch (_: Exception) {}
    }

    private fun runPowerShell(script: String) {
        try {
            ProcessBuilder("powershell", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true).start().waitFor()
        } catch (_: Exception) {}
    }
}
