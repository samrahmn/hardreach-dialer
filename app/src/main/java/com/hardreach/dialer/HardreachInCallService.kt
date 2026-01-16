package com.hardreach.dialer

import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

/**
 * InCallService to detect active calls
 * User will manually merge calls in native phone UI
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

        if (activeCalls.size == 2) {
            Log.i(TAG, "✓✓ BOTH CALLS PLACED - manually tap Merge button to connect")
            RemoteLogger.i(applicationContext, TAG, "✓✓ BOTH CALLS PLACED - manually tap Merge to connect")
            StatusManager.log("2 calls active - manually tap Merge button in phone UI")
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        activeCalls.remove(call)
        Log.i(TAG, "Call removed - Total active calls: ${activeCalls.size}")
    }

    override fun onDestroy() {
        super.onDestroy()
        activeCalls.clear()
        Log.i(TAG, "InCallService destroyed")
    }
}
