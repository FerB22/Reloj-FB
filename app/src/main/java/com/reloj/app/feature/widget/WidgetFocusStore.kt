package com.reloj.app.feature.widget

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

object WidgetFocusStore {
    private const val PREFS_NAME = "reloj_widget_focus"
    private const val KEY_PREFIX = "focused_timer_"

    private val updates = MutableStateFlow(0L)

    fun observeFocusedTimerId(context: Context, appWidgetId: Int): Flow<Long?> =
        updates
            .map { getFocusedTimerId(context, appWidgetId) }
            .distinctUntilChanged()

    fun getFocusedTimerId(context: Context, appWidgetId: Int): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getLong("$KEY_PREFIX$appWidgetId", -1L)
        return if (id >= 0L) id else null
    }

    fun setFocusedTimerId(context: Context, appWidgetId: Int, timerId: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong("$KEY_PREFIX$appWidgetId", timerId)
            .apply()
        updates.update { it + 1L }
    }

    fun clearFocus(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("$KEY_PREFIX$appWidgetId")
            .apply()
        updates.update { it + 1L }
    }
}
