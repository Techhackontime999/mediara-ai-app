package com.mediara.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Per-accent palettes for the personalization settings. Each accent defines its
 * full Material-3 primary step for both light and dark schemes so the whole app
 * re-themes from one source of truth (single-theme rule from the UX skill pack).
 */
data class AccentPalette(
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightOnPrimaryContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color,
)

object MediaraAccents {

    val Indigo = AccentPalette(
        lightPrimary = Color(0xFF4338CA),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFEEF2FF),
        lightOnPrimaryContainer = Color(0xFF1E1B4B),
        darkPrimary = Color(0xFF818CF8),
        darkOnPrimary = Color(0xFF1E1B4B),
        darkPrimaryContainer = Color(0xFF312E81),
        darkOnPrimaryContainer = Color(0xFFE0E7FF),
    )

    val Teal = AccentPalette(
        lightPrimary = Color(0xFF0D9488),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFCCFBF1),
        lightOnPrimaryContainer = Color(0xFF042F2E),
        darkPrimary = Color(0xFF2DD4BF),
        darkOnPrimary = Color(0xFF042F2E),
        darkPrimaryContainer = Color(0xFF134E4A),
        darkOnPrimaryContainer = Color(0xFFCCFBF1),
    )

    val Navy = AccentPalette(
        lightPrimary = Color(0xFF1D4ED8),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFDBEAFE),
        lightOnPrimaryContainer = Color(0xFF172554),
        darkPrimary = Color(0xFF93C5FD),
        darkOnPrimary = Color(0xFF172554),
        darkPrimaryContainer = Color(0xFF1E3A8A),
        darkOnPrimaryContainer = Color(0xFFDBEAFE),
    )

    val Forest = AccentPalette(
        lightPrimary = Color(0xFF059669),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFD1FAE5),
        lightOnPrimaryContainer = Color(0xFF064E3B),
        darkPrimary = Color(0xFF34D399),
        darkOnPrimary = Color(0xFF064E3B),
        darkPrimaryContainer = Color(0xFF065F46),
        darkOnPrimaryContainer = Color(0xFFD1FAE5),
    )

    val Rose = AccentPalette(
        lightPrimary = Color(0xFFE11D48),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFFFE4E6),
        lightOnPrimaryContainer = Color(0xFF881337),
        darkPrimary = Color(0xFFFB7185),
        darkOnPrimary = Color(0xFF881337),
        darkPrimaryContainer = Color(0xFF881337),
        darkOnPrimaryContainer = Color(0xFFFFE4E6),
    )

    val Purple = AccentPalette(
        lightPrimary = Color(0xFF7C3AED),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFEDE9FE),
        lightOnPrimaryContainer = Color(0xFF4C1D95),
        darkPrimary = Color(0xFFA78BFA),
        darkOnPrimary = Color(0xFF4C1D95),
        darkPrimaryContainer = Color(0xFF5B21B6),
        darkOnPrimaryContainer = Color(0xFFEDE9FE),
    )

    val Amber = AccentPalette(
        lightPrimary = Color(0xFFC2410C),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFFEF3C7),
        lightOnPrimaryContainer = Color(0xFF7C2D12),
        darkPrimary = Color(0xFFFBBF24),
        darkOnPrimary = Color(0xFF7C2D12),
        darkPrimaryContainer = Color(0xFF92400E),
        darkOnPrimaryContainer = Color(0xFFFEF3C7),
    )

    val Slate = AccentPalette(
        lightPrimary = Color(0xFF475569),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFE2E8F0),
        lightOnPrimaryContainer = Color(0xFF1E293B),
        darkPrimary = Color(0xFFCBD5E1),
        darkOnPrimary = Color(0xFF1E293B),
        darkPrimaryContainer = Color(0xFF334155),
        darkOnPrimaryContainer = Color(0xFFE2E8F0),
    )
}