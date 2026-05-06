package com.focusflow.enforcement

data class BlockPreset(
    val id: String,
    val name: String,
    val description: String,
    val emoji: String,
    val processNames: List<String>
)

object BlockPresets {

    val all = listOf(
        BlockPreset(
            id = "social_media",
            name = "Social Media",
            description = "Discord, Telegram, WhatsApp, TikTok, Signal",
            emoji = "📱",
            processNames = listOf(
                "discord.exe", "telegram.exe", "whatsapp.exe", "signal.exe", "tiktok.exe"
            )
        ),
        BlockPreset(
            id = "browsers",
            name = "Web Browsers",
            description = "Chrome, Firefox, Edge, Opera, Brave",
            emoji = "🌐",
            processNames = listOf(
                "chrome.exe", "firefox.exe", "msedge.exe", "opera.exe", "brave.exe"
            )
        ),
        BlockPreset(
            id = "gaming",
            name = "Gaming",
            description = "Steam, Epic Games, Battle.net, League of Legends, Origin",
            emoji = "🎮",
            processNames = listOf(
                "steam.exe", "epicgameslauncher.exe", "battle.net.exe",
                "leagueclient.exe", "origin.exe"
            )
        ),
        BlockPreset(
            id = "entertainment",
            name = "Entertainment",
            description = "Spotify, Netflix, VLC, Twitch, Windows Media Player",
            emoji = "🎬",
            processNames = listOf(
                "spotify.exe", "netflix.exe", "vlc.exe", "twitch.exe", "wmplayer.exe"
            )
        ),
        BlockPreset(
            id = "messaging",
            name = "Messaging & Chat",
            description = "Discord, Slack, Teams, Telegram, WhatsApp, Zoom",
            emoji = "💬",
            processNames = listOf(
                "discord.exe", "slack.exe", "teams.exe", "telegram.exe",
                "whatsapp.exe", "signal.exe", "zoom.exe"
            )
        ),
        BlockPreset(
            id = "work_mode",
            name = "Work Mode",
            description = "Block all games & social — keep work tools free",
            emoji = "💼",
            processNames = listOf(
                "steam.exe", "epicgameslauncher.exe", "battle.net.exe",
                "discord.exe", "spotify.exe", "tiktok.exe",
                "twitch.exe", "leagueclient.exe", "origin.exe"
            )
        ),
        BlockPreset(
            id = "deep_focus",
            name = "Deep Focus",
            description = "Block browsers, social, games & entertainment",
            emoji = "🧠",
            processNames = listOf(
                "chrome.exe", "firefox.exe", "msedge.exe",
                "discord.exe", "slack.exe", "steam.exe",
                "epicgameslauncher.exe", "spotify.exe", "tiktok.exe",
                "twitch.exe", "telegram.exe", "whatsapp.exe",
                "netflix.exe", "leagueclient.exe"
            )
        )
    )

    val goalSuggestions: Map<String, List<String>> = mapOf(
        "social"  to listOf("social_media", "messaging"),
        "gaming"  to listOf("gaming", "entertainment"),
        "web"     to listOf("browsers", "entertainment"),
        "deep"    to listOf("deep_focus", "work_mode")
    )

    fun findById(id: String): BlockPreset? = all.find { it.id == id }

    fun presetForProcessName(processName: String): BlockPreset? =
        all.find { it.processNames.contains(processName.lowercase()) }
}
