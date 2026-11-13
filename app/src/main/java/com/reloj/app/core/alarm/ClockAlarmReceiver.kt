package com.reloj.app.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AlarmRepository.get(context)
                val alarm = repo.getById(id)
                if (!isSnooze && alarm != null) {
                    if (alarm.repeatDaysMask != 0) {
                        repo.rescheduleSingle(id)
                    } else {
                        repo.setEnabled(id, false)
                    }
                }
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
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ALARM_TRIGGERED = "com.reloj.app.action.ALARM_TRIGGERED"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_IS_SNOOZE = "is_snooze"
    }
}
