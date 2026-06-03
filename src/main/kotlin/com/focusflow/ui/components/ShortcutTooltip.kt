package com.focusflow.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.ui.theme.OnSurface2
import com.focusflow.ui.theme.Surface3

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortcutTooltip(
    shortcut: String,
    modifier: Modifier = Modifier,
    delayMillis: Int = 400,
    content: @Composable () -> Unit
) {
    TooltipArea(
        tooltip = { ShortcutBadge(shortcut) },
        modifier = modifier,
        delayMillis = delayMillis,
        tooltipPlacement = TooltipPlacement.CursorPoint(
            alignment = Alignment.BottomCenter,
            offset    = DpOffset(0.dp, 14.dp)
        ),
        content = content
    )
}

@Composable
fun ShortcutBadge(shortcut: String) {
    Text(
        text       = shortcut,
        color      = OnSurface2,
        fontSize   = 11.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
        modifier   = Modifier
            .shadow(4.dp, RoundedCornerShape(6.dp))
            .background(Surface3, RoundedCornerShape(6.dp))
            .border(1.dp, OnSurface2.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp)
    )
}
