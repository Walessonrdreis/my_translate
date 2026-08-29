package com.walesson.screentranslator.capture

import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString

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

        // Read the surface before entering verification mode: interacting with another mock
        // while a verify() is pending confuses Mockito's ongoing-verification state.
        val surface = imageReader.surface
        verify(projection).createVirtualDisplay(
            "ScreenTranslatorCapture",
            1080, 2400, 420,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
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
                anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyOrNull(), anyOrNull(), anyOrNull()
            )
        ) doReturn virtualDisplay
        val manager = ScreenCaptureManager(projection) { _, _, _ -> imageReader }
        manager.start(width = 1080, height = 2400, dpi = 420)

        manager.stop()

        verify(virtualDisplay).release()
        verify(imageReader).close()
    }
}
