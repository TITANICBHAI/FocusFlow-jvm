package com.focusflow.services

import java.awt.TrayIcon

object NotificationService {

    fun sessionStarted(taskName: String, minutes: Int) {
        SystemTrayManager.showNotification(
            title = "Focus Session Started",
            message = "$taskName — ${minutes}m",
            type = TrayIcon.MessageType.INFO
        )
        SystemTrayManager.updateTooltip("FocusFlow — $taskName ($minutes m)")
        SoundAversion.playSessionStart()
    }

    fun sessionEnded(taskName: String, completed: Boolean) {
        if (completed) {
            SystemTrayManager.showNotification(
                title = "Session Complete!",
                message = "$taskName — Great work, keep the streak going!",
                type = TrayIcon.MessageType.INFO
            )
        } else {
            SystemTrayManager.showNotification(
                title = "Session Ended",
                message = "$taskName — Session interrupted",
                type = TrayIcon.MessageType.WARNING
            )
        }
        SystemTrayManager.updateTooltip("FocusFlow — Ready")
        SoundAversion.playSessionEnd()
    }

    fun appBlocked(appName: String) {
        SystemTrayManager.showNotification(
            title = "Blocked: $appName",
            message = "Stay focused — you can do it!",
            type = TrayIcon.MessageType.WARNING
        )
    }

    fun breakStarted(minutes: Int, isLongBreak: Boolean) {
        val label = if (isLongBreak) "Long Break" else "Short Break"
        SystemTrayManager.showNotification(
            title = "$label — ${minutes}m",
            message = "Session complete! Take a real break away from your screen.",
            type = TrayIcon.MessageType.INFO
        )
        SystemTrayManager.updateTooltip("FocusFlow — Break ($minutes m)")
        SoundAversion.playSessionEnd()
    }

    fun breakEnded() {
        SystemTrayManager.showNotification(
            title = "Break Over — Time to Focus!",
            message = "Your break is up. Ready for the next session?",
            type = TrayIcon.MessageType.INFO
        )
        SystemTrayManager.updateTooltip("FocusFlow — Ready")
        SoundAversion.playSessionStart()
    }

    fun weeklyReport(report: WeeklyReportService.WeeklyReport) {
        SystemTrayManager.showNotification(
            title = "Weekly Report — ${report.weekLabel}",
            message = "${report.hoursFormatted} focus  •  ${report.sessionsCompleted} sessions  •  ${report.currentStreakDays}d streak",
            type = TrayIcon.MessageType.INFO
        )
    }
}
