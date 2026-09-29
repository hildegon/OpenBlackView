package com.blackvueeventos.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF101214)
val Panel = Color(0xFF1C2126)
val Line = Color(0xFF2E373E)
val Amber = Color(0xFFE8A317)
val InkText = Color(0xFF1A1203)
val Paper = Color(0xFFE7ECE8)
val Muted = Color(0xFF9AA4A8)
val Danger = Color(0xFFE07A64)

private val Colors = darkColorScheme(
    primary = Amber,
    onPrimary = InkText,
    background = Ink,
    onBackground = Paper,
    surface = Panel,
    onSurface = Paper,
    surfaceVariant = Panel,
    onSurfaceVariant = Muted,
    outline = Line,
    error = Danger,
)

@Composable
fun BlackvueTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
