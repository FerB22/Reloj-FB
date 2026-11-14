package com.reloj.app.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.compose.ui.graphics.toArgb
import com.reloj.app.MainActivity
import com.reloj.app.R
import com.reloj.app.core.data.local.ThemePreferences
import com.reloj.app.core.data.local.TimerEntity
import com.reloj.app.core.data.local.TimerState
import com.reloj.app.core.data.repository.TimerRepository
import com.reloj.app.core.designsystem.AppColorScheme
import com.reloj.app.core.designsystem.buildAppColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Objects

object TimerWidget {

    suspend fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TimerWidgetReceiver::class.java))
        if (ids.isEmpty()) return

        val timers = TimerRepository.get(context).getAll()
        val scheme = resolveColorScheme(context)
        val nowMillis = System.currentTimeMillis()
        val elapsedNow = SystemClock.elapsedRealtime()

        for (appWidgetId in ids) {
            val focusedId = WidgetFocusStore.getFocusedTimerId(context, appWidgetId)
            val focusedTimer = focusedId?.let { target -> timers.firstOrNull { it.id == target } }
            val views = if (focusedTimer != null) {
                buildFocusedViews(context, appWidgetId, focusedTimer, scheme, nowMillis, elapsedNow)
            } else {
                buildListViews(context, appWidgetId, timers, scheme)
            }
            manager.updateAppWidget(appWidgetId, views)
            if (focusedTimer == null && timers.isNotEmpty()) {
                manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list_view)
            }
        }
    }

    internal fun resolveColorScheme(context: Context): AppColorScheme {
        val appearance = ThemePreferences.get(context).settings.value
        val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val systemDark = nightMask == Configuration.UI_MODE_NIGHT_YES
        return buildAppColorScheme(
            themeMode = appearance.themeMode,
            systemDark = systemDark,
            accent = appearance.accent,
        )
    }

    private fun buildListViews(
        context: Context,
        appWidgetId: Int,
        timers: List<TimerEntity>,
        scheme: AppColorScheme,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_timer_list)
        views.setInt(R.id.widget_bg, "setColorFilter", scheme.background.toArgb())
        views.setTextColor(R.id.widget_title, scheme.textPrimary.toArgb())
        views.setInt(R.id.btn_open_app_bg, "setColorFilter", scheme.surface.toArgb())
        views.setTextColor(R.id.btn_open_app_text, scheme.textSecondary.toArgb())
        views.setOnClickPendingIntent(R.id.btn_open_app, openAppPendingIntent(context))

        if (timers.isEmpty()) {
            views.setViewVisibility(R.id.empty_container, View.VISIBLE)
            views.setViewVisibility(R.id.widget_list_view, View.GONE)
            views.setInt(R.id.empty_bg, "setColorFilter", scheme.surface.toArgb())
            views.setTextColor(R.id.empty_text, scheme.textSecondary.toArgb())
            views.setOnClickPendingIntent(R.id.empty_container, openAppPendingIntent(context))
        } else {
            views.setViewVisibility(R.id.empty_container, View.GONE)
            views.setViewVisibility(R.id.widget_list_view, View.VISIBLE)

            val serviceIntent = Intent(context, TimerWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_list_view, serviceIntent)
            views.setPendingIntentTemplate(
                R.id.widget_list_view,
                widgetListTemplatePendingIntent(context, appWidgetId),
            )
        }
        return views
    }

    private fun widgetListTemplatePendingIntent(
        context: Context,
        appWidgetId: Int,
    ): PendingIntent {
        val intent = Intent(context, TimerWidgetReceiver::class.java).apply {
            action = TimerWidgetReceiver.ACTION_SELECT_START
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, appWidgetId, intent, flags)
    }

    private fun buildFocusedViews(
        context: Context,
        appWidgetId: Int,
        timer: TimerEntity,
        scheme: AppColorScheme,
        nowMillis: Long,
        elapsedNow: Long,
    ): RemoteViews {
        val remaining = remainingForDisplay(timer, nowMillis)
        val isRunning = timer.state == TimerState.RUNNING && remaining > 0L
        val isFinished = timer.state == TimerState.FINISHED ||
            (timer.state == TimerState.RUNNING && remaining == 0L)

        val statusText = when {
            isFinished -> "Finalizado"
            isRunning -> "En curso"
            timer.state == TimerState.PAUSED -> "Pausado"
            else -> "Listo"
        }

        val views = RemoteViews(context.packageName, R.layout.widget_timer_focused)
        views.setInt(R.id.widget_bg, "setColorFilter", scheme.background.toArgb())

        views.setInt(R.id.btn_back_bg, "setColorFilter", scheme.surface.toArgb())
        views.setTextColor(R.id.btn_back_text, scheme.textSecondary.toArgb())
        views.setOnClickPendingIntent(
            R.id.btn_back,
            widgetActionPendingIntent(
                context = context,
                action = TimerWidgetReceiver.ACTION_BACK_TO_LIST,
                appWidgetId = appWidgetId,
                timerId = timer.id,
            ),
        )

        views.setTextViewText(R.id.focused_label, timer.label)
        views.setTextColor(R.id.focused_label, scheme.textPrimary.toArgb())

        views.setTextViewText(R.id.focused_status, statusText)
        views.setTextColor(
            R.id.focused_status,
            if (isRunning || isFinished) scheme.accent.toArgb() else scheme.textSecondary.toArgb(),
        )

        if (isRunning) {
            val base = elapsedNow + remaining + 900L
            views.setViewVisibility(R.id.focused_chronometer, View.VISIBLE)
            views.setViewVisibility(R.id.focused_static_time, View.GONE)
            views.setTextColor(R.id.focused_chronometer, scheme.textPrimary.toArgb())
            views.setChronometerCountDown(R.id.focused_chronometer, true)
            views.setChronometer(R.id.focused_chronometer, base, null, true)
        } else {
            views.setChronometer(R.id.focused_chronometer, elapsedNow, null, false)
            views.setViewVisibility(R.id.focused_chronometer, View.GONE)
            views.setViewVisibility(R.id.focused_static_time, View.VISIBLE)
            views.setTextViewText(R.id.focused_static_time, formatMillis(remaining))
            views.setTextColor(
                R.id.focused_static_time,
                if (isFinished) scheme.accent.toArgb() else scheme.textPrimary.toArgb(),
            )
        }

        views.setInt(R.id.btn_reset_bg, "setColorFilter", scheme.surfaceElevated.toArgb())
        views.setTextColor(R.id.btn_reset_text, scheme.textPrimary.toArgb())
        views.setOnClickPendingIntent(
            R.id.btn_reset,
            widgetActionPendingIntent(
                context = context,
                action = TimerWidgetReceiver.ACTION_RESET,
                appWidgetId = appWidgetId,
                timerId = timer.id,
            ),
        )

        if (isFinished) {
            views.setViewVisibility(R.id.btn_toggle, View.GONE)
        } else {
            views.setViewVisibility(R.id.btn_toggle, View.VISIBLE)
            views.setInt(R.id.btn_toggle_bg, "setColorFilter", scheme.accent.toArgb())
            views.setTextViewText(R.id.btn_toggle_text, if (isRunning) "Pausar" else "Reanudar")
            views.setTextColor(R.id.btn_toggle_text, scheme.onAccent.toArgb())
            views.setOnClickPendingIntent(
                R.id.btn_toggle,
                widgetActionPendingIntent(
                    context = context,
                    action = if (isRunning) {
                        TimerWidgetReceiver.ACTION_PAUSE
                    } else {
                        TimerWidgetReceiver.ACTION_START
                    },
                    appWidgetId = appWidgetId,
                    timerId = timer.id,
                ),
            )
        }

        return views
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun widgetActionPendingIntent(
        context: Context,
        action: String,
        appWidgetId: Int,
        timerId: Long,
    ): PendingIntent {
        val intent = Intent(context, TimerWidgetReceiver::class.java)
            .setAction(action)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .putExtra(TimerWidgetReceiver.EXTRA_TIMER_ID, timerId)
        val requestCode = Objects.hash(action, appWidgetId, timerId)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class TimerWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TimerRepository.get(context).syncNotifications()
                TimerWidget.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { WidgetFocusStore.clearFocus(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SELECT_START,
            ACTION_START,
            ACTION_PAUSE,
            ACTION_RESET,
            ACTION_ADD_MINUTE,
            ACTION_BACK_TO_LIST,
            ACTION_REPOST_ONGOING,
            -> {
                val pending = goAsync()
                val action = intent.action
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                )
                val timerId = intent.getLongExtra(EXTRA_TIMER_ID, -1L)

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repo = TimerRepository.get(context)
                        when (action) {
                            ACTION_SELECT_START -> {
                                val timer = repo.getById(timerId)
                                if (timer != null) {
                                    if (timer.state != TimerState.RUNNING) {
                                        repo.start(timerId)
                                    }
                                    if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                        WidgetFocusStore.setFocusedTimerId(context, appWidgetId, timerId)
                                    }
                                }
                            }
                            ACTION_START -> if (timerId >= 0L) repo.start(timerId)
                            ACTION_PAUSE -> if (timerId >= 0L) repo.pause(timerId)
                            ACTION_RESET -> if (timerId >= 0L) repo.reset(timerId)
                            ACTION_ADD_MINUTE -> if (timerId >= 0L) repo.addMinute(timerId)
                            ACTION_BACK_TO_LIST -> {
                                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                    WidgetFocusStore.clearFocus(context, appWidgetId)
                                }
                            }
                            ACTION_REPOST_ONGOING -> repo.syncNotifications()
                        }
                        TimerWidget.updateAll(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        const val ACTION_SELECT_START = "com.reloj.app.widget.SELECT_START"
        const val ACTION_START = "com.reloj.app.widget.START"
        const val ACTION_PAUSE = "com.reloj.app.widget.PAUSE"
        const val ACTION_RESET = "com.reloj.app.widget.RESET"
        const val ACTION_ADD_MINUTE = "com.reloj.app.widget.ADD_MINUTE"
        const val ACTION_BACK_TO_LIST = "com.reloj.app.widget.BACK_TO_LIST"
        const val ACTION_REPOST_ONGOING = "com.reloj.app.widget.REPOST_ONGOING"
        const val EXTRA_TIMER_ID = "timer_id"
    }
}

private fun remainingForDisplay(timer: TimerEntity, nowMillis: Long): Long = when (timer.state) {
    TimerState.RUNNING -> ((timer.endAtMillis ?: nowMillis) - nowMillis).coerceAtLeast(0L)
    TimerState.PAUSED -> (timer.remainingMillis ?: timer.durationMillis).coerceAtLeast(0L)
    TimerState.IDLE -> timer.durationMillis
    TimerState.FINISHED -> 0L
}

private fun formatMillis(millis: Long): String {
    val totalSeconds = ((millis + 999L) / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

class TimerWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        return TimerWidgetRemoteViewsFactory(applicationContext, appWidgetId)
    }
}

private class TimerWidgetRemoteViewsFactory(
    private val context: Context,
    private val appWidgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {

    private data class Snapshot(
        val timers: List<TimerEntity>,
        val scheme: AppColorScheme,
    )

    @Volatile
    private var snapshot: Snapshot = Snapshot(
        timers = emptyList(),
        scheme = TimerWidget.resolveColorScheme(context),
    )

    override fun onCreate() {
        onDataSetChanged()
    }

    override fun onDataSetChanged() {
        val identity = Binder.clearCallingIdentity()
        try {
            snapshot = runBlocking(Dispatchers.IO) {
                Snapshot(
                    timers = TimerRepository.get(context).getAll(),
                    scheme = TimerWidget.resolveColorScheme(context),
                )
            }
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    override fun onDestroy() = Unit

    override fun getCount(): Int = snapshot.timers.size

    override fun getViewAt(position: Int): RemoteViews {
        val current = snapshot
        val timer = current.timers.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.widget_timer_row)
        val scheme = current.scheme
        val nowMillis = System.currentTimeMillis()
        val elapsedNow = SystemClock.elapsedRealtime()
        val remaining = remainingForDisplay(timer, nowMillis)
        val isRunning = timer.state == TimerState.RUNNING && remaining > 0L
        val isFinished = timer.state == TimerState.FINISHED ||
            (timer.state == TimerState.RUNNING && remaining == 0L)

        val row = RemoteViews(context.packageName, R.layout.widget_timer_row)
        row.setInt(R.id.row_bg, "setColorFilter", scheme.surface.toArgb())
        row.setTextViewText(R.id.row_label, timer.label)
        row.setTextColor(R.id.row_label, scheme.textPrimary.toArgb())

        when {
            isFinished -> {
                row.setViewVisibility(R.id.row_status, View.VISIBLE)
                row.setTextViewText(R.id.row_status, "Finalizado")
                row.setTextColor(R.id.row_status, scheme.accent.toArgb())
            }
            isRunning -> {
                row.setViewVisibility(R.id.row_status, View.VISIBLE)
                row.setTextViewText(R.id.row_status, "En curso")
                row.setTextColor(R.id.row_status, scheme.accent.toArgb())
            }
            timer.state == TimerState.PAUSED -> {
                row.setViewVisibility(R.id.row_status, View.VISIBLE)
                row.setTextViewText(R.id.row_status, "Pausado")
                row.setTextColor(R.id.row_status, scheme.textSecondary.toArgb())
            }
            else -> {
                row.setViewVisibility(R.id.row_status, View.GONE)
            }
        }

        if (isRunning) {
            val base = elapsedNow + remaining + 900L
            row.setViewVisibility(R.id.row_chronometer, View.VISIBLE)
            row.setViewVisibility(R.id.row_time, View.GONE)
            row.setTextColor(R.id.row_chronometer, scheme.accent.toArgb())
            row.setChronometerCountDown(R.id.row_chronometer, true)
            row.setChronometer(R.id.row_chronometer, base, null, true)
        } else {
            row.setChronometer(R.id.row_chronometer, elapsedNow, null, false)
            row.setViewVisibility(R.id.row_chronometer, View.GONE)
            row.setViewVisibility(R.id.row_time, View.VISIBLE)
            row.setTextViewText(R.id.row_time, formatMillis(remaining))
            row.setTextColor(
                R.id.row_time,
                if (isFinished) scheme.accent.toArgb() else scheme.textSecondary.toArgb(),
            )
        }

        row.setInt(R.id.row_pill_bg, "setColorFilter", scheme.accent.toArgb())
        row.setTextViewText(R.id.row_pill_text, if (isRunning) "Ver" else "▶")
        row.setTextColor(R.id.row_pill_text, scheme.onAccent.toArgb())

        val fillInIntent = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(TimerWidgetReceiver.EXTRA_TIMER_ID, timer.id)
        }
        row.setOnClickFillInIntent(R.id.row_root, fillInIntent)

        return row
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        snapshot.timers.getOrNull(position)?.id ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
