package com.walesson.screentranslator

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

private const val SETTLE_DELAY_MS = 1000L

/**
 * Lightweight accessibility service used only to detect "scrolling has stopped" for
 * [TranslationMode.CONTINUOUS] — it does not read or store any screen content. Listens only
 * to [android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED] (configured in
 * `scroll_detector_service.xml`), deliberately excluding window-content-changed events: those
 * fire for practically any on-screen change — including our own bubble/overlay windows being
 * shown, hidden, or resized — and some of those events aren't attributable to our package via
 * [AccessibilityEvent.getPackageName], which made continuous mode re-trigger itself forever.
 * Every scroll event resets a 1s timer; if nothing else arrives before it fires,
 * [onScrollSettled] is invoked so the bubble can auto-translate the now-still screen.
 */
class ScrollDetectorService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val settleRunnable = Runnable { onScrollSettled?.invoke() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (onScrollSettled == null) return
        // Defense in depth: real scroll events from our own app should never happen (we have
        // no scrollable views), but skip them if they somehow do.
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
