package com.tuananh.bothost.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6EE7B7),
    onPrimary = Color(0xFF06281D),
    secondary = Color(0xFF60A5FA),
    tertiary = Color(0xFFFBBF24),
    background = Color(0xFF0B1020),
    surface = Color(0xFF121A2E),
    surfaceVariant = Color(0xFF1B2740),
    onBackground = Color(0xFFE8EEF9),
    onSurface = Color(0xFFE8EEF9),
    onSurfaceVariant = Color(0xFFB7C3D8),
    error = Color(0xFFF87171)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF047857),
    secondary = Color(0xFF2563EB),
    tertiary = Color(0xFFD97706),
    background = Color(0xFFF4F7FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFE8EEF7)
)

@Composable
fun BotHostTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
