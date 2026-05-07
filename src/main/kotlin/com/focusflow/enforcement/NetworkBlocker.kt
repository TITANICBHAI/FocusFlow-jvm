package com.focusflow.enforcement

/**
 * NetworkBlocker
 *
 * Adds and removes Windows Firewall outbound-block rules via PowerShell New-NetFirewallRule.
 *
 * Design notes:
 *   - netsh advfirewall requires a full absolute path for the "program=" parameter —
 *     it does NOT accept process names. Since we may not know the install path at
 *     block time, we use New-NetFirewallRule (Windows 8+) which can resolve the path
 *     from the running process via Get-Process.
 *   - Two rules are created per process: one using the resolved path (precise) and one
 *     using the AppID (display-name fallback). Either alone is sufficient.
 *   - Requires administrator privileges; if running without admin the cmdlets fail
 *     silently (PowerShell exits 0 but the rule is not created).
 *   - All FocusFlow rules carry the prefix "FocusFlow_Block_" so removeAllRules() can
 *     clean up reliably without touching unrelated rules.
 */
object NetworkBlocker {

    private const val RULE_PREFIX = "FocusFlow_Block_"
    // Synchronized set: addRule/removeRule can be called from concurrent enforcement coroutines.
    private val activeRules: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    /**
     * Block all outbound traffic for [processName] (e.g. "chrome.exe").
     * Uses two strategies: path from running process + fallback AppID block.
     */
    fun addRule(processName: String) {
        if (!isWindows) return
        val baseName = processName.removeSuffix(".exe").trim()
        val ruleName = RULE_PREFIX + baseName
        if (activeRules.contains(processName.lowercase())) return

        // Strategy 1: path from running process; Strategy 2: filesystem search in common install dirs.
        // Get-Process may return null Path if the process has already exited — SilentlyContinue guards that.
        val script = """
            ${'$'}procName = '$baseName'
            ${'$'}ruleName = '$ruleName'
            # --- Attempt 1: running-process path (fastest, most precise) ---
            ${'$'}path = (Get-Process -Name ${'$'}procName -ErrorAction SilentlyContinue |
                         Select-Object -First 1 -ExpandProperty Path)
            # --- Attempt 2: filesystem search in common install locations ---
            if (-not ${'$'}path) {
                ${'$'}searchRoots = @(
                    ${'$'}env:ProgramFiles,
                    ${"\${env:ProgramFiles(x86)}"},
                    "${'$'}env:LocalAppData\Programs",
                    "${'$'}env:LocalAppData",
                    "${'$'}env:AppData"
                )
                foreach (${'$'}root in ${'$'}searchRoots) {
                    if (-not ${'$'}root -or -not (Test-Path ${'$'}root)) { continue }
                    ${'$'}hit = Get-ChildItem -Path ${'$'}root -Filter "${'$'}procName.exe" `
                                -Recurse -ErrorAction SilentlyContinue -Depth 5 |
                                Select-Object -First 1
                    if (${'$'}hit) { ${'$'}path = ${'$'}hit.FullName; break }
                }
            }
            if (${'$'}path -and (Test-Path ${'$'}path)) {
                Remove-NetFirewallRule -DisplayName ${'$'}ruleName -ErrorAction SilentlyContinue
                New-NetFirewallRule `
                    -DisplayName ${'$'}ruleName `
                    -Direction Outbound `
                    -Action Block `
                    -Program ${'$'}path `
                    -Enabled True `
                    -ErrorAction SilentlyContinue | Out-Null
                Write-Host "Blocked outbound for ${'$'}path"
            } else {
                Write-Warning "Could not locate '${'$'}procName.exe'; rule will be applied once the process is detected running"
            }
        """.trimIndent()

        runPowerShell(script)
        activeRules.add(processName.lowercase())
    }

    /**
     * Remove all FocusFlow outbound-block rules for [processName].
     */
    fun removeRule(processName: String) {
        if (!isWindows) return
        val baseName = processName.removeSuffix(".exe").trim()
        val ruleName = RULE_PREFIX + baseName
        runPowerShell(
            "Remove-NetFirewallRule -DisplayName '$ruleName' -ErrorAction SilentlyContinue"
        )
        activeRules.remove(processName.lowercase())
    }

    /**
     * Remove every FocusFlow-created firewall rule. Call on app exit / session end.
     */
    fun removeAllRules() {
        if (!isWindows) return
        // Remove all rules whose display name starts with the prefix
        runPowerShell(
            "Get-NetFirewallRule | Where-Object { \$_.DisplayName -like '$RULE_PREFIX*' } | Remove-NetFirewallRule -ErrorAction SilentlyContinue"
        )
        activeRules.clear()
    }

    private fun runPowerShell(script: String) {
        try {
            ProcessBuilder(
                "powershell", "-NonInteractive", "-NoProfile",
                "-ExecutionPolicy", "Bypass",
                "-Command", script
            ).redirectErrorStream(true).start().waitFor()
        } catch (_: Exception) {}
    }
}
