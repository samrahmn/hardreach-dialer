package com.hardreach.dialer

import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

/**
 * InCallService to detect active calls and show custom InCallActivity UI
 */
class HardreachInCallService : InCallService() {

    companion object {
        var currentCall: Call? = null
        var instance: HardreachInCallService? = null
    }

    private val TAG = "InCallService"
    private val activeCalls = mutableListOf<Call>()

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        activeCalls.add(call)
        currentCall = call
        instance = this

        Log.i(TAG, "✓ Call added - Total active calls: ${activeCalls.size}")
        RemoteLogger.i(applicationContext, TAG, "✓ Call added - Total active calls: ${activeCalls.size}")
        StatusManager.log("Call detected - Total calls: ${activeCalls.size}")

        // Launch InCallActivity UI
        launchInCallUI(call)

        if (activeCalls.size == 2) {
            Log.i(TAG, "✓✓ BOTH CALLS PLACED - manually tap Merge button to connect")
            RemoteLogger.i(applicationContext, TAG, "✓✓ BOTH CALLS PLACED - manually tap Merge to connect")
            StatusManager.log("2 calls active - manually tap Merge button in phone UI")
        }
    }

    private fun launchInCallUI(call: Call) {
        try {
            // Get phone number from call details
            val phoneNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                call.details?.handle?.schemeSpecificPart ?: "Unknown"
            } else {
                call.details?.handle?.schemeSpecificPart ?: "Unknown"
            }

            Log.i(TAG, "Launching InCallActivity for: $phoneNumber")
            RemoteLogger.i(applicationContext, TAG, "Launching InCallActivity for: $phoneNumber")

            val intent = Intent(this, InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra("phone_number", phoneNumber)
                putExtra("contact_name", phoneNumber) // Can be enhanced to lookup contact name
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch InCallActivity: ${e.message}")
            RemoteLogger.e(applicationContext, TAG, "Failed to launch InCallActivity: ${e.message}")
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        activeCalls.remove(call)
        Log.i(TAG, "Call removed - Total active calls: ${activeCalls.size}")
        RemoteLogger.i(applicationContext, TAG, "Call removed - Total active calls: ${activeCalls.size}")

        if (call == currentCall) {
            currentCall = activeCalls.lastOrNull()
        }

        // If no more calls, clear state
        if (activeCalls.isEmpty()) {
            currentCall = null
            StatusManager.log("All calls ended")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activeCalls.clear()
        currentCall = null
        instance = null
        Log.i(TAG, "InCallService destroyed")
    }
}
