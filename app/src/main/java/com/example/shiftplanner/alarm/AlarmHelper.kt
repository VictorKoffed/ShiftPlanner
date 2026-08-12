package com.example.shiftplanner.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Calendar

// Helper object for managing exact-time alarms using AlarmManager.
// Handles scheduling, Doze-mode compatibility, and cancellation of evening reminders.
object AlarmHelper {

    private const val TAG = "ShiftPlanner_AlarmHelper"

    // Schedules the next reminder alarm based on user settings.
    // If the target time for today has already passed, it automatically schedules it for tomorrow.
    fun scheduleNextAlarm(context: Context) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("reminders_enabled", false)

        if (!isEnabled) {
            cancelAlarm(context)
            return
        }

        val reminderTime = prefs.getString("reminder_time", "20:00") ?: "20:00"
        val parts = reminderTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 20
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        Log.d(TAG, "Scheduling next alarm for $reminderTime...")
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context, 1001, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)

            // If the specified time has already passed today, roll over to tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Use exact and doze-aware scheduling so the reminder triggers reliably
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        } catch (e: SecurityException) {
            // Fallback for devices restricting exact alarms
            alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
    }

    // Cancels any currently active reminder alarms.
    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 1001, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Reminder alarm successfully cancelled.")
    }
}