package com.focusflow.ui.screens

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.services.CrashReporter
import com.focusflow.ui.components.openUrl
import com.focusflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import java.awt.Desktop
import java.net.URI

@Composable
fun ContactScreen() {
    val scope       = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var crashLogs      by remember { mutableStateOf(listOf<File>()) }
    var selectedLog    by remember { mutableStateOf<File?>(null) }
    var logPreview     by remember { mutableStateOf("") }
    var showPreview    by remember { mutableStateOf(false) }
    var statusMessage  by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        crashLogs = withContext(Dispatchers.IO) { CrashReporter.findCrashLogs() }
    }

    fun refreshLogs() {
        scope.launch {
            crashLogs = withContext(Dispatchers.IO) { CrashReporter.findCrashLogs() }
        }
    }

    fun emailLog(file: File) {
        scope.launch {
            val content = withContext(Dispatchers.IO) {
                try { file.readText(Charsets.UTF_8).take(6000) } catch (_: Throwable) { "" }
            }
            try {
                val subject = "FocusFlow Crash Report – ${file.name}"
                val body = buildString {
                    appendLine("Hi,")
                    appendLine()
                    appendLine("I'm attaching a crash report from FocusFlow.")
                    appendLine("Log file: ${file.absolutePath}")
                    appendLine()
                    appendLine("──────────────────────────────────────────")
                    appendLine(content)
                    if (content.length >= 6000) appendLine("\n[...log truncated — please also attach the full file]")
                }
                val encoded = java.net.URLEncoder.encode(body, "UTF-8").replace("+", "%20")
                val subjectEnc = java.net.URLEncoder.encode(subject, "UTF-8").replace("+", "%20")
                val uri = URI("mailto:${CrashReporter.SUPPORT_EMAIL}?subject=$subjectEnc&body=$encoded")
                Desktop.getDesktop().mail(uri)
                statusMessage = "Email client opened. Please also attach the log file manually."
            } catch (_: Throwable) {
                openUrl("mailto:${CrashReporter.SUPPORT_EMAIL}?subject=FocusFlow+Crash+Report")
                statusMessage = "Opened email client. Attach the log file: ${file.absolutePath}"
            }
        }
    }

    fun openLogFolder(file: File) {
        try {
            Desktop.getDesktop().open(file.parentFile ?: file)
        } catch (_: Throwable) {
            statusMessage = "Log location: ${file.absolutePath}"
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Surface)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // ── Header ──────────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.SupportAgent, contentDescription = null, tint = Purple80, modifier = Modifier.size(28.dp))
                Column {
                    Text("Contact & Bug Reports", style = MaterialTheme.typography.headlineSmall,
                        color = OnSurface, fontWeight = FontWeight.Bold)
                    Text("Report crashes or get in touch with support",
                        style = MaterialTheme.typography.bodyMedium, color = OnSurface2)
                }
            }

            // ── Status banner ───────────────────────────────────────────────────
            if (statusMessage.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Success.copy(alpha = 0.12f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.size(18.dp))
                    Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = OnSurface, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Close, contentDescription = "Dismiss",
                        tint = OnSurface2, modifier = Modifier.size(16.dp).clickable { statusMessage = "" })
                }
            }

            // ── Contact buttons ─────────────────────────────────────────────────
            SectionCard(title = "Get in Touch", icon = Icons.Default.Email) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ContactRow(
                        icon    = Icons.Default.Email,
                        label   = "Email Support",
                        sublabel = CrashReporter.SUPPORT_EMAIL,
                        onClick  = { openUrl("mailto:${CrashReporter.SUPPORT_EMAIL}") }
                    )
                    ContactRow(
                        icon    = Icons.Default.BugReport,
                        label   = "Report a Bug on GitHub",
                        sublabel = "github.com/TITANICBHAI/FocusFlow-jvm/issues",
                        onClick  = { openUrl("https://github.com/TITANICBHAI/FocusFlow-jvm/issues/new") }
                    )
                }
            }

            // ── Crash logs ──────────────────────────────────────────────────────
            SectionCard(
                title = "Crash Logs",
                icon  = Icons.Default.Article,
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { refreshLogs() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Refresh", fontSize = 12.sp)
                        }
                    }
                }
            ) {
                if (crashLogs.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface3)
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                            tint = Success, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("No crash logs found – all good!", color = OnSurface2,
                            style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${crashLogs.size} crash log${if (crashLogs.size != 1) "s" else ""} found",
                            style = MaterialTheme.typography.bodySmall, color = OnSurface2)

                        crashLogs.forEach { log ->
                            CrashLogRow(
                                file      = log,
                                isSelected = selectedLog == log,
                                onEmail   = { emailLog(log) },
                                onOpenFolder = { openLogFolder(log) },
                                onPreview = {
                                    scope.launch {
                                        val text = withContext(Dispatchers.IO) {
                                            try { log.readText(Charsets.UTF_8).take(3000) }
                                            catch (_: Throwable) { "Could not read file." }
                                        }
                                        selectedLog  = log
                                        logPreview   = text
                                        showPreview  = true
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // ── Help tip ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Purple80.copy(alpha = 0.08f))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Purple80, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("How to send a crash report", color = Purple80,
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Click \"Email Report\" next to a log file. Your email client will open with the crash details pre-filled. " +
                        "For best results, also attach the log file manually — click \"Open Folder\" to find it.",
                        color = OnSurface2, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        VerticalScrollbar(
            adapter  = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp)
        )
    }

    // ── Log preview dialog ───────────────────────────────────────────────────────
    if (showPreview && selectedLog != null) {
        AlertDialog(
            onDismissRequest = { showPreview = false },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showPreview = false; selectedLog?.let { emailLog(it) } }) {
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Email Report")
                    }
                    TextButton(onClick = { showPreview = false }) { Text("Close") }
                }
            },
            title = {
                Text(selectedLog?.name ?: "Crash Log", fontWeight = FontWeight.Bold, color = OnSurface)
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Surface3)
                        .padding(12.dp)
                ) {
                    val previewScroll = rememberScrollState()
                    Text(
                        logPreview + if (logPreview.length >= 3000) "\n\n[...preview truncated]" else "",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = OnSurface2,
                        fontSize = 11.sp,
                        modifier = Modifier.verticalScroll(previewScroll)
                    )
                }
            },
            containerColor = Surface2,
            titleContentColor = OnSurface
        )
    }
}

// ── CrashLogRow ─────────────────────────────────────────────────────────────────
@Composable
private fun CrashLogRow(
    file: File,
    isSelected: Boolean,
    onEmail: () -> Unit,
    onOpenFolder: () -> Unit,
    onPreview: () -> Unit
) {
    val sizeKb = file.length() / 1024L
    val fmt    = DecimalFormat("#,###")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Purple80.copy(alpha = 0.10f) else Surface3)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Description, contentDescription = null,
            tint = if (isSelected) Purple80 else OnSurface2, modifier = Modifier.size(20.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(file.name, color = OnSurface, style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium, maxLines = 1)
            Text("${fmt.format(sizeKb)} KB  •  ${file.parent}",
                color = OnSurface2, style = MaterialTheme.typography.labelSmall,
                maxLines = 1, fontSize = 10.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onPreview, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Visibility, contentDescription = "Preview",
                    tint = OnSurface2, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onOpenFolder, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Open folder",
                    tint = OnSurface2, modifier = Modifier.size(16.dp))
            }
            Button(
                onClick = onEmail,
                colors  = ButtonDefaults.buttonColors(containerColor = Purple80),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Email Report", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ── ContactRow ───────────────────────────────────────────────────────────────────
@Composable
private fun ContactRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    sublabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface3)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Purple80, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = OnSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(sublabel, color = OnSurface2, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
        }
        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = OnSurface2, modifier = Modifier.size(14.dp))
    }
}

// ── SectionCard ──────────────────────────────────────────────────────────────────
@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(icon, contentDescription = null, tint = Purple80, modifier = Modifier.size(18.dp))
            Text(title, color = OnSurface, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        HorizontalDivider(color = Surface3, thickness = 1.dp)
        content()
    }
}
