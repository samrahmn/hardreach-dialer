package com.hardreach.dialer

import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

/**
 * InCallService to properly access and merge active calls
 * This has proper permissions to manage calls on Android 9+
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

        // Launch InCallActivity when first call added
        if (activeCalls.size == 1 && call.state == Call.STATE_ACTIVE) {
            launchInCallUI(call)
        }

        // Try to merge if we have 2 active calls
        if (activeCalls.size == 2) {
            Log.i(TAG, "✓ 2 calls detected - checking if both answered...")
            RemoteLogger.i(applicationContext, TAG, "✓ 2 calls detected - checking if both answered...")
            StatusManager.log("2 calls detected - will merge when both answered")
            checkAndMergeCalls()
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

        // Launch InCallActivity when first call becomes active
        if (state == Call.STATE_ACTIVE && activeCalls.size == 1) {
            launchInCallUI(call)
        }

        // When second call becomes active, merge immediately
        if (state == Call.STATE_ACTIVE && activeCalls.size == 2) {
            val activeCount = activeCalls.count { it.state == Call.STATE_ACTIVE }
            if (activeCount == 2) {
                Log.i(TAG, "✓✓ BOTH CALLS ACTIVE - MERGING NOW!")
                RemoteLogger.i(applicationContext, TAG, "✓✓ BOTH CALLS ACTIVE - MERGING NOW!")
                mergeCalls()
            }
        }
    }

    private fun checkAndMergeCalls() {
        // Wait a moment for second call to become active
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (activeCalls.size >= 2) {
                val activeCount = activeCalls.count { it.state == Call.STATE_ACTIVE }
                Log.i(TAG, "Merge check: ${activeCount} calls are ACTIVE")

                if (activeCount >= 2) {
                    mergeCalls()
                } else {
                    Log.i(TAG, "Waiting for both calls to be active...")
                    // Try again in 2 seconds
                    checkAndMergeCalls()
                }
            }
        }, 2000)
    }

    private fun mergeCalls() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (activeCalls.size >= 2) {
                    val call1 = activeCalls[0]
                    val call2 = activeCalls[1]

                    // Method 1: Use conference() method
                    call1.conference(call2)
                    Log.i(TAG, "✅ CONFERENCE CREATED using call.conference()")
                    RemoteLogger.i(applicationContext, TAG, "✅ CONFERENCE CREATED using call.conference()")

                    // Auto-mute microphone so dialer's voice is not heard
                    autoMuteMicrophone()

                    // Alternative: Create parent call and add children
                    // This might work better on some devices
                    if (call1.parent == null && call2.parent == null) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (call1.parent == null) {
                                Log.w(TAG, "Conference not created yet, trying alternative method...")
                                RemoteLogger.w(applicationContext, TAG, "Conference not created yet, trying alternative method...")
                                // Some devices need explicit conference request
                                call1.conference(call2)
                            } else {
                                RemoteLogger.i(applicationContext, TAG, "✅ Conference parent created successfully")
                            }
                            // Auto-mute again to ensure it's applied
                            autoMuteMicrophone()
                        }, 1000)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to merge calls: ${e.message}")
            RemoteLogger.e(applicationContext, TAG, "❌ Failed to merge calls: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun autoMuteMicrophone() {
        try {
            // Mute the microphone so dialer's voice is not heard by other participants
            setMuted(true)
            Log.i(TAG, "🔇 MICROPHONE AUTO-MUTED - your voice will not be heard")
            RemoteLogger.i(applicationContext, TAG, "🔇 MICROPHONE AUTO-MUTED - you are silent, team member and prospect can only hear each other")
            StatusManager.conferenceCreated()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to mute microphone: ${e.message}")
            RemoteLogger.e(applicationContext, TAG, "❌ Failed to mute microphone: ${e.message}")
        }
    }

    private fun launchInCallUI(call: Call) {
        try {
            val intent = android.content.Intent(applicationContext, InCallActivity::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP

            // Get phone number from call
            val phoneNumber = call.details?.handle?.schemeSpecificPart ?: "Unknown"
            intent.putExtra("phone_number", phoneNumber)
            intent.putExtra("contact_name", "Unknown") // TODO: Lookup contact name

            applicationContext.startActivity(intent)
            Log.i(TAG, "✓ Launched InCallActivity for $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch InCallActivity: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activeCalls.clear()
        Log.i(TAG, "InCallService destroyed")
    }
}
