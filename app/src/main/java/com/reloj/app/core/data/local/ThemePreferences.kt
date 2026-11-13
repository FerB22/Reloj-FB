package com.reloj.app.core.data.local

import android.content.Context
import com.reloj.app.core.designsystem.AccentPreset
import com.reloj.app.core.designsystem.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentPreset = AccentPreset.AMBER,
    val use24HourFormat: Boolean = true,
)

class ThemePreferences private constructor(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppearanceSettings> = _settings.asStateFlow()

    private fun load(): AppearanceSettings {
        val mode = prefs.getString(KEY_THEME_MODE, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
        val accent = prefs.getString(KEY_ACCENT, null)
            ?.let { runCatching { AccentPreset.valueOf(it) }.getOrNull() }
            ?: AccentPreset.AMBER
        val use24Hour = prefs.getBoolean(KEY_USE_24H, true)
        return AppearanceSettings(
            themeMode = mode,
            accent = accent,
            use24HourFormat = use24Hour,
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    fun setAccent(accent: AccentPreset) {
        prefs.edit().putString(KEY_ACCENT, accent.name).apply()
        _settings.value = _settings.value.copy(accent = accent)
    }

    fun setUse24HourFormat(use24Hour: Boolean) {
        prefs.edit().putBoolean(KEY_USE_24H, use24Hour).apply()
        _settings.value = _settings.value.copy(use24HourFormat = use24Hour)
    }

    companion object {
        private const val PREFS_NAME = "reloj_appearance"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_ACCENT = "accent_preset"
        private const val KEY_USE_24H = "use_24h_format"

        @Volatile private var instance: ThemePreferences? = null

        fun get(context: Context): ThemePreferences =
            instance ?: synchronized(this) {
                instance ?: ThemePreferences(context.applicationContext).also { instance = it }
            }
    }
}
