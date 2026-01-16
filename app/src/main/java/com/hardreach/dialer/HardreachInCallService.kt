package com.hardreach.dialer

import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

/**
 * InCallService to detect active calls
 * Accessibility service will handle merging by tapping UI button
 */
class HardreachInCallService : InCallService() {

    private val TAG = "InCallService"
    private val activeCalls = mutableListOf<Call>()

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        activeCalls.add(call)
        Log.i(TAG, "✓ Call added - Total active calls: ${activeCalls.size}")
        RemoteLogger.i(applicationContext, TAG, "✓ Call added - Total active calls: ${activeCalls.size}")
        StatusManager.log("Call detected - Total calls: ${activeCalls.size}")

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                handleCallStateChange(call, state)
            }
        })

        // Notify when 2 calls detected - accessibility service will merge
        if (activeCalls.size == 2) {
            Log.i(TAG, "✓ 2 calls detected - accessibility service will auto-merge")
            RemoteLogger.i(applicationContext, TAG, "✓ 2 calls detected - accessibility service will auto-merge")
            StatusManager.log("2 calls detected - waiting for accessibility service to merge")
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        activeCalls.remove(call)
        Log.i(TAG, "Call removed - Total active calls: ${activeCalls.size}")
    }

    private fun handleCallStateChange(call: Call, state: Int) {
        val stateString = when (state) {
            Call.STATE_NEW -> "NEW"
            Call.STATE_DIALING -> "DIALING"
            Call.STATE_RINGING -> "RINGING"
            Call.STATE_ACTIVE -> "ACTIVE"
            Call.STATE_HOLDING -> "HOLDING"
            Call.STATE_DISCONNECTED -> "DISCONNECTED"
            else -> "UNKNOWN"
        }

        Log.i(TAG, "Call state changed: $stateString (Total calls: ${activeCalls.size})")
        RemoteLogger.i(applicationContext, TAG, "Call state: $stateString (Total: ${activeCalls.size})")

        // When both calls active, accessibility service will tap merge button
        if (state == Call.STATE_ACTIVE && activeCalls.size == 2) {
            val activeCount = activeCalls.count { it.state == Call.STATE_ACTIVE }
            if (activeCount == 2) {
                Log.i(TAG, "✓✓ BOTH CALLS ACTIVE - accessibility service should merge now")
                RemoteLogger.i(applicationContext, TAG, "✓✓ BOTH CALLS ACTIVE - accessibility service will merge")
                StatusManager.conferenceCreated()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activeCalls.clear()
        Log.i(TAG, "InCallService destroyed")
    }
}
