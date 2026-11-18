package com.reloj.app.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.reloj.app.R
import com.reloj.app.core.data.local.AlarmRepeatUtils
import com.reloj.app.core.data.local.ThemePreferences
import com.reloj.app.core.data.repository.AlarmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ClockAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM_TRIGGERED) return
        val id = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        if (id < 0) return
        val isSnooze = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false)
        val hasPayload = intent.getBooleanExtra(EXTRA_HAS_PAYLOAD, false)

        // Mantener la CPU despierta desde onReceive() para evitar que Doze suspenda el proceso
        // antes de que AlarmAlertService tome el control y Room complete la reprogramación.
        val wakeLock = runCatching {
            context.getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Reloj:AlarmReceiverWakeLock")
                ?.apply {
                    setReferenceCounted(false)
                    acquire(15_000L)
                }
        }.getOrNull()

        var startedSynchronously = false
        if (hasPayload) {
            val hour = intent.getIntExtra(EXTRA_HOUR, -1)
            val minute = intent.getIntExtra(EXTRA_MINUTE, -1)
            val label = intent.getStringExtra(EXTRA_LABEL)?.ifBlank { null }
            val use24Hour = ThemePreferences.get(context).settings.value.use24HourFormat
            val title = if (hour in 0..23 && minute in 0..59) {
                AlarmRepeatUtils.formatAlarmTime(hour, minute, use24Hour)
            } else {
                context.getString(R.string.alarm_ringing)
            }
            val subtitle = listOfNotNull(
                if (isSnooze) "Pospuesta" else null,
                label,
            ).joinToString(" · ")

            AlarmAlertService.startAlert(
                context = context,
                isAlarm = true,
                itemId = id,
                title = title,
                subtitle = subtitle,
                soundPreset = intent.getStringExtra(EXTRA_SOUND_PRESET),
                customPath = intent.getStringExtra(EXTRA_CUSTOM_PATH),
                customName = intent.getStringExtra(EXTRA_CUSTOM_NAME),
            )
            startedSynchronously = true
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AlarmRepository.get(context)
                val alarm = repo.getById(id)

                if (!startedSynchronously) {
                    val use24Hour = ThemePreferences.get(context).settings.value.use24HourFormat
                    val label = alarm?.label?.ifBlank { null }
                    val timeLabel = alarm?.let { AlarmRepeatUtils.formatAlarmTime(it.hour, it.minute, use24Hour) }
                    val title = timeLabel ?: context.getString(R.string.alarm_ringing)
                    val subtitle = listOfNotNull(
                        if (isSnooze) "Pospuesta" else null,
                        label,
                    ).joinToString(" · ")

                    AlarmAlertService.startAlert(
                        context = context,
                        isAlarm = true,
                        itemId = id,
                        title = title,
                        subtitle = subtitle,
                        soundPreset = alarm?.soundPreset,
                        customPath = alarm?.customSoundPath,
                        customName = alarm?.customSoundName,
                    )
                }

                if (!isSnooze && alarm != null) {
                    if (alarm.repeatDaysMask != 0) {
                        repo.rescheduleSingle(id)
                    } else {
                        repo.markTriggeredOneShot(id)
                    }
                }
            } finally {
                runCatching {
                    if (wakeLock?.isHeld == true) wakeLock.release()
                }
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ALARM_TRIGGERED = "com.reloj.app.action.ALARM_TRIGGERED"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_IS_SNOOZE = "is_snooze"
        const val EXTRA_HAS_PAYLOAD = "has_payload"
        const val EXTRA_HOUR = "alarm_hour"
        const val EXTRA_MINUTE = "alarm_minute"
        const val EXTRA_LABEL = "alarm_label"
        const val EXTRA_SOUND_PRESET = "alarm_sound_preset"
        const val EXTRA_CUSTOM_PATH = "alarm_custom_path"
        const val EXTRA_CUSTOM_NAME = "alarm_custom_name"
    }
}
