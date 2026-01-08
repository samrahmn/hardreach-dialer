package com.hardreach.dialer

import android.content.Context
import android.telecom.Call
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

    private var currentCallId: Int? = null
    private var isFirstCallConnected = false
    private var isSecondCallInProgress = false
    private var teamMemberNumber: String? = null
    private var contactNumber: String? = null

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private val phoneStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> {
                    Log.d(TAG, "Call ringing")
                }
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    Log.i(TAG, "Call connected (off hook)")
                    if (!isFirstCallConnected && !isSecondCallInProgress) {
                        isFirstCallConnected = true
                        Log.i(TAG, "First call (team member) connected")
                    } else if (isSecondCallInProgress) {
                        Log.i(TAG, "Second call (contact) connected")
                    }
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    Log.i(TAG, "Call ended (idle)")
                    if (!isFirstCallConnected) {
                        // First call was rejected or not answered
                        Log.w(TAG, "First call was not answered - marking as failed")
                        markCallFailed()
                    } else {
                        // Calls completed normally
                        Log.i(TAG, "Calls completed - marking as completed")
                        markCallCompleted()
                    }
                    reset()
                }
            }
        }
    }

    fun startMonitoring(callId: Int, teamNumber: String, contactNum: String) {
        currentCallId = callId
        teamMemberNumber = teamNumber
        contactNumber = contactNum
        isFirstCallConnected = false
        isSecondCallInProgress = false

        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        Log.i(TAG, "Started monitoring call $callId")
    }

    fun setSecondCallInProgress() {
        isSecondCallInProgress = true
        Log.i(TAG, "Second call marked as in progress")
    }

    fun isFirstCallAnswered(): Boolean {
        return isFirstCallConnected
    }

    private fun markCallCompleted() {
        currentCallId?.let { updateCallStatus(it, "completed") }
    }

    private fun markCallFailed() {
        currentCallId?.let { updateCallStatus(it, "failed") }
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
                    Log.i(TAG, "Call $callId marked as $status")
                } else {
                    Log.e(TAG, "Failed to update status: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating call status: ${e.message}", e)
            }
        }.start()
    }

    fun stopMonitoring() {
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        Log.i(TAG, "Stopped monitoring")
    }

    private fun reset() {
        currentCallId = null
        isFirstCallConnected = false
        isSecondCallInProgress = false
        teamMemberNumber = null
        contactNumber = null
    }
}
