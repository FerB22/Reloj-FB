package com.reloj.app.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.reloj.app.MainActivity
import com.reloj.app.core.data.local.SoundPreferences

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(timerId: Long, triggerAtMillis: Long) {
        scheduleExactOrFallback(triggerAtMillis, timerPendingIntent(timerId))
    }

    fun cancel(timerId: Long) {
        alarmManager.cancel(timerPendingIntent(timerId))
    }

    fun scheduleClockAlarm(alarmId: Long, triggerAtMillis: Long) {
        val showIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pending = clockAlarmPendingIntent(alarmId, isSnooze = false)
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
        val showIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pending = clockAlarmPendingIntent(alarmId, isSnooze = true)
        runCatching {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent), pending)
        }.onFailure {
            scheduleExactOrFallback(triggerAtMillis, pending)
        }
    }

    fun cancelClockAlarm(alarmId: Long) {
        alarmManager.cancel(clockAlarmPendingIntent(alarmId, isSnooze = false))
        alarmManager.cancel(clockAlarmPendingIntent(alarmId, isSnooze = true))
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

    private fun timerPendingIntent(timerId: Long): PendingIntent {
        val intent = Intent(context, TimerReceiver::class.java)
            .setAction(TimerReceiver.ACTION_TIMER_FINISHED)
            .putExtra(TimerReceiver.EXTRA_TIMER_ID, timerId)
        return PendingIntent.getBroadcast(
            context,
            timerId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun clockAlarmPendingIntent(alarmId: Long, isSnooze: Boolean): PendingIntent {
        val offset = if (isSnooze) CLOCK_ALARM_SNOOZE_OFFSET else CLOCK_ALARM_REQUEST_OFFSET
        val intent = Intent(context, ClockAlarmReceiver::class.java)
            .setAction(ClockAlarmReceiver.ACTION_ALARM_TRIGGERED)
            .putExtra(ClockAlarmReceiver.EXTRA_ALARM_ID, alarmId)
            .putExtra(ClockAlarmReceiver.EXTRA_IS_SNOOZE, isSnooze)
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
