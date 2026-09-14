package com.mediara.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// Deterministic type scale: tight display/headline tracking, generous body
// leading, and clear hierarchy driven by weight + size rather than serifs.
// `buildTypography` wraps the scale so personalization (font family + text-size
// scale) stays a single source change — matching the UX skill's one-theme rule.
fun buildTypography(
    scale: Float = 1f,
    family: FontFamily = FontFamily.SansSerif
): Typography {
    fun s(v: TextUnit): TextUnit = v * scale
    return Typography(
        displayLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Light,
            fontSize = s(57.sp),
            lineHeight = s(60.sp),
            letterSpacing = (-0.5f).sp
        ),
        displayMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Light,
            fontSize = s(45.sp),
            lineHeight = s(50.sp),
            letterSpacing = (-0.4f).sp
        ),
        displaySmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Light,
            fontSize = s(36.sp),
            lineHeight = s(42.sp),
            letterSpacing = (-0.3f).sp
        ),
        headlineLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = s(30.sp),
            lineHeight = s(36.sp),
            letterSpacing = (-0.25f).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = s(26.sp),
            lineHeight = s(32.sp),
            letterSpacing = (-0.2f).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = s(22.sp),
            lineHeight = s(28.sp),
            letterSpacing = (-0.15f).sp
        ),
        titleLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = s(20.sp),
            lineHeight = s(26.sp),
            letterSpacing = (-0.1f).sp
        ),
        titleMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = s(17.sp),
            lineHeight = s(22.sp),
            letterSpacing = 0.05f.sp
        ),
        titleSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = s(15.sp),
            lineHeight = s(20.sp),
            letterSpacing = 0.05f.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = s(16.sp),
            lineHeight = s(25.sp),
            letterSpacing = 0.2f.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = s(14.sp),
            lineHeight = s(21.sp),
            letterSpacing = 0.15f.sp
        ),
        bodySmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = s(12.sp),
            lineHeight = s(17.sp),
            letterSpacing = 0.1f.sp
        ),
        labelLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = s(14.sp),
            lineHeight = s(18.sp),
            letterSpacing = 0.1f.sp
        ),
        labelMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = s(12.sp),
            lineHeight = s(16.sp),
            letterSpacing = 0.3f.sp
        ),
        labelSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = s(11.sp),
            lineHeight = s(15.sp),
            letterSpacing = 0.4f.sp
        )
    )
}