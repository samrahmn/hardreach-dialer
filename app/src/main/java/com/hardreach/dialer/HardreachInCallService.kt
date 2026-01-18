package com.hardreach.dialer

import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

/**
 * InCallService to detect active calls and show custom InCallActivity UI
 * Also tracks call state changes to detect when calls are actually connected
 */
class HardreachInCallService : InCallService() {

    companion object {
        var currentCall: Call? = null
        var instance: HardreachInCallService? = null

        // Callback for when first call connects (used by CallManager)
        var onFirstCallConnected: (() -> Unit)? = null

        // Track if first call is connected
        var isFirstCallConnected = false

        // Track call states
        private val callStates = mutableMapOf<Call, Int>()

        fun getCallState(call: Call): Int {
            return callStates[call] ?: Call.STATE_NEW
        }

        fun reset() {
            onFirstCallConnected = null
            isFirstCallConnected = false
            callStates.clear()
        }
    }

    private val TAG = "InCallService"
    private val activeCalls = mutableListOf<Call>()
    private val callCallbacks = mutableMapOf<Call, Call.Callback>()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        activeCalls.add(call)
        currentCall = call
        instance = this

        val phoneNumber = call.details?.handle?.schemeSpecificPart ?: "Unknown"
        val callIndex = activeCalls.size

        Log.i(TAG, "✓ Call #$callIndex added: $phoneNumber - Total active calls: ${activeCalls.size}")
        RemoteLogger.i(applicationContext, TAG, "✓ Call #$callIndex added: $phoneNumber")
        StatusManager.log("Call #$callIndex detected: $phoneNumber")

        // Register callback to track call state changes
        val callback = createCallCallback(call, callIndex)
        call.registerCallback(callback)
        callCallbacks[call] = callback
        callStates[call] = call.state

        // Launch InCallActivity UI
        launchInCallUI(call)

        if (activeCalls.size == 2) {
            Log.i(TAG, "✓✓ BOTH CALLS PLACED - manually tap Merge button to connect")
            RemoteLogger.i(applicationContext, TAG, "✓✓ BOTH CALLS PLACED - manually tap Merge to connect")
            StatusManager.log("2 calls active - tap Merge button in phone UI")
        }
    }

    private fun createCallCallback(call: Call, callIndex: Int): Call.Callback {
        return object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                val stateName = getStateName(state)
                val phoneNumber = call.details?.handle?.schemeSpecificPart ?: "Unknown"

                Log.i(TAG, "Call #$callIndex ($phoneNumber) state: $stateName")
                RemoteLogger.i(applicationContext, TAG, "Call #$callIndex state: $stateName")
                StatusManager.log("Call #$callIndex: $stateName")

                callStates[call] = state

                when (state) {
                    Call.STATE_ACTIVE -> {
                        // Call is connected/answered
                        Log.i(TAG, "✓✓ Call #$callIndex CONNECTED!")
                        RemoteLogger.i(applicationContext, TAG, "✓✓ Call #$callIndex CONNECTED!")
                        StatusManager.log("✓ Call #$callIndex connected!")

                        // If this is the first call connecting, notify CallManager
                        if (callIndex == 1 && !isFirstCallConnected) {
                            isFirstCallConnected = true
                            Log.i(TAG, "First call connected - triggering callback for second call")
                            RemoteLogger.i(applicationContext, TAG, "First call connected - ready for second call")

                            // Notify CallManager that first call is connected
                            handler.post {
                                onFirstCallConnected?.invoke()
                            }
                        }
                    }
                    Call.STATE_DISCONNECTED -> {
                        Log.i(TAG, "Call #$callIndex disconnected")
                        StatusManager.log("Call #$callIndex ended")
                    }
                    Call.STATE_DIALING -> {
                        StatusManager.log("Call #$callIndex dialing...")
                    }
                    Call.STATE_RINGING -> {
                        StatusManager.log("Call #$callIndex ringing...")
                    }
                    Call.STATE_HOLDING -> {
                        StatusManager.log("Call #$callIndex on hold")
                    }
                }
            }
        }
    }

    private fun getStateName(state: Int): String {
        return when (state) {
            Call.STATE_NEW -> "NEW"
            Call.STATE_DIALING -> "DIALING"
            Call.STATE_RINGING -> "RINGING"
            Call.STATE_HOLDING -> "HOLDING"
            Call.STATE_ACTIVE -> "ACTIVE/CONNECTED"
            Call.STATE_DISCONNECTED -> "DISCONNECTED"
            Call.STATE_CONNECTING -> "CONNECTING"
            Call.STATE_DISCONNECTING -> "DISCONNECTING"
            Call.STATE_SELECT_PHONE_ACCOUNT -> "SELECT_PHONE_ACCOUNT"
            Call.STATE_PULLING_CALL -> "PULLING_CALL"
            else -> "UNKNOWN($state)"
        }
    }

    private fun launchInCallUI(call: Call) {
        try {
            val phoneNumber = call.details?.handle?.schemeSpecificPart ?: "Unknown"

            Log.i(TAG, "Launching InCallActivity for: $phoneNumber")
            RemoteLogger.i(applicationContext, TAG, "Launching InCallActivity for: $phoneNumber")

            val intent = Intent(this, InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra("phone_number", phoneNumber)
                putExtra("contact_name", phoneNumber)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch InCallActivity: ${e.message}")
            RemoteLogger.e(applicationContext, TAG, "Failed to launch InCallActivity: ${e.message}")
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)

        // Unregister callback
        callCallbacks[call]?.let { callback ->
            call.unregisterCallback(callback)
        }
        callCallbacks.remove(call)
        callStates.remove(call)

        activeCalls.remove(call)
        Log.i(TAG, "Call removed - Total active calls: ${activeCalls.size}")
        RemoteLogger.i(applicationContext, TAG, "Call removed - Total active calls: ${activeCalls.size}")

        if (call == currentCall) {
            currentCall = activeCalls.lastOrNull()
        }

        // If no more calls, clear state
        if (activeCalls.isEmpty()) {
            currentCall = null
            isFirstCallConnected = false
            StatusManager.log("All calls ended")
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Unregister all callbacks
        callCallbacks.forEach { (call, callback) ->
            try {
                call.unregisterCallback(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering callback: ${e.message}")
            }
        }
        callCallbacks.clear()
        callStates.clear()

        activeCalls.clear()
        currentCall = null
        instance = null
        isFirstCallConnected = false
        Log.i(TAG, "InCallService destroyed")
    }
}
