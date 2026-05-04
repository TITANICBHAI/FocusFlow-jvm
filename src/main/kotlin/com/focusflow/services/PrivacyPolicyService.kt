package com.focusflow.services

/**
 * In-app privacy policy and EULA text.
 * Stored here so it can be shown in-app (Settings → Privacy & Permissions).
 * Also used for Microsoft Store certification — must match what the app actually does.
 */
object PrivacyPolicyService {

    const val PRIVACY_POLICY = """
FocusFlow — Privacy Policy
Last updated: May 2026

1. DATA COLLECTION
FocusFlow does not collect, transmit, or share any personal data.
All data (tasks, focus sessions, habits, notes, settings) is stored
locally on your device at:
  %USERPROFILE%\.focusflow\focusflow.db   (SQLite database)

2. NO NETWORK CALLS
FocusFlow makes no network calls. It does not contact any server,
analytics service, telemetry endpoint, or third-party API.

3. PROCESS MONITORING
To enforce app blocking, FocusFlow reads the list of running processes
on your computer using the Java ProcessHandle API (JVM 9+).
Process names are checked against your block list. No process data
is stored or transmitted beyond in-session temptation log entries
that are written to your local SQLite database.

4. WINDOWS FIREWALL
When "Block Network" is enabled for an app, FocusFlow adds a Windows
Firewall outbound rule using netsh advfirewall. This requires
administrator privileges. Rules are named "FocusFlow_[processname]"
and can be removed in Windows Defender Firewall → Outbound Rules.

5. WINDOWS REGISTRY
When "Start with Windows" is enabled, FocusFlow writes a single
registry entry to HKCU\Software\Microsoft\Windows\CurrentVersion\Run.
This only affects your user account (HKCU), not the system.

6. NO ACCOUNTS
FocusFlow has no user accounts, no sign-in, and no cloud sync.
Your data belongs to you and stays on your device.

7. CONTACT
FocusFlow is an open-source project. Source code is available at:
https://github.com/TITANICBHAI/FocusFlow-jvm
"""

    const val EULA = """
FocusFlow — End User License Agreement (EULA)
Last updated: May 2026

By installing or using FocusFlow ("the App"), you agree to these terms.

1. LICENSE
FocusFlow is provided free of charge. You may use it for personal
or professional productivity purposes.

2. USER RESPONSIBILITY
FocusFlow enforces focus rules that YOU configure. You acknowledge:
- You have authorised FocusFlow to terminate processes you list as blocked.
- You are responsible for any consequence of enabling Nuclear Mode,
  network blocking, or session PIN on a shared machine.
- FocusFlow is a productivity aid — it does not guarantee productivity.

3. NO WARRANTY
FocusFlow is provided "as is" without warranty of any kind.
The developer is not liable for data loss, system instability,
or missed deadlines resulting from use of this software.

4. OPEN SOURCE
FocusFlow is open-source (Apache 2.0). Source code is available at
https://github.com/TITANICBHAI/FocusFlow-jvm

5. TERMINATION
Uninstall FocusFlow to terminate this agreement.
Your local data at %USERPROFILE%\.focusflow\ is not automatically
deleted on uninstall.
"""

    val PERMISSIONS_SUMMARY = listOf(
        Triple("Process Monitoring", "Reads running process names every 500ms", "Required for app blocking enforcement"),
        Triple("Process Termination", "Kills blocked apps using Java ProcessHandle", "Only kills apps YOU put on the block list"),
        Triple("Windows Firewall Rules", "Adds outbound block rules via netsh", "Optional — only when 'Block Network' is enabled; requires admin"),
        Triple("Windows Registry (HKCU)", "Writes a Run key for startup launch", "Optional — only when 'Start with Windows' is enabled"),
        Triple("File System Access", "Reads/writes ~/.focusflow/focusflow.db", "Your data is stored locally only"),
        Triple("Audio Output", "Plays synthesised alert tones", "Optional — only when 'Sound Aversion' is enabled"),
        Triple("System Tray", "Shows icon in Windows notification area", "Lets you toggle blocking and restore window without closing")
    )
}
