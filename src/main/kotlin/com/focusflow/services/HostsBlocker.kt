package com.focusflow.services

import com.focusflow.enforcement.isWindows

/**
 * HostsBlocker
 *
 * Adds / removes entries in the Windows hosts file to block websites by domain.
 * Requires administrator privileges (hosts file is in System32\drivers\etc\).
 *
 * Entry format:
 *   127.0.0.1  example.com  # FocusFlow
 *   127.0.0.1  www.example.com  # FocusFlow
 */
object HostsBlocker {

    private const val HOSTS_PATH = "C:\\Windows\\System32\\drivers\\etc\\hosts"
    private const val MARKER     = "# FocusFlow"

    fun blockDomain(domain: String): Boolean {
        if (!isWindows) return false
        return try {
            val hostsFile = java.io.File(HOSTS_PATH)
            val content   = hostsFile.readText()
            val sb        = StringBuilder(content)
            if (!content.contains("\n")) sb.append("\n")
            listOf(domain, "www.$domain").forEach { d ->
                val line = "127.0.0.1  $d  $MARKER\n"
                if (!content.contains(line)) sb.append(line)
            }
            hostsFile.writeText(sb.toString())
            flushDnsCache()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun unblockDomain(domain: String): Boolean {
        if (!isWindows) return false
        return try {
            val hostsFile = java.io.File(HOSTS_PATH)
            val lines = hostsFile.readLines()
            // Match only exact entries we wrote — prevents substring collisions like
            // unblocking "test.com" also removing "mytest.com".
            val exactEntries = listOf(
                "127.0.0.1  $domain  $MARKER",
                "127.0.0.1  www.$domain  $MARKER"
            )
            val filtered = lines.filter { line -> line.trim() !in exactEntries }
            hostsFile.writeText(filtered.joinToString("\n") + "\n")
            flushDnsCache()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun unblockAll(): Boolean {
        if (!isWindows) return false
        return try {
            val hostsFile = java.io.File(HOSTS_PATH)
            val lines = hostsFile.readLines()
            val filtered = lines.filter { line -> !line.contains(MARKER) }
            hostsFile.writeText(filtered.joinToString("\n") + "\n")
            flushDnsCache()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getBlockedDomains(): List<String> {
        if (!isWindows) return emptyList()
        return try {
            java.io.File(HOSTS_PATH).readLines()
                .filter { it.contains(MARKER) }
                .mapNotNull { line ->
                    line.trim()
                        .removePrefix("127.0.0.1").trim()
                        .substringBefore(MARKER).trim()
                        .takeIf { it.isNotBlank() }
                }
                .filter { !it.startsWith("www.") }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun flushDnsCache() {
        try {
            ProcessBuilder("ipconfig", "/flushdns").start().waitFor()
        } catch (_: Exception) {}
    }

    val isAdminRequired: Boolean get() = isWindows
}
