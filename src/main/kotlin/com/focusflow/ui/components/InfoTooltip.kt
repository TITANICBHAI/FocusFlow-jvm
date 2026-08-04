package com.focusflow.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.ui.theme.OnSurface2
import com.focusflow.ui.theme.Surface3

/**
 * Hover tooltip for informational text — same [TooltipArea] base as [ShortcutTooltip]
 * but styled for readable multi-line descriptions rather than keyboard shortcut badges.
 *
 * Usage:
 * ```
 * InfoTooltip("Some explanation") {
 *     MyWidget()
 * }
 * ```
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InfoTooltip(
    text: String,
    modifier: Modifier = Modifier,
    delayMillis: Int = 500,
    content: @Composable () -> Unit
) {
    TooltipArea(
        tooltip = {
            Text(
                text     = text,
                color    = OnSurface2,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .shadow(6.dp, RoundedCornerShape(9.dp))
                    .background(Surface3, RoundedCornerShape(9.dp))
                    .border(1.dp, OnSurface2.copy(alpha = 0.12f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 11.dp, vertical = 8.dp)
            )
        },
        modifier         = modifier,
        delayMillis      = delayMillis,
        tooltipPlacement = TooltipPlacement.CursorPoint(
            alignment = Alignment.BottomCenter,
            offset    = DpOffset(0.dp, 14.dp)
        ),
        content = content
    )
}
