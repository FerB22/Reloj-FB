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
import com.reloj.app.core.designsystem.ClockWidgetStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

object ClockWidget {

    private data class WidgetStyleSpec(
        val layoutRes: Int,
        val hasBackground: Boolean = true,
        val useAccentBackground: Boolean = false,
        val hasBorder: Boolean = false,
        val hasAccentBar: Boolean = false,
        val hasBottomBand: Boolean = false,
        val hasSplitTiles: Boolean = false,
        val isSplitTime: Boolean = false,
        val hasAlarmPillBg: Boolean = false,
    )

    private fun specFor(style: ClockWidgetStyle): WidgetStyleSpec = when (style) {
        ClockWidgetStyle.CLASSIC -> WidgetStyleSpec(
            layoutRes = R.layout.widget_clock,
            hasAlarmPillBg = true,
        )
        ClockWidgetStyle.MINIMAL_CLEAR -> WidgetStyleSpec(
            layoutRes = R.layout.widget_clock_minimal,
            hasBackground = false,
        )
        ClockWidgetStyle.COMPACT_ROW -> WidgetStyleSpec(
            layoutRes = R.layout.widget_clock_compact_row,
            hasAlarmPillBg = true,
        )
        ClockWidgetStyle.STACKED_DUO -> WidgetStyleSpec(
            layoutRes = R.layout.widget_clock_stacked_duo,
            isSplitTime = true,
            hasAlarmPillBg = true,
        )
        ClockWidgetStyle.SPLIT_TILES -> WidgetStyleSpec(
            layoutRes = R.layout.widget_clock_split_tiles,
            hasSplitTiles = true,
            isSplitTime = true,
        )
        ClockWidgetStyle.ACCENT_HERO -> WidgetStyleSpec(
            layoutRes = R.layout.widget_clock_accent_hero,
            useAccentBackground = true,
        )
        ClockWidgetStyle.TECH_MONO -> WidgetStyleSpec(
            layoutRes = R.layout.widget_clock_tech_mono,
            hasBorder = true,
            hasAlarmPillBg = true,
        )
        ClockWidgetStyle.CAPSULE_SPLIT -> WidgetStyleSpec(
            layoutRes = R.layout.widget_clock_capsule_split,
            hasBottomBand = true,
        )
        ClockWidgetStyle.BOLD_EDGE -> WidgetStyleSpec(
            layoutRes = R.layout.widget_clock_bold_edge,
            hasAccentBar = true,
        )
        ClockWidgetStyle.EDITORIAL_SERIF -> WidgetStyleSpec(
            layoutRes = R.layout.widget_clock_editorial_serif,
            hasAccentBar = true,
        )
    }

    suspend fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, ClockWidgetReceiver::class.java))
        if (ids.isEmpty()) return

        val scheme = TimerWidget.resolveColorScheme(context)
        val appearance = ThemePreferences.get(context).settings.value
        val alarms = AlarmRepository.get(context).getAll()
        val nowMillis = System.currentTimeMillis()
        val nextAlarm = findNextEnabledAlarm(alarms, nowMillis)

        val views = buildViews(
            context = context,
            scheme = scheme,
            style = appearance.clockWidgetStyle,
            use24Hour = appearance.use24HourFormat,
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
        style: ClockWidgetStyle,
        use24Hour: Boolean,
        nextAlarm: NextAlarmSummary?,
    ): RemoteViews {
        val spec = specFor(style)
        val views = RemoteViews(context.packageName, spec.layoutRes)

        val primaryTextColor = if (spec.useAccentBackground) scheme.onAccent.toArgb() else scheme.textPrimary.toArgb()
        val secondaryTextColor = if (spec.useAccentBackground) scheme.onAccent.toArgb() else scheme.textSecondary.toArgb()
        val accentColor = if (spec.useAccentBackground) scheme.onAccent.toArgb() else scheme.accent.toArgb()

        if (spec.hasBackground) {
            val bgColor = if (spec.useAccentBackground) scheme.accent.toArgb() else scheme.background.toArgb()
            views.setInt(R.id.clock_widget_bg, "setColorFilter", bgColor)
        }
        if (spec.hasBorder) {
            views.setInt(R.id.clock_widget_border, "setColorFilter", scheme.accent.toArgb())
        }
        if (spec.hasAccentBar) {
            views.setInt(R.id.clock_accent_bar, "setColorFilter", scheme.accent.toArgb())
        }
        if (spec.hasBottomBand) {
            views.setInt(R.id.clock_bottom_band, "setColorFilter", scheme.surface.toArgb())
        }
        if (spec.hasSplitTiles) {
            val tileColor = scheme.surfaceElevated.toArgb()
            views.setInt(R.id.clock_tile_hours_bg, "setColorFilter", tileColor)
            views.setInt(R.id.clock_tile_minutes_bg, "setColorFilter", tileColor)
        }

        if (spec.isSplitTime) {
            val hourPattern = if (use24Hour) "HH" else "hh"
            views.setCharSequence(R.id.clock_time_hours, "setFormat12Hour", hourPattern)
            views.setCharSequence(R.id.clock_time_hours, "setFormat24Hour", hourPattern)
            views.setTextColor(R.id.clock_time_hours, primaryTextColor)
            views.setTextColor(R.id.clock_time_minutes, accentColor)
        } else {
            val timePattern = if (use24Hour) "HH:mm" else "h:mm"
            views.setCharSequence(R.id.clock_time, "setFormat12Hour", timePattern)
            views.setCharSequence(R.id.clock_time, "setFormat24Hour", timePattern)
            views.setTextColor(R.id.clock_time, primaryTextColor)
        }

        if (use24Hour) {
            views.setViewVisibility(R.id.clock_period, View.GONE)
        } else {
            views.setViewVisibility(R.id.clock_period, View.VISIBLE)
            views.setCharSequence(R.id.clock_period, "setFormat12Hour", "a")
            views.setCharSequence(R.id.clock_period, "setFormat24Hour", "a")
            views.setTextColor(R.id.clock_period, accentColor)
        }

        views.setTextColor(R.id.clock_date, secondaryTextColor)
        if (spec.hasAlarmPillBg) {
            views.setInt(R.id.clock_alarm_pill_bg, "setColorFilter", scheme.surface.toArgb())
        }

        if (nextAlarm != null) {
            val dayShort = dayShortLabel(nextAlarm.triggerMillis)
            val alarmTime = AlarmRepeatUtils.formatAlarmTime(
                hour = nextAlarm.alarm.hour,
                minute = nextAlarm.alarm.minute,
                use24Hour = use24Hour,
            )
            views.setInt(R.id.clock_alarm_icon, "setColorFilter", accentColor)
            views.setTextViewText(R.id.clock_alarm_text, "$dayShort · $alarmTime")
            views.setTextColor(R.id.clock_alarm_text, accentColor)
        } else {
            views.setInt(R.id.clock_alarm_icon, "setColorFilter", secondaryTextColor)
            views.setTextViewText(R.id.clock_alarm_text, "Sin alarmas")
            views.setTextColor(R.id.clock_alarm_text, secondaryTextColor)
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
