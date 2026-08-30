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

private const val MIN_TEXT_SIZE = 12f
private const val DEFAULT_TEXT_SIZE = 36f
private const val TEXT_SIZE_STEP = 2f
private const val BOX_PADDING = 8f

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

    private val textPaint = TextPaint().apply {
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
            val boxWidth = (block.boundingBox.right - block.boundingBox.left - 2 * BOX_PADDING)
                .coerceAtLeast(1f)
            val layout = buildFittingLayout(block.text, boxWidth.toInt())

            // The box grows downward to fit wrapped text; it never shrinks below the
            // OCR-detected height, so short translations still cover the original text.
            val boxHeight = maxOf(
                (block.boundingBox.bottom - block.boundingBox.top).toFloat(),
                layout.height + 2 * BOX_PADDING
            )

            canvas.drawRect(
                block.boundingBox.left.toFloat(),
                block.boundingBox.top.toFloat(),
                block.boundingBox.right.toFloat(),
                block.boundingBox.top + boxHeight,
                backgroundPaint
            )

            canvas.save()
            canvas.translate(
                block.boundingBox.left.toFloat() + BOX_PADDING,
                block.boundingBox.top.toFloat() + BOX_PADDING
            )
            layout.draw(canvas)
            canvas.restore()
        }
    }

    /**
     * Builds a wrapped, multi-line [StaticLayout] for [text] within [maxWidth] px,
     * shrinking [textPaint]'s size first if even the widest word wouldn't fit.
     */
    private fun buildFittingLayout(text: String, maxWidth: Int): StaticLayout {
        textPaint.textSize = DEFAULT_TEXT_SIZE
        val words = text.split(" ")
        fun longestWordWidth() = words.maxOfOrNull { textPaint.measureText(it) } ?: 0f
        while (textPaint.textSize > MIN_TEXT_SIZE && longestWordWidth() > maxWidth) {
            textPaint.textSize = (textPaint.textSize - TEXT_SIZE_STEP).coerceAtLeast(MIN_TEXT_SIZE)
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()
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
