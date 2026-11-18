package com.reloj.app.core.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.reloj.app.MainActivity
import com.reloj.app.R
import com.reloj.app.core.data.local.SoundPreferences
import com.reloj.app.core.data.local.TimerEntity
import com.reloj.app.core.data.local.TimerState
import com.reloj.app.feature.widget.TimerWidgetReceiver
import java.util.Objects

object NotificationHelper {

    const val ALERTS_CHANNEL_ID = "alerts_ringing_v2"
    const val ALERTS_ONGOING_CHANNEL_ID = "alerts_ongoing_quiet_v1"
    const val TIMERS_RUNNING_CHANNEL_ID = "timers_running_live_v1"
    private const val TAG_RUNNING_TIMER = "reloj_running_timer"

    fun ensureAlertChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        if (manager.getNotificationChannel(ALERTS_CHANNEL_ID) == null) {
            val highChannel = NotificationChannel(
                ALERTS_CHANNEL_ID,
                "Alertas de alarma y temporizador",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notificaciones activas mientras suena una alarma o temporizador"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(highChannel)
        }

        if (manager.getNotificationChannel(ALERTS_ONGOING_CHANNEL_ID) == null) {
            val quietChannel = NotificationChannel(
                ALERTS_ONGOING_CHANNEL_ID,
                "Temporizador activo en pantalla",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Mantiene el aviso en la barra de estado mientras se muestra el banner flotante"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(quietChannel)
        }

        if (manager.getNotificationChannel(TIMERS_RUNNING_CHANNEL_ID) == null) {
            val runningTimersChannel = NotificationChannel(
                TIMERS_RUNNING_CHANNEL_ID,
                "Temporizadores en curso",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Muestra el contador en vivo y controles mientras hay un temporizador activo"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(runningTimersChannel)
        }
    }

    /**
     * Sincroniza las notificaciones persistentes de todos los temporizadores activos
     * (en ejecución con cronómetro descendente y en pausa con botón de reanudar)
     * y cancela las que ya no correspondan.
     */
    fun syncRunningTimerNotifications(
        context: Context,
        timers: List<TimerEntity>,
        now: Long = System.currentTimeMillis(),
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureAlertChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val activeTimers = timers.filter { timer ->
            (timer.state == TimerState.RUNNING && (timer.endAtMillis ?: 0L) > now) ||
                (timer.state == TimerState.PAUSED && (timer.remainingMillis ?: 0L) > 0L)
        }
        val activeIds = activeTimers.map { it.id.toInt() }.toSet()

        manager.activeNotifications
            .filter { it.tag == TAG_RUNNING_TIMER && it.id !in activeIds }
            .forEach { manager.cancel(TAG_RUNNING_TIMER, it.id) }

        activeTimers.forEach { timer ->
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPending = PendingIntent.getActivity(
                context,
                Objects.hash("open_timer", timer.id),
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val repostPending = PendingIntent.getBroadcast(
                context,
                Objects.hash(TimerWidgetReceiver.ACTION_REPOST_ONGOING, timer.id),
                Intent(context, TimerWidgetReceiver::class.java)
                    .setAction(TimerWidgetReceiver.ACTION_REPOST_ONGOING)
                    .putExtra(TimerWidgetReceiver.EXTRA_TIMER_ID, timer.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val builder = NotificationCompat.Builder(context, TIMERS_RUNNING_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(timer.label.ifBlank { "Temporizador" })
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setGroup("reloj_running_timers")
                .setContentIntent(openAppPending)
                .setDeleteIntent(repostPending)

            if (timer.state == TimerState.RUNNING) {
                val endAt = timer.endAtMillis ?: return@forEach
                val pausePending = PendingIntent.getBroadcast(
                    context,
                    Objects.hash(TimerWidgetReceiver.ACTION_PAUSE, timer.id),
                    Intent(context, TimerWidgetReceiver::class.java)
                        .setAction(TimerWidgetReceiver.ACTION_PAUSE)
                        .putExtra(TimerWidgetReceiver.EXTRA_TIMER_ID, timer.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val addMinutePending = PendingIntent.getBroadcast(
                    context,
                    Objects.hash(TimerWidgetReceiver.ACTION_ADD_MINUTE, timer.id),
                    Intent(context, TimerWidgetReceiver::class.java)
                        .setAction(TimerWidgetReceiver.ACTION_ADD_MINUTE)
                        .putExtra(TimerWidgetReceiver.EXTRA_TIMER_ID, timer.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                builder
                    .setContentText("Temporizador en curso")
                    .setWhen(endAt)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .addAction(0, "Pausar", pausePending)
                    .addAction(0, "+1 min", addMinutePending)
            } else {
                val remaining = (timer.remainingMillis ?: 0L).coerceAtLeast(0L)
                val resumePending = PendingIntent.getBroadcast(
                    context,
                    Objects.hash(TimerWidgetReceiver.ACTION_START, timer.id),
                    Intent(context, TimerWidgetReceiver::class.java)
                        .setAction(TimerWidgetReceiver.ACTION_START)
                        .putExtra(TimerWidgetReceiver.EXTRA_TIMER_ID, timer.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val resetPending = PendingIntent.getBroadcast(
                    context,
                    Objects.hash(TimerWidgetReceiver.ACTION_RESET, timer.id),
                    Intent(context, TimerWidgetReceiver::class.java)
                        .setAction(TimerWidgetReceiver.ACTION_RESET)
                        .putExtra(TimerWidgetReceiver.EXTRA_TIMER_ID, timer.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                builder
                    .setContentText("En pausa · ${formatRemainingClock(remaining)}")
                    .setShowWhen(false)
                    .setUsesChronometer(false)
                    .addAction(0, "Reanudar", resumePending)
                    .addAction(0, "Reiniciar", resetPending)
            }

            manager.notify(TAG_RUNNING_TIMER, timer.id.toInt(), builder.build())
        }
    }

    private fun formatRemainingClock(millis: Long): String {
        val totalSeconds = (millis + 999L) / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    fun buildAlertNotification(
        context: Context,
        title: String,
        body: String,
        isAlarm: Boolean,
        useFullScreen: Boolean = true,
        suppressHeadsUp: Boolean = false,
    ): Notification {
        ensureAlertChannels(context)

        val alertIntent = Intent(context, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            10,
            alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(context, AlarmAlertService::class.java).setAction(AlarmAlertService.ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            context,
            11,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val snoozeIntent = Intent(context, AlarmAlertService::class.java).setAction(AlarmAlertService.ACTION_SNOOZE)
        val snoozePendingIntent = PendingIntent.getService(
            context,
            12,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val snoozeMinutes = SoundPreferences.get(context).settings.value.snoozeMinutes
        val snoozeLabel = if (isAlarm) "Posponer $snoozeMinutes min" else "+1 min"
        val channelId = if (suppressHeadsUp) ALERTS_ONGOING_CHANNEL_ID else ALERTS_CHANNEL_ID
        val priority = if (suppressHeadsUp) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MAX

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(if (isAlarm) R.drawable.ic_alarm else R.drawable.ic_timer)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(priority)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .apply {
                if (useFullScreen && !suppressHeadsUp) {
                    setFullScreenIntent(fullScreenPendingIntent, true)
                }
            }
            .addAction(0, "Detener", stopPendingIntent)
            .addAction(0, snoozeLabel, snoozePendingIntent)
            .build()
    }

    fun canUseFullScreenIntent(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
}
