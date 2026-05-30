package com.paisabrain.app

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * LanguageManager - Handles complete multi-language support.
 * 
 * AUTO-ADAPTS to user's device language by default.
 * Also allows manual in-app language switching.
 * 
 * Supported languages (22 Indian + 10 international):
 * Hindi, Tamil, Telugu, Kannada, Malayalam, Marathi, Bengali, Gujarati,
 * Punjabi, Odia, Assamese, Urdu, Nepali, Sanskrit, Konkani, Manipuri,
 * Kashmiri, Sindhi, Dogri, Maithili, Santali, Bodo,
 * English, Spanish, French, German, Portuguese, Arabic, Japanese, Korean, Chinese, Russian
 */
object LanguageManager {

    // All supported language codes
    val SUPPORTED_LANGUAGES = mapOf(
        "en" to "English",
        "hi" to "हिन्दी",
        "ta" to "தமிழ்",
        "te" to "తెలుగు",
        "kn" to "ಕನ್ನಡ",
        "ml" to "മലയാളം",
        "mr" to "मराठी",
        "bn" to "বাংলা",
        "gu" to "ગુજરાતી",
        "pa" to "ਪੰਜਾਬੀ",
        "or" to "ଓଡ଼ିଆ",
        "as" to "অসমীয়া",
        "ur" to "اردو",
        "ne" to "नेपाली",
        "sa" to "संस्कृतम्",
        "kok" to "कोंकणी",
        "mni" to "মৈতৈলোন্",
        "ks" to "کٲشُر",
        "sd" to "سنڌي",
        "doi" to "डोगरी",
        "mai" to "मैथिली",
        "sat" to "ᱥᱟᱱᱛᱟᱲᱤ",
        "brx" to "बर'",
        "es" to "Español",
        "fr" to "Français",
        "de" to "Deutsch",
        "pt" to "Português",
        "ar" to "العربية",
        "ja" to "日本語",
        "ko" to "한국어",
        "zh" to "中文",
        "ru" to "Русский"
    )

    private const val PREF_LANGUAGE = "selected_language"
    private const val PREF_NAME = "paisa_brain_language"
    private const val AUTO_DETECT = "auto"

    /**
     * Initialize language on app start.
     * If user hasn't set preference, uses system language.
     * If system language isn't supported, falls back to English.
     */
    fun initialize(context: Context) {
        val savedLang = getSavedLanguage(context)
        if (savedLang == AUTO_DETECT) {
            // Use system language — Android handles this automatically
            // via resource qualifiers (values-hi/, values-ta/, etc.)
            return
        }
        applyLanguage(context, savedLang)
    }

    /**
     * Change app language programmatically.
     * Works on ALL Android versions (API 26+).
     * On API 33+: Uses Per-App Language API (best experience).
     * On API 26-32: Uses AppCompatDelegate + Configuration override.
     */
    fun setLanguage(context: Context, languageCode: String) {
        saveLanguage(context, languageCode)

        if (languageCode == AUTO_DETECT) {
            // Reset to system default
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            }
            return
        }

        applyLanguage(context, languageCode)
    }

    private fun applyLanguage(context: Context, languageCode: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33): Per-App Language Preferences
            val localeList = LocaleListCompat.forLanguageTags(languageCode)
            AppCompatDelegate.setApplicationLocales(localeList)
        } else {
            // Android 8-12 (API 26-32): Manual locale override
            val locale = Locale(languageCode)
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocales(LocaleList(locale))
            }
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }
    }

    /**
     * Get localized context for use in Workers/Services (non-Activity contexts)
     */
    fun getLocalizedContext(context: Context): Context {
        val savedLang = getSavedLanguage(context)
        if (savedLang == AUTO_DETECT) return context

        val locale = Locale(savedLang)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun getSavedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_LANGUAGE, AUTO_DETECT) ?: AUTO_DETECT
    }

    private fun saveLanguage(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_LANGUAGE, languageCode).apply()
    }

    fun getCurrentLanguageName(context: Context): String {
        val code = getSavedLanguage(context)
        if (code == AUTO_DETECT) return "Auto (System)"
        return SUPPORTED_LANGUAGES[code] ?: "English"
    }

    /**
     * Check if current language is RTL (Arabic, Urdu, Kashmiri, Sindhi)
     */
    fun isRtl(context: Context): Boolean {
        val code = getSavedLanguage(context)
        return code in listOf("ar", "ur", "ks", "sd")
    }
}
