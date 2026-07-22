package com.focusflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focusflow.ui.theme.*

/**
 * Standalone Block screen — a timed, session-free block for a fixed duration.
 *
 * This is a direct extraction of TimedBlockTab from AppBlockerScreen. It lives
 * as its own top-level screen under Block Controls so it is immediately
 * discoverable without navigating into the App Blocker tab bar.
 *
 * Standalone blocks are enforced by [com.focusflow.services.StandaloneBlockService]
 * independently of Always-On Enforcement and Focus Sessions — no enforcement
 * status banner is shown here because the block starts the moment you activate it.
 */
@Composable
fun StandaloneBlockScreen() {
    Column(modifier = Modifier.fillMaxSize().background(Surface)) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface2)
                .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Default.Timer, null, tint = Purple80, modifier = Modifier.size(28.dp))
            Column {
                Text(
                    "Standalone Block",
                    style      = MaterialTheme.typography.headlineMedium,
                    color      = OnSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Block specific apps for a fixed duration — starts immediately, no session needed",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface2
                )
            }
        }

        // ── Content ───────────────────────────────────────────────────────────
        TimedBlockTab()
    }
}
