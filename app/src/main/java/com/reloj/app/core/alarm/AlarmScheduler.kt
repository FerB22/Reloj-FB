package com.reloj.app.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.reloj.app.MainActivity
import com.reloj.app.core.data.local.AlarmEntity
import com.reloj.app.core.data.local.SoundPreferences
import com.reloj.app.core.data.local.TimerEntity

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(timer: TimerEntity, triggerAtMillis: Long) {
        scheduleExactOrFallback(triggerAtMillis, timerPendingIntent(timer.id, timer))
    }

    fun schedule(timerId: Long, triggerAtMillis: Long) {
        scheduleExactOrFallback(triggerAtMillis, timerPendingIntent(timerId, timer = null))
    }

    fun cancel(timerId: Long) {
        alarmManager.cancel(timerPendingIntent(timerId, timer = null))
    }

    fun scheduleClockAlarm(alarm: AlarmEntity, triggerAtMillis: Long) {
        val showIntent = buildShowAlarmsPendingIntent()
        val pending = clockAlarmPendingIntent(alarm.id, isSnooze = false, alarm = alarm)
        runCatching {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent), pending)
        }.onFailure {
            scheduleExactOrFallback(triggerAtMillis, pending)
        }
    }

    fun scheduleClockAlarm(alarmId: Long, triggerAtMillis: Long) {
        val showIntent = buildShowAlarmsPendingIntent()
        val pending = clockAlarmPendingIntent(alarmId, isSnooze = false, alarm = null)
        runCatching {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent), pending)
        }.onFailure {
            scheduleExactOrFallback(triggerAtMillis, pending)
        }
    }

    fun scheduleSnoozeAlarm(
        alarmId: Long,
        delayMillis: Long = SoundPreferences.get(context).settings.value.snoozeMinutes * 60_000L,
    ) {
        val triggerAtMillis = System.currentTimeMillis() + delayMillis
        val showIntent = buildShowAlarmsPendingIntent()
        val pending = clockAlarmPendingIntent(alarmId, isSnooze = true, alarm = null)
        runCatching {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent), pending)
        }.onFailure {
            scheduleExactOrFallback(triggerAtMillis, pending)
        }
    }

    fun cancelClockAlarm(alarmId: Long) {
        alarmManager.cancel(clockAlarmPendingIntent(alarmId, isSnooze = false, alarm = null))
        alarmManager.cancel(clockAlarmPendingIntent(alarmId, isSnooze = true, alarm = null))
    }

    private fun buildShowAlarmsPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_ALARMS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun scheduleExactOrFallback(triggerAtMillis: Long, pending: PendingIntent) {
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    private fun timerPendingIntent(timerId: Long, timer: TimerEntity?): PendingIntent {
        val intent = Intent(context, TimerReceiver::class.java)
            .setAction(TimerReceiver.ACTION_TIMER_FINISHED)
            .putExtra(TimerReceiver.EXTRA_TIMER_ID, timerId)
            .apply {
                if (timer != null) {
                    putExtra(TimerReceiver.EXTRA_HAS_PAYLOAD, true)
                    putExtra(TimerReceiver.EXTRA_LABEL, timer.label)
                    putExtra(TimerReceiver.EXTRA_SOUND_PRESET, timer.soundPreset)
                    putExtra(TimerReceiver.EXTRA_CUSTOM_PATH, timer.customSoundPath)
                    putExtra(TimerReceiver.EXTRA_CUSTOM_NAME, timer.customSoundName)
                }
            }
        return PendingIntent.getBroadcast(
            context,
            timerId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun clockAlarmPendingIntent(
        alarmId: Long,
        isSnooze: Boolean,
        alarm: AlarmEntity?,
    ): PendingIntent {
        val offset = if (isSnooze) CLOCK_ALARM_SNOOZE_OFFSET else CLOCK_ALARM_REQUEST_OFFSET
        val intent = Intent(context, ClockAlarmReceiver::class.java)
            .setAction(ClockAlarmReceiver.ACTION_ALARM_TRIGGERED)
            .putExtra(ClockAlarmReceiver.EXTRA_ALARM_ID, alarmId)
            .putExtra(ClockAlarmReceiver.EXTRA_IS_SNOOZE, isSnooze)
            .apply {
                if (alarm != null) {
                    putExtra(ClockAlarmReceiver.EXTRA_HAS_PAYLOAD, true)
                    putExtra(ClockAlarmReceiver.EXTRA_HOUR, alarm.hour)
                    putExtra(ClockAlarmReceiver.EXTRA_MINUTE, alarm.minute)
                    putExtra(ClockAlarmReceiver.EXTRA_LABEL, alarm.label)
                    putExtra(ClockAlarmReceiver.EXTRA_SOUND_PRESET, alarm.soundPreset)
                    putExtra(ClockAlarmReceiver.EXTRA_CUSTOM_PATH, alarm.customSoundPath)
                    putExtra(ClockAlarmReceiver.EXTRA_CUSTOM_NAME, alarm.customSoundName)
                }
            }
        return PendingIntent.getBroadcast(
            context,
            offset + alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val CLOCK_ALARM_REQUEST_OFFSET = 100_000
        private const val CLOCK_ALARM_SNOOZE_OFFSET = 200_000
    }
}
