package com.hardreach.dialer

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class InCallActivity : AppCompatActivity() {

    private lateinit var contactName: TextView
    private lateinit var phoneNumber: TextView
    private lateinit var callStatus: TextView
    private lateinit var callDuration: TextView

    private lateinit var btnMute: ImageButton
    private lateinit var btnSpeaker: ImageButton
    private lateinit var btnDialpad: ImageButton
    private lateinit var btnHold: ImageButton
    private lateinit var btnEndCall: ImageButton
    private lateinit var btnRecord: ImageButton
    private lateinit var btnMerge: ImageButton

    private lateinit var labelMute: TextView
    private lateinit var labelSpeaker: TextView
    private lateinit var labelHold: TextView
    private lateinit var labelRecord: TextView

    private lateinit var dialpadOverlay: LinearLayout
    private lateinit var activeCallsContainer: LinearLayout
    private lateinit var activeCallsList: LinearLayout
    private lateinit var btnMergeContainer: LinearLayout
    private lateinit var btnMinimize: Button

    private var isMuted = true  // Auto-mute by default (prospect shouldn't hear third party)
    private var isSpeakerOn = false
    private var isOnHold = false
    private var isRecording = false

    // Recording features temporarily disabled for build testing
    // private var mediaRecorder: MediaRecorder? = null
    // private var recordingFile: File? = null

    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val handler = Handler(Looper.getMainLooper())
    private var callStartTime = 0L
    private var durationRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_in_call)

        initializeViews()
        setupButtons()

        // Get call info from intent
        val number = intent.getStringExtra("phone_number") ?: "Unknown"
        val name = intent.getStringExtra("contact_name") ?: "Unknown"

        phoneNumber.text = number
        contactName.text = name
        callStatus.text = "Calling..."

        // Auto-mute microphone by default (prospect shouldn't hear third party)
        applyAutoMute()

        // Start call duration timer
        startCallDurationTimer()
    }

    private fun applyAutoMute() {
        audioManager.isMicrophoneMute = true
        isMuted = true
        btnMute.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF2196F3.toInt())
        labelMute.text = "Unmute"
    }

    private fun initializeViews() {
        contactName = findViewById(R.id.contact_name)
        phoneNumber = findViewById(R.id.phone_number)
        callStatus = findViewById(R.id.call_status)
        callDuration = findViewById(R.id.call_duration)

        btnMute = findViewById(R.id.btn_mute)
        btnSpeaker = findViewById(R.id.btn_speaker)
        btnDialpad = findViewById(R.id.btn_dialpad)
        btnHold = findViewById(R.id.btn_hold)
        btnEndCall = findViewById(R.id.btn_end_call)
        btnRecord = findViewById(R.id.btn_record)
        btnMerge = findViewById(R.id.btn_merge)

        labelMute = findViewById(R.id.label_mute)
        labelSpeaker = findViewById(R.id.label_speaker)
        labelHold = findViewById(R.id.label_hold)
        labelRecord = findViewById(R.id.label_record)

        dialpadOverlay = findViewById(R.id.in_call_dialpad_overlay)
        activeCallsContainer = findViewById(R.id.active_calls_container)
        activeCallsList = findViewById(R.id.active_calls_list)
        btnMergeContainer = findViewById(R.id.btn_merge_container)
        btnMinimize = findViewById(R.id.btn_minimize)
    }

    private fun setupButtons() {
        btnMute.setOnClickListener { toggleMute() }
        btnSpeaker.setOnClickListener { toggleSpeaker() }
        btnDialpad.setOnClickListener { showDialpad() }
        btnHold.setOnClickListener { toggleHold() }
        btnEndCall.setOnClickListener { endCall() }
        btnRecord.setOnClickListener { toggleRecording() }
        btnMerge.setOnClickListener { manualMerge() }
        btnMinimize.setOnClickListener { minimizeToLogs() }

        findViewById<Button>(R.id.btn_close_dialpad).setOnClickListener {
            dialpadOverlay.visibility = View.GONE
        }

        // Setup in-call dialpad buttons
        setupInCallDialpad()

        // Monitor calls for conference state
        monitorActiveCalls()
    }

    private fun minimizeToLogs() {
        // Open MainActivity to view logs while keeping call active
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        // Don't finish - call stays active, can return via notification or app switcher
    }

    private fun setupInCallDialpad() {
        val dialpadButtons = mapOf(
            R.id.dialpad_btn_0 to "0",
            R.id.dialpad_btn_1 to "1",
            R.id.dialpad_btn_2 to "2",
            R.id.dialpad_btn_3 to "3",
            R.id.dialpad_btn_4 to "4",
            R.id.dialpad_btn_5 to "5",
            R.id.dialpad_btn_6 to "6",
            R.id.dialpad_btn_7 to "7",
            R.id.dialpad_btn_8 to "8",
            R.id.dialpad_btn_9 to "9",
            R.id.dialpad_btn_star to "*",
            R.id.dialpad_btn_hash to "#"
        )

        dialpadButtons.forEach { (id, digit) ->
            findViewById<Button>(id).setOnClickListener {
                sendDtmf(digit)
            }
        }
    }

    private fun toggleMute() {
        isMuted = !isMuted
        audioManager.isMicrophoneMute = isMuted

        if (isMuted) {
            btnMute.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF2196F3.toInt())
            labelMute.text = "Unmute"
        } else {
            btnMute.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF424242.toInt())
            labelMute.text = "Mute"
        }

        Toast.makeText(this, if (isMuted) "Muted" else "Unmuted", Toast.LENGTH_SHORT).show()
    }

    private fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        audioManager.isSpeakerphoneOn = isSpeakerOn

        if (isSpeakerOn) {
            btnSpeaker.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF2196F3.toInt())
            labelSpeaker.text = "Speaker Off"
        } else {
            btnSpeaker.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF424242.toInt())
            labelSpeaker.text = "Speaker"
        }

        Toast.makeText(this, if (isSpeakerOn) "Speaker ON" else "Speaker OFF", Toast.LENGTH_SHORT).show()
    }

    private fun showDialpad() {
        dialpadOverlay.visibility = View.VISIBLE
    }

    private fun toggleHold() {
        isOnHold = !isOnHold

        if (isOnHold) {
            btnHold.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFFA000.toInt())
            labelHold.text = "Resume"
            callStatus.text = "On Hold"
        } else {
            btnHold.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF424242.toInt())
            labelHold.text = "Hold"
            callStatus.text = "Active"
        }

        Toast.makeText(this, if (isOnHold) "Call on hold" else "Call resumed", Toast.LENGTH_SHORT).show()
    }

    private fun monitorActiveCalls() {
        // Check for multiple calls periodically
        val checkRunnable = object : Runnable {
            override fun run() {
                val activeCalls = HardreachInCallService.getActiveCalls()

                if (activeCalls.size >= 2) {
                    // Show merge button when 2+ calls
                    btnMergeContainer.visibility = View.VISIBLE
                    activeCallsContainer.visibility = View.VISIBLE
                    updateCallsList(activeCalls)
                    callStatus.text = "${activeCalls.size} calls active"
                } else if (activeCalls.size == 1) {
                    btnMergeContainer.visibility = View.GONE
                    activeCallsContainer.visibility = View.GONE
                    callStatus.text = "Active"
                } else {
                    // No calls - close activity
                    finish()
                    return
                }

                handler.postDelayed(this, 1000)
            }
        }
        handler.post(checkRunnable)
    }

    private fun updateCallsList(calls: List<*>) {
        activeCallsList.removeAllViews()

        if (calls.size > 1) {
            activeCallsContainer.visibility = View.VISIBLE

            calls.forEach { call ->
                val callItem = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 0, 0, 16)
                    }
                    setPadding(16, 12, 16, 12)
                    setBackgroundColor(0xFF333333.toInt())
                }

                val callInfo = TextView(this).apply {
                    text = "Call" // Will show number if available
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }

                val btnDisconnect = Button(this).apply {
                    text = "Disconnect"
                    setBackgroundColor(0xFFF44336.toInt())
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 12f
                    setPadding(16, 8, 16, 8)
                    setOnClickListener {
                        disconnectCall(call)
                    }
                }

                callItem.addView(callInfo)
                callItem.addView(btnDisconnect)
                activeCallsList.addView(callItem)
            }
        } else {
            activeCallsContainer.visibility = View.GONE
        }
    }

    private fun manualMerge() {
        Toast.makeText(this, "Merging calls...", Toast.LENGTH_SHORT).show()

        val merged = HardreachInCallService.mergeCalls()

        if (merged) {
            Toast.makeText(this, "✓ Calls merged!", Toast.LENGTH_SHORT).show()
            callStatus.text = "Conference"
            btnMergeContainer.visibility = View.GONE
        } else {
            Toast.makeText(this, "Merge failed - try phone's native merge button", Toast.LENGTH_LONG).show()
        }
    }

    private fun disconnectCall(call: Any?) {
        if (call == null) return

        try {
            // Disconnect individual call using reflection
            val disconnectMethod = call.javaClass.getMethod("disconnect")
            disconnectMethod.invoke(call)
            Toast.makeText(this, "Call disconnected", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to disconnect: ${e.message}", Toast.LENGTH_SHORT).show()
            android.util.Log.e("InCallActivity", "Error disconnecting call: ${e.message}")
        }
    }

    private fun sendDtmf(digit: String) {
        findViewById<TextView>(R.id.dialpad_number_display).append(digit)

        // Play DTMF tone
        val toneMap = mapOf(
            "1" to android.media.ToneGenerator.TONE_DTMF_1,
            "2" to android.media.ToneGenerator.TONE_DTMF_2,
            "3" to android.media.ToneGenerator.TONE_DTMF_3,
            "4" to android.media.ToneGenerator.TONE_DTMF_4,
            "5" to android.media.ToneGenerator.TONE_DTMF_5,
            "6" to android.media.ToneGenerator.TONE_DTMF_6,
            "7" to android.media.ToneGenerator.TONE_DTMF_7,
            "8" to android.media.ToneGenerator.TONE_DTMF_8,
            "9" to android.media.ToneGenerator.TONE_DTMF_9,
            "0" to android.media.ToneGenerator.TONE_DTMF_0,
            "*" to android.media.ToneGenerator.TONE_DTMF_S,
            "#" to android.media.ToneGenerator.TONE_DTMF_P
        )

        toneMap[digit]?.let { tone ->
            try {
                val toneGenerator = android.media.ToneGenerator(
                    android.media.AudioManager.STREAM_VOICE_CALL, 100
                )
                toneGenerator.startTone(tone, 150)
                handler.postDelayed({ toneGenerator.release() }, 200)
            } catch (e: Exception) {
                android.util.Log.e("InCallActivity", "Error playing DTMF: ${e.message}")
            }
        }
    }

    private fun toggleRecording() {
        Toast.makeText(this, "Recording feature - Coming soon", Toast.LENGTH_SHORT).show()
        // Recording temporarily disabled for build testing
    }

    // Recording functions temporarily disabled
    /*
    private fun startRecording() {
        // ... recording implementation
    }

    private fun stopRecording() {
        // ... stop recording implementation
    }
    */

    private fun endCall() {
        // End the call using TelecomManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecomManager.endCall()
        }

        finish()
    }

    private fun startCallDurationTimer() {
        callStartTime = System.currentTimeMillis()
        callDuration.visibility = View.VISIBLE

        durationRunnable = object : Runnable {
            override fun run() {
                val duration = (System.currentTimeMillis() - callStartTime) / 1000
                val minutes = duration / 60
                val seconds = duration % 60
                callDuration.text = String.format("%02d:%02d", minutes, seconds)
                handler.postDelayed(this, 1000)
            }
        }

        handler.post(durationRunnable!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        durationRunnable?.let { handler.removeCallbacks(it) }
    }
}
