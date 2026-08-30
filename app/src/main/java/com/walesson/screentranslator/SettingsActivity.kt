package com.walesson.screentranslator

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var currentModeText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        currentModeText = findViewById(R.id.currentModeText)

        findViewById<Button>(R.id.openAccessibilitySettingsButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        val mode = TranslationMode.load(this)
        currentModeText.text = getString(
            if (mode == TranslationMode.CONTINUOUS) R.string.mode_continuous else R.string.mode_manual
        )
    }
}
