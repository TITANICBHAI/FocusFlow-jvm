package com.focusflow.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.ui.theme.OnSurface
import com.focusflow.ui.theme.Purple60
import com.focusflow.ui.theme.Purple80

@Composable
fun FocusFlowLogo(
    size: Dp = 32.dp,
    showText: Boolean = true,
    textColor: Color = OnSurface
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LogoMark(size = size)
        if (showText) {
            Column(verticalArrangement = Arrangement.spacedBy((-4).dp)) {
                Text(
                    text = "FocusFlow",
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.55f).sp,
                    color = textColor,
                    letterSpacing = 0.3.sp
                )
                Text(
                    text = "stay in the zone",
                    fontSize = (size.value * 0.28f).sp,
                    color = Purple60,
                    letterSpacing = 0.5.sp,
                    fontWeight = FontWeight.Light
                )
            }
        }
    }
}

@Composable
fun LogoMark(size: Dp = 32.dp) {
    val accentPurple   = Purple80
    val accentLight    = Color(0xFF9B8FFF)
    val bgGradStart    = Color(0xFF2D1B69)
    val bgGradEnd      = Color(0xFF1A0E3D)
    val glowColor      = Purple80.copy(alpha = 0.35f)

    Canvas(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape((size.value * 0.28f).dp))
    ) {
        val w = size.toPx()
        val h = size.toPx()
        val cx = w / 2f
        val cy = h / 2f
        val r  = w * 0.48f

        // Background gradient circle
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(bgGradStart, bgGradEnd),
                center = Offset(cx, cy * 0.85f),
                radius = r * 1.1f
            ),
            radius = r,
            center = Offset(cx, cy)
        )

        // Subtle glow ring
        drawCircle(
            color = glowColor,
            radius = r * 0.92f,
            center = Offset(cx, cy),
            style = Stroke(width = w * 0.04f)
        )

        // Outer arc — partial circle (focus ring)
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(accentLight, accentPurple, accentLight),
                center = Offset(cx, cy)
            ),
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter  = false,
            topLeft    = Offset(cx - r * 0.72f, cy - r * 0.72f),
            size       = androidx.compose.ui.geometry.Size(r * 1.44f, r * 1.44f),
            style      = Stroke(width = w * 0.06f, cap = StrokeCap.Round)
        )

        // Lightning bolt path (stylized "flow" mark)
        val boltPath = Path().apply {
            moveTo(cx + w * 0.06f, cy - h * 0.24f)
            lineTo(cx - w * 0.12f, cy + h * 0.02f)
            lineTo(cx + w * 0.02f, cy + h * 0.02f)
            lineTo(cx - w * 0.06f, cy + h * 0.24f)
            lineTo(cx + w * 0.14f, cy - h * 0.03f)
            lineTo(cx + w * 0.01f, cy - h * 0.03f)
            close()
        }
        drawPath(
            path  = boltPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color.White, accentLight),
                startY = cy - h * 0.24f,
                endY   = cy + h * 0.24f
            )
        )

        // Center dot
        drawCircle(
            color  = Color.White.copy(alpha = 0.9f),
            radius = w * 0.04f,
            center = Offset(cx, cy)
        )
    }
}
