package com.hardreach.dialer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.*
import java.io.IOException
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var serverUrlInput: EditText
    private lateinit var apiKeyInput: EditText
    private lateinit var serviceSwitch: Switch
    private lateinit var saveButton: Button
    private lateinit var testPollButton: Button
    private lateinit var requestDefaultDialerButton: Button
    private lateinit var statusText: TextView
    private lateinit var liveStatusText: TextView
    private lateinit var logText: TextView

    private val PERMISSIONS_REQUEST_CODE = 100
    private val DEFAULT_DIALER_REQUEST_CODE = 200

    private val logLines = mutableListOf<String>()
    private val MAX_LOG_LINES = 50

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                LiveStatusLogger.ACTION_STATUS_UPDATE -> {
                    val status = intent.getStringExtra(LiveStatusLogger.EXTRA_STATUS)
                    status?.let { updateLiveStatus(it) }
                }
                LiveStatusLogger.ACTION_LOG_UPDATE -> {
                    val log = intent.getStringExtra(LiveStatusLogger.EXTRA_LOG)
                    log?.let { addLogLine(it) }
                }
            }
        }
    }

    private fun cleanApiKey(key: String): String {
        return key.replace("\\s".toRegex(), "")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        loadSettings()
        requestPermissions()
        // registerPhoneAccount() - NOT NEEDED! InCallService binds automatically as default dialer
        updateUI()
    }
    
    override fun onResume() {
        super.onResume()
        updateUI()
        registerStatusReceiver()
    }

    override fun onPause() {
        super.onPause()
        unregisterStatusReceiver()
    }

    private fun registerStatusReceiver() {
        val filter = IntentFilter().apply {
            addAction(LiveStatusLogger.ACTION_STATUS_UPDATE)
            addAction(LiveStatusLogger.ACTION_LOG_UPDATE)
        }
        registerReceiver(statusReceiver, filter)
    }

    private fun unregisterStatusReceiver() {
        try {
            unregisterReceiver(statusReceiver)
        } catch (e: Exception) {
            // Receiver not registered, ignore
        }
    }

    private fun updateLiveStatus(status: String) {
        runOnUiThread {
            liveStatusText.text = status
        }
    }

    private fun addLogLine(log: String) {
        runOnUiThread {
            logLines.add(0, log) // Add to beginning
            if (logLines.size > MAX_LOG_LINES) {
                logLines.removeAt(logLines.size - 1) // Remove oldest
            }
            logText.text = logLines.joinToString("\n")
        }
    }
    
    private fun initializeViews() {
        serverUrlInput = findViewById(R.id.server_url)
        apiKeyInput = findViewById(R.id.api_key)
        serviceSwitch = findViewById(R.id.service_switch)
        saveButton = findViewById(R.id.save_button)
        testPollButton = findViewById(R.id.test_poll_button)
        requestDefaultDialerButton = findViewById(R.id.request_default_dialer_button)
        statusText = findViewById(R.id.status_text)
        liveStatusText = findViewById(R.id.live_status_text)
        logText = findViewById(R.id.log_text)

        saveButton.setOnClickListener { saveSettings() }
        testPollButton.setOnClickListener { testPoll() }
        requestDefaultDialerButton.setOnClickListener { requestDefaultDialer() }
        serviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) startWebhookService() else stopWebhookService()
        }

        // Initialize with default status
        liveStatusText.text = "Waiting for calls..."
        logText.text = "Logs will appear here as calls are made..."
    }
    
    private fun testPoll() {
        val serverUrl = serverUrlInput.text.toString().trim()
        val apiKey = cleanApiKey(apiKeyInput.text.toString())

        if (serverUrl.isEmpty() || apiKey.isEmpty()) {
            Toast.makeText(this, "Enter URL and API Key first", Toast.LENGTH_SHORT).show()
            return
        }

        logText.text = "Testing poll...\nURL: $serverUrl/api/dialer/pending-calls\nAPI Key: ${apiKey.take(10)}..."

        thread {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("$serverUrl/api/dialer/pending-calls")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: "No body"
                
                runOnUiThread {
                    logText.text = "Response [${response.code}]:\n$body"
                    if (response.isSuccessful) {
                        Toast.makeText(this, "✅ Poll successful!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "❌ Poll failed: ${response.code}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    logText.text = "Error:\n${e.message}"
                    Toast.makeText(this, "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun loadSettings() {
        val prefs = getSharedPreferences("hardreach_dialer", MODE_PRIVATE)
        serverUrlInput.setText(prefs.getString("server_url", "https://grow.hardreach.com"))
        apiKeyInput.setText(prefs.getString("api_key", ""))
        serviceSwitch.isChecked = prefs.getBoolean("service_enabled", false)
    }
    
    private fun saveSettings() {
        val prefs = getSharedPreferences("hardreach_dialer", MODE_PRIVATE)
        prefs.edit().apply {
            putString("server_url", serverUrlInput.text.toString().trim())
            putString("api_key", cleanApiKey(apiKeyInput.text.toString()))
            putBoolean("service_enabled", serviceSwitch.isChecked)
            apply()
        }
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        updateUI()
    }
    
    private fun startWebhookService() {
        if (!hasRequiredPermissions()) {
            Toast.makeText(this, "Please grant all permissions", Toast.LENGTH_LONG).show()
            serviceSwitch.isChecked = false
            return
        }
        
        val intent = Intent(this, WebhookService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateUI()
    }
    
    private fun stopWebhookService() {
        stopService(Intent(this, WebhookService::class.java))
        updateUI()
    }
    
    private fun updateUI() {
        val isRunning = WebhookService.isRunning
        statusText.text = if (isRunning) "Status: Running ✅" else "Status: Stopped ⭕"
    }
    
    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.POST_NOTIFICATIONS
        )

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSIONS_REQUEST_CODE)
        }

        // Also request dialer role for Android 10+
        requestDialerRole()
    }

    private fun requestDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = getSystemService(android.app.role.RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_DIALER)) {
                    if (!roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)) {
                        val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER)
                        startActivityForResult(intent, 999)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error requesting dialer role: ${e.message}")
            }
        }
    }

    private fun requestDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val telecomManager = getSystemService(TelecomManager::class.java)
            val isDefaultDialer = packageName == telecomManager?.defaultDialerPackage

            logText.text = "Current default dialer: ${telecomManager?.defaultDialerPackage}\n" +
                          "Is Hardreach default: $isDefaultDialer"

            RemoteLogger.i(this, "MainActivity", "Current default dialer: ${telecomManager?.defaultDialerPackage}")
            RemoteLogger.i(this, "MainActivity", "Is Hardreach default: $isDefaultDialer")

            if (!isDefaultDialer) {
                // Try BOTH methods - TelecomManager (older but might work on Honor) AND RoleManager
                try {
                    // Method 1: TelecomManager intent (works on all Android 6+)
                    val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                    intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                    startActivityForResult(intent, DEFAULT_DIALER_REQUEST_CODE)

                    RemoteLogger.i(this, "MainActivity", "✓ Launched TelecomManager.ACTION_CHANGE_DEFAULT_DIALER intent")
                    Toast.makeText(this, "Please select Hardreach Dialer from the list", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    RemoteLogger.e(this, "MainActivity", "❌ Error requesting default dialer: ${e.message}")
                }
            } else {
                Toast.makeText(this, "Hardreach is already the default dialer!", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Requires Android 6.0 or higher", Toast.LENGTH_SHORT).show()
        }
    }

    private fun registerPhoneAccount() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val telecomManager = getSystemService(TelecomManager::class.java)
                val componentName = ComponentName(this, HardreachConnectionService::class.java)
                val phoneAccountHandle = PhoneAccountHandle(componentName, "HardreachDialer")

                // UNREGISTER our PhoneAccount so calls use SIM instead
                telecomManager?.unregisterPhoneAccount(phoneAccountHandle)

                android.util.Log.i("MainActivity", "✅ PhoneAccount UNREGISTERED - calls will use SIM")
                RemoteLogger.i(this, "MainActivity", "✅ PhoneAccount unregistered - using SIM for calls")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error unregistering PhoneAccount: ${e.message}")
                RemoteLogger.e(this, "MainActivity", "❌ Error unregistering PhoneAccount: ${e.message}")
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }
}
