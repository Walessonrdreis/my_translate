### Task 6: `BubbleService` + draggable bubble UI

**Files:**
- Modify: `app/src/main/java/com/walesson/screentranslator/BubbleService.kt` (replace Task 2's stub)
- Create: `app/src/main/res/drawable/ic_bubble.xml`

**Interfaces:**
- Consumes: `WindowManager`, `android.view.View`.
- Produces: `class BubbleService : Service()` that shows a draggable circular bubble via `WindowManager`, exposes a `fun interface OnBubbleTapped { fun onTap() }` callback slot (`var onBubbleTapped: (() -> Unit)?`) that Task 8 wires to the translation pipeline. Also exposes `fun setLoading(isLoading: Boolean)` to visually indicate processing.

This task's bubble drag/tap logic has no external dependencies to mock meaningfully in a JVM unit test (it's pure `View.OnTouchListener` math over `MotionEvent`, which requires the Android framework's `MotionEvent.obtain`, only available via instrumented tests). Per the spec's testing section, this component is covered by the manual test checklist in Task 8; this task focuses on correct, readable implementation.

- [ ] **Step 1: Create `ic_bubble.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#2196F3" />
    <size android:width="56dp" android:height="56dp" />
</shape>
```

- [ ] **Step 2: Write `BubbleService.kt`**

```kotlin
package com.walesson.screentranslator

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView

class BubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: ImageView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    var onBubbleTapped: (() -> Unit)? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addBubble()
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

        val view = ImageView(this).apply {
            setImageResource(R.drawable.ic_bubble)
            setOnTouchListener(::handleTouch)
        }
        bubbleView = view
        windowManager.addView(view, params)
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
        bubbleView?.alpha = if (isLoading) 0.5f else 1.0f
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { windowManager.removeView(it) }
    }
}
```

- [ ] **Step 3: Build the app**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/walesson/screentranslator/BubbleService.kt app/src/main/res/drawable/ic_bubble.xml
git commit -m "feat: add draggable floating bubble service"
```
