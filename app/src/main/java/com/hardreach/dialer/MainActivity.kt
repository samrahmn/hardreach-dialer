package com.hardreach.dialer

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
    private lateinit var batteryOptimizationButton: Button
    private lateinit var accessibilityServiceButton: Button
    private lateinit var statusText: TextView
    private lateinit var liveStatusText: TextView
    private lateinit var logText: TextView

    private val PERMISSIONS_REQUEST_CODE = 100
    private val DEFAULT_DIALER_REQUEST_CODE = 200
    private val BATTERY_OPTIMIZATION_REQUEST_CODE = 300

    private fun cleanApiKey(key: String): String {
        return key.replace("\\s".toRegex(), "")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        loadSettings()
        requestPermissions()
        updateUI()
        observeStatusUpdates()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        updateBatteryOptimizationStatus()
        updateAccessibilityServiceStatus()

        // Auto-restart service if it was enabled but isn't running
        val prefs = getSharedPreferences("hardreach_dialer", MODE_PRIVATE)
        val shouldBeEnabled = prefs.getBoolean("service_enabled", false)
        if (shouldBeEnabled && !WebhookService.isRunning) {
            android.util.Log.w("MainActivity", "Service should be running but isn't - restarting")
            startWebhookService()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            BATTERY_OPTIMIZATION_REQUEST_CODE -> {
                updateBatteryOptimizationStatus()
                val powerManager = getSystemService(PowerManager::class.java)
                val isIgnoring = powerManager?.isIgnoringBatteryOptimizations(packageName) ?: false
                if (isIgnoring) {
                    Toast.makeText(this, "✅ Battery optimization disabled!", Toast.LENGTH_LONG).show()
                    logText.text = "Battery optimization disabled ✅\nService can now run in background"
                } else {
                    Toast.makeText(this, "⚠️ Battery optimization still enabled", Toast.LENGTH_LONG).show()
                    logText.text = "Warning: Battery optimization still enabled\nService may be killed"
                }
            }
            DEFAULT_DIALER_REQUEST_CODE -> {
                val telecomManager = getSystemService(TelecomManager::class.java)
                val isDefaultDialer = packageName == telecomManager?.defaultDialerPackage
                if (isDefaultDialer) {
                    Toast.makeText(this, "✅ Hardreach is now default dialer!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Observe StateFlow updates from StatusManager
     * Lifecycle-aware - automatically stops when Activity is destroyed
     */
    private fun observeStatusUpdates() {
        // Collect status updates
        lifecycleScope.launch {
            StatusManager.currentStatus.collect { status ->
                liveStatusText.text = status
            }
        }

        // Collect log entries
        lifecycleScope.launch {
            StatusManager.logEntries.collect { logs ->
                logText.text = logs.joinToString("\n")
            }
        }
    }
    
    private fun initializeViews() {
        serverUrlInput = findViewById(R.id.server_url)
        apiKeyInput = findViewById(R.id.api_key)
        serviceSwitch = findViewById(R.id.service_switch)
        saveButton = findViewById(R.id.save_button)
        testPollButton = findViewById(R.id.test_poll_button)
        requestDefaultDialerButton = findViewById(R.id.request_default_dialer_button)
        batteryOptimizationButton = findViewById(R.id.battery_optimization_button)
        statusText = findViewById(R.id.status_text)
        liveStatusText = findViewById(R.id.live_status_text)
        logText = findViewById(R.id.log_text)

        saveButton.setOnClickListener { saveSettings() }
        testPollButton.setOnClickListener { testPoll() }
        requestDefaultDialerButton.setOnClickListener { requestDefaultDialer() }
        batteryOptimizationButton.setOnClickListener { requestBatteryOptimization() }
        accessibilityServiceButton.setOnClickListener { requestAccessibilityService() }
        serviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) startWebhookService() else stopWebhookService()
        }

        // Initialize with default status
        liveStatusText.text = "Waiting for calls..."
        logText.text = "Logs will appear here as calls are made..."

        // Check and update button states
        updateBatteryOptimizationStatus()
        updateAccessibilityServiceStatus()
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

        // Check exact alarm permission on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.app.AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(this, "Exact alarm permission required", Toast.LENGTH_LONG).show()
                // Request permission
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
                serviceSwitch.isChecked = false
                return
            }
        }

        // Start alarm-based polling
        AlarmScheduler.schedulePolling(this)
        Toast.makeText(this, "Polling enabled via AlarmManager", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun stopWebhookService() {
        // Cancel alarm-based polling
        AlarmScheduler.cancelPolling(this)
        // Stop any running service instance
        stopService(Intent(this, WebhookService::class.java))
        Toast.makeText(this, "Polling disabled", Toast.LENGTH_SHORT).show()
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

        // DO NOT auto-request dialer role - only when user clicks button
        // This prevents infinite popup loop
    }

    private fun requestDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val isDefaultDialer = packageName == telecomManager.defaultDialerPackage

            logText.text = "Current default dialer: ${telecomManager.defaultDialerPackage}\n" +
                          "Is Hardreach default: $isDefaultDialer"

            RemoteLogger.i(this, "MainActivity", "Current default dialer: ${telecomManager.defaultDialerPackage}")
            RemoteLogger.i(this, "MainActivity", "Is Hardreach default: $isDefaultDialer")

            if (!isDefaultDialer) {
                try {
                    val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                        putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                    }

                    // Verify the intent can be resolved
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivityForResult(intent, DEFAULT_DIALER_REQUEST_CODE)
                        RemoteLogger.i(this, "MainActivity", "✓ Launched default dialer selection dialog")
                        Toast.makeText(this, "Select Hardreach Dialer from the list", Toast.LENGTH_LONG).show()
                    } else {
                        // Fallback: Open default apps settings
                        val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                        startActivity(settingsIntent)
                        Toast.makeText(this, "Go to Phone app → Select Hardreach Dialer", Toast.LENGTH_LONG).show()
                        RemoteLogger.w(this, "MainActivity", "Intent not resolvable, opened settings instead")
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Please set manually: Settings → Apps → Default apps → Phone app", Toast.LENGTH_LONG).show()
                    RemoteLogger.e(this, "MainActivity", "❌ Error requesting default dialer: ${e.message}")
                }
            } else {
                Toast.makeText(this, "✅ Hardreach is already the default dialer!", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Requires Android 6.0 or higher", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(PowerManager::class.java)
            val isIgnoring = powerManager?.isIgnoringBatteryOptimizations(packageName) ?: false

            if (isIgnoring) {
                Toast.makeText(this, "✅ Battery optimization already disabled", Toast.LENGTH_LONG).show()
                logText.text = "Battery optimization: Already disabled ✅"
            } else {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivityForResult(intent, BATTERY_OPTIMIZATION_REQUEST_CODE)
                    Toast.makeText(this, "Please allow unrestricted battery usage", Toast.LENGTH_LONG).show()
                    RemoteLogger.i(this, "MainActivity", "Requesting battery optimization exemption")
                } catch (e: Exception) {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    RemoteLogger.e(this, "MainActivity", "❌ Battery optimization request failed: ${e.message}")
                }
            }
        } else {
            Toast.makeText(this, "Not needed on Android 5 and below", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateBatteryOptimizationStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(PowerManager::class.java)
            val isIgnoring = powerManager?.isIgnoringBatteryOptimizations(packageName) ?: false

            batteryOptimizationButton.text = if (isIgnoring) {
                "BATTERY: UNRESTRICTED ✅"
            } else {
                "DISABLE BATTERY OPTIMIZATION"
            }

            batteryOptimizationButton.backgroundTintList = if (isIgnoring) {
                android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
            } else {
                android.content.res.ColorStateList.valueOf(0xFFFF9800.toInt())
            }
        }
    }

    private fun requestAccessibilityService() {
        val isEnabled = isAccessibilityServiceEnabled()

        if (isEnabled) {
            Toast.makeText(this, "✅ Accessibility service already enabled", Toast.LENGTH_LONG).show()
            logText.text = "Accessibility service: Enabled ✅\nAuto-merge will work!"
        } else {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
                Toast.makeText(this, "Enable 'Hardreach Dialer' accessibility service", Toast.LENGTH_LONG).show()
                RemoteLogger.i(this, "MainActivity", "Opening accessibility settings")
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                RemoteLogger.e(this, "MainActivity", "❌ Failed to open accessibility settings: ${e.message}")
            }
        }
    }

    private fun updateAccessibilityServiceStatus() {
        val isEnabled = isAccessibilityServiceEnabled()

        accessibilityServiceButton.text = if (isEnabled) {
            "ACCESSIBILITY: ENABLED ✅"
        } else {
            "ENABLE ACCESSIBILITY SERVICE"
        }

        accessibilityServiceButton.backgroundTintList = if (isEnabled) {
            android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
        } else {
            android.content.res.ColorStateList.valueOf(0xFFFF9800.toInt())
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, CallMergeAccessibilityService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)

        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = ComponentName.unflattenFromString(componentNameString)

            if (enabledService != null && enabledService == expectedComponentName) {
                return true
            }
        }
        return false
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
