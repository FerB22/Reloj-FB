package com.reloj.app.core.data.repository

import android.content.Context
import com.reloj.app.core.alarm.AlarmAlertService
import com.reloj.app.core.alarm.AlarmScheduler
import com.reloj.app.core.data.local.AlarmEntity
import com.reloj.app.core.data.local.AlarmRepeatUtils
import com.reloj.app.core.data.local.AppDatabase
import com.reloj.app.core.data.local.CustomSoundManager
import kotlinx.coroutines.flow.Flow

class AlarmRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val dao = AppDatabase.get(appContext).alarmDao()
    private val scheduler = AlarmScheduler(appContext)

    fun observeAll(): Flow<List<AlarmEntity>> = dao.observeAll()

    suspend fun getById(id: Long): AlarmEntity? = dao.getById(id)

    suspend fun create(
        hour: Int,
        minute: Int,
        label: String,
        repeatDaysMask: Int = 0,
        soundPreset: String? = null,
        customSoundPath: String? = null,
        customSoundName: String? = null,
    ): Long {
        val alarm = AlarmEntity(
            hour = hour,
            minute = minute,
            label = label,
            enabled = true,
            repeatDaysMask = repeatDaysMask,
            soundPreset = soundPreset,
            customSoundPath = customSoundPath,
            customSoundName = customSoundName,
        )
        val id = dao.insert(alarm)
        scheduler.scheduleClockAlarm(
            id,
            AlarmRepeatUtils.nextTriggerMillis(hour, minute, repeatDaysMask),
        )
        return id
    }

    suspend fun update(
        id: Long,
        hour: Int,
        minute: Int,
        label: String,
        repeatDaysMask: Int,
        soundPreset: String?,
        customSoundPath: String?,
        customSoundName: String?,
    ) {
        val current = dao.getById(id) ?: return
        val updated = current.copy(
            hour = hour,
            minute = minute,
            label = label,
            repeatDaysMask = repeatDaysMask,
            soundPreset = soundPreset,
            customSoundPath = customSoundPath,
            customSoundName = customSoundName,
        )
        dao.update(updated)
        if (updated.enabled) {
            scheduler.scheduleClockAlarm(
                id,
                AlarmRepeatUtils.nextTriggerMillis(hour, minute, repeatDaysMask),
            )
        }
        CustomSoundManager.pruneUnusedSounds(appContext)
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        val alarm = dao.getById(id) ?: return
        dao.update(alarm.copy(enabled = enabled))
        if (enabled) {
            scheduler.scheduleClockAlarm(
                id,
                AlarmRepeatUtils.nextTriggerMillis(alarm.hour, alarm.minute, alarm.repeatDaysMask),
            )
        } else {
            scheduler.cancelClockAlarm(id)
            AlarmAlertService.stopIfMatching(appContext, isAlarm = true, itemId = id)
        }
    }

    suspend fun rescheduleSingle(id: Long) {
        val alarm = dao.getById(id) ?: return
        if (!alarm.enabled) return
        scheduler.scheduleClockAlarm(
            id,
            AlarmRepeatUtils.nextTriggerMillis(alarm.hour, alarm.minute, alarm.repeatDaysMask),
        )
    }

    suspend fun delete(alarm: AlarmEntity) {
        scheduler.cancelClockAlarm(alarm.id)
        AlarmAlertService.stopIfMatching(appContext, isAlarm = true, itemId = alarm.id)
        dao.delete(alarm)
        CustomSoundManager.pruneUnusedSounds(appContext)
    }

    suspend fun rescheduleEnabled() {
        for (alarm in dao.getAll()) {
            if (alarm.enabled) {
                scheduler.scheduleClockAlarm(
                    alarm.id,
                    AlarmRepeatUtils.nextTriggerMillis(alarm.hour, alarm.minute, alarm.repeatDaysMask),
                )
            }
        }
    }

    companion object {
        @Volatile private var instance: AlarmRepository? = null

        fun get(context: Context): AlarmRepository =
            instance ?: synchronized(this) {
                instance ?: AlarmRepository(context.applicationContext).also { instance = it }
            }
    }
}
