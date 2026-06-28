package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import android.content.Context
import android.content.SharedPreferences

enum class ThemePalette {
    SILVER_MONOCHROME
}

object ThemeManager {
    private var prefs: SharedPreferences? = null
    var currentTheme by mutableStateOf(ThemePalette.SILVER_MONOCHROME)
        private set

    fun init(context: Context) {}
    fun setTheme(palette: ThemePalette) {}
}

val Black = Color(0xFF000000)
val DarkGray = Color(0xFF111111)
val MediumGray = Color(0xFF333333)
val Silver = Color(0xFFC0C0C0)
val LightSilver = Color(0xFFE0E0E0)
val White = Color(0xFFFFFFFF)

// Backwards compatibility bindings
val CyanPrimary: Color get() = Silver
val CyanPrimaryContainer: Color get() = MediumGray
val BlueSecondary: Color get() = LightSilver
val BlueSecondaryContainer: Color get() = DarkGray
val ObsidianBackground: Color get() = Black
val DarkSurface: Color get() = DarkGray
val DarkSurfaceCard: Color get() = DarkGray
val CyberRed: Color get() = White
val TextPrimary: Color get() = White
val TextSecondary: Color get() = Silver
val BorderColor: Color get() = MediumGray
val ChromeYellow: Color get() = White
val ChromeYellowContainer: Color get() = MediumGray
val CherryRed: Color get() = White
val CyberGold: Color get() = Silver
val ElectricOrange: Color get() = Silver
val DarkChocolateBg: Color get() = Black
val ChocolateSurface: Color get() = DarkGray
val ChocolateSurfaceCard: Color get() = DarkGray
val ChocolateBorder: Color get() = MediumGray
val ElectricGreen: Color get() = Silver
val PureWhiteBg: Color get() = White
val LightSurface: Color get() = LightSilver
val LightSurfaceCard: Color get() = Silver
val LightBorder: Color get() = MediumGray
val TextPrimaryDark: Color get() = White
val TextSecondaryDark: Color get() = Silver
val TextPrimaryLight: Color get() = Black
val TextSecondaryLight: Color get() = MediumGray
val NeonGreen: Color get() = White


