package com.focusflow.services

import com.focusflow.enforcement.isWindows
import kotlinx.coroutines.*

/**
 * HostsBlocker — three-layer hosts-file website blocking
 *
 * Layer 1 — Write robustly:
 *   Blocks bare domain + www + m + mobile + app subdomains. Normalises Windows
 *   line endings (CRLF → LF) before parsing to prevent duplicate entries.
 *   Appends only lines that are not already present.
 *
 * Layer 2 — Verify:
 *   After each write, calls [verifyBlock] which runs `nslookup` against the
 *   domain and confirms the resolved IP is 127.0.0.1. Returns a [BlockResult]
 *   that callers can surface to the user (blocked / not-blocked / no-admin).
 *
 * Layer 3 — Monitor:
 *   A background coroutine ([startMonitor] / [stopMonitor]) watches the hosts
 *   file for external modification (e.g. antivirus or another app removing
 *   entries). If blocks are missing they are silently re-applied.
 */
object HostsBlocker {

    private const val HOSTS_PATH = "C:\\Windows\\System32\\drivers\\etc\\hosts"
    private const val MARKER     = "# FocusFlow"

    /** Subdomains written for every blocked root domain. */
    private val SUBDOMAINS = listOf("", "www.", "m.", "mobile.", "app.")

    private val monitorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null

    /** Tracks the last-known good set of blocked domains for the monitor loop. */
    @Volatile private var monitoredDomains: Set<String> = emptySet()

    // ── Layer 1: Write ────────────────────────────────────────────────────────

    sealed class BlockResult {
        object Success          : BlockResult()
        object AlreadyBlocked   : BlockResult()
        object NoAdmin          : BlockResult()
        object NotWindows       : BlockResult()
        object VerificationFail : BlockResult()
        data class Error(val reason: String) : BlockResult()
    }

    /**
     * Block [domain] by adding hosts entries for all common subdomains.
     * Returns a [BlockResult] describing outcome.
     */
    fun blockDomain(domain: String): BlockResult {
        if (!isWindows) return BlockResult.NotWindows
        if (!canWriteHostsFile()) return BlockResult.NoAdmin

        val root = domain.lowercase().removePrefix("www.").trim()
        return try {
            val hostsFile = java.io.File(HOSTS_PATH)
            val existing  = normalizeContent(hostsFile.readText())
            val sb        = StringBuilder(existing)
            if (!existing.endsWith("\n")) sb.append("\n")

            var anyAdded = false
            SUBDOMAINS.forEach { prefix ->
                val fqdn = "$prefix$root"
                val line = "127.0.0.1  $fqdn  $MARKER\n"
                if (!existing.contains("127.0.0.1  $fqdn  $MARKER")) {
                    sb.append(line)
                    anyAdded = true
                }
            }

            if (!anyAdded) return BlockResult.AlreadyBlocked

            hostsFile.writeText(sb.toString())
            flushDnsCache()

            // Update monitor's domain set
            monitoredDomains = monitoredDomains + root

            // Layer 2: verify
            if (verifyBlock(root)) BlockResult.Success else BlockResult.VerificationFail
        } catch (e: Exception) {
            BlockResult.Error(e.message ?: "Unknown error")
        }
    }

    fun unblockDomain(domain: String): Boolean {
        if (!isWindows) return false
        val root = domain.lowercase().removePrefix("www.").trim()
        return try {
            val hostsFile = java.io.File(HOSTS_PATH)
            val lines     = normalizeContent(hostsFile.readText()).lines()
            val exactEntries = SUBDOMAINS.map { prefix ->
                "127.0.0.1  $prefix$root  $MARKER"
            }.toSet()
            val filtered = lines.filter { it.trim() !in exactEntries }
            hostsFile.writeText(filtered.joinToString("\n") + "\n")
            flushDnsCache()
            monitoredDomains = monitoredDomains - root
            true
        } catch (_: Exception) { false }
    }

    fun unblockAll(): Boolean {
        if (!isWindows) return false
        return try {
            val hostsFile = java.io.File(HOSTS_PATH)
            val lines     = normalizeContent(hostsFile.readText()).lines()
            val filtered  = lines.filter { !it.contains(MARKER) }
            hostsFile.writeText(filtered.joinToString("\n") + "\n")
            flushDnsCache()
            monitoredDomains = emptySet()
            true
        } catch (_: Exception) { false }
    }

    fun getBlockedDomains(): List<String> {
        if (!isWindows) return emptyList()
        return try {
            normalizeContent(java.io.File(HOSTS_PATH).readText())
                .lines()
                .filter { it.contains(MARKER) }
                .mapNotNull { line ->
                    line.trim()
                        .removePrefix("127.0.0.1").trim()
                        .substringBefore(MARKER).trim()
                        .takeIf { it.isNotBlank() }
                }
                // Deduplicate: return only the root (no www./m./mobile./app. prefixes)
                .filter { d -> SUBDOMAINS.none { prefix -> prefix.isNotEmpty() && d.startsWith(prefix) } }
                .distinct()
        } catch (_: Exception) { emptyList() }
    }

    // ── Layer 2: Verify ───────────────────────────────────────────────────────

    /**
     * Returns true if [domain] resolves to 127.0.0.1 in a fresh nslookup.
     * Uses `nslookup` against 127.0.0.1 (localhost DNS) to bypass any cached
     * upstream result. A false return means the OS DNS cache may still serve the
     * real IP — the caller should surface this as a warning.
     */
    fun verifyBlock(domain: String): Boolean {
        if (!isWindows) return false
        return try {
            val proc = ProcessBuilder("nslookup", domain, "127.0.0.1")
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            // nslookup output contains "Address:  127.0.0.1" when the hosts entry is active
            output.contains("127.0.0.1")
        } catch (_: Exception) { false }
    }

    /**
     * Checks how many of the expected hosts entries are currently in the file.
     * Returns a fraction 0.0–1.0. A value < 1.0 means some entries are missing.
     */
    fun integrityScore(domain: String): Float {
        if (!isWindows) return 0f
        return try {
            val root    = domain.lowercase().removePrefix("www.").trim()
            val content = normalizeContent(java.io.File(HOSTS_PATH).readText())
            val present = SUBDOMAINS.count { prefix ->
                content.contains("127.0.0.1  $prefix$root  $MARKER")
            }
            present.toFloat() / SUBDOMAINS.size.toFloat()
        } catch (_: Exception) { 0f }
    }

    // ── Layer 3: Monitor ──────────────────────────────────────────────────────

    /**
     * Start a background coroutine that checks every [intervalMs] ms whether
     * the hosts file still contains all FocusFlow entries. Missing entries are
     * silently re-applied. Call this after the first [blockDomain] succeeds.
     */
    fun startMonitor(intervalMs: Long = 30_000L) {
        if (monitorJob?.isActive == true) return
        monitorJob = monitorScope.launch {
            while (isActive) {
                delay(intervalMs)
                reapplyMissingBlocks()
            }
        }
    }

    fun stopMonitor() {
        monitorJob?.cancel()
        monitorJob = null
    }

    /**
     * For every domain in [monitoredDomains], check if all expected entries are
     * present in the hosts file. Re-apply the full block if any are missing.
     * This silently corrects tampering by antivirus or other tools.
     */
    private fun reapplyMissingBlocks() {
        if (!canWriteHostsFile()) return
        val domains = monitoredDomains
        if (domains.isEmpty()) return

        try {
            val content = normalizeContent(java.io.File(HOSTS_PATH).readText())
            val needsRepair = domains.any { root ->
                SUBDOMAINS.any { prefix -> !content.contains("127.0.0.1  $prefix$root  $MARKER") }
            }
            if (needsRepair) {
                domains.forEach { root -> blockDomain(root) }
            }
        } catch (_: Exception) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Strip Windows CRLF so line parsing is consistent across encodings. */
    private fun normalizeContent(content: String): String =
        content.replace("\r\n", "\n").replace("\r", "\n")

    private fun flushDnsCache() {
        try {
            ProcessBuilder("ipconfig", "/flushdns")
                .redirectErrorStream(true)
                .start()
                .waitFor()
        } catch (_: Exception) {}
    }

    val isAdminRequired: Boolean get() = isWindows

    fun canWriteHostsFile(): Boolean {
        if (!isWindows) return false
        return try { java.io.File(HOSTS_PATH).canWrite() } catch (_: Exception) { false }
    }
}
