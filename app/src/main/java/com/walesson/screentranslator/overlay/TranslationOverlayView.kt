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

private const val MIN_TEXT_SIZE = 8f
private const val TEXT_SIZE_STEP = 1f
private const val BOX_PADDING = 4f
/** Gap kept between the translation film and the original text line it sits above. */
private const val LINE_GAP = 4f
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

    // Dark, semi-transparent film drawn above the original line, rather than over it, so the
    // untouched source text stays visible below its translation.
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
        // Sorted so each block's film can be capped by the space above it (before the
        // previous line's original text, or its own translation film), preventing overlap.
        val sorted = blocks.sortedBy { it.boundingBox.top }
        for ((index, block) in sorted.withIndex()) {
            val box = block.boundingBox
            val matchedSize = (box.bottom - box.top) * FONT_HEIGHT_RATIO

            val prevBottom = sorted.getOrNull(index - 1)?.boundingBox?.bottom?.toFloat() ?: 0f
            val availableAbove = (box.top - prevBottom - LINE_GAP - 2 * BOX_PADDING).coerceAtLeast(0f)

            val innerWidth = (box.right - box.left - 2 * BOX_PADDING).coerceAtLeast(1f)
            val layout = buildMatchedLayout(block.text, innerWidth.toInt(), matchedSize, availableAbove)

            val filmHeight = layout.height + 2 * BOX_PADDING
            val filmBottom = box.top.toFloat() - LINE_GAP
            val filmTop = (filmBottom - filmHeight).coerceAtLeast(prevBottom)

            canvas.drawRect(box.left.toFloat(), filmTop, box.right.toFloat(), filmBottom, backgroundPaint)

            canvas.save()
            canvas.translate(box.left.toFloat() + BOX_PADDING, filmTop + BOX_PADDING)
            layout.draw(canvas)
            canvas.restore()
        }
    }

    /**
     * Builds a wrapped [StaticLayout] for [text] at [preferredSize] px — matching the original
     * line's font size — within [maxWidth] px. Only shrinks (down to [MIN_TEXT_SIZE]) if the
     * space available above the line ([maxHeight]) can't hold it at that size; as a last resort
     * at the floor size, truncates with an ellipsis rather than overlapping the line above.
     */
    private fun buildMatchedLayout(
        text: String,
        maxWidth: Int,
        preferredSize: Float,
        maxHeight: Float
    ): StaticLayout {
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

        textPaint.textSize = preferredSize.coerceAtLeast(MIN_TEXT_SIZE)
        var layout = layoutOf(maxLines = null)
        while (textPaint.textSize > MIN_TEXT_SIZE && layout.height > maxHeight) {
            textPaint.textSize = (textPaint.textSize - TEXT_SIZE_STEP).coerceAtLeast(MIN_TEXT_SIZE)
            layout = layoutOf(maxLines = null)
        }

        if (layout.height <= maxHeight) return layout

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
