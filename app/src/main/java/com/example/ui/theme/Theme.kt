package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = MediaraIndigoAccent,
    onPrimary = Color.White,
    primaryContainer = MediaraIndigoContainer,
    onPrimaryContainer = MediaraIndigo,
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
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = MediaraNavy,
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
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

@Composable
fun MediaraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Preserve brand palette consistency
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
