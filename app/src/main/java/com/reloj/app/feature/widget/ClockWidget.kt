package com.reloj.app.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.reloj.app.MainActivity
import com.reloj.app.R
import com.reloj.app.core.data.local.AlarmEntity
import com.reloj.app.core.data.local.AlarmRepeatUtils
import com.reloj.app.core.data.local.ThemePreferences
import com.reloj.app.core.data.repository.AlarmRepository
import com.reloj.app.core.designsystem.AppColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

object ClockWidget {

    suspend fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, ClockWidgetReceiver::class.java))
        if (ids.isEmpty()) return

        val scheme = TimerWidget.resolveColorScheme(context)
        val use24Hour = ThemePreferences.get(context).settings.value.use24HourFormat
        val alarms = AlarmRepository.get(context).getAll()
        val nowMillis = System.currentTimeMillis()
        val nextAlarm = findNextEnabledAlarm(alarms, nowMillis)

        val views = buildViews(
            context = context,
            scheme = scheme,
            use24Hour = use24Hour,
            nextAlarm = nextAlarm,
        )
        ids.forEach { appWidgetId ->
            manager.updateAppWidget(appWidgetId, views)
        }
    }

    private data class NextAlarmSummary(
        val alarm: AlarmEntity,
        val triggerMillis: Long,
    )

    private fun findNextEnabledAlarm(
        alarms: List<AlarmEntity>,
        nowMillis: Long,
    ): NextAlarmSummary? = alarms
        .asSequence()
        .filter { it.enabled }
        .map { alarm ->
            NextAlarmSummary(
                alarm = alarm,
                triggerMillis = AlarmRepeatUtils.nextTriggerMillis(
                    hour = alarm.hour,
                    minute = alarm.minute,
                    repeatDaysMask = alarm.repeatDaysMask,
                    nowMillis = nowMillis,
                ),
            )
        }
        .minByOrNull { it.triggerMillis }

    private fun buildViews(
        context: Context,
        scheme: AppColorScheme,
        use24Hour: Boolean,
        nextAlarm: NextAlarmSummary?,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_clock)
        views.setInt(R.id.clock_widget_bg, "setColorFilter", scheme.background.toArgb())

        val timePattern = if (use24Hour) "HH:mm" else "h:mm"
        views.setCharSequence(R.id.clock_time, "setFormat12Hour", timePattern)
        views.setCharSequence(R.id.clock_time, "setFormat24Hour", timePattern)
        views.setTextColor(R.id.clock_time, scheme.textPrimary.toArgb())

        if (use24Hour) {
            views.setViewVisibility(R.id.clock_period, View.GONE)
        } else {
            views.setViewVisibility(R.id.clock_period, View.VISIBLE)
            views.setCharSequence(R.id.clock_period, "setFormat12Hour", "a")
            views.setCharSequence(R.id.clock_period, "setFormat24Hour", "a")
            views.setTextColor(R.id.clock_period, scheme.accent.toArgb())
        }

        views.setTextColor(R.id.clock_date, scheme.textSecondary.toArgb())
        views.setInt(R.id.clock_alarm_pill_bg, "setColorFilter", scheme.surface.toArgb())

        if (nextAlarm != null) {
            val dayShort = dayShortLabel(nextAlarm.triggerMillis)
            val alarmTime = AlarmRepeatUtils.formatAlarmTime(
                hour = nextAlarm.alarm.hour,
                minute = nextAlarm.alarm.minute,
                use24Hour = use24Hour,
            )
            views.setInt(R.id.clock_alarm_icon, "setColorFilter", scheme.accent.toArgb())
            views.setTextViewText(R.id.clock_alarm_text, "$dayShort · $alarmTime")
            views.setTextColor(R.id.clock_alarm_text, scheme.accent.toArgb())
        } else {
            views.setInt(R.id.clock_alarm_icon, "setColorFilter", scheme.textSecondary.toArgb())
            views.setTextViewText(R.id.clock_alarm_text, "Sin alarmas")
            views.setTextColor(R.id.clock_alarm_text, scheme.textSecondary.toArgb())
        }

        views.setOnClickPendingIntent(R.id.clock_widget_root, openAlarmsPendingIntent(context))
        return views
    }

    private fun dayShortLabel(triggerMillis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = triggerMillis }
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Lun"
            Calendar.TUESDAY -> "Mar"
            Calendar.WEDNESDAY -> "Mié"
            Calendar.THURSDAY -> "Jue"
            Calendar.FRIDAY -> "Vie"
            Calendar.SATURDAY -> "Sáb"
            Calendar.SUNDAY -> "Dom"
            else -> ""
        }
    }

    private fun openAlarmsPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_ALARMS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            2001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class ClockWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        refreshAsync(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            -> refreshAsync(context)
            else -> super.onReceive(context, intent)
        }
    }

    private fun refreshAsync(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ClockWidget.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
