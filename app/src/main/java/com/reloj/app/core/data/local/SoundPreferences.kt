package com.reloj.app.core.data.local

import android.content.Context
import androidx.annotation.RawRes
import com.reloj.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val SILENT_SOUND_PRESET = "SILENT"

fun isSilentSoundPreset(soundPreset: String?): Boolean = soundPreset == SILENT_SOUND_PRESET

enum class AlarmSoundPreset(
    val label: String,
    val description: String,
    @param:RawRes val rawResId: Int,
) {
    SUNRISE(
        label = "Despertar intenso",
        description = "Pulsos firmes y brillantes para despertar",
        rawResId = R.raw.sound_alarm_sunrise,
    ),
    NORDIC(
        label = "Sirena rítmica",
        description = "Alternancia aguda de alta penetración",
        rawResId = R.raw.sound_alarm_nordic,
    ),
    CRYSTAL(
        label = "Timbre energético",
        description = "Repetición metálica rápida e inconfundible",
        rawResId = R.raw.sound_alarm_crystal,
    ),
}

enum class TimerSoundPreset(
    val label: String,
    val description: String,
    @param:RawRes val rawResId: Int,
) {
    CHIME(
        label = "Aviso claro",
        description = "Tres tonos ascendentes de alta presencia",
        rawResId = R.raw.sound_timer_chime,
    ),
    PULSE(
        label = "Bip rápido",
        description = "Secuencia aguda directa e intensa",
        rawResId = R.raw.sound_timer_pulse,
    ),
}

data class SoundSettings(
    val alarmSound: AlarmSoundPreset = AlarmSoundPreset.SUNRISE,
    val customAlarmPath: String? = null,
    val customAlarmName: String? = null,
    val timerSound: TimerSoundPreset = TimerSoundPreset.CHIME,
    val customTimerPath: String? = null,
    val customTimerName: String? = null,
    val vibrateEnabled: Boolean = true,
    val gradualVolume: Boolean = false,
    val snoozeMinutes: Int = 5,
    val volumeButtonsEnabled: Boolean = true,
)

class SoundPreferences private constructor(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<SoundSettings> = _settings.asStateFlow()

    private fun load(): SoundSettings {
        val alarmSound = prefs.getString(KEY_ALARM_SOUND, null)
            ?.let { runCatching { AlarmSoundPreset.valueOf(it) }.getOrNull() }
            ?: AlarmSoundPreset.SUNRISE
        val timerSound = prefs.getString(KEY_TIMER_SOUND, null)
            ?.let { runCatching { TimerSoundPreset.valueOf(it) }.getOrNull() }
            ?: TimerSoundPreset.CHIME
        return SoundSettings(
            alarmSound = alarmSound,
            customAlarmPath = prefs.getString(KEY_CUSTOM_ALARM_PATH, null),
            customAlarmName = prefs.getString(KEY_CUSTOM_ALARM_NAME, null),
            timerSound = timerSound,
            customTimerPath = prefs.getString(KEY_CUSTOM_TIMER_PATH, null),
            customTimerName = prefs.getString(KEY_CUSTOM_TIMER_NAME, null),
            vibrateEnabled = prefs.getBoolean(KEY_VIBRATE, true),
            gradualVolume = prefs.getBoolean(KEY_GRADUAL_VOLUME, false),
            snoozeMinutes = prefs.getInt(KEY_SNOOZE_MINUTES, 5).takeIf { it in listOf(5, 10, 15) } ?: 5,
            volumeButtonsEnabled = prefs.getBoolean(KEY_VOLUME_BUTTONS, true),
        )
    }

    fun setAlarmPreset(preset: AlarmSoundPreset) {
        prefs.edit()
            .putString(KEY_ALARM_SOUND, preset.name)
            .remove(KEY_CUSTOM_ALARM_PATH)
            .remove(KEY_CUSTOM_ALARM_NAME)
            .apply()
        _settings.value = _settings.value.copy(
            alarmSound = preset,
            customAlarmPath = null,
            customAlarmName = null,
        )
    }

    fun setCustomAlarmAudio(path: String, name: String) {
        prefs.edit()
            .putString(KEY_CUSTOM_ALARM_PATH, path)
            .putString(KEY_CUSTOM_ALARM_NAME, name)
            .apply()
        _settings.value = _settings.value.copy(
            customAlarmPath = path,
            customAlarmName = name,
        )
    }

    fun setTimerPreset(preset: TimerSoundPreset) {
        prefs.edit()
            .putString(KEY_TIMER_SOUND, preset.name)
            .remove(KEY_CUSTOM_TIMER_PATH)
            .remove(KEY_CUSTOM_TIMER_NAME)
            .apply()
        _settings.value = _settings.value.copy(
            timerSound = preset,
            customTimerPath = null,
            customTimerName = null,
        )
    }

    fun setCustomTimerAudio(path: String, name: String) {
        prefs.edit()
            .putString(KEY_CUSTOM_TIMER_PATH, path)
            .putString(KEY_CUSTOM_TIMER_NAME, name)
            .apply()
        _settings.value = _settings.value.copy(
            customTimerPath = path,
            customTimerName = name,
        )
    }

    fun setVibrateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATE, enabled).apply()
        _settings.value = _settings.value.copy(vibrateEnabled = enabled)
    }

    fun setGradualVolume(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GRADUAL_VOLUME, enabled).apply()
        _settings.value = _settings.value.copy(gradualVolume = enabled)
    }

    fun setSnoozeMinutes(minutes: Int) {
        val clamped = minutes.takeIf { it in listOf(5, 10, 15) } ?: 5
        prefs.edit().putInt(KEY_SNOOZE_MINUTES, clamped).apply()
        _settings.value = _settings.value.copy(snoozeMinutes = clamped)
    }

    fun setVolumeButtonsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOLUME_BUTTONS, enabled).apply()
        _settings.value = _settings.value.copy(volumeButtonsEnabled = enabled)
    }

    companion object {
        private const val PREFS_NAME = "reloj_sound_settings"
        private const val KEY_ALARM_SOUND = "alarm_sound"
        private const val KEY_CUSTOM_ALARM_PATH = "custom_alarm_path"
        private const val KEY_CUSTOM_ALARM_NAME = "custom_alarm_name"
        private const val KEY_TIMER_SOUND = "timer_sound"
        private const val KEY_CUSTOM_TIMER_PATH = "custom_timer_path"
        private const val KEY_CUSTOM_TIMER_NAME = "custom_timer_name"
        private const val KEY_VIBRATE = "vibrate_enabled"
        private const val KEY_GRADUAL_VOLUME = "gradual_volume"
        private const val KEY_SNOOZE_MINUTES = "snooze_minutes"
        private const val KEY_VOLUME_BUTTONS = "volume_buttons_enabled"

        @Volatile private var instance: SoundPreferences? = null

        fun get(context: Context): SoundPreferences =
            instance ?: synchronized(this) {
                instance ?: SoundPreferences(context.applicationContext).also { instance = it }
            }
    }
}
