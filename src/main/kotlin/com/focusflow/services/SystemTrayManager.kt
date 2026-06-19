package com.focusflow.services

import java.awt.*
import java.awt.geom.Arc2D
import java.awt.geom.Line2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

object SystemTrayManager {

    // @Volatile: both fields are initialised on the AWT EDT (inside EventQueue.invokeLater)
    // but are read from IO and Main threads (showNotification, updateTooltip,
    // updateKillSwitchItem). Without @Volatile those threads can see a stale null
    // after initialisation, silently dropping notifications or tooltip updates.
    @Volatile private var trayIcon: TrayIcon? = null
    @Volatile private var killSwitchItem: MenuItem? = null

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
            // Guard against duplicate installation. install() is normally called once,
            // but if called again (e.g., after an accidental double-init) this prevents
            // a second unreachable ghost icon being added to the system tray — the first
            // icon's reference would be overwritten, making it impossible to remove later.
            if (trayIcon != null) return@invokeLater

            val tray = SystemTray.getSystemTray()
            val traySize = tray.trayIconSize
            val image = createTrayImage(traySize.width, traySize.height)

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
            icon.isImageAutoSize = false
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

    private fun createTrayImage(w: Int, h: Int): Image {
        // Scale to the exact pixel dimensions the OS tray requests.
        // isImageAutoSize is OFF — if we pass the wrong size AWT fires another
        // lazy FilteredImageSource pass (getScaledInstance path) which OOMs inside
        // WTrayIconPeer.updateNativeImage on JVMs with small heaps.
        val tw = w.coerceAtLeast(16)
        val th = h.coerceAtLeast(16)

        // Load the real FocusFlow logo from resources; fall back to programmatic drawing.
        try {
            val stream = SystemTrayManager::class.java.classLoader
                .getResourceAsStream("focusflow_256.png")
            if (stream != null) {
                val src = javax.imageio.ImageIO.read(stream)
                stream.close()
                if (src != null) {
                    val out = BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB)
                    val g2 = out.createGraphics()
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.drawImage(src, 0, 0, tw, th, null)
                    g2.dispose()
                    return out
                }
            }
        } catch (_: Throwable) { }

        // Fallback: draw programmatically into a BufferedImage at exact tray size.
        val size = tw.coerceAtLeast(th)
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
        return img
    }
}
