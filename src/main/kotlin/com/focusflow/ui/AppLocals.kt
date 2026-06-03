package com.focusflow.ui

import androidx.compose.runtime.compositionLocalOf
import com.focusflow.data.models.Screen

val LocalNavigate = compositionLocalOf<(Screen) -> Unit> { {} }
