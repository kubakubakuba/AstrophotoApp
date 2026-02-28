package eu.swpelc.astrophotoapp

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalNightMode = compositionLocalOf { false }

val nightColorScheme = darkColorScheme(
    primary              = Color(0xFFCC2200),
    onPrimary            = Color.Black,
    primaryContainer     = Color(0xFF3A0000),
    onPrimaryContainer   = Color(0xFFFF9988),
    secondary            = Color(0xFF991100),
    onSecondary          = Color.Black,
    secondaryContainer   = Color(0xFF280000),
    onSecondaryContainer = Color(0xFFFF6655),
    background           = Color(0xFF000000),
    onBackground         = Color(0xFFCC2200),
    surface              = Color(0xFF0A0000),
    onSurface            = Color(0xFFCC2200),
    surfaceVariant       = Color(0xFF180000),
    onSurfaceVariant     = Color(0xFFAA1100),
    outline              = Color(0xFF550000),
    outlineVariant       = Color(0xFF2A0000),
    error                = Color(0xFFFF6666),
    onError              = Color.Black,
)
