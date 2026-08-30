package com.walesson.screentranslator.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * Upscale factor applied by [enhanceForOcr]. Callers must divide any bounding box coming out
 * of OCR run on the enhanced bitmap by this factor to map it back to real screen coordinates.
 */
const val OCR_UPSCALE_FACTOR = 1.5f
private const val OCR_CONTRAST = 1.25f

/**
 * Mild upscale + contrast boost applied before OCR — helps ML Kit read small or thin-stroked
 * text a bit better. It does not fix genuinely decorative/cursive fonts, which remain a hard
 * limit of on-device OCR models. Does not recycle [source]; the caller owns its lifecycle.
 */
fun enhanceForOcr(source: Bitmap): Bitmap {
    val scaledWidth = (source.width * OCR_UPSCALE_FACTOR).toInt().coerceAtLeast(1)
    val scaledHeight = (source.height * OCR_UPSCALE_FACTOR).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)

    val translate = (-0.5f * OCR_CONTRAST + 0.5f) * 255f
    val colorMatrix = ColorMatrix(
        floatArrayOf(
            OCR_CONTRAST, 0f, 0f, 0f, translate,
            0f, OCR_CONTRAST, 0f, 0f, translate,
            0f, 0f, OCR_CONTRAST, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
    )
    val output = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(colorMatrix)
    }
    canvas.drawBitmap(scaled, 0f, 0f, paint)
    if (scaled !== source) scaled.recycle()
    return output
}
