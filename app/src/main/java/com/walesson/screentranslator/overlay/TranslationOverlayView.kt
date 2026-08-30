package com.walesson.screentranslator.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.walesson.screentranslator.ocr.TextBlock

private const val MIN_TEXT_SIZE = 8f
private const val BOX_PADDING = 6f
/** Matches the translated font size to the original line's detected height. */
private const val FONT_HEIGHT_RATIO = 0.75f

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

    // Dark, semi-transparent film that overwrites the original text in place.
    private val backgroundPaint = Paint().apply {
        color = Color.argb(200, 20, 20, 20)
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
    }

    fun show(blocks: List<TextBlock>) {
        this.blocks = blocks
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (block in blocks) {
            val box = block.boundingBox
            val boxHeight = (box.bottom - box.top).toFloat()
            textPaint.textSize = (boxHeight * FONT_HEIGHT_RATIO).coerceAtLeast(MIN_TEXT_SIZE)

            // Single line, same height as the original — only the width is allowed to grow
            // rightward to fit longer translated text, never the height.
            val textWidth = textPaint.measureText(block.text)
            val filmLeft = box.left.toFloat()
            val filmRight = maxOf(box.right.toFloat(), filmLeft + textWidth + 2 * BOX_PADDING)

            canvas.drawRect(filmLeft, box.top.toFloat(), filmRight, box.bottom.toFloat(), backgroundPaint)

            val fm = textPaint.fontMetrics
            val textHeight = fm.descent - fm.ascent
            val baseline = box.top.toFloat() + (boxHeight - textHeight) / 2f - fm.ascent
            canvas.drawText(block.text, filmLeft + BOX_PADDING, baseline, textPaint)
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
