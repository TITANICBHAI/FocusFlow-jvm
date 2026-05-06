package com.focusflow.enforcement

data class ScannedApp(
    val processName: String,
    val displayName: String,
    val isRunning: Boolean
)

object InstalledAppsScanner {

    private val curated = mapOf(
        "chrome.exe"            to "Google Chrome",
        "firefox.exe"           to "Mozilla Firefox",
        "msedge.exe"            to "Microsoft Edge",
        "opera.exe"             to "Opera",
        "brave.exe"             to "Brave Browser",
        "discord.exe"           to "Discord",
        "slack.exe"             to "Slack",
        "teams.exe"             to "Microsoft Teams",
        "zoom.exe"              to "Zoom",
        "telegram.exe"          to "Telegram",
        "whatsapp.exe"          to "WhatsApp",
        "signal.exe"            to "Signal",
        "spotify.exe"           to "Spotify",
        "steam.exe"             to "Steam",
        "epicgameslauncher.exe" to "Epic Games Launcher",
        "origin.exe"            to "EA Origin",
        "battle.net.exe"        to "Battle.net",
        "leagueclient.exe"      to "League of Legends",
        "twitch.exe"            to "Twitch",
        "obs64.exe"             to "OBS Studio",
        "tiktok.exe"            to "TikTok",
        "netflix.exe"           to "Netflix",
        "vlc.exe"               to "VLC Media Player",
        "wmplayer.exe"          to "Windows Media Player",
        "itunes.exe"            to "iTunes",
        "outlook.exe"           to "Microsoft Outlook",
        "winword.exe"           to "Microsoft Word",
        "excel.exe"             to "Microsoft Excel",
        "powerpnt.exe"          to "Microsoft PowerPoint",
        "notepad.exe"           to "Notepad",
        "notepad++.exe"         to "Notepad++",
        "code.exe"              to "Visual Studio Code",
        "devenv.exe"            to "Visual Studio",
        "idea64.exe"            to "IntelliJ IDEA",
        "pycharm64.exe"         to "PyCharm",
        "webstorm64.exe"        to "WebStorm",
        "clion64.exe"           to "CLion",
        "studio64.exe"          to "Android Studio"
    )

    private val systemIgnore = setOf(
        "system", "system idle process", "registry", "smss.exe", "csrss.exe",
        "wininit.exe", "winlogon.exe", "lsass.exe", "svchost.exe", "services.exe",
        "spoolsv.exe", "searchindexer.exe", "audiodg.exe", "dwm.exe", "conhost.exe",
        "dllhost.exe", "rundll32.exe", "wermgr.exe", "wmiprvse.exe", "msiexec.exe",
        "fontdrvhost.exe", "sihost.exe", "taskhostw.exe", "explorer.exe",
        "securityhealthsystray.exe", "runtimebroker.exe", "applicationframehost.exe",
        "shellexperiencehost.exe", "startmenuexperiencehost.exe", "searchhost.exe",
        "ctfmon.exe", "textinputhost.exe", "lockapp.exe", "logonui.exe",
        "userinit.exe", "wlanext.exe", "dashost.exe", "igfxem.exe", "igfxhk.exe",
        "nvdisplay.container.exe", "amdow.exe", "focusflow.exe"
    )

    fun getRunningApps(): List<ScannedApp> {
        val running: List<ScannedApp> = try {
            ProcessHandle.allProcesses().toList()
                .filter { ph -> ph.info().command().isPresent }
                .map { ph ->
                    val cmd = ph.info().command().get()
                    val exe = java.io.File(cmd).name.lowercase()
                    val display = curated[exe] ?: friendlyName(exe)
                    ScannedApp(exe, display, isRunning = true)
                }
                .filter { app ->
                    app.processName.isNotBlank() &&
                    app.processName !in systemIgnore &&
                    app.processName.endsWith(".exe")
                }
                .distinctBy { it.processName }
        } catch (_: Exception) { emptyList() }

        return running
            .sortedBy { it.displayName }
    }

    fun getCuratedApps(): List<ScannedApp> {
        return curated.map { (exe, name) ->
            ScannedApp(exe, name, isRunning = false)
        }.sortedBy { it.displayName }
    }

    fun friendlyNameFor(processName: String): String =
        curated[processName.lowercase()] ?: friendlyName(processName.lowercase())

    private fun friendlyName(exe: String): String =
        exe.substringBeforeLast(".")
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace(Regex("\\d+$"), "")
            .trim()
            .replaceFirstChar { c -> c.uppercaseChar() }
}
