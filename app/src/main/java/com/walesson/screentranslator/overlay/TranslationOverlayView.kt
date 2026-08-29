package com.walesson.screentranslator.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.walesson.screentranslator.ocr.TextBlock

private const val MIN_TEXT_SIZE = 12f
private const val DEFAULT_TEXT_SIZE = 36f
private const val TEXT_SIZE_STEP = 2f

fun isOutsideAllBlocks(x: Float, y: Float, blocks: List<TextBlock>): Boolean {
    // Manual containment check: does not rely on Rect.contains(), which is a stubbed
    // no-op under the unit-test Android jar (returnDefaultValues).
    return blocks.none { block ->
        val box = block.boundingBox
        x >= box.left && x <= box.right && y >= box.top && y <= box.bottom
    }
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
        textSize = DEFAULT_TEXT_SIZE
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
            fitTextSize(block.text, (block.boundingBox.right - block.boundingBox.left - 16).toFloat())
            canvas.drawText(
                block.text,
                block.boundingBox.left.toFloat() + 8f,
                block.boundingBox.bottom.toFloat() - 8f,
                textPaint
            )
        }
    }

    /** Shrinks [textPaint]'s size until [text] fits into [maxWidth], flooring at [MIN_TEXT_SIZE]. */
    private fun fitTextSize(text: String, maxWidth: Float) {
        textPaint.textSize = DEFAULT_TEXT_SIZE
        if (maxWidth <= 0f) return
        while (textPaint.textSize > MIN_TEXT_SIZE && textPaint.measureText(text) > maxWidth) {
            textPaint.textSize = (textPaint.textSize - TEXT_SIZE_STEP).coerceAtLeast(MIN_TEXT_SIZE)
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
