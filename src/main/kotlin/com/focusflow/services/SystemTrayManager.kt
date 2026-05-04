package com.focusflow.services

import java.awt.*
import java.awt.image.BufferedImage

object SystemTrayManager {

    private var trayIcon: TrayIcon? = null

    data class TrayCallbacks(
        val onRestore: () -> Unit,
        val onQuit: () -> Unit,
        val onToggleBlocking: () -> Unit
    )

    val isSupported: Boolean get() = SystemTray.isSupported()

    fun install(callbacks: TrayCallbacks) {
        if (!SystemTray.isSupported()) return

        EventQueue.invokeLater {
            val tray = SystemTray.getSystemTray()
            val image = createTrayImage()

            val popup = PopupMenu()

            val openItem = MenuItem("Open FocusFlow")
            val baseFont = openItem.font ?: Font("Dialog", Font.BOLD, 12)
            openItem.font = Font(baseFont.name, Font.BOLD, baseFont.size)
            openItem.addActionListener { callbacks.onRestore() }

            val toggleItem = MenuItem("Toggle Blocking")
            toggleItem.addActionListener { callbacks.onToggleBlocking() }

            popup.add(openItem)
            popup.add(toggleItem)
            popup.addSeparator()

            val quitItem = MenuItem("Quit")
            quitItem.addActionListener { callbacks.onQuit() }
            popup.add(quitItem)

            val icon = TrayIcon(image, "FocusFlow — Focus & Block", popup)
            icon.isImageAutoSize = true
            icon.addActionListener { callbacks.onRestore() }

            trayIcon = icon
            try {
                tray.add(icon)
            } catch (e: AWTException) {
                System.err.println("[FocusFlow] Tray install failed: ${e.message}")
            }
        }
    }

    fun remove() {
        trayIcon?.let { icon ->
            EventQueue.invokeLater {
                SystemTray.getSystemTray().remove(icon)
            }
        }
        trayIcon = null
    }

    fun showNotification(
        title: String,
        message: String,
        type: TrayIcon.MessageType = TrayIcon.MessageType.INFO
    ) {
        trayIcon?.displayMessage(title, message, type)
    }

    fun updateTooltip(text: String) {
        EventQueue.invokeLater { trayIcon?.toolTip = text }
    }

    private fun createTrayImage(): Image {
        val size = 64
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        g.color = Color(144, 102, 238)
        g.fillRoundRect(2, 2, size - 4, size - 4, 14, 14)

        g.color = Color.WHITE
        val fontSize = (size * 0.34).toInt()
        g.font = Font("Arial", Font.BOLD, fontSize)
        val fm = g.fontMetrics
        val textX = (size - fm.stringWidth("FF")) / 2
        val textY = (size + fm.ascent - fm.descent) / 2
        g.drawString("FF", textX, textY)
        g.dispose()

        return img.getScaledInstance(16, 16, Image.SCALE_SMOOTH)
    }
}
