package com.reloj.app.core.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.reloj.app.core.data.repository.AlarmRepository
import com.reloj.app.core.data.repository.TimerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val validAction = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> true
            else -> false
        }
        if (!validAction) return

        val wakeLock = runCatching {
            context.getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Reloj:BootRescheduleWakeLock")
                ?.apply {
                    setReferenceCounted(false)
                    acquire(15_000L)
                }
        }.getOrNull()

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TimerRepository.get(context).rescheduleRunning()
                AlarmRepository.get(context).rescheduleEnabled()
            } finally {
                runCatching {
                    if (wakeLock?.isHeld == true) wakeLock.release()
                }
                pending.finish()
            }
        }
    }
}
