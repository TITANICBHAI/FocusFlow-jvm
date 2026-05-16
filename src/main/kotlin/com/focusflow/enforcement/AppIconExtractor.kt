package com.focusflow.enforcement

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.swing.filechooser.FileSystemView

/**
 * AppIconExtractor
 *
 * Extracts the real Windows shell icon for any .exe file using the JVM's
 * FileSystemView (backed by Windows Shell32) and converts it to a Compose
 * ImageBitmap for display.
 *
 * Results are cached in-memory by absolute path so each icon is extracted at
 * most once per session.
 */
object AppIconExtractor {

    private val cache = ConcurrentHashMap<String, ImageBitmap?>()

    /**
     * Returns the real icon for the given exe file, or null if extraction fails.
     * Call from a background dispatcher (Dispatchers.IO) — FileSystemView can
     * be slow on the first call per directory.
     */
    fun extractIcon(exePath: String): ImageBitmap? {
        return cache.getOrPut(exePath) { doExtract(exePath) }
    }

    private fun doExtract(exePath: String): ImageBitmap? {
        return try {
            val file = File(exePath)
            if (!file.exists()) return null

            val fsv  = FileSystemView.getFileSystemView()
            val icon = fsv.getSystemIcon(file) ?: return null

            val iconW = icon.iconWidth.coerceAtLeast(16)
            val iconH = icon.iconHeight.coerceAtLeast(16)

            val bi = BufferedImage(iconW, iconH, BufferedImage.TYPE_INT_ARGB)
            val g  = bi.createGraphics()
            try {
                icon.paintIcon(null, g, 0, 0)
            } finally {
                g.dispose()
            }

            bi.toComposeImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    /** Pre-warm icon for a path in the background (best-effort). */
    fun prefetch(exePath: String) {
        if (!cache.containsKey(exePath)) doExtract(exePath)?.also { cache[exePath] = it }
    }
}
