package com.example.shiftplanner.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

// BroadcastReceiver that listens for device boot events.
// This is crucial because Android clears all scheduled AlarmManager alarms when the device restarts.
// This receiver ensures that our daily shift reminders are immediately rescheduled upon power-on.
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ShiftPlanner_Boot"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {

            Log.d(TAG, "Device reboot completed. Rescheduling next alarm...")
            AlarmHelper.scheduleNextAlarm(context)
        }
    }
}