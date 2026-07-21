package com.focusflow.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.ui.theme.*
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// HintType — controls colour + default icon
// ─────────────────────────────────────────────────────────────────────────────

enum class HintType {
    /** Neutral informational tip (purple). */
    INFO,
    /** Positive guidance / next-step tip (green). */
    TIP,
    /** Something that may prevent a feature from working (orange). */
    WARNING
}

private fun HintType.color(): Color = when (this) {
    HintType.INFO    -> Purple80
    HintType.TIP     -> Success
    HintType.WARNING -> Warning
}

private fun HintType.defaultIcon(): ImageVector = when (this) {
    HintType.INFO    -> Icons.Default.Info
    HintType.TIP     -> Icons.Default.Lightbulb
    HintType.WARNING -> Icons.Default.Warning
}

// ─────────────────────────────────────────────────────────────────────────────
// HintCard — static, collapsible hint shown inline inside a screen
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A static contextual hint card that lives permanently inside a screen.
 *
 * - [title]        Short bold label shown in the collapsed row.
 * - [message]      Full explanation shown when expanded.
 * - [type]         Controls colour and default icon.
 * - [icon]         Override the default icon for this [type].
 * - [collapsible]  When false the card is always fully expanded (no toggle arrow).
 * - [startExpanded] Initial expanded state; the user can collapse afterwards.
 */
@Composable
fun HintCard(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    type: HintType = HintType.INFO,
    icon: ImageVector? = null,
    collapsible: Boolean = true,
    startExpanded: Boolean = true,
) {
    val color    = type.color()
    val iconVec  = icon ?: type.defaultIcon()
    var expanded by remember { mutableStateOf(startExpanded) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.09f))
            .then(
                if (collapsible) Modifier.clickable { expanded = !expanded }
                else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // ── Header row ────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(iconVec, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
            Text(
                text       = title ?: when (type) {
                    HintType.INFO    -> "How this works"
                    HintType.TIP     -> "Tip"
                    HintType.WARNING -> "Important"
                },
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color      = color,
                modifier   = Modifier.weight(1f)
            )
            if (collapsible) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = color.copy(alpha = 0.7f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // ── Body (animated expand/collapse) ───────────────────────────────────
        AnimatedVisibility(
            visible = expanded || !collapsible,
            enter   = expandVertically(tween(160)) + fadeIn(tween(160)),
            exit    = shrinkVertically(tween(140)) + fadeOut(tween(140))
        ) {
            Text(
                text      = message,
                fontSize  = 11.sp,
                color     = OnSurface2,
                lineHeight = 16.sp,
                modifier  = Modifier.padding(top = 6.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LiveHintBanner — transient hint triggered by a user action
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A transient animated banner that slides in when [visible] becomes true and
 * auto-dismisses after [autoDismissMs] milliseconds. Call [onDismiss] to clear
 * the trigger in the parent's state.
 *
 * Typical usage:
 * ```
 * var hint by remember { mutableStateOf<Pair<HintType, String>?>(null) }
 *
 * // somewhere in an onClick:
 * hint = HintType.TIP to "Always-On is now live — blocked apps will be killed immediately."
 *
 * // in the composable layout:
 * LiveHintBanner(hint?.second ?: "", visible = hint != null, type = hint?.first ?: HintType.INFO) {
 *     hint = null
 * }
 * ```
 */
@Composable
fun LiveHintBanner(
    message: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
    type: HintType = HintType.INFO,
    autoDismissMs: Long = 4_500L,
    onDismiss: () -> Unit = {},
) {
    val color   = type.color()
    val iconVec = type.defaultIcon()

    // Auto-dismiss timer — restarted every time [visible] flips to true
    LaunchedEffect(visible) {
        if (visible) {
            delay(autoDismissMs)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(tween(200)) { -it / 2 } + fadeIn(tween(200)),
        exit  = slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(180))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.13f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(iconVec, null, tint = color, modifier = Modifier.size(16.dp))
            Text(
                text       = message,
                fontSize   = 12.sp,
                color      = OnSurface,
                lineHeight = 17.sp,
                modifier   = Modifier.weight(1f)
            )
            IconButton(
                onClick  = onDismiss,
                modifier = Modifier.size(22.dp)
            ) {
                Icon(Icons.Default.Close, "Dismiss", tint = OnSurface2, modifier = Modifier.size(14.dp))
            }
        }
    }
}
