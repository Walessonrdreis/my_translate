### Task 5: `ScreenCaptureManager` (MediaProjection)

**Files:**
- Create: `app/src/main/java/com/walesson/screentranslator/capture/ScreenCaptureManager.kt`
- Test: `app/src/test/java/com/walesson/screentranslator/capture/ScreenCaptureManagerTest.kt`

**Interfaces:**
- Consumes: `android.media.projection.MediaProjection`, `android.media.ImageReader`.
- Produces: `class ScreenCaptureManager(private val projection: MediaProjection, private val imageReaderFactory: (width: Int, height: Int, dpi: Int) -> ImageReader)` with `fun start(width: Int, height: Int, dpi: Int)`, `fun captureFrame(): Bitmap?`, `fun stop()`.

This class only handles the capture mechanics; it does not request the MediaProjection permission itself (that intent/result-handling belongs in `MainActivity`/`BubbleService` per Task 8, since it requires an `Activity` result callback). Unit tests here cover the lifecycle logic (start/stop state), not actual pixel capture, which requires a real display.

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

Note: add `org.mockito.kotlin.*` imports for `anyString`, `anyInt`, `any`, `anyOrNull`, `doReturn` as used above.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*ScreenCaptureManagerTest*"`
Expected: FAIL — `ScreenCaptureManager` does not exist yet.

- [ ] **Step 3: Write `ScreenCaptureManager.kt`**

```kotlin
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
        val image = reader.acquireLatestImage() ?: return null
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
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*ScreenCaptureManagerTest*"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/walesson/screentranslator/capture app/src/test/java/com/walesson/screentranslator/capture
git commit -m "feat: add MediaProjection screen capture manager"
```
