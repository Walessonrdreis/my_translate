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
/** Gap kept between a block's film and the next line below it. */
private const val LINE_GAP = 2f
/**
 * The OCR bounding box includes line-spacing above/below the glyphs, so matching its full
 * height overshoots the real letter size. This ratio brings the translated font a bit under
 * the original's apparent size, which is what keeps it from crowding neighboring lines.
 */
private const val FONT_HEIGHT_RATIO = 0.6f

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
            val originalHeight = (box.bottom - box.top).toFloat()
            val preferredSize = (originalHeight * FONT_HEIGHT_RATIO).coerceAtLeast(MIN_TEXT_SIZE)

            val nextTop = sorted.getOrNull(index + 1)?.boundingBox?.top?.toFloat()
            val maxHeight = if (nextTop != null && nextTop > box.top) {
                maxOf(originalHeight, nextTop - box.top - LINE_GAP)
            } else {
                Float.MAX_VALUE
            }

            // Width stays at the original box width unless a single word is wider than that
            // — then it widens just enough to fit that word, never enough to cut it mid-way.
            val originalWidth = (box.right - box.left).toFloat()
            textPaint.textSize = preferredSize
            val longestWordWidth = block.text.split(" ").maxOfOrNull { textPaint.measureText(it) } ?: 0f
            val filmWidth = maxOf(originalWidth, longestWordWidth + 2 * BOX_PADDING)
            val wrapWidth = (filmWidth - 2 * BOX_PADDING).toInt().coerceAtLeast(1)

            val layout = buildMatchedLayout(block.text, wrapWidth, preferredSize, maxHeight - 2 * BOX_PADDING)
            val filmHeight = maxOf(originalHeight, layout.height + 2 * BOX_PADDING)
                .coerceAtMost(if (maxHeight == Float.MAX_VALUE) Float.MAX_VALUE else maxHeight)

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

    /**
     * Builds a wrapped [StaticLayout] for [text] at [preferredSize] px within [maxWidth] px.
     * Only shrinks below [preferredSize] (down to [MIN_TEXT_SIZE]) if [maxHeight] — the space
     * before the next line — can't hold it; as a last resort at the floor size, truncates
     * with an ellipsis rather than overlapping the line below.
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

        textPaint.textSize = preferredSize
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
