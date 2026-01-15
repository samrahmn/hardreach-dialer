package com.hardreach.dialer

import android.content.Context
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Logger that sends logs to remote server for debugging
 */
object RemoteLogger {
    private val client = OkHttpClient()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun i(context: Context, tag: String, message: String) {
        Log.i(tag, message)
        sendLog(context, "INFO", tag, message)
    }

    fun w(context: Context, tag: String, message: String) {
        Log.w(tag, message)
        sendLog(context, "WARN", tag, message)
    }

    fun e(context: Context, tag: String, message: String) {
        Log.e(tag, message)
        sendLog(context, "ERROR", tag, message)
    }

    private fun sendLog(context: Context, level: String, tag: String, message: String) {
        Thread {
            try {
                val prefs = context.getSharedPreferences("hardreach_dialer", Context.MODE_PRIVATE)
                val serverUrl = prefs.getString("server_url", "")?.trim() ?: ""
                val apiKey = prefs.getString("api_key", "")?.replace("\\s".toRegex(), "") ?: ""

                if (serverUrl.isEmpty() || apiKey.isEmpty()) {
                    return@Thread
                }

                val json = JSONObject()
                json.put("level", level)
                json.put("tag", tag)
                json.put("message", message)
                json.put("timestamp", dateFormat.format(Date()))

                val body = json.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$serverUrl/api/dialer/logs")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w("RemoteLogger", "Failed to send log: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                // Silently fail - don't want logging to break the app
                Log.w("RemoteLogger", "Failed to send remote log: ${e.message}")
            }
        }.start()
    }
}
