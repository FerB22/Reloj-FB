package com.reloj.app.core.data.local

import java.util.Calendar

/**
 * Utilidades de repetición semanal (máscara de bits Lunes=0 .. Domingo=6),
 * cálculo del próximo disparo y formateo horario `12 h / 24 h`.
 */
object AlarmRepeatUtils {

    const val MASK_NONE = 0
    const val MASK_WEEKDAYS = 0b0011111
    const val MASK_WEEKENDS = 0b1100000
    const val MASK_EVERYDAY = 0b1111111

    val DAY_SHORT_LETTERS = listOf("L", "M", "X", "J", "V", "S", "D")
    private val DAY_LABELS = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

    fun hasDay(mask: Int, dayIndex: Int): Boolean = (mask and (1 shl dayIndex)) != 0

    fun toggleDay(mask: Int, dayIndex: Int): Int = mask xor (1 shl dayIndex)

    fun dayIndexFromCalendar(calendarDayOfWeek: Int): Int = when (calendarDayOfWeek) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        Calendar.SATURDAY -> 5
        Calendar.SUNDAY -> 6
        else -> 0
    }

    fun nextTriggerMillis(
        hour: Int,
        minute: Int,
        repeatDaysMask: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        if (repeatDaysMask == MASK_NONE) {
            return Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= nowMillis) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }.timeInMillis
        }

        for (offset in 0..7) {
            val candidate = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayBit = dayIndexFromCalendar(candidate.get(Calendar.DAY_OF_WEEK))
            if (hasDay(repeatDaysMask, dayBit) && candidate.timeInMillis > nowMillis) {
                return candidate.timeInMillis
            }
        }

        return nowMillis + 24 * 60 * 60_000L
    }

    fun formatRepeatSummary(mask: Int): String = when (mask) {
        MASK_NONE -> "Una vez"
        MASK_EVERYDAY -> "Todos los días"
        MASK_WEEKDAYS -> "Lun a vie"
        MASK_WEEKENDS -> "Fines de semana"
        else -> DAY_LABELS.indices
            .filter { hasDay(mask, it) }
            .joinToString(" · ") { DAY_LABELS[it] }
    }

    fun formatRemainingUntil(
        triggerMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val diffMillis = (triggerMillis - nowMillis).coerceAtLeast(0L)
        if (diffMillis < 60_000L) return "En menos de 1 min"

        val totalMinutes = (diffMillis + 59_999L) / 60_000L
        val days = totalMinutes / (24L * 60L)
        val hours = (totalMinutes % (24L * 60L)) / 60L
        val minutes = totalMinutes % 60L

        return when {
            days > 0L -> if (hours > 0L) "En ${days} d ${hours} h" else "En ${days} d"
            hours > 0L -> if (minutes > 0L) "En ${hours} h ${minutes} min" else "En ${hours} h"
            else -> "En ${minutes} min"
        }
    }

    fun formatAlarmTime(
        hour: Int,
        minute: Int,
        use24Hour: Boolean,
    ): String {
        if (use24Hour) return "%02d:%02d".format(hour, minute)
        val hour12 = when (val mod = hour % 12) {
            0 -> 12
            else -> mod
        }
        val period = if (hour < 12) "AM" else "PM"
        return "%d:%02d %s".format(hour12, minute, period)
    }
}
