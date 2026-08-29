package com.walesson.screentranslator.capture

import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ScreenCaptureManagerTest {

    @Test
    fun `captureFrame returns null when not started`() {
        val projection = mock<MediaProjection>()
        val manager = ScreenCaptureManager(projection) { _, _, _ -> mock() }

        val frame = manager.captureFrame()

        assertNull(frame)
    }

    @Test
    fun `start creates a virtual display via the projection`() {
        val projection = mock<MediaProjection>()
        val imageReader = mock<ImageReader>()
        val manager = ScreenCaptureManager(projection) { _, _, _ -> imageReader }

        manager.start(width = 1080, height = 2400, dpi = 420)

        verify(projection).createVirtualDisplay(
            "ScreenTranslatorCapture",
            1080, 2400, 420,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null, null
        )
    }

    @Test
    fun `stop releases the virtual display and image reader`() {
        val projection = mock<MediaProjection>()
        val imageReader = mock<ImageReader>()
        val virtualDisplay = mock<VirtualDisplay>()
        whenever(
            projection.createVirtualDisplay(
                anyString(), anyInt(), anyInt(), anyInt(), anyInt(), any(), anyOrNull(), anyOrNull()
            )
        ) doReturn virtualDisplay
        val manager = ScreenCaptureManager(projection) { _, _, _ -> imageReader }
        manager.start(width = 1080, height = 2400, dpi = 420)

        manager.stop()

        verify(virtualDisplay).release()
        verify(imageReader).close()
    }
}
