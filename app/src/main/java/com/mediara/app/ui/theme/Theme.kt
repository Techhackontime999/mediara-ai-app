package com.mediara.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mediara.app.data.preferences.AccentOption
import com.mediara.app.data.preferences.AppThemeSettings
import com.mediara.app.data.preferences.DarkModeOption
import com.mediara.app.data.preferences.FontStyleOption

// Consistent corner language: small controls stay tight, surfaces sit at 16dp,
// and hero containers use generous radii.
val MediaraShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private fun paletteFor(accent: AccentPalette): LightAndDark = LightAndDark(
    light = lightColorScheme(
        primary = accent.lightPrimary,
        onPrimary = accent.lightOnPrimary,
        primaryContainer = accent.lightPrimaryContainer,
        onPrimaryContainer = accent.lightOnPrimaryContainer,
        secondary = MediaraTeal,
        onSecondary = Color.White,
        secondaryContainer = MediaraTealContainer,
        onSecondaryContainer = MediaraTealDark,
        tertiary = MediaraAmber,
        onTertiary = Color.White,
        tertiaryContainer = MediaraAmberContainer,
        onTertiaryContainer = Color(0xFF78350F),
        background = MediaraBackgroundLight,
        onBackground = MediaraNavy,
        surface = MediaraSurfaceLight,
        onSurface = MediaraNavy,
        surfaceVariant = MediaraSurfaceSubtle,
        onSurfaceVariant = Color(0xFF475569),
        error = MediaraRoseAlert,
        onError = Color.White,
        errorContainer = MediaraRoseContainer,
        onErrorContainer = Color(0xFF881337),
        outline = MediaraBorder
    ),
    dark = darkColorScheme(
        primary = accent.darkPrimary,
        onPrimary = accent.darkOnPrimary,
        primaryContainer = accent.darkPrimaryContainer,
        onPrimaryContainer = accent.darkOnPrimaryContainer,
        secondary = MediaraTealLight,
        onSecondary = MediaraNavy,
        secondaryContainer = Color(0xFF134E4A),
        onSecondaryContainer = MediaraTealContainer,
        tertiary = Color(0xFFFBBF24),
        onTertiary = MediaraNavy,
        tertiaryContainer = Color(0xFF78350F),
        onTertiaryContainer = MediaraAmberContainer,
        background = MediaraBackgroundDark,
        onBackground = Color(0xFFF1F5F9),
        surface = MediaraSurfaceDark,
        onSurface = Color(0xFFF8FAFC),
        surfaceVariant = MediaraSurfaceSubtleDark,
        onSurfaceVariant = Color(0xFF94A3B8),
        error = Color(0xFFFB7185),
        onError = MediaraNavy,
        errorContainer = Color(0xFF4C0519),
        onErrorContainer = MediaraRoseContainer,
        outline = MediaraBorderDark
    )
)

private data class LightAndDark(
    val light: androidx.compose.material3.ColorScheme,
    val dark: androidx.compose.material3.ColorScheme
)

private fun fontFamilyFor(font: FontStyleOption): FontFamily = when (font) {
    FontStyleOption.DEFAULT -> FontFamily.SansSerif
    FontStyleOption.SERIF -> FontFamily.Serif
    FontStyleOption.MONOSPACE -> FontFamily.Monospace
}

@Composable
fun MediaraTheme(
    settings: AppThemeSettings = AppThemeSettings(),
    content: @Composable () -> Unit
) {
    val accent = when (settings.accent) {
        AccentOption.INDIGO -> MediaraAccents.Indigo
        AccentOption.TEAL -> MediaraAccents.Teal
        AccentOption.NAVY -> MediaraAccents.Navy
        AccentOption.FOREST -> MediaraAccents.Forest
        AccentOption.ROSE -> MediaraAccents.Rose
        AccentOption.PURPLE -> MediaraAccents.Purple
        AccentOption.AMBER -> MediaraAccents.Amber
        AccentOption.SLATE -> MediaraAccents.Slate
    }
    val palette = paletteFor(accent)

    val darkTheme = when (settings.darkMode) {
        DarkModeOption.SYSTEM -> isSystemInDarkTheme()
        DarkModeOption.LIGHT -> false
        DarkModeOption.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) palette.dark else palette.light,
        typography = buildTypography(
            scale = settings.textSize.scale,
            family = fontFamilyFor(settings.fontStyle)
        ),
        shapes = MediaraShapes,
        content = content
    )
}