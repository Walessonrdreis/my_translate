package com.walesson.screentranslator

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.walesson.screentranslator.capture.ScreenCaptureManager
import com.walesson.screentranslator.ocr.TextRecognitionManager
import com.walesson.screentranslator.overlay.TranslationOverlayView
import com.walesson.screentranslator.translate.TranslationManager
import com.walesson.screentranslator.translate.TranslatorFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CHANNEL_ID = "screen_translator_channel"
private const val NOTIFICATION_ID = 1
private const val BUBBLE_SIZE_DP = 56
private const val ICON_SIZE_DP = 28
private const val MAGNIFIER_SIZE_DP = 18
private const val ORBIT_RADIUS_DP = 22f
private const val ORBIT_DURATION_MS = 1200L

class BubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var magnifierIcon: ImageView? = null
    private var orbitAnimator: ValueAnimator? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    var onBubbleTapped: (() -> Unit)? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private val scope = CoroutineScope(Dispatchers.Main)
    private var captureManager: ScreenCaptureManager? = null
    private lateinit var ocrManager: TextRecognitionManager
    private lateinit var translationManager: TranslationManager
    private var recognizerClient: TextRecognizer? = null
    private var translatorClient: Translator? = null
    private var overlayView: TranslationOverlayView? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission not granted; stopping service.")
            stopSelf()
            return
        }
        addBubble()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizerClient = recognizer
        ocrManager = TextRecognitionManager(recognizer)
        val translator = TranslatorFactory.createEnglishToPortuguese()
        translatorClient = translator
        translationManager = TranslationManager(translator)
        onBubbleTapped = { onBubbleTap() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_WITH_PROJECTION) {
            startForeground(NOTIFICATION_ID, buildNotification())
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
            if (resultData != null) {
                val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val projection = projectionManager.getMediaProjection(resultCode, resultData)
                // API 34+ requires a registered callback before createVirtualDisplay().
                projection.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        Log.w(TAG, "MediaProjection stopped; tearing down capture.")
                        captureManager?.stop()
                        captureManager = null
                    }
                }, null)
                val (width, height) = displaySize()
                val dpi = resources.displayMetrics.densityDpi
                captureManager?.stop()
                captureManager = ScreenCaptureManager(projection) { w, h, _ ->
                    android.media.ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
                }.also {
                    it.start(width, height, dpi)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun displaySize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            return bounds.width() to bounds.height()
        }
        val metrics = resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

    private fun addBubble() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }
        layoutParams = params

        val density = resources.displayMetrics.density
        val bubbleSizePx = (BUBBLE_SIZE_DP * density).toInt()
        val iconSizePx = (ICON_SIZE_DP * density).toInt()
        val magnifierSizePx = (MAGNIFIER_SIZE_DP * density).toInt()

        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(bubbleSizePx, bubbleSizePx)
            background = androidx.core.content.ContextCompat.getDrawable(this@BubbleService, R.drawable.ic_bubble)
        }

        val translateIcon = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(iconSizePx, iconSizePx, Gravity.CENTER)
            setImageResource(R.drawable.ic_translate)
        }
        container.addView(translateIcon)

        val magnifier = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(magnifierSizePx, magnifierSizePx, Gravity.CENTER)
            setImageResource(R.drawable.ic_search)
            visibility = View.GONE
        }
        container.addView(magnifier)
        magnifierIcon = magnifier

        container.setOnTouchListener(::handleTouch)
        bubbleView = container
        windowManager.addView(container, params)
    }

    /** Orbits [magnifierIcon] around the bubble's center to signal an in-progress translation. */
    private fun startOrbitAnimation() {
        val magnifier = magnifierIcon ?: return
        magnifier.visibility = View.VISIBLE
        val radiusPx = ORBIT_RADIUS_DP * resources.displayMetrics.density
        orbitAnimator?.cancel()
        orbitAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = ORBIT_DURATION_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val angleRad = Math.toRadians((animator.animatedValue as Float).toDouble())
                magnifier.translationX = (radiusPx * kotlin.math.cos(angleRad)).toFloat()
                magnifier.translationY = (radiusPx * kotlin.math.sin(angleRad)).toFloat()
            }
            start()
        }
    }

    private fun stopOrbitAnimation() {
        orbitAnimator?.cancel()
        orbitAnimator = null
        magnifierIcon?.let {
            it.visibility = View.GONE
            it.translationX = 0f
            it.translationY = 0f
        }
    }

    private fun handleTouch(view: View, event: MotionEvent): Boolean {
        val params = layoutParams ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                    isDragging = true
                }
                params.x = initialX + dx
                params.y = initialY + dy
                windowManager.updateViewLayout(view, params)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    onBubbleTapped?.invoke()
                }
                return true
            }
        }
        return false
    }

    fun setLoading(isLoading: Boolean) {
        if (isLoading) startOrbitAnimation() else stopOrbitAnimation()
    }

    private fun onBubbleTap() {
        val capture = captureManager ?: return
        setLoading(true)
        scope.launch {
            try {
                val bitmap = withContext(Dispatchers.Default) { capture.captureFrame() }
                if (bitmap == null) {
                    Log.w(TAG, "No frame captured; aborting translation.")
                    setLoading(false); return@launch
                }
                val blocks = try {
                    ocrManager.recognize(bitmap)
                } finally {
                    bitmap.recycle()
                }
                if (blocks.isEmpty()) {
                    Log.w(TAG, "OCR returned zero text blocks.")
                    setLoading(false); return@launch
                }
                val downloadResult = translationManager.ensureModelDownloaded()
                if (downloadResult.isFailure) {
                    Log.e(TAG, "Translation model download failed.", downloadResult.exceptionOrNull())
                    setLoading(false); return@launch
                }
                val translated = translationManager.translateAll(blocks)
                showOverlay(translated)
                setLoading(false)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Translation pipeline failed.", e)
                setLoading(false)
            }
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
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

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        orbitAnimator?.cancel()
        scope.cancel()
        captureManager?.stop()
        captureManager = null
        recognizerClient?.close()
        recognizerClient = null
        translatorClient?.close()
        translatorClient = null
        bubbleView?.let { windowManager.removeView(it) }
        bubbleView = null
    }

    companion object {
        private const val TAG = "ScreenTranslator"
        const val ACTION_START_WITH_PROJECTION = "action_start_with_projection"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
    }
}
