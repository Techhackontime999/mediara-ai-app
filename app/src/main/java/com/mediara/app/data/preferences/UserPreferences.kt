package com.mediara.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Accent/seed colors the user can pick for personalization. */
enum class AccentOption(val id: String, val label: String) {
    INDIGO("indigo", "Indigo"),
    TEAL("teal", "Teal"),
    NAVY("navy", "Ocean Blue"),
    FOREST("forest", "Forest Green"),
    ROSE("rose", "Rose"),
    PURPLE("purple", "Violet"),
    AMBER("amber", "Amber"),
    SLATE("slate", "Slate");

    companion object {
        fun fromId(id: String?): AccentOption = entries.firstOrNull { it.id == id } ?: INDIGO
    }
}

enum class FontStyleOption(val id: String, val label: String) {
    DEFAULT("default", "Default"),
    SERIF("serif", "Serif"),
    MONOSPACE("monospace", "Monospace");

    companion object {
        fun fromId(id: String?): FontStyleOption = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

enum class DarkModeOption(val id: String, val label: String) {
    SYSTEM("system", "Follow System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromId(id: String?): DarkModeOption = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

enum class TextSizeOption(val id: String, val label: String, val scale: Float) {
    SMALL("small", "Compact", 0.9f),
    DEFAULT("default", "Default", 1f),
    LARGE("large", "Large", 1.18f);

    companion object {
        fun fromId(id: String?): TextSizeOption = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Supported app languages (Indian languages first, English default). */
enum class LanguageOption(val code: String, val nativeName: String, val englishName: String) {
    ENGLISH("en", "English", "English"),
    HINDI("hi", "हिन्दी", "Hindi"),
    BENGALI("bn", "বাংলা", "Bengali"),
    TAMIL("ta", "தமிழ்", "Tamil"),
    TELUGU("te", "తెలుగు", "Telugu"),
    MARATHI("mr", "मराठी", "Marathi"),
    GUJARATI("gu", "ગુજરાતી", "Gujarati"),
    PUNJABI("pa", "ਪੰਜਾਬੀ", "Punjabi"),
    KANNADA("kn", "ಕನ್ನಡ", "Kannada"),
    MALAYALAM("ml", "മലയാളം", "Malayalam"),
    ODIA("or", "ଓଡ଼ିଆ", "Odia"),
    URDU("ur", "اردو", "Urdu");

    companion object {
        fun fromCode(code: String?): LanguageOption = entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

data class AppThemeSettings(
    val accent: AccentOption = AccentOption.INDIGO,
    val fontStyle: FontStyleOption = FontStyleOption.DEFAULT,
    val darkMode: DarkModeOption = DarkModeOption.SYSTEM,
    val textSize: TextSizeOption = TextSizeOption.DEFAULT,
    val languageCode: String = LanguageOption.ENGLISH.code,
)

/**
 * Persists the user's personalization choices (accent color, font, dark mode,
 * text size, language) in plain SharedPreferences and exposes them as a
 * [StateFlow] so the theme and settings screen react live.
 */
class UserPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppThemeSettings> = _settings.asStateFlow()

    fun update(transform: (AppThemeSettings) -> AppThemeSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        prefs.edit()
            .putString(KEY_ACCENT, updated.accent.id)
            .putString(KEY_FONT, updated.fontStyle.id)
            .putString(KEY_DARK_MODE, updated.darkMode.id)
            .putString(KEY_TEXT_SIZE, updated.textSize.id)
            .putString(KEY_LANGUAGE, updated.languageCode)
            .apply()
    }

    private fun load(): AppThemeSettings = AppThemeSettings(
        accent = AccentOption.fromId(prefs.getString(KEY_ACCENT, null)),
        fontStyle = FontStyleOption.fromId(prefs.getString(KEY_FONT, null)),
        darkMode = DarkModeOption.fromId(prefs.getString(KEY_DARK_MODE, null)),
        textSize = TextSizeOption.fromId(prefs.getString(KEY_TEXT_SIZE, null)),
        languageCode = prefs.getString(KEY_LANGUAGE, LanguageOption.ENGLISH.code)
            ?.let(LanguageOption::fromCode)?.code ?: LanguageOption.ENGLISH.code,
    )

    private companion object {
        const val PREFS_NAME = "mediara_preferences"
        const val KEY_ACCENT = "accent"
        const val KEY_FONT = "font_family"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_TEXT_SIZE = "text_size"
        const val KEY_LANGUAGE = "language"
    }
}