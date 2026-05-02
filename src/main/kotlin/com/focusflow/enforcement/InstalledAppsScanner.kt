package com.focusflow.enforcement

data class ScannedApp(
    val processName: String,
    val displayName: String,
    val isRunning: Boolean
)

object InstalledAppsScanner {

    private val curated = listOf(
        ScannedApp("chrome.exe",           "Google Chrome",        false),
        ScannedApp("firefox.exe",          "Mozilla Firefox",      false),
        ScannedApp("msedge.exe",           "Microsoft Edge",       false),
        ScannedApp("opera.exe",            "Opera",                false),
        ScannedApp("brave.exe",            "Brave Browser",        false),
        ScannedApp("discord.exe",          "Discord",              false),
        ScannedApp("slack.exe",            "Slack",                false),
        ScannedApp("teams.exe",            "Microsoft Teams",      false),
        ScannedApp("zoom.exe",             "Zoom",                 false),
        ScannedApp("telegram.exe",         "Telegram",             false),
        ScannedApp("whatsapp.exe",         "WhatsApp",             false),
        ScannedApp("signal.exe",           "Signal",               false),
        ScannedApp("spotify.exe",          "Spotify",              false),
        ScannedApp("steam.exe",            "Steam",                false),
        ScannedApp("epicgameslauncher.exe","Epic Games Launcher",  false),
        ScannedApp("origin.exe",           "EA Origin",            false),
        ScannedApp("battle.net.exe",       "Battle.net",           false),
        ScannedApp("leagueclient.exe",     "League of Legends",    false),
        ScannedApp("twitch.exe",           "Twitch",               false),
        ScannedApp("obs64.exe",            "OBS Studio",           false),
        ScannedApp("tiktok.exe",           "TikTok",               false),
        ScannedApp("netflix.exe",          "Netflix",              false),
        ScannedApp("vlc.exe",              "VLC Media Player",     false),
        ScannedApp("wmplayer.exe",         "Windows Media Player", false),
        ScannedApp("itunes.exe",           "iTunes",               false),
        ScannedApp("outlook.exe",          "Microsoft Outlook",    false),
        ScannedApp("winword.exe",          "Microsoft Word",       false),
        ScannedApp("excel.exe",            "Microsoft Excel",      false),
        ScannedApp("powerpnt.exe",         "Microsoft PowerPoint", false),
        ScannedApp("notepad.exe",          "Notepad",              false),
        ScannedApp("notepad++.exe",        "Notepad++",            false),
        ScannedApp("code.exe",             "Visual Studio Code",   false),
        ScannedApp("devenv.exe",           "Visual Studio",        false),
        ScannedApp("idea64.exe",           "IntelliJ IDEA",        false),
        ScannedApp("pycharm64.exe",        "PyCharm",              false),
        ScannedApp("webstorm64.exe",       "WebStorm",             false),
        ScannedApp("clion64.exe",          "CLion",                false),
        ScannedApp("studio64.exe",         "Android Studio",       false)
    )

    private val systemIgnore = setOf(
        "system", "system idle process", "registry", "smss.exe", "csrss.exe",
        "wininit.exe", "winlogon.exe", "lsass.exe", "svchost.exe", "services.exe",
        "spoolsv.exe", "searchindexer.exe", "audiodg.exe", "dwm.exe", "conhost.exe",
        "dllhost.exe", "rundll32.exe", "wermgr.exe", "wmiprvse.exe", "msiexec.exe",
        "fontdrvhost.exe", "sihost.exe", "taskhostw.exe", "explorer.exe",
        "securityhealthsystray.exe", "runtimebroker.exe", "applicationframehost.exe"
    )

    fun getRunningApps(): List<ScannedApp> {
        val running: List<ScannedApp> = try {
            val handles = ProcessHandle.allProcesses().toList()
            handles
                .filter { ph -> ph.info().command().isPresent }
                .map { ph ->
                    val cmd = ph.info().command().get()
                    val exe = java.io.File(cmd).name.lowercase()
                    ScannedApp(exe, friendlyName(exe), isRunning = true)
                }
                .filter { app -> app.processName.isNotBlank() && app.processName !in systemIgnore }
                .distinctBy { app -> app.processName }
        } catch (_: Exception) { emptyList() }

        val runningNames = running.map { app -> app.processName }.toSet()
        val notRunning   = curated.filter { app -> app.processName !in runningNames }

        return (running + notRunning)
            .distinctBy { app -> app.processName.lowercase() }
            .sortedWith(compareByDescending<ScannedApp> { app -> app.isRunning }.thenBy { app -> app.displayName })
    }

    private fun friendlyName(exe: String): String {
        return curated.find { app -> app.processName.equals(exe, ignoreCase = true) }?.displayName
            ?: exe.substringBeforeLast(".")
                .replace(Regex("([a-z])([A-Z])"), "$1 $2")
                .replace(Regex("\\d+$"), "")
                .trim()
                .replaceFirstChar { c -> c.uppercaseChar() }
    }
}
