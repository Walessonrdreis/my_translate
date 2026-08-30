package com.walesson.screentranslator.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
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

    private val textPaint = TextPaint().apply {
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

            // Width stays at the original box width unless a single word is wider than that
            // — then it widens just enough to fit that word, never enough to cut it mid-way.
            val originalWidth = (box.right - box.left).toFloat()
            val longestWordWidth = block.text.split(" ").maxOfOrNull { textPaint.measureText(it) } ?: 0f
            val filmWidth = maxOf(originalWidth, longestWordWidth + 2 * BOX_PADDING)
            val wrapWidth = (filmWidth - 2 * BOX_PADDING).toInt().coerceAtLeast(1)

            val layout = StaticLayout.Builder.obtain(block.text, 0, block.text.length, textPaint, wrapWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()

            // Height grows downward to fit every wrapped line — it's never capped or shrunk.
            val filmHeight = maxOf(boxHeight, layout.height + 2 * BOX_PADDING)

            canvas.drawRect(
                box.left.toFloat(),
                box.top.toFloat(),
                box.left + filmWidth,
                box.top + filmHeight,
                backgroundPaint
            )

            canvas.save()
            canvas.translate(box.left.toFloat() + BOX_PADDING, box.top.toFloat() + BOX_PADDING)
            layout.draw(canvas)
            canvas.restore()
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
