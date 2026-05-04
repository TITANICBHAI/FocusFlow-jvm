package com.focusflow.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.ui.theme.OnSurface
import com.focusflow.ui.theme.Purple60
import com.focusflow.ui.theme.Purple80

private val CyanBlue   = Color(0xFF4FC3F7)
private val ArcPurple  = Color(0xFF7C4DFF)
private val CrosshairC = Color(0xFF3A4A7A)

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
                    text = "by TBTechs",
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
    Canvas(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape((size.value * 0.22f).dp))
    ) {
        val w  = this.size.width
        val h  = this.size.height
        val cx = w / 2f
        val cy = h / 2f

        // ── Background: near-black ───────────────────────────────────────────
        drawRect(color = Color(0xFF09090F))

        // ── Subtle diagonal crosshair lines ──────────────────────────────────
        val crossPaint = Paint().apply {
            color = CrosshairC.copy(alpha = 0.35f)
            strokeWidth = w * 0.018f
        }
        drawIntoCanvas { canvas ->
            canvas.drawLine(Offset(0f, 0f), Offset(w, h), crossPaint)
            canvas.drawLine(Offset(w, 0f), Offset(0f, h), crossPaint)
        }

        // ── Glow bloom behind arc ────────────────────────────────────────────
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    CyanBlue.copy(alpha = 0.18f),
                    ArcPurple.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = w * 0.52f
            ),
            radius = w * 0.52f,
            center = Offset(cx, cy)
        )

        val arcStroke = w * 0.115f
        val arcR      = w * 0.345f
        val arcTop    = Offset(cx - arcR, cy - arcR)
        val arcSize   = Size(arcR * 2f, arcR * 2f)

        // ── Upper-right arc: cyan-blue (from ~315° sweeping ~185°) ──────────
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(
                    CyanBlue.copy(alpha = 0.0f),
                    CyanBlue.copy(alpha = 0.6f),
                    CyanBlue,
                    CyanBlue,
                    CyanBlue.copy(alpha = 0.7f),
                    CyanBlue.copy(alpha = 0.0f)
                ),
                center = Offset(cx, cy)
            ),
            startAngle = -130f,
            sweepAngle = 190f,
            useCenter  = false,
            topLeft    = arcTop,
            size       = arcSize,
            style      = Stroke(width = arcStroke, cap = StrokeCap.Round)
        )

        // ── Lower-left arc: purple (from ~135° sweeping ~140°) ──────────────
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(
                    ArcPurple.copy(alpha = 0.0f),
                    ArcPurple.copy(alpha = 0.5f),
                    ArcPurple,
                    ArcPurple,
                    ArcPurple.copy(alpha = 0.0f)
                ),
                center = Offset(cx, cy)
            ),
            startAngle = 100f,
            sweepAngle = 130f,
            useCenter  = false,
            topLeft    = arcTop,
            size       = arcSize,
            style      = Stroke(width = arcStroke, cap = StrokeCap.Round)
        )

        // ── Center dash/minus ────────────────────────────────────────────────
        val dashLen  = w * 0.16f
        val dashY    = cy
        val dashPaint = Paint().apply {
            color = Color(0xFF6699EE)
            strokeWidth = w * 0.055f
            strokeCap   = androidx.compose.ui.graphics.StrokeCap.Round
        }
        drawIntoCanvas { canvas ->
            canvas.drawLine(
                p1    = Offset(cx - dashLen, dashY),
                p2    = Offset(cx + dashLen, dashY),
                paint = dashPaint
            )
        }
    }
}
