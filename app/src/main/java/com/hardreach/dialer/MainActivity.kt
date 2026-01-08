package com.hardreach.dialer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    
    private val PERMISSIONS_REQUEST_CODE = 100

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
    }
    
    override fun onResume() {
        super.onResume()
        updateUI()
    }
    
    private fun initializeViews() {
        serverUrlInput = findViewById(R.id.server_url)
        apiKeyInput = findViewById(R.id.api_key)
        serviceSwitch = findViewById(R.id.service_switch)
        saveButton = findViewById(R.id.save_button)
        testPollButton = findViewById(R.id.test_poll_button)
        statusText = findViewById(R.id.status_text)
        logText = findViewById(R.id.log_text)
        
        saveButton.setOnClickListener { saveSettings() }
        testPollButton.setOnClickListener { testPoll() }
        serviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) startWebhookService() else stopWebhookService()
        }
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
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.POST_NOTIFICATIONS
        )
        
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSIONS_REQUEST_CODE)
        }
    }
    
    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }
}
