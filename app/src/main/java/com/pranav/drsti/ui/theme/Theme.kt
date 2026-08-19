package com.pranav.drsti.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = SaffronDark,
    onPrimary = Parchment,
    secondary = IndigoNightLight,
    background = Parchment,
    surface = Parchment,
    onBackground = Ink,
    onSurface = Ink,
    error = SoftRed
)

private val DarkColors = darkColorScheme(
    primary = Saffron,
    onPrimary = IndigoNight,
    secondary = MutedRose,
    background = IndigoNight,
    surface = IndigoNightLight,
    onBackground = Parchment,
    onSurface = Parchment,
    error = SoftRed
)

@Composable
fun DrshtiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // off by default — Dṛṣṭi has an intentional palette, not a generic dynamic one
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DrshtiTypography,
        content = content
    )
}
