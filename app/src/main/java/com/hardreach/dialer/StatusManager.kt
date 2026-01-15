package com.hardreach.dialer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * Modern StateFlow-based status manager for real-time updates
 * Replaces BroadcastReceiver approach with lifecycle-aware reactive streams
 */
object StatusManager {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // StateFlow for current status (always has latest value)
    private val _currentStatus = MutableStateFlow("Waiting for calls...")
    val currentStatus: StateFlow<String> = _currentStatus.asStateFlow()

    // StateFlow for log entries (limited list)
    private val _logEntries = MutableStateFlow<List<String>>(emptyList())
    val logEntries: StateFlow<List<String>> = _logEntries.asStateFlow()

    private const val MAX_LOG_LINES = 50

    /**
     * Update the live status banner
     */
    fun updateStatus(status: String) {
        _currentStatus.value = status
    }

    /**
     * Add a log entry
     */
    fun log(message: String) {
        val timestamp = timeFormat.format(Date())
        val logEntry = "[$timestamp] $message"

        val currentLogs = _logEntries.value.toMutableList()
        currentLogs.add(0, logEntry) // Add to beginning
        if (currentLogs.size > MAX_LOG_LINES) {
            currentLogs.removeAt(currentLogs.size - 1) // Remove oldest
        }
        _logEntries.value = currentLogs
    }

    // Convenience methods
    fun callStarted(number: String) {
        updateStatus("📞 Calling $number...")
        log("Initiating call to $number")
    }

    fun callConnected(number: String) {
        updateStatus("✓ Connected to $number")
        log("Call connected to $number")
    }

    fun waitingForAnswer(seconds: Int) {
        updateStatus("⏳ Waiting ${seconds}s for answer...")
        log("Waiting ${seconds}s for call to be answered")
    }

    fun mergingCalls() {
        updateStatus("🔀 Merging calls into conference...")
        log("Attempting to merge calls")
    }

    fun conferenceCreated() {
        updateStatus("✅ Conference active - You are MUTED")
        log("✅ Conference created successfully - Microphone muted")
    }

    fun callFailed(reason: String) {
        updateStatus("❌ Call failed: $reason")
        log("Call failed: $reason")
    }

    fun idle() {
        updateStatus("Waiting for calls...")
        log("Ready for next call")
    }
}
