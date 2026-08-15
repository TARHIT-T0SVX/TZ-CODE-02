package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TZeronColorScheme = darkColorScheme(
    primary = TZeronPrimary,
    onPrimary = TZeronBgDark,
    primaryContainer = TZeronSurfaceElevated,
    onPrimaryContainer = TZeronTextPrimary,
    secondary = TZeronSecondary,
    onSecondary = TZeronBgDark,
    secondaryContainer = TZeronSurfaceElevated,
    onSecondaryContainer = TZeronTextPrimary,
    tertiary = TZeronTertiary,
    background = TZeronBgDark,
    onBackground = TZeronTextPrimary,
    surface = TZeronSurface,
    onSurface = TZeronTextPrimary,
    surfaceVariant = TZeronSurfaceElevated,
    onSurfaceVariant = TZeronTextSecondary,
    outline = TZeronBorder,
    outlineVariant = TZeronBorderSubtle,
    error = TZeronError,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TZeronColorScheme,
        typography = Typography,
        content = content
    )
}
