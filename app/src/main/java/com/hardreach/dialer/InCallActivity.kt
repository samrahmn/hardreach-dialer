package com.hardreach.dialer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.TelecomManager
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class InCallActivity : AppCompatActivity() {

    private lateinit var contactName: TextView
    private lateinit var phoneNumber: TextView
    private lateinit var callStatus: TextView
    private lateinit var callDuration: TextView

    private lateinit var btnMute: FloatingActionButton
    private lateinit var btnSpeaker: FloatingActionButton
    private lateinit var btnDialpad: FloatingActionButton
    private lateinit var btnHold: FloatingActionButton
    private lateinit var btnAddCall: FloatingActionButton
    private lateinit var btnEndCall: FloatingActionButton
    private lateinit var btnRecord: FloatingActionButton

    private lateinit var labelMute: TextView
    private lateinit var labelSpeaker: TextView
    private lateinit var labelHold: TextView
    private lateinit var labelRecord: TextView

    private lateinit var dialpadOverlay: LinearLayout

    private var isMuted = false
    private var isSpeakerOn = false
    private var isOnHold = false
    private var isRecording = false

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null

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

        // Start call duration timer
        startCallDurationTimer()
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
        btnAddCall = findViewById(R.id.btn_add_call)
        btnEndCall = findViewById(R.id.btn_end_call)

        labelMute = findViewById(R.id.label_mute)
        labelSpeaker = findViewById(R.id.label_speaker)
        labelHold = findViewById(R.id.label_hold)

        dialpadOverlay = findViewById(R.id.in_call_dialpad_overlay)

        // Add record button dynamically
        val recordContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = android.view.Gravity.CENTER
        }

        btnRecord = FloatingActionButton(this).apply {
            id = View.generateViewId()
            setImageResource(android.R.drawable.ic_btn_speak_now)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF424242.toInt())
            imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
        }

        labelRecord = TextView(this).apply {
            text = "Record"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8
            }
        }

        recordContainer.addView(btnRecord)
        recordContainer.addView(labelRecord)

        // Add to first row of buttons
        val firstRow = findViewById<LinearLayout>(R.id.btn_mute_container).parent as LinearLayout
        firstRow.addView(recordContainer, 0)
    }

    private fun setupButtons() {
        btnMute.setOnClickListener { toggleMute() }
        btnSpeaker.setOnClickListener { toggleSpeaker() }
        btnDialpad.setOnClickListener { showDialpad() }
        btnHold.setOnClickListener { toggleHold() }
        btnAddCall.setOnClickListener { addCall() }
        btnEndCall.setOnClickListener { endCall() }
        btnRecord.setOnClickListener { toggleRecording() }

        findViewById<Button>(R.id.btn_close_dialpad).setOnClickListener {
            dialpadOverlay.visibility = View.GONE
        }

        // Setup in-call dialpad buttons
        setupInCallDialpad()
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

    private fun addCall() {
        Toast.makeText(this, "Add call - Coming soon", Toast.LENGTH_SHORT).show()
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
        if (isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 200)
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 201)
            return
        }

        try {
            val recordingsDir = File(getExternalFilesDir(null), "CallRecordings")
            if (!recordingsDir.exists()) {
                recordingsDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val number = phoneNumber.text.toString().replace("+", "").replace(" ", "")
            recordingFile = File(recordingsDir, "REC_${number}_${timestamp}.m4a")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(recordingFile!!.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            btnRecord.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFF44336.toInt())
            labelRecord.text = "Stop Rec"
            callStatus.text = "Recording..."

            Toast.makeText(this, "🔴 Recording started", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Recording failed: ${e.message}", Toast.LENGTH_LONG).show()
            android.util.Log.e("InCallActivity", "Recording error: ${e.message}")
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            isRecording = false
            btnRecord.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF424242.toInt())
            labelRecord.text = "Record"
            callStatus.text = if (isOnHold) "On Hold" else "Active"

            val filePath = recordingFile?.absolutePath ?: "unknown"
            Toast.makeText(this, "✅ Recording saved:\n$filePath", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error stopping recording: ${e.message}", Toast.LENGTH_LONG).show()
            android.util.Log.e("InCallActivity", "Stop recording error: ${e.message}")
        }
    }

    private fun endCall() {
        if (isRecording) {
            stopRecording()
        }

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

        if (isRecording) {
            stopRecording()
        }
    }
}
