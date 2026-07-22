package com.example.toolkit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NexusDarkScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = Color.White,
    primaryContainer = SurfaceRaised,
    onPrimaryContainer = NeonGreen,
    secondary = DimGreen,
    onSecondary = VoidBlack,
    secondaryContainer = PanelGreen,
    onSecondaryContainer = GhostWhite,
    tertiary = SoftGreen,
    onTertiary = VoidBlack,
    background = VoidBlack,
    onBackground = GhostWhite,
    surface = MatrixBlack,
    onSurface = GhostWhite,
    surfaceVariant = PanelGreen,
    onSurfaceVariant = MuteGreen,
    surfaceTint = NeonGreen,
    outline = BorderGreen,
    outlineVariant = BorderGreen,
    error = AlertRed,
    onError = Color.White
)

@Composable
fun ToolkitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NexusDarkScheme,
        typography = Typography,
        shapes = NexusShapes,
        content = content
    )
}
