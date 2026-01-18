package com.hardreach.dialer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
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
                Log.w(TAG, "Cannot schedule exact alarms - using inexact")
                RemoteLogger.w(context, TAG, "Using inexact alarms (permission denied)")
                // Fall back to inexact alarm instead of returning
                scheduleInexactPolling(context, alarmManager)
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

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
            Log.i(TAG, "Exact polling alarm scheduled - fires in ${POLL_INTERVAL / 1000}s")
            RemoteLogger.i(context, TAG, "AlarmManager polling enabled (exact)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception scheduling alarm: ${e.message}")
            scheduleInexactPolling(context, alarmManager)
        }
    }

    private fun scheduleInexactPolling(context: Context, alarmManager: AlarmManager) {
        val intent = Intent(context, PollAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)

        // Use setAndAllowWhileIdle for inexact but doze-aware alarms
        val triggerTime = System.currentTimeMillis() + POLL_INTERVAL

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
        Log.i(TAG, "Inexact polling alarm scheduled")
        RemoteLogger.i(context, TAG, "AlarmManager polling enabled (inexact)")
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

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }
        return true
    }
}
