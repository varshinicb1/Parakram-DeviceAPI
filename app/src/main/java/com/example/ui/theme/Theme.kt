package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = DarkChocolateBg,
    primaryContainer = CyanPrimaryContainer,
    onPrimaryContainer = TextPrimaryDark,
    secondary = BlueSecondary,
    onSecondary = TextPrimaryDark,
    secondaryContainer = ChocolateSurfaceCard,
    onSecondaryContainer = TextPrimaryDark,
    background = DarkChocolateBg,
    onBackground = TextPrimaryDark,
    surface = ChocolateSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ChocolateSurfaceCard,
    onSurfaceVariant = TextPrimaryDark,
    error = CherryRed,
    onError = TextPrimaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = CyanPrimary,
    onPrimary = TextPrimaryLight,
    primaryContainer = CyanPrimaryContainer,
    onPrimaryContainer = TextPrimaryDark,
    secondary = ElectricOrange,
    onSecondary = PureWhiteBg,
    secondaryContainer = LightSurfaceCard,
    onSecondaryContainer = TextPrimaryLight,
    background = PureWhiteBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceCard,
    onSurfaceVariant = TextSecondaryLight,
    error = CherryRed,
    onError = PureWhiteBg
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Configurable theme
    dynamicColor: Boolean = false, // Keep our unique custom premium brand intact
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
