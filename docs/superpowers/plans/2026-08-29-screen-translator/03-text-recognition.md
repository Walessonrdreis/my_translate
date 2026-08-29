### Task 3: `TextBlock` model + `TextRecognitionManager` (OCR)

**Files:**
- Create: `app/src/main/java/com/walesson/screentranslator/ocr/TextBlock.kt`
- Create: `app/src/main/java/com/walesson/screentranslator/ocr/TextRecognitionManager.kt`
- Test: `app/src/test/java/com/walesson/screentranslator/ocr/TextRecognitionManagerTest.kt`

**Interfaces:**
- Consumes: `android.graphics.Bitmap` (standard Android type), ML Kit's `com.google.mlkit.vision.text.TextRecognizer` and `com.google.mlkit.vision.text.Text`.
- Produces:
  - `data class TextBlock(val text: String, val boundingBox: Rect)`
  - `class TextRecognitionManager(private val recognizer: TextRecognizer)` with `suspend fun recognize(bitmap: Bitmap): List<TextBlock>`

- [ ] **Step 1: Create `TextBlock.kt`**

```kotlin
package com.walesson.screentranslator.ocr

import android.graphics.Rect

data class TextBlock(
    val text: String,
    val boundingBox: Rect
)
```

- [ ] **Step 2: Write the failing test**

```kotlin
package com.walesson.screentranslator.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TextRecognitionManagerTest {

    @Test
    fun `recognize maps ML Kit blocks to TextBlock with text and bounding box`() = runTest {
        val bitmap = mock<Bitmap>()
        val mlKitBlock = mock<Text.TextBlock> {
            on { text } doReturn "Hello world"
            on { boundingBox } doReturn Rect(10, 20, 100, 60)
        }
        val text = mock<Text> {
            on { textBlocks } doReturn listOf(mlKitBlock)
        }
        val recognizer = mock<TextRecognizer> {
            on { process(any<InputImage>()) } doReturn Tasks.forResult(text)
        }

        val manager = TextRecognitionManager(recognizer, imageFactory = { mock() })

        val result = manager.recognize(bitmap)

        assertEquals(1, result.size)
        assertEquals("Hello world", result[0].text)
        assertEquals(Rect(10, 20, 100, 60), result[0].boundingBox)
    }

    @Test
    fun `recognize skips blocks with null bounding box`() = runTest {
        val bitmap = mock<Bitmap>()
        val mlKitBlock = mock<Text.TextBlock> {
            on { text } doReturn "No box"
            on { boundingBox } doReturn null
        }
        val text = mock<Text> {
            on { textBlocks } doReturn listOf(mlKitBlock)
        }
        val recognizer = mock<TextRecognizer> {
            on { process(any<InputImage>()) } doReturn Tasks.forResult(text)
        }

        val manager = TextRecognitionManager(recognizer, imageFactory = { mock() })

        val result = manager.recognize(bitmap)

        assertEquals(0, result.size)
    }
}
```

Note: `import org.mockito.kotlin.any` and `org.mockito.kotlin.doReturn` are needed alongside the imports above.

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*TextRecognitionManagerTest*"`
Expected: FAIL — `TextRecognitionManager` does not exist yet.

- [ ] **Step 4: Write `TextRecognitionManager.kt`**

`imageFactory` is injected so the test doesn't need a real `Bitmap`-to-`InputImage` conversion (which requires an Android runtime).

```kotlin
package com.walesson.screentranslator.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import kotlinx.coroutines.tasks.await

class TextRecognitionManager(
    private val recognizer: TextRecognizer,
    private val imageFactory: (Bitmap) -> InputImage = { InputImage.fromBitmap(it, 0) }
) {
    suspend fun recognize(bitmap: Bitmap): List<TextBlock> {
        val image = imageFactory(bitmap)
        val result = recognizer.process(image).await()
        return result.textBlocks.mapNotNull { block ->
            val box = block.boundingBox ?: return@mapNotNull null
            TextBlock(text = block.text, boundingBox = box)
        }
    }
}
```

Add to `app/build.gradle.kts` dependencies (needed for `.await()` on ML Kit's `Task`):

```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*TextRecognitionManagerTest*"`
Expected: PASS (2 tests)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/walesson/screentranslator/ocr app/src/test/java/com/walesson/screentranslator/ocr app/build.gradle.kts
git commit -m "feat: add OCR text recognition manager"
```
