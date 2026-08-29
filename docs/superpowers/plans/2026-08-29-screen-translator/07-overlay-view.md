### Task 7: `TranslationOverlayView` (in-place overlay)

**Files:**
- Create: `app/src/main/java/com/walesson/screentranslator/overlay/TranslationOverlayView.kt`
- Test: `app/src/test/java/com/walesson/screentranslator/overlay/TranslationOverlayHitTestTest.kt`

**Interfaces:**
- Consumes: `com.walesson.screentranslator.ocr.TextBlock` (from Task 3, reused as `(Rect, translatedText)` carrier).
- Produces:
  - `class TranslationOverlayView(context: Context) : View(context)` with `fun show(blocks: List<TextBlock>)` (sets the blocks to draw) and `var onDismissRequested: (() -> Unit)?` (invoked when a touch lands outside every block).
  - A pure, unit-testable function `fun isOutsideAllBlocks(x: Float, y: Float, blocks: List<TextBlock>): Boolean` used internally by the touch handler — this is what's actually unit tested, since drawing/canvas logic needs an Android runtime.

- [ ] **Step 1: Write the failing test for the pure hit-test logic**

```kotlin
package com.walesson.screentranslator.overlay

import android.graphics.Rect
import com.walesson.screentranslator.ocr.TextBlock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationOverlayHitTestTest {

    private val blocks = listOf(
        TextBlock("Olá", Rect(0, 0, 100, 50)),
        TextBlock("Mundo", Rect(200, 200, 300, 250))
    )

    @Test
    fun `point inside a block is not outside all blocks`() {
        assertFalse(isOutsideAllBlocks(50f, 25f, blocks))
    }

    @Test
    fun `point outside every block is outside all blocks`() {
        assertTrue(isOutsideAllBlocks(500f, 500f, blocks))
    }

    @Test
    fun `empty block list means every point is outside`() {
        assertTrue(isOutsideAllBlocks(10f, 10f, emptyList()))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*TranslationOverlayHitTestTest*"`
Expected: FAIL — `isOutsideAllBlocks` does not exist yet.

- [ ] **Step 3: Write `TranslationOverlayView.kt`**

```kotlin
package com.walesson.screentranslator.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.walesson.screentranslator.ocr.TextBlock

fun isOutsideAllBlocks(x: Float, y: Float, blocks: List<TextBlock>): Boolean {
    return blocks.none { it.boundingBox.contains(x.toInt(), y.toInt()) }
}

class TranslationOverlayView(context: Context) : View(context) {

    var onDismissRequested: (() -> Unit)? = null

    private var blocks: List<TextBlock> = emptyList()

    private val backgroundPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 36f
        isAntiAlias = true
    }

    fun show(blocks: List<TextBlock>) {
        this.blocks = blocks
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (block in blocks) {
            canvas.drawRect(block.boundingBox, backgroundPaint)
            canvas.drawText(
                block.text,
                block.boundingBox.left.toFloat() + 8f,
                block.boundingBox.bottom.toFloat() - 8f,
                textPaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN &&
            isOutsideAllBlocks(event.x, event.y, blocks)
        ) {
            onDismissRequested?.invoke()
            return true
        }
        return true
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*TranslationOverlayHitTestTest*"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/walesson/screentranslator/overlay app/src/test/java/com/walesson/screentranslator/overlay
git commit -m "feat: add in-place translation overlay view"
```
