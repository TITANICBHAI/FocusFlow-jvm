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
import com.focusflow.data.Database
import com.focusflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class KeywordPreset(
    val label: String,
    val emoji: String,
    val keywords: List<String>
)

private val PRESETS = listOf(
    KeywordPreset("Doomscroll Bait", "📰", listOf("breaking news","trending","viral","outrage","shocking","drama","exposed")),
    KeywordPreset("Social Drama", "🎭", listOf("beef","callout","canceled","toxic","fight","clout","tea")),
    KeywordPreset("Shorts Bait", "📱", listOf("pov:","wait for it","you won't believe","try not to laugh","insane reaction","plot twist")),
    KeywordPreset("Shopping", "🛒", listOf("deal","sale","% off","limited time","add to cart","buy now","flash deal","coupon")),
    KeywordPreset("Gambling", "🎰", listOf("bet","casino","jackpot","slots","poker","spin","wager","odds","prize")),
    KeywordPreset("NSFW", "🔞", listOf("nude","nsfw","explicit","adult content","18+","onlyfans","xxx"))
)

@Composable
fun KeywordBlockerScreen() {
    val scope = rememberCoroutineScope()

    var enabled       by remember { mutableStateOf(false) }
    var keywords      by remember { mutableStateOf(listOf<String>()) }
    var newKeyword    by remember { mutableStateOf("") }
    var expandPresets by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            withContext(Dispatchers.IO) {
                enabled  = Database.isKeywordBlockerEnabled()
                keywords = Database.getBlockedKeywords()
            }
        }
    }

    fun save(newList: List<String>) {
        scope.launch {
            withContext(Dispatchers.IO) { Database.setBlockedKeywords(newList) }
            keywords = newList
        }
    }

    fun toggleEnabled(v: Boolean) {
        scope.launch {
            withContext(Dispatchers.IO) { Database.setKeywordBlockerEnabled(v) }
            enabled = v
        }
    }

    LaunchedEffect(Unit) { reload() }

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize().background(Surface)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 32.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Text("Keyword Blocker", style = MaterialTheme.typography.headlineLarge, color = OnSurface)
            Text("Block browser tabs and sites matching these words or phrases", style = MaterialTheme.typography.bodyMedium, color = OnSurface2)

            // Enable card
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface2)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Enable Keyword Blocker", color = OnSurface, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (enabled) "Active · matching tabs will be blocked"
                        else "Disabled · keywords are saved but not enforced",
                        color = if (enabled) Success else OnSurface2,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { toggleEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Surface, checkedTrackColor = Purple80)
                )
            }

            // Add keyword
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface2)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Add Keyword or Phrase", color = OnSurface, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newKeyword,
                        onValueChange = { newKeyword = it },
                        placeholder = { Text("e.g. trending, viral, breaking…", color = OnSurface2) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Purple80,
                            unfocusedBorderColor = OnSurface2
                        )
                    )
                    Button(
                        onClick = {
                            val kw = newKeyword.trim().lowercase()
                            if (kw.isNotEmpty() && !keywords.contains(kw)) {
                                val updated = keywords + kw
                                save(updated)
                                newKeyword = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Purple80)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }

            // Current keyword list
            if (keywords.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface2)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Keywords (${keywords.size})", color = OnSurface, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { save(emptyList()) }) {
                            Text("Clear All", color = Error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    keywords.forEach { kw ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Surface3)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(kw, color = OnSurface, style = MaterialTheme.typography.bodyMedium)
                            IconButton(
                                onClick = { save(keywords - kw) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = OnSurface2, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Quick presets
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface2)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { expandPresets = !expandPresets },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Quick Presets", color = OnSurface, fontWeight = FontWeight.SemiBold)
                        Text("Add curated keyword sets with one click", color = OnSurface2, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(
                        if (expandPresets) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null, tint = OnSurface2
                    )
                }

                if (expandPresets) {
                    PRESETS.forEach { preset ->
                        val allAdded = preset.keywords.all { keywords.contains(it) }
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Surface3)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${preset.emoji} ${preset.label}", color = OnSurface, fontWeight = FontWeight.Medium)
                                Text(
                                    preset.keywords.take(4).joinToString(", ") + if (preset.keywords.size > 4) "…" else "",
                                    color = OnSurface2,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            if (allAdded) {
                                TextButton(onClick = { save(keywords - preset.keywords.toSet()) }) {
                                    Text("Remove", color = Error)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        val merged = (keywords + preset.keywords).distinct()
                                        save(merged)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Purple80)
                                ) {
                                    Text("Add All")
                                }
                            }
                        }
                    }
                }
            }

            // Footer note
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Warning.copy(alpha = 0.08f))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Warning, modifier = Modifier.size(18.dp))
                Text(
                    "Keywords are saved and enforced by watching the foreground window title on Windows. When an active window's title contains a blocked keyword, the app is killed. For full URL-level blocking, pair with the Hosts Blocker.",
                    color = OnSurface2,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(16.dp))
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp)
        )
    }
}
