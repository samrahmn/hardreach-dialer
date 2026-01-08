package com.hardreach.dialer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

class WebhookService : Service() {
    
    companion object {
        var isRunning = false
        private const val CHANNEL_ID = "webhook_service"
        private const val NOTIFICATION_ID = 1
    }
    
    private val TAG = "WebhookService"
    private lateinit var callManager: CallManager
    
    override fun onCreate() {
        super.onCreate()
        callManager = CallManager(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        scheduleWebhookPolling()
        isRunning = true
        Log.i(TAG, "Service started")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        WorkManager.getInstance(this).cancelAllWorkByTag("webhook_poll")
        Log.i(TAG, "Service stopped")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hardreach Dialer Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hardreach Dialer Active")
            .setContentText("Listening for calls from CRM")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    
    /**
     * Poll CRM server every 10 seconds for pending calls
     */
    private fun scheduleWebhookPolling() {
        val workRequest = PeriodicWorkRequestBuilder<WebhookWorker>(
            15, TimeUnit.SECONDS,
            5, TimeUnit.SECONDS
        )
            .addTag("webhook_poll")
            .build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "webhook_polling",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}

/**
 * Background worker that polls CRM for pending calls
 */
class WebhookWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    
    private val TAG = "WebhookWorker"
    private val client = OkHttpClient()
    
    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("hardreach_dialer", Context.MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "") ?: ""
        val apiKey = prefs.getString("api_key", "") ?: ""
        
        if (serverUrl.isEmpty() || apiKey.isEmpty()) {
            Log.w(TAG, "Server URL or API key not configured")
            return Result.success()
        }
        
        try {
            val request = Request.Builder()
                .url("$serverUrl/api/dialer/pending-calls")
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    Log.d(TAG, "Response: $body")
                    // TODO: Parse JSON and initiate calls
                    // For now, just log
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Polling failed: ${e.message}")
        }
        
        return Result.success()
    }
}
