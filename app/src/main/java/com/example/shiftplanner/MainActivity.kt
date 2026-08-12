package com.example.shiftplanner

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.shiftplanner.alarm.AlarmHelper
import com.example.shiftplanner.ui.navigation.NavGraph
import com.example.shiftplanner.ui.theme.ShiftPlannerTheme
import com.example.shiftplanner.widget.WidgetRefreshWorker
import com.example.shiftplanner.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val viewModel: ScheduleViewModel by viewModels()
    private var lastCheckedDate: LocalDate = LocalDate.now()

    companion object {
        private const val TAG = "ShiftPlanner_Debug"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastCheckedDate = LocalDate.now()

        // Enqueue a periodic background worker to keep home screen widgets updated hourly
        val periodicRequest = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
            1, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "WidgetRefreshPeriodicWork",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )

        setContent {
            ShiftPlannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val today = LocalDate.now()
        Log.d(TAG, "onResume() called. Current date: $today")

        // Trigger updates if a new day has started while the app was in the background
        if (today != lastCheckedDate) {
            lastCheckedDate = today
            Log.d(TAG, "New day detected. Refreshing widget state.")

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    WidgetUpdater.updateWidgetState(applicationContext)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update widget state in onResume", e)
                }
            }
        }

        // Ensure alarm triggers are rescheduled safely on resume
        try {
            Log.d(TAG, "Rescheduling next alarm...")
            AlarmHelper.scheduleNextAlarm(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm: ${e.message}", e)
        }
    }
}