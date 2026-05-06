package com.focusflow.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Purple80   = Color(0xFF6C63FF)
val Purple60   = Color(0xFF9D97FF)
val PurpleGrey = Color(0xFF625B71)
val Pink80     = Color(0xFFEF9A9A)

var Surface    by mutableStateOf(Color(0xFF12111A))
var Surface2   by mutableStateOf(Color(0xFF1C1B26))
var Surface3   by mutableStateOf(Color(0xFF252436))
var OnSurface  by mutableStateOf(Color(0xFFE8E6F0))
var OnSurface2 by mutableStateOf(Color(0xFFB0AEC8))

val Success = Color(0xFF4CAF50)
val Warning = Color(0xFFFFA726)
val Error   = Color(0xFFEF5350)

var isDarkTheme by mutableStateOf(true)

fun applyDarkTheme() {
    isDarkTheme = true
    Surface    = Color(0xFF12111A)
    Surface2   = Color(0xFF1C1B26)
    Surface3   = Color(0xFF252436)
    OnSurface  = Color(0xFFE8E6F0)
    OnSurface2 = Color(0xFFB0AEC8)
}

fun applyLightTheme() {
    isDarkTheme = false
    Surface    = Color(0xFFF3F2FF)
    Surface2   = Color(0xFFFFFFFF)
    Surface3   = Color(0xFFEAE9F8)
    OnSurface  = Color(0xFF1A1830)
    OnSurface2 = Color(0xFF55536E)
}

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
        fontSize = 12.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    )
)

@Composable
fun FocusFlowTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isDarkTheme) {
        darkColorScheme(
            primary              = Purple80,
            onPrimary            = Color(0xFF1A1830),
            primaryContainer     = Color(0xFF3D3580),
            onPrimaryContainer   = Color(0xFFE0DCFF),
            secondary            = Pink80,
            onSecondary          = Color(0xFF2D1515),
            background           = Surface,
            onBackground         = OnSurface,
            surface              = Surface2,
            onSurface            = OnSurface,
            surfaceVariant       = Surface3,
            onSurfaceVariant     = OnSurface2,
            outline              = Color(0xFF49475E),
            error                = Error,
            onError              = Color.White
        )
    } else {
        lightColorScheme(
            primary              = Purple80,
            onPrimary            = Color.White,
            primaryContainer     = Color(0xFFE8E6FF),
            onPrimaryContainer   = Color(0xFF1A1830),
            secondary            = Purple60,
            onSecondary          = Color.White,
            background           = Surface,
            onBackground         = OnSurface,
            surface              = Surface2,
            onSurface            = OnSurface,
            surfaceVariant       = Surface3,
            onSurfaceVariant     = OnSurface2,
            outline              = Color(0xFFB0AEC8),
            error                = Error,
            onError              = Color.White
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        content     = content
    )
}
