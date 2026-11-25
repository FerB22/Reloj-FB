package com.reloj.app.core.data.local

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import com.reloj.app.R
import java.util.Locale

enum class AppLanguage(
    val languageTag: String?,
    @param:StringRes val labelRes: Int,
) {
    SYSTEM(null, R.string.language_system),
    SPANISH("es", R.string.language_spanish),
    ENGLISH("en", R.string.language_english),
}

object AppLocaleManager {

    private const val PREFS_NAME = "reloj_locale"
    private const val KEY_LANGUAGE = "app_language"

    fun getSelectedLanguage(context: Context): AppLanguage {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (locales != null && !locales.isEmpty) {
                val tag = locales.get(0)?.language
                return when (tag) {
                    "es" -> AppLanguage.SPANISH
                    "en" -> AppLanguage.ENGLISH
                    else -> AppLanguage.SYSTEM
                }
            }
            return AppLanguage.SYSTEM
        }
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
        return stored?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: AppLanguage.SYSTEM
    }

    /**
     * Aplica el idioma seleccionado. En Android 13+ usa LocaleManager nativo (que recrea
     * las actividades automáticamente); en versiones anteriores persiste la preferencia
     * y devuelve true para que la Activity pueda recrearse.
     */
    fun setLanguage(context: Context, language: AppLanguage): Boolean {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.name)
            .apply()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val list = if (language.languageTag != null) {
                LocaleList.forLanguageTags(language.languageTag)
            } else {
                LocaleList.getEmptyLocaleList()
            }
            context.getSystemService(LocaleManager::class.java)?.applicationLocales = list
            return false
        }
        return true
    }

    fun wrapContext(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        return localizedContext(base)
    }

    fun localizedContext(base: Context): Context {
        val language = getSelectedLanguage(base)
        val tag = language.languageTag ?: return base
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    fun effectiveLocale(context: Context): Locale {
        val locales = context.resources.configuration.locales
        return if (!locales.isEmpty) locales.get(0) else Locale.getDefault()
    }
}
