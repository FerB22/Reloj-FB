package com.reloj.app.core.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.reloj.app.R
import com.reloj.app.core.data.local.SoundPreferences

object NotificationHelper {

    const val ALERTS_CHANNEL_ID = "alerts_ringing_v2"
    const val ALERTS_ONGOING_CHANNEL_ID = "alerts_ongoing_quiet_v1"

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
