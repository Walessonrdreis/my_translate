package com.walesson.screentranslator

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

private const val SETTLE_DELAY_MS = 1000L

/**
 * Lightweight accessibility service used only to detect "scrolling has stopped" for
 * [TranslationMode.CONTINUOUS] — it does not read or store any screen content. Every
 * scroll/content-change event resets a 1s timer; if nothing else arrives before it fires,
 * [onScrollSettled] is invoked so the bubble can auto-translate the now-still screen.
 */
class ScrollDetectorService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val settleRunnable = Runnable { onScrollSettled?.invoke() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (onScrollSettled == null) return
        // Our own bubble/overlay windows also emit window-content-changed events when shown
        // or hidden; without this filter, translating would immediately reset our own "did
        // it settle" timer, and continuous mode would never stop re-triggering itself.
        if (event?.packageName == packageName) return
        handler.removeCallbacks(settleRunnable)
        handler.postDelayed(settleRunnable, SETTLE_DELAY_MS)
    }

    override fun onInterrupt() {
        handler.removeCallbacks(settleRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(settleRunnable)
    }

    companion object {
        /** Set by [BubbleService] while continuous mode is active; null otherwise. */
        var onScrollSettled: (() -> Unit)? = null
    }
}
