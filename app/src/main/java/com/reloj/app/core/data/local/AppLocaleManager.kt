package com.reloj.app.core.data.local

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
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
     * Persiste el idioma elegido. En Android 13+ lo sincroniza con LocaleManager;
     * gracias a configChanges="locale|layoutDirection" en MainActivity, Compose actualiza
     * toda la interfaz en el mismo fotograma sin recrear ni parpadear la ventana.
     */
    fun setLanguage(context: Context, language: AppLanguage) {
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
        }
    }

    fun wrapContext(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        return localizedContext(base)
    }

    fun localizedContext(
        base: Context,
        language: AppLanguage = getSelectedLanguage(base),
    ): Context {
        val locale = resolveLocale(language)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /**
     * Envuelve una Activity preservando startActivity() sin FLAG_ACTIVITY_NEW_TASK
     * mientras entrega Resources localizados al idioma activo en Compose.
     */
    fun wrapActivityResources(activity: Activity, language: AppLanguage): Context {
        val resContext = localizedContext(activity, language)
        return object : ContextWrapper(activity) {
            override fun getResources(): Resources = resContext.resources
        }
    }

    fun effectiveLocale(context: Context): Locale {
        val locales = context.resources.configuration.locales
        return if (!locales.isEmpty) locales.get(0) else Locale.getDefault()
    }

    private fun resolveLocale(language: AppLanguage): Locale {
        val tag = language.languageTag
        if (tag != null) return Locale.forLanguageTag(tag)
        val systemLocales = Resources.getSystem().configuration.locales
        return if (!systemLocales.isEmpty) systemLocales.get(0) else Locale.getDefault()
    }
}
