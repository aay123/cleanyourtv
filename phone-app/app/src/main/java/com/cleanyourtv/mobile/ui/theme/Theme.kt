package com.cleanyourtv.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCFFFCF),
    onPrimary = Color(0xFF0B0D10),
    primaryContainer = Color(0xFF1E3A26),
    onPrimaryContainer = Color(0xFFCFFFCF),
    secondary = Color(0xFF9BD8B0),
    onSecondary = Color(0xFF0B0D10),
    background = Color(0xFF0B0D10),
    onBackground = Color(0xFFE8EDE9),
    surface = Color(0xFF14181D),
    onSurface = Color(0xFFE8EDE9),
    surfaceVariant = Color(0xFF1D232A),
    onSurfaceVariant = Color(0xFFB8C2BC),
    outline = Color(0xFF3A444C),
    error = Color(0xFFFF9A9A),
    onError = Color(0xFF3A0A0A),
)

@Composable
fun CleanYourTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography(),
        content = content,
    )
}
