package com.hardreach.dialer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    private const val POLL_INTERVAL = 15000L // 15 seconds
    private const val REQUEST_CODE = 1001

    fun schedulePolling(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Check if we can schedule exact alarms (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Cannot schedule exact alarms - permission denied")
                RemoteLogger.w(context, TAG, "Exact alarm permission denied")
                return
            }
        }

        val intent = Intent(context, PollAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel any existing alarms
        alarmManager.cancel(pendingIntent)

        // Schedule repeating exact alarm
        val triggerTime = System.currentTimeMillis() + POLL_INTERVAL

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Use setExactAndAllowWhileIdle for Android 6+
            // This allows starting foreground service from background
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }

        Log.i(TAG, "Polling alarm scheduled - fires in ${POLL_INTERVAL / 1000}s")
        RemoteLogger.i(context, TAG, "AlarmManager polling enabled")
    }

    fun cancelPolling(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PollAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        Log.i(TAG, "Polling alarm cancelled")
        RemoteLogger.i(context, TAG, "AlarmManager polling disabled")
    }
}
