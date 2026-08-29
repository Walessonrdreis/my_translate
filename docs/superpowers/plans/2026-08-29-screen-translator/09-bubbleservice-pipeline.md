### Task 9: Wire full pipeline in `BubbleService` + foreground notification

**Files:**
- Modify: `app/src/main/java/com/walesson/screentranslator/BubbleService.kt`

**Interfaces:**
- Consumes: `ScreenCaptureManager` (Task 5), `TextRecognitionManager` (Task 3), `TranslationManager` + `TranslatorFactory` (Task 4), `TranslationOverlayView` (Task 7), `BubbleService.onBubbleTapped`/`setLoading` (Task 6), `ACTION_START_WITH_PROJECTION`/`EXTRA_RESULT_CODE`/`EXTRA_RESULT_DATA` (Task 8).
- Produces: a working end-to-end app. No new public interfaces — this is the integration point.

- [ ] **Step 1: Rewrite `BubbleService.kt` to wire the full pipeline**

Merge this into the class from Tasks 6 and 8 — keep `bubbleView`, `layoutParams`, `handleTouch`, `overlayType()`, `addBubble()`, `setLoading()`, drag-tracking fields, and the `companion object` constants; add everything below.

```kotlin
package com.walesson.screentranslator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.graphics.PixelFormat
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.walesson.screentranslator.capture.ScreenCaptureManager
import com.walesson.screentranslator.ocr.TextRecognitionManager
import com.walesson.screentranslator.overlay.TranslationOverlayView
import com.walesson.screentranslator.translate.TranslationManager
import com.walesson.screentranslator.translate.TranslatorFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Add inside the existing BubbleService class body:

private const val CHANNEL_ID = "screen_translator_channel"
private const val NOTIFICATION_ID = 1

private val scope = CoroutineScope(Dispatchers.Main)
private var captureManager: ScreenCaptureManager? = null
private lateinit var ocrManager: TextRecognitionManager
private lateinit var translationManager: TranslationManager
private var overlayView: TranslationOverlayView? = null

// Extend the existing onCreate() (from Task 6) by appending these two
// initializations and the tap wiring, after windowManager and addBubble():
//   ocrManager = TextRecognitionManager(TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS))
//   translationManager = TranslationManager(TranslatorFactory.createEnglishToPortuguese())
//   onBubbleTapped = { onBubbleTap() }

override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_START_WITH_PROJECTION) {
        startForeground(NOTIFICATION_ID, buildNotification())
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (resultData != null) {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(resultCode, resultData)
            val metrics = resources.displayMetrics
            captureManager = ScreenCaptureManager(projection) { w, h, dpi ->
                android.media.ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            }.also {
                it.start(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
            }
        }
    }
    return START_STICKY
}

private fun onBubbleTap() {
    val capture = captureManager ?: return
    setLoading(true)
    scope.launch {
        val bitmap = capture.captureFrame()
        if (bitmap == null) { setLoading(false); return@launch }
        val blocks = ocrManager.recognize(bitmap)
        if (blocks.isEmpty()) { setLoading(false); return@launch }
        translationManager.ensureModelDownloaded().onFailure {
            setLoading(false); return@launch
        }
        val translated = translationManager.translateAll(blocks)
        showOverlay(translated)
        setLoading(false)
    }
}

private fun showOverlay(blocks: List<com.walesson.screentranslator.ocr.TextBlock>) {
    removeOverlay()
    val view = TranslationOverlayView(this).apply {
        onDismissRequested = { removeOverlay() }
        show(blocks)
    }
    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        overlayType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    )
    windowManager.addView(view, params)
    overlayView = view
}

private fun removeOverlay() {
    overlayView?.let { windowManager.removeView(it) }
    overlayView = null
}

private fun buildNotification(): Notification {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID, "Screen Translator", NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }
    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Screen Translator ativo")
        .setSmallIcon(R.drawable.ic_bubble)
        .setOngoing(true)
        .build()
}

// Extend the existing onDestroy() (from Task 6) by prepending:
//   removeOverlay()
//   captureManager?.stop()
```

- [ ] **Step 2: Build the app**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run the full unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: all tests from Tasks 3, 4, 5, 7 PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/walesson/screentranslator/BubbleService.kt
git commit -m "feat: wire capture, OCR, translation and overlay pipeline"
```

Continue to `10-manual-verification.md` for the final manual end-to-end checklist.
