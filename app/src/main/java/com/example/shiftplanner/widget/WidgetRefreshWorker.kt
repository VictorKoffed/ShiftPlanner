package com.example.shiftplanner.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WidgetRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    // Periodically updates the home screen widget state in the background
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val context = applicationContext
            WidgetUpdater.updateWidgetState(context)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}