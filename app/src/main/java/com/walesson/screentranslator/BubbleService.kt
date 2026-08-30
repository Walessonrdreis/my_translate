package com.walesson.screentranslator

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.graphics.PixelFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CHANNEL_ID = "screen_translator_channel"
private const val NOTIFICATION_ID = 1
private const val BUBBLE_SIZE_DP = 56
private const val ICON_SIZE_DP = 28
private const val MAGNIFIER_SIZE_DP = 18
private const val ORBIT_RADIUS_DP = 22f
private const val ORBIT_DURATION_MS = 1200L
private const val CLOSE_TARGET_SIZE_DP = 64
private const val CLOSE_TARGET_ICON_DP = 28
private const val CLOSE_TARGET_MARGIN_BOTTOM_DP = 48
/** How close the bubble's center must get to the close target's center to arm it, in dp. */
private const val CLOSE_TARGET_SNAP_RADIUS_DP = 60f
private const val CLOSE_TARGET_ARMED_SCALE = 1.2f
private const val LONG_PRESS_MS = 500L
private const val MENU_BUTTON_SIZE_DP = 48
private const val MENU_BUTTON_ICON_DP = 24
private const val MENU_GAP_DP = 16
/** Frame settle time after removing the overlay, before capturing, so it isn't re-captured. */
private const val OVERLAY_REMOVAL_SETTLE_MS = 150L

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

    private var closeTargetView: View? = null
    private var closeTargetArmed = false

    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var longPressTriggered = false
    private var topMenuButton: View? = null
    private var bottomMenuButton: View? = null
    private var isMenuShowing = false

    private val scope = CoroutineScope(Dispatchers.Main)
    private var captureManager: ScreenCaptureManager? = null
    private lateinit var ocrManager: TextRecognitionManager
    private lateinit var translationManager: TranslationManager
    private var recognizerClient: TextRecognizer? = null
    private var translatorClient: Translator? = null
    private var overlayView: TranslationOverlayView? = null
    private var isTranslating = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission not granted; stopping service.")
            stopSelf()
            return
        }
        addBubble()
        addCloseTarget()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizerClient = recognizer
        ocrManager = TextRecognitionManager(recognizer)
        val translator = TranslatorFactory.createEnglishToPortuguese()
        translatorClient = translator
        translationManager = TranslationManager(translator)
        onBubbleTapped = { onBubbleTap() }
        applyContinuousModeState(TranslationMode.load(this))
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
                longPressTriggered = false
                val runnable = Runnable {
                    longPressTriggered = true
                    if (isMenuShowing) hideRadialMenu() else showRadialMenu()
                }
                longPressRunnable = runnable
                longPressHandler.postDelayed(runnable, LONG_PRESS_MS)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                    if (!isDragging) {
                        cancelLongPress()
                        if (isMenuShowing) hideRadialMenu()
                        showCloseTarget()
                    }
                    isDragging = true
                }
                params.x = initialX + dx
                params.y = initialY + dy
                windowManager.updateViewLayout(view, params)
                if (isDragging) updateCloseTargetArmedState(params)
                return true
            }
            MotionEvent.ACTION_UP -> {
                cancelLongPress()
                if (longPressTriggered) {
                    return true
                }
                if (isDragging) {
                    hideCloseTarget()
                    if (closeTargetArmed) {
                        closeBubbleAndOpenApp()
                    }
                } else if (isMenuShowing) {
                    hideRadialMenu()
                } else {
                    onBubbleTapped?.invoke()
                }
                return true
            }
        }
        return false
    }

    private fun cancelLongPress() {
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun addCloseTarget() {
        val density = resources.displayMetrics.density
        val sizePx = (CLOSE_TARGET_SIZE_DP * density).toInt()
        val iconPx = (CLOSE_TARGET_ICON_DP * density).toInt()
        val marginBottomPx = (CLOSE_TARGET_MARGIN_BOTTOM_DP * density).toInt()

        val target = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
            background = androidx.core.content.ContextCompat.getDrawable(this@BubbleService, R.drawable.ic_close_target)
            visibility = View.GONE
        }
        val closeIcon = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER)
            setImageResource(R.drawable.ic_close)
        }
        target.addView(closeIcon)

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = marginBottomPx
        }
        windowManager.addView(target, params)
        closeTargetView = target
    }

    private fun showCloseTarget() {
        closeTargetArmed = false
        closeTargetView?.apply {
            visibility = View.VISIBLE
            scaleX = 1f
            scaleY = 1f
        }
    }

    private fun hideCloseTarget() {
        closeTargetView?.visibility = View.GONE
    }

    /** Checks whether the bubble's center is close enough to the close target to arm it. */
    private fun updateCloseTargetArmedState(bubbleParams: WindowManager.LayoutParams) {
        val target = closeTargetView ?: return
        val density = resources.displayMetrics.density
        val bubbleSizePx = (BUBBLE_SIZE_DP * density)
        val (screenWidth, screenHeight) = displaySize()

        val bubbleCenterX = bubbleParams.x + bubbleSizePx / 2f
        val bubbleCenterY = bubbleParams.y + bubbleSizePx / 2f
        val targetCenterX = screenWidth / 2f
        val targetCenterY = screenHeight - (CLOSE_TARGET_MARGIN_BOTTOM_DP * density) - (CLOSE_TARGET_SIZE_DP * density) / 2f

        val dx = bubbleCenterX - targetCenterX
        val dy = bubbleCenterY - targetCenterY
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        val snapRadiusPx = CLOSE_TARGET_SNAP_RADIUS_DP * density

        val shouldArm = distance <= snapRadiusPx
        if (shouldArm != closeTargetArmed) {
            closeTargetArmed = shouldArm
            val scale = if (shouldArm) CLOSE_TARGET_ARMED_SCALE else 1f
            target.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
        }
    }

    private fun closeBubbleAndOpenApp() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(openAppIntent)
        stopSelf()
    }

    /** Shows the two-button radial menu (top: toggle mode, bottom: settings) above/below the bubble. */
    private fun showRadialMenu() {
        val params = layoutParams ?: return
        isMenuShowing = true
        val density = resources.displayMetrics.density
        val bubbleSizePx = (BUBBLE_SIZE_DP * density).toInt()
        val buttonSizePx = (MENU_BUTTON_SIZE_DP * density).toInt()
        val gapPx = (MENU_GAP_DP * density).toInt()

        val buttonX = params.x + bubbleSizePx / 2 - buttonSizePx / 2
        val topY = (params.y - buttonSizePx - gapPx).coerceAtLeast(0)
        val bottomY = params.y + bubbleSizePx + gapPx

        val modeIsOn = TranslationMode.load(this) == TranslationMode.CONTINUOUS
        val modeBackgroundRes = if (modeIsOn) R.drawable.ic_mode_on else R.drawable.ic_mode_off
        topMenuButton = addMenuButton(R.drawable.ic_autorenew, modeBackgroundRes, buttonSizePx, buttonX, topY) { toggleMode() }
        bottomMenuButton = addMenuButton(R.drawable.ic_settings, R.drawable.ic_close_target, buttonSizePx, buttonX, bottomY) { openSettings() }
    }

    private fun addMenuButton(
        iconRes: Int,
        backgroundRes: Int,
        sizePx: Int,
        x: Int,
        y: Int,
        onClick: () -> Unit
    ): View {
        val density = resources.displayMetrics.density
        val iconPx = (MENU_BUTTON_ICON_DP * density).toInt()

        val button = FrameLayout(this).apply {
            background = androidx.core.content.ContextCompat.getDrawable(this@BubbleService, backgroundRes)
            setOnClickListener { onClick() }
        }
        val icon = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER)
            setImageResource(iconRes)
        }
        button.addView(icon)

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
        windowManager.addView(button, params)
        return button
    }

    private fun hideRadialMenu() {
        isMenuShowing = false
        topMenuButton?.let { windowManager.removeView(it) }
        topMenuButton = null
        bottomMenuButton?.let { windowManager.removeView(it) }
        bottomMenuButton = null
    }

    /**
     * Turning continuous mode on the first time (before the accessibility service is enabled)
     * only opens the system permission screen — it does not flip the mode yet, since without
     * the service "scroll stopped" can never fire. Once the service is enabled, the next tap
     * actually switches the mode on.
     */
    private fun toggleMode() {
        val current = TranslationMode.load(this)
        if (current == TranslationMode.MANUAL) {
            if (!isAccessibilityServiceEnabled()) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
                hideRadialMenu()
                return
            }
            TranslationMode.save(this, TranslationMode.CONTINUOUS)
            applyContinuousModeState(TranslationMode.CONTINUOUS)
        } else {
            TranslationMode.save(this, TranslationMode.MANUAL)
            applyContinuousModeState(TranslationMode.MANUAL)
        }
        hideRadialMenu()
    }

    /** For now, opens the app's home screen — it will become the dedicated settings screen. */
    private fun openSettings() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        hideRadialMenu()
    }

    /** Wires (or tears down) the scroll-stop → auto-translate callback for continuous mode. */
    private fun applyContinuousModeState(mode: TranslationMode) {
        ScrollDetectorService.onScrollSettled = if (mode == TranslationMode.CONTINUOUS) {
            { onBubbleTap() }
        } else {
            null
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, ScrollDetectorService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun setLoading(isLoading: Boolean) {
        if (isLoading) startOrbitAnimation() else stopOrbitAnimation()
    }

    private fun onBubbleTap() {
        if (isTranslating) {
            Log.d(TAG, "Translation already in progress; ignoring this trigger.")
            return
        }
        val capture = captureManager ?: return
        isTranslating = true
        setLoading(true)
        // Remove any translation still on screen *before* capturing — MediaProjection
        // captures our own overlay window too, so leaving it up would feed the previous
        // translation back into OCR as if it were new source text.
        removeOverlay()
        scope.launch {
            try {
                // Give the compositor a frame to actually redraw without the overlay before
                // the screenshot is taken.
                delay(OVERLAY_REMOVAL_SETTLE_MS)
                val bitmap = withContext(Dispatchers.Default) { capture.captureFrame() }
                if (bitmap == null) {
                    Log.w(TAG, "No frame captured; aborting translation.")
                    return@launch
                }
                val blocks = try {
                    ocrManager.recognize(bitmap)
                } finally {
                    bitmap.recycle()
                }
                if (blocks.isEmpty()) {
                    Log.w(TAG, "OCR returned zero text blocks.")
                    return@launch
                }
                val downloadResult = translationManager.ensureModelDownloaded()
                if (downloadResult.isFailure) {
                    Log.e(TAG, "Translation model download failed.", downloadResult.exceptionOrNull())
                    return@launch
                }
                val translated = translationManager.translateAll(blocks)
                showOverlay(translated)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Translation pipeline failed.", e)
            } finally {
                setLoading(false)
                isTranslating = false
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
        cancelLongPress()
        hideRadialMenu()
        ScrollDetectorService.onScrollSettled = null
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
        closeTargetView?.let { windowManager.removeView(it) }
        closeTargetView = null
    }

    companion object {
        private const val TAG = "ScreenTranslator"
        const val ACTION_START_WITH_PROJECTION = "action_start_with_projection"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
    }
}
