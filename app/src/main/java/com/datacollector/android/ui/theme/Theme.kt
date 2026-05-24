package com.datacollector.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = LavenderDeep,
    onPrimary = PaperWhite,
    primaryContainer = Lavender,
    onPrimaryContainer = Ink,
    secondary = PeachDeep,
    onSecondary = Ink,
    secondaryContainer = Peach,
    onSecondaryContainer = Ink,
    tertiary = MintDeep,
    onTertiary = Ink,
    tertiaryContainer = Mint,
    onTertiaryContainer = Ink,
    background = LavenderBackground,
    onBackground = Ink,
    surface = PaperWhite,
    onSurface = Ink,
    surfaceVariant = LavenderBackgroundDeep,
    onSurfaceVariant = InkMuted,
    outline = InkSoft,
)

@Composable
fun RI4SUTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content,
    )
}
