package com.fmhub24.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFFFF6B35),
    secondary = Color(0xFF5EE7FF),
    background = Color(0xFF070B12),
    surface = Color(0xFF101722),
    surfaceVariant = Color(0xFF182232),
    onPrimary = Color.White,
    onSecondary = Color(0xFF041018),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFF94A3B8),
)

@Composable
fun FMHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
