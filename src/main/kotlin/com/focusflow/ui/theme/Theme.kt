package com.focusflow.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Purple80   = Color(0xFF6C63FF)
val Purple60   = Color(0xFF9D97FF)
val PurpleGrey = Color(0xFF625B71)
val Pink80     = Color(0xFFEF9A9A)

val Surface    = Color(0xFF12111A)
val Surface2   = Color(0xFF1C1B26)
val Surface3   = Color(0xFF252436)
val OnSurface  = Color(0xFFE8E6F0)
val OnSurface2 = Color(0xFFB0AEC8)

val Success    = Color(0xFF4CAF50)
val Warning    = Color(0xFFFFA726)
val Error      = Color(0xFFEF5350)

private val DarkColorScheme = darkColorScheme(
    primary         = Purple80,
    onPrimary       = Color(0xFF1A1830),
    primaryContainer = Color(0xFF3D3580),
    onPrimaryContainer = Color(0xFFE0DCFF),
    secondary       = Pink80,
    onSecondary     = Color(0xFF2D1515),
    background      = Surface,
    onBackground    = OnSurface,
    surface         = Surface2,
    onSurface       = OnSurface,
    surfaceVariant  = Surface3,
    onSurfaceVariant = OnSurface2,
    outline         = Color(0xFF49475E),
    error           = Error,
    onError         = Color.White
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        color = OnSurface2
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    )
)

@Composable
fun FocusFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
