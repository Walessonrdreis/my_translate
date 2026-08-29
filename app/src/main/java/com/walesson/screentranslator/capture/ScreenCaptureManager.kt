package com.walesson.screentranslator.capture

import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection

class ScreenCaptureManager(
    private val projection: MediaProjection,
    private val imageReaderFactory: (width: Int, height: Int, dpi: Int) -> ImageReader
) {
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    fun start(width: Int, height: Int, dpi: Int) {
        val reader = imageReaderFactory(width, height, dpi)
        imageReader = reader
        virtualDisplay = projection.createVirtualDisplay(
            "ScreenTranslatorCapture",
            width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null, null
        )
    }

    fun captureFrame(): Bitmap? {
        val reader = imageReader ?: return null
        var image = reader.acquireLatestImage() ?: return null
        // Drain any additional queued/stale frames so we return the truly latest one.
        var next = reader.acquireLatestImage()
        while (next != null) {
            image.close()
            image = next
            next = reader.acquireLatestImage()
        }
        return image.use { toBitmap(it) }
    }

    fun stop() {
        virtualDisplay?.release()
        imageReader?.close()
        virtualDisplay = null
        imageReader = null
    }

    private fun toBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        if (rowPadding == 0) {
            val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            return bitmap
        }
        val paddedBitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        paddedBitmap.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(paddedBitmap, 0, 0, image.width, image.height)
        paddedBitmap.recycle()
        return cropped
    }
}
