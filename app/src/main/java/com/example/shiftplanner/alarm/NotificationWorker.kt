package com.example.shiftplanner.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.shiftplanner.MainActivity
import com.example.shiftplanner.model.ShiftType
import com.example.shiftplanner.widget.WidgetDataHelper
import java.time.LocalDate

// Background worker responsible for evaluating tomorrow's schedule and dispatching
// a notification if the user is scheduled to work.
class NotificationWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ShiftPlanner_Worker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "NotificationWorker triggered. Fetching real-time schedule data...")

        try {
            val tomorrow = LocalDate.now().plusDays(1)
            val tomorrowStr = tomorrow.toString()

            // Reusing WidgetDataHelper ensures consistent data logic across the app, widgets, and alarms
            val widgetInfo = WidgetDataHelper.getWidgetInfo(appContext)

            val tomorrowModel = widgetInfo.days.find {
                val monday = LocalDate.now().minusDays(((LocalDate.now().dayOfWeek.value + 6) % 7).toLong())
                val dayIndex = widgetInfo.days.indexOf(it)
                monday.plusDays(dayIndex.toLong()).toString() == tomorrowStr
            }

            val rawCode = tomorrowModel?.shiftCode?.uppercase() ?: ""
            val shiftTitle = if (rawCode.isNotBlank() && rawCode != "-" && rawCode != "ABSENT") {
                if (rawCode == "BY") {
                    "Bortbytt"
                } else {
                    val match = ShiftType.values().find { it.code.equals(rawCode, ignoreCase = true) }
                    match?.title ?: "Pass $rawCode"
                }
            } else {
                "Ledig"
            }

            // CORE LOGIC: Only trigger a push notification if the user is actually scheduled to work
            if (shiftTitle != "Ledig" && shiftTitle != "Bortbytt" && rawCode != "SEM" && rawCode != "SJU") {
                showNotification(shiftTitle, tomorrowStr)
                Log.d(TAG, "Notification successfully dispatched for shift: $shiftTitle")
            } else {
                Log.d(TAG, "Notification skipped: User is off, sick, or on vacation tomorrow ($rawCode).")
            }

            // Schedule the alarm for the next day to keep the chain alive
            AlarmHelper.scheduleNextAlarm(appContext)

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error processing NotificationWorker: ${e.message}", e)
            return Result.retry()
        }
    }

    private fun showNotification(shiftTitle: String, tomorrowDate: String) {
        val channelId = "evening_reminder_channel"
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create the NotificationChannel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Kvällspåminnelser",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Påminnelse om morgondagens arbetspass"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val message = "Imorgon ($tomorrowDate) har du ett inplanerat pass ($shiftTitle)."

        // Intent to launch the main app if the user taps the notification
        val clickIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val clickPendingIntent = PendingIntent.getActivity(
            appContext, 0, clickIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("ShiftPlanner - Påminnelse")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(clickPendingIntent)
            .build()

        notificationManager.notify(1001, notification)
    }
}