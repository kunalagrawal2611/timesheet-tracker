package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = DarkTertiary,
    onPrimary = DarkPurpleDeep,
    primaryContainer = DarkPurpleDeep,
    onPrimaryContainer = DarkTertiary,
    secondary = DarkSecondary,
    onSecondary = DarkBackground,
    tertiary = DarkTertiary,
    background = DarkBackground,
    onBackground = DarkPrimary,
    surface = DarkSurface,
    onSurface = DarkPrimary,
    outline = DarkBorder,
    surfaceVariant = DarkBorder,
    onSurfaceVariant = DarkSecondary
)

private val LightColorScheme = lightColorScheme(
    primary = LightTertiary,
    onPrimary = LightSurface,
    secondary = LightSecondary,
    onSecondary = LightPrimary,
    tertiary = LightTertiary,
    background = LightBackground,
    onBackground = LightPrimary,
    surface = LightSurface,
    onSurface = LightPrimary,
    outline = LightBorder,
    surfaceVariant = LightBorder,
    onSurfaceVariant = LightSecondary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
