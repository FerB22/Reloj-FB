package com.reloj.app.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reloj.app.core.data.repository.AlarmRepository
import com.reloj.app.core.data.repository.TimerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TimerRepository.get(context).rescheduleRunning()
                AlarmRepository.get(context).rescheduleEnabled()
            } finally {
                pending.finish()
            }
        }
    }
}
