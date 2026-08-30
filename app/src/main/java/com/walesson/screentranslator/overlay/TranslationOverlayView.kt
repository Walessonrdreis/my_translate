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
private const val DEFAULT_TEXT_SIZE = 36f
private const val TEXT_SIZE_STEP = 1f
private const val BOX_PADDING = 4f

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

    // Dark, semi-transparent film over the original text, matching its footprint, instead
    // of a solid box that would need to grow (and risk overlapping neighbors) to fit longer
    // translated text.
    private val backgroundPaint = Paint().apply {
        color = Color.argb(200, 20, 20, 20)
        style = Paint.Style.FILL
    }

    private val textPaint = TextPaint().apply {
        color = Color.WHITE
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
            val box = block.boundingBox
            canvas.drawRect(box, backgroundPaint)

            val innerWidth = (box.right - box.left - 2 * BOX_PADDING).coerceAtLeast(1f)
            val innerHeight = (box.bottom - box.top - 2 * BOX_PADDING).coerceAtLeast(1f)
            val layout = buildFittingLayout(block.text, innerWidth.toInt(), innerHeight)

            canvas.save()
            canvas.translate(box.left.toFloat() + BOX_PADDING, box.top.toFloat() + BOX_PADDING)
            layout.draw(canvas)
            canvas.restore()
        }
    }

    /**
     * Builds a wrapped [StaticLayout] for [text] that fits within [maxWidth]/[maxHeight] px,
     * shrinking [textPaint]'s size down to [MIN_TEXT_SIZE] first. The box never grows beyond
     * the OCR-detected region, so at the size floor an ellipsis truncates as a last resort
     * rather than overlapping neighboring blocks.
     */
    private fun buildFittingLayout(text: String, maxWidth: Int, maxHeight: Float): StaticLayout {
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

        textPaint.textSize = DEFAULT_TEXT_SIZE
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
