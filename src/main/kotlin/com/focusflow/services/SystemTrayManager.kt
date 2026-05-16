package com.focusflow.services

import java.awt.*
import java.awt.geom.Arc2D
import java.awt.geom.Line2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

object SystemTrayManager {

    private var trayIcon: TrayIcon? = null
    private var killSwitchItem: MenuItem? = null

    data class TrayCallbacks(
        val onRestore: () -> Unit,
        val onQuit: () -> Unit,
        val onToggleBlocking: () -> Unit,
        val onKillSwitch: () -> Unit
    )

    val isSupported: Boolean get() = SystemTray.isSupported()

    fun install(callbacks: TrayCallbacks) {
        if (!SystemTray.isSupported()) return

        EventQueue.invokeLater {
            val tray = SystemTray.getSystemTray()
            val image = createTrayImage()

            val popup = PopupMenu()

            val openItem = MenuItem("Open FocusFlow")
            val baseFont = try { openItem.font } catch (_: Exception) { null }
                ?: Font("Dialog", Font.BOLD, 12)
            openItem.font = Font(baseFont.name ?: "Dialog", Font.BOLD, baseFont.size.takeIf { it > 0 } ?: 12)
            openItem.addActionListener { callbacks.onRestore() }

            val toggleItem = MenuItem("Toggle Blocking")
            toggleItem.addActionListener { callbacks.onToggleBlocking() }

            val ksItem = MenuItem("Emergency Break (5m 00s/day left)")
            ksItem.addActionListener { callbacks.onKillSwitch() }
            killSwitchItem = ksItem

            popup.add(openItem)
            popup.add(toggleItem)
            popup.add(ksItem)
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

    fun updateKillSwitchItem(label: String) {
        EventQueue.invokeLater { killSwitchItem?.label = label }
    }

    private fun createTrayImage(): Image {
        // Load the real FocusFlow logo from resources; fall back to programmatic drawing
        try {
            val stream = SystemTrayManager::class.java.classLoader
                .getResourceAsStream("focusflow_256.png")
            if (stream != null) {
                val img = javax.imageio.ImageIO.read(stream)
                stream.close()
                if (img != null) return img.getScaledInstance(64, 64, Image.SCALE_SMOOTH)
            }
        } catch (_: Exception) { }

        // Fallback: draw programmatically
        val size = 64
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

        g.color = Color(0x09, 0x09, 0x0F)
        g.fill(RoundRectangle2D.Float(0f, 0f, size.toFloat(), size.toFloat(), size * 0.22f, size * 0.22f))

        val cx = size / 2f
        val cy = size / 2f
        val arcR = size * 0.345f
        val strokeW = size * 0.115f
        val left   = cx - arcR
        val top    = cy - arcR
        val diam   = arcR * 2f

        val cyanBlue = Color(0x4F, 0xC3, 0xF7)
        g.stroke = BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.color = cyanBlue
        g.draw(Arc2D.Float(left, top, diam, diam, 130f, -190f, Arc2D.OPEN))

        val arcPurple = Color(0x7C, 0x4D, 0xFF)
        g.color = arcPurple
        g.draw(Arc2D.Float(left, top, diam, diam, -100f, -130f, Arc2D.OPEN))

        val dashLen = size * 0.16f
        g.stroke = BasicStroke(size * 0.055f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.color = Color(0x66, 0x99, 0xEE)
        g.draw(Line2D.Float(cx - dashLen, cy, cx + dashLen, cy))

        g.dispose()
        return img.getScaledInstance(16, 16, Image.SCALE_SMOOTH)
    }
}
