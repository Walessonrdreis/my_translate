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

private const val MIN_TEXT_SIZE = 6f
private const val START_TEXT_SIZE = 48f
private const val TEXT_SIZE_STEP = 1f
private const val BOX_PADDING = 3f
/** Gap kept between a block's film and the next line below it. */
private const val LINE_GAP = 2f
/** Added to the exactly-fitted size, since the exact fit reads a touch small in practice. */
private const val TEXT_SIZE_BOOST = 2f

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
        // Sorted so each block's film can be capped by the space before the next line,
        // preventing a wrapped translation from growing into (and obscuring) it.
        val sorted = blocks.sortedBy { it.boundingBox.top }
        for ((index, block) in sorted.withIndex()) {
            val box = block.boundingBox
            val originalWidth = (box.right - box.left).toFloat()
            val originalHeight = (box.bottom - box.top).toFloat()

            val nextTop = sorted.getOrNull(index + 1)?.boundingBox?.top?.toFloat()
            val ceilingHeight = if (nextTop != null && nextTop > box.top) {
                maxOf(originalHeight, nextTop - box.top - LINE_GAP)
            } else {
                Float.MAX_VALUE
            }

            val fit = fitTextToBox(
                text = block.text,
                targetWidth = originalWidth - 2 * BOX_PADDING,
                targetHeight = originalHeight - 2 * BOX_PADDING,
                ceilingHeight = if (ceilingHeight == Float.MAX_VALUE) ceilingHeight else ceilingHeight - 2 * BOX_PADDING
            )

            val filmWidth = maxOf(originalWidth, fit.width + 2 * BOX_PADDING)
            val filmHeight = maxOf(originalHeight, fit.layout.height + 2 * BOX_PADDING)
                .coerceAtMost(if (ceilingHeight == Float.MAX_VALUE) Float.MAX_VALUE else ceilingHeight)

            canvas.drawRect(
                box.left.toFloat(),
                box.top.toFloat(),
                box.left + filmWidth,
                box.top + filmHeight,
                backgroundPaint
            )

            canvas.save()
            canvas.translate(box.left.toFloat() + BOX_PADDING, box.top.toFloat() + BOX_PADDING)
            fit.layout.draw(canvas)
            canvas.restore()
        }
    }

    private class Fit(val layout: StaticLayout, val width: Float)

    /**
     * Finds the largest text size (down to [MIN_TEXT_SIZE]) at which [text] — wrapped at
     * [targetWidth] — fits within [targetHeight], i.e. the actual space the original text
     * occupied, rather than assuming a fixed ratio of the box. Only exceeds [targetHeight]
     * (up to [ceilingHeight], the space before the next line) if even the smallest readable
     * size still wraps past it; beyond that, truncates with an ellipsis as a last resort.
     */
    private fun fitTextToBox(
        text: String,
        targetWidth: Float,
        targetHeight: Float,
        ceilingHeight: Float
    ): Fit {
        val wrapWidth = targetWidth.toInt().coerceAtLeast(1)

        fun layoutOf(size: Float, maxLines: Int?): StaticLayout {
            textPaint.textSize = size
            val builder = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, wrapWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
            if (maxLines != null) {
                builder.setMaxLines(maxLines)
                builder.setEllipsize(TextUtils.TruncateAt.END)
            }
            return builder.build()
        }

        fun widestLine(layout: StaticLayout): Float =
            (0 until layout.lineCount).maxOf { layout.getLineWidth(it) }

        var size = START_TEXT_SIZE
        var layout = layoutOf(size, maxLines = null)
        while (size > MIN_TEXT_SIZE && layout.height > targetHeight) {
            size = (size - TEXT_SIZE_STEP).coerceAtLeast(MIN_TEXT_SIZE)
            layout = layoutOf(size, maxLines = null)
        }

        // Bump the fitted size up for readability (closer to the reference app's look), but
        // only as far as the space before the next line actually allows — a long sentence
        // that fits at the exact size shouldn't get truncated just because the boost pushed
        // it past the ceiling.
        var boostedSize = size
        var boostedLayout = layout
        val maxBoostedSize = size + TEXT_SIZE_BOOST
        while (boostedSize < maxBoostedSize) {
            val candidateSize = (boostedSize + TEXT_SIZE_STEP).coerceAtMost(maxBoostedSize)
            val candidateLayout = layoutOf(candidateSize, maxLines = null)
            if (candidateLayout.height > ceilingHeight) break
            boostedSize = candidateSize
            boostedLayout = candidateLayout
        }

        if (boostedLayout.height <= ceilingHeight) {
            return Fit(boostedLayout, widestLine(boostedLayout))
        }

        // Even the un-boosted fit doesn't clear the space before the next line: truncate as a
        // last resort rather than overlapping it.
        val lineHeight = textPaint.fontMetrics.let { it.descent - it.ascent }
        val maxLines = (ceilingHeight / lineHeight).toInt().coerceAtLeast(1)
        val finalLayout = layoutOf(boostedSize, maxLines)
        return Fit(finalLayout, widestLine(finalLayout))
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
