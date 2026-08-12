package com.example.shiftplanner.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget = ScheduleWidget()

    companion object {
        private const val TAG = "ShiftPlanner_Widget"
    }

    // Triggered automatically when the widget is added to the home screen
    // or during OS-scheduled periodic updates.
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        Log.d(TAG, "ScheduleWidgetReceiver: onUpdate called. Fetching data immediately...")

        // Trigger a background data fetch to rebuild state and redraw the widget.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetUpdater.updateWidgetState(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed initial update in receiver: ${e.message}", e)
            }
        }
    }
}