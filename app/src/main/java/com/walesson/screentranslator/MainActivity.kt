package com.walesson.screentranslator

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView

private const val PREFS_NAME = "screen_translator_prefs"
private const val KEY_SHORTCUT_REQUESTED = "shortcut_requested"
private const val SHORTCUT_ID = "main_shortcut"

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    private val projectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, BubbleService::class.java).apply {
                action = BubbleService.ACTION_START_WITH_PROJECTION
                putExtra(BubbleService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(BubbleService.EXTRA_RESULT_DATA, result.data)
            }
            startForegroundService(intent)
        }
    }

    /**
     * The notification permission is best-effort: whatever the user answers, we continue with
     * the capture flow (a missing notification only degrades the visible service indicator).
     */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        launchCaptureFlow()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)

        findViewById<Button>(R.id.grantPermissionButton).setOnClickListener {
            requestOverlayPermission()
        }

        findViewById<Button>(R.id.startBubbleButton).setOnClickListener {
            if (hasOverlayPermission()) {
                startWithNotificationPermission()
            } else {
                requestOverlayPermission()
            }
        }

        maybeRequestPinnedShortcut()
    }

    /**
     * Asks the system, once per install, to pin a home-screen shortcut for the app. Android
     * requires an explicit user confirmation dialog for this (silent home-screen icon
     * placement is blocked since API 26) — this just triggers that one-time prompt.
     */
    private fun maybeRequestPinnedShortcut() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SHORTCUT_REQUESTED, false)) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val shortcutManager = getSystemService(ShortcutManager::class.java)
        if (shortcutManager?.isRequestPinShortcutSupported != true) return

        val shortcutIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
        }
        val shortcut = ShortcutInfo.Builder(this, SHORTCUT_ID)
            .setShortLabel(getString(R.string.app_name))
            .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(shortcutIntent)
            .build()

        shortcutManager.requestPinShortcut(shortcut, null)
        prefs.edit().putBoolean(KEY_SHORTCUT_REQUESTED, true).apply()
    }

    private fun startWithNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        launchCaptureFlow()
    }

    private fun launchCaptureFlow() {
        captureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        statusText.text = if (hasOverlayPermission()) {
            getString(R.string.permission_granted_ready)
        } else {
            getString(R.string.permission_required)
        }
    }

    private fun hasOverlayPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}
