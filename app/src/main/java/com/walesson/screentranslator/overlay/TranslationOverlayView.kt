package com.walesson.screentranslator.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import com.walesson.screentranslator.ocr.TextBlock

private const val MIN_TEXT_SIZE = 12f
private const val DEFAULT_TEXT_SIZE = 36f
private const val TEXT_SIZE_STEP = 2f
private const val BOX_PADDING = 8f
/** Vertical gap kept between a grown box and the next block below it, so they never touch. */
private const val BOX_GAP = 4f

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
        // Sorted so each block can be capped by the vertical space before the next one,
        // preventing grown boxes from overlapping and obscuring each other.
        val sorted = blocks.sortedBy { it.boundingBox.top }
        for ((index, block) in sorted.withIndex()) {
            val boxWidth = (block.boundingBox.right - block.boundingBox.left - 2 * BOX_PADDING)
                .coerceAtLeast(1f)
            val ocrHeight = (block.boundingBox.bottom - block.boundingBox.top).toFloat()

            val nextTop = sorted.getOrNull(index + 1)?.boundingBox?.top?.toFloat()
            val availableHeight = if (nextTop != null && nextTop > block.boundingBox.top) {
                maxOf(ocrHeight, nextTop - block.boundingBox.top - BOX_GAP)
            } else {
                Float.MAX_VALUE
            }

            val layout = buildFittingLayout(block.text, boxWidth.toInt(), availableHeight - 2 * BOX_PADDING)

            // The box grows downward to fit wrapped text, capped by availableHeight so it
            // never overlaps the next block; it never shrinks below the OCR-detected height.
            val boxHeight = maxOf(ocrHeight, layout.height + 2 * BOX_PADDING)
                .coerceAtMost(if (availableHeight == Float.MAX_VALUE) Float.MAX_VALUE else availableHeight)

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
     * Builds a wrapped, multi-line [StaticLayout] for [text] within [maxWidth] px, shrinking
     * [textPaint]'s size first if even the widest word wouldn't fit, and finally truncating
     * with an ellipsis if [maxHeight] still can't hold every line (e.g. dense text where
     * neighboring blocks leave little vertical room).
     */
    private fun buildFittingLayout(text: String, maxWidth: Int, maxHeight: Float): StaticLayout {
        textPaint.textSize = DEFAULT_TEXT_SIZE
        val words = text.split(" ")
        fun longestWordWidth() = words.maxOfOrNull { textPaint.measureText(it) } ?: 0f
        while (textPaint.textSize > MIN_TEXT_SIZE && longestWordWidth() > maxWidth) {
            textPaint.textSize = (textPaint.textSize - TEXT_SIZE_STEP).coerceAtLeast(MIN_TEXT_SIZE)
        }

        fun layoutOf(maxLines: Int?): StaticLayout {
            val builder = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
            if (maxLines != null) {
                builder.setMaxLines(maxLines)
                builder.setEllipsize(TextUtils.TruncateAt.END)
            }
            return builder.build()
        }

        val unbounded = layoutOf(maxLines = null)
        if (maxHeight <= 0f || unbounded.height <= maxHeight) return unbounded

        val lineHeight = textPaint.fontMetrics.let { it.descent - it.ascent }
        val maxLines = (maxHeight / lineHeight).toInt().coerceAtLeast(1)
        return layoutOf(maxLines)
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
