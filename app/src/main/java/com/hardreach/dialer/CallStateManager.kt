package com.hardreach.dialer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class CallStateManager(private val context: Context) {

    private val TAG = "CallStateManager"
    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())

    private var currentCallId: Int? = null
    private var isFirstCallConnected = false
    private var isSecondCallConnected = false
    private var isSecondCallInProgress = false
    private var callStartTime = 0L
    private var firstCallConnectTime = 0L
    private var hasAttemptedStateChange = false

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    // Timeouts
    private val CALL_ATTEMPT_TIMEOUT = 20000L // 20 seconds - if no state change, mark as failed
    private val OVERALL_TIMEOUT = 120000L // 2 minutes - absolute max duration

    private var timeoutRunnable: Runnable? = null
    private var overallTimeoutRunnable: Runnable? = null

    private val phoneStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            hasAttemptedStateChange = true

            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> {
                    Log.d(TAG, "Call ringing")
                }
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    Log.i(TAG, "Call connected (off hook)")

                    if (!isFirstCallConnected && !isSecondCallInProgress) {
                        // First call connected
                        isFirstCallConnected = true
                        firstCallConnectTime = System.currentTimeMillis()
                        Log.i(TAG, "✓ First call (team member) connected")

                        // Cancel call attempt timeout since call connected
                        cancelCallAttemptTimeout()

                    } else if (isSecondCallInProgress && !isSecondCallConnected) {
                        // Second call connected
                        isSecondCallConnected = true
                        Log.i(TAG, "✓ Second call (prospect) connected - both calls active!")

                        // Both calls connected - mark as success
                        markCallCompleted()
                    }
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    Log.i(TAG, "Call ended (idle)")

                    if (!isFirstCallConnected) {
                        // First call never connected - failed
                        Log.w(TAG, "First call failed (never connected) - marking as failed")
                        markCallFailed()
                    } else if (isSecondCallInProgress && !isSecondCallConnected) {
                        // First call worked but second call failed
                        Log.w(TAG, "Second call failed (never connected) - marking as failed")
                        markCallFailed()
                    } else if (!isSecondCallInProgress) {
                        // First call ended before second call started
                        Log.w(TAG, "First call ended early - marking as failed")
                        markCallFailed()
                    }
                    // If both connected, already marked as completed in OFFHOOK

                    cleanup()
                }
            }
        }
    }

    fun startMonitoring(callId: Int, teamNumber: String, contactNum: String) {
        currentCallId = callId
        isFirstCallConnected = false
        isSecondCallConnected = false
        isSecondCallInProgress = false
        callStartTime = System.currentTimeMillis()
        firstCallConnectTime = 0L
        hasAttemptedStateChange = false

        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        Log.i(TAG, "Started monitoring call $callId")

        // Set call attempt timeout - if no state change in 20 seconds, mark as failed
        timeoutRunnable = Runnable {
            if (!hasAttemptedStateChange) {
                Log.e(TAG, "Call attempt timeout - no state change in 20s (no balance/network error)")
                markCallFailed()
                cleanup()
            }
        }
        handler.postDelayed(timeoutRunnable!!, CALL_ATTEMPT_TIMEOUT)

        // Set overall timeout - maximum 2 minutes for entire flow
        overallTimeoutRunnable = Runnable {
            Log.w(TAG, "Overall timeout reached (2 minutes) - cleaning up")
            if (!isSecondCallConnected) {
                markCallFailed()
            }
            cleanup()
        }
        handler.postDelayed(overallTimeoutRunnable!!, OVERALL_TIMEOUT)
    }

    fun setSecondCallInProgress() {
        isSecondCallInProgress = true
        Log.i(TAG, "Second call marked as in progress")
    }

    fun isFirstCallAnswered(): Boolean {
        return isFirstCallConnected
    }

    fun isSecondCallAnswered(): Boolean {
        return isSecondCallConnected
    }

    fun areBothCallsConnected(): Boolean {
        return isFirstCallConnected && isSecondCallConnected
    }

    private fun cancelCallAttemptTimeout() {
        timeoutRunnable?.let {
            handler.removeCallbacks(it)
            Log.d(TAG, "Cancelled call attempt timeout")
        }
    }

    private fun markCallCompleted() {
        // Only mark as completed once
        if (currentCallId == null) return

        val callId = currentCallId
        currentCallId = null // Prevent duplicate updates

        callId?.let {
            Log.i(TAG, "✓✓ Both calls connected successfully - marking as COMPLETED")
            updateCallStatus(it, "completed")
        }
    }

    private fun markCallFailed() {
        // Only mark as failed once
        if (currentCallId == null) return

        val callId = currentCallId
        currentCallId = null // Prevent duplicate updates

        callId?.let {
            Log.w(TAG, "✗ Call flow failed - marking as FAILED")
            updateCallStatus(it, "failed")
        }
    }

    private fun updateCallStatus(callId: Int, status: String) {
        Thread {
            try {
                val prefs = context.getSharedPreferences("hardreach_dialer", Context.MODE_PRIVATE)
                val serverUrl = prefs.getString("server_url", "")?.trim() ?: ""
                val apiKey = prefs.getString("api_key", "")?.replace("\\s".toRegex(), "") ?: ""

                if (serverUrl.isEmpty() || apiKey.isEmpty()) {
                    Log.w(TAG, "Cannot update status - no server config")
                    return@Thread
                }

                val json = JSONObject()
                json.put("status", status)

                val body = json.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$serverUrl/api/dialer/pending-calls/$callId")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .patch(body)
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    Log.i(TAG, "✓ Call $callId marked as $status in database")
                } else {
                    Log.e(TAG, "Failed to update status: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating call status: ${e.message}", e)
            }
        }.start()
    }

    fun stopMonitoring() {
        cleanup()
    }

    private fun cleanup() {
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)

        timeoutRunnable?.let { handler.removeCallbacks(it) }
        overallTimeoutRunnable?.let { handler.removeCallbacks(it) }

        Log.i(TAG, "Cleanup complete - stopped monitoring")
    }
}
