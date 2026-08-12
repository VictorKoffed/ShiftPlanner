package com.example.shiftplanner.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

// Receives the exact-time alarm broadcast from the Android OS.
// To comply with Android's background execution limits and avoid blocking the main thread,
// it immediately delegates the heavy lifting (database access and notification posting) to WorkManager.
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ShiftPlanner_Alarm"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm triggered! Delegating background work to WorkManager...")

        val workRequest = OneTimeWorkRequestBuilder<NotificationWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}