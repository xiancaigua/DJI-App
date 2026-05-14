package com.example.uavmobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = SkyAccent,
    secondary = Sand,
    tertiary = Alert,
    background = SkyDark,
    surface = SkyPanel,
    onPrimary = SkyDark,
    onSecondary = SkyDark,
    onTertiary = Ink,
    onBackground = Ink,
    onSurface = Ink,
)

private val ColorWhite = androidx.compose.ui.graphics.Color(0xFFF8FBFD)

private val LightColors = lightColorScheme(
    primary = SkyDark,
    secondary = Sand,
    tertiary = Alert,
    background = Ink,
    surface = ColorWhite,
    onPrimary = Ink,
    onSecondary = SkyDark,
    onTertiary = Ink,
    onBackground = SkyDark,
    onSurface = SkyDark,
)

@Composable
fun Px4MobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
