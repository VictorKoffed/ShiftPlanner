package com.example.shiftplanner.widget

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.google.gson.Gson

object WidgetUpdater {

    // Key used to store and retrieve widget data in DataStore
    val widgetDataKey = stringPreferencesKey("widget_data_json")

    private const val TAG = "ShiftPlanner_Widget"

    suspend fun updateWidgetState(context: Context) {
        try {
            // 1. Fetch data safely outside the Glance UI thread
            val widgetData = WidgetDataHelper.getWidgetInfo(context)

            // 2. Convert the data object into a JSON string
            val jsonString = Gson().toJson(widgetData)

            // 3. Save the JSON string to the widget's DataStore preferences
            val glanceManager = GlanceAppWidgetManager(context)
            val glanceIds = glanceManager.getGlanceIds(ScheduleWidget::class.java)

            for (glanceId in glanceIds) {
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[widgetDataKey] = jsonString
                }
            }

            // 4. Trigger UI refresh for all active widgets
            ScheduleWidget().updateAll(context)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to update widget state", e)
        }
    }
}