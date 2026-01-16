package com.hardreach.dialer

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Main dialer activity with bottom navigation
 * Uses fragments for Keypad and Recents
 */
class DialerActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var btnSettings: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialer)

        bottomNavigation = findViewById(R.id.bottom_navigation)
        btnSettings = findViewById(R.id.btn_settings)

        // Setup bottom navigation
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_keypad -> {
                    showFragment(KeypadFragment())
                    true
                }
                R.id.nav_recents -> {
                    showFragment(RecentsFragment())
                    true
                }
                else -> false
            }
        }

        // Setup settings button
        btnSettings.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        // Show keypad fragment by default
        if (savedInstanceState == null) {
            showFragment(KeypadFragment())
            bottomNavigation.selectedItemId = R.id.nav_keypad
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}

