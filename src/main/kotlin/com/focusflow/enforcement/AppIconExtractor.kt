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
 * On Windows: uses Shell32 SHGetFileInfo (SHGFI_LARGEICON, 32×32) via JNA
 * reflection so the code compiles on any platform.  Falls back to
 * FileSystemView (the original 16×16 shell-icon path) if Shell32 fails.
 *
 * On Linux / macOS (Replit preview): uses FileSystemView only.
 *
 * Results are cached so each path is resolved at most once per session.
 */
object AppIconExtractor {

    private val cache     = ConcurrentHashMap<String, ImageBitmap?>()
    private val isWindows = System.getProperty("os.name")
        ?.lowercase()?.contains("windows") == true

    fun extractIcon(exePath: String): ImageBitmap? =
        cache.getOrPut(exePath) { doExtract(exePath) }

    private fun doExtract(exePath: String): ImageBitmap? {
        val file = File(exePath)
        if (!file.exists()) return null
        return if (isWindows) {
            extractLargeIconWindows(exePath) ?: extractViaFileSystemView(file)
        } else {
            extractViaFileSystemView(file)
        }
    }

    /**
     * Calls Shell32 via reflection so we never get compile-time errors on Linux.
     * Equivalent to: Shell32.INSTANCE.SHGetFileInfo(path, 0, shfi, size, SHGFI_ICON|SHGFI_LARGEICON)
     * then renders the HICON into a 32×32 BufferedImage.
     */
    private fun extractLargeIconWindows(exePath: String): ImageBitmap? {
        return try {
            // Use sun.awt.shell.ShellFolder — available on all Windows JDKs,
            // already used internally by FileSystemView on Windows.
            val shellFolderClass = Class.forName("sun.awt.shell.ShellFolder")
            val getShellFolder   = shellFolderClass.getMethod("getShellFolder", File::class.java)
            val sf               = getShellFolder.invoke(null, File(exePath))
            val getIcon          = shellFolderClass.getMethod("getIcon", Boolean::class.javaPrimitiveType)
            val icon             = getIcon.invoke(sf, true) as? javax.swing.Icon ?: return null

            val w  = icon.iconWidth.coerceAtLeast(16)
            val h  = icon.iconHeight.coerceAtLeast(16)
            val bi = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            val g  = bi.createGraphics()
            try { icon.paintIcon(null, g, 0, 0) } finally { g.dispose() }
            bi.toComposeImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    /** Fallback: Swing FileSystemView — cross-platform, returns 16×16 shell icon. */
    private fun extractViaFileSystemView(file: File): ImageBitmap? {
        return try {
            val fsv  = FileSystemView.getFileSystemView()
            val icon = fsv.getSystemIcon(file) ?: return null
            val w    = icon.iconWidth.coerceAtLeast(16)
            val h    = icon.iconHeight.coerceAtLeast(16)
            val bi   = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            val g    = bi.createGraphics()
            try { icon.paintIcon(null, g, 0, 0) } finally { g.dispose() }
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
