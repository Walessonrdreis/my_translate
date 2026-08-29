### Task 4: `TranslationManager` (on-device translation)

**Files:**
- Create: `app/src/main/java/com/walesson/screentranslator/translate/TranslationManager.kt`
- Test: `app/src/test/java/com/walesson/screentranslator/translate/TranslationManagerTest.kt`

**Interfaces:**
- Consumes: `com.walesson.screentranslator.ocr.TextBlock` (from Task 3), ML Kit's `com.google.mlkit.nl.translate.Translator`.
- Produces: `class TranslationManager(private val translator: Translator)` with `suspend fun translateAll(blocks: List<TextBlock>): List<TextBlock>` (returns new `TextBlock`s with `text` replaced by the translation, same `boundingBox`), and `suspend fun ensureModelDownloaded(): Result<Unit>`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.walesson.screentranslator.translate

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.translate.Translator
import com.walesson.screentranslator.ocr.TextBlock
import android.graphics.Rect
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TranslationManagerTest {

    @Test
    fun `translateAll replaces text but keeps bounding box`() = runTest {
        val translator = mock<Translator> {
            on { translate("Hello") } doReturn Tasks.forResult("Olá")
            on { translate("World") } doReturn Tasks.forResult("Mundo")
        }
        val manager = TranslationManager(translator)
        val blocks = listOf(
            TextBlock("Hello", Rect(0, 0, 10, 10)),
            TextBlock("World", Rect(20, 20, 30, 30))
        )

        val result = manager.translateAll(blocks)

        assertEquals(listOf("Olá", "Mundo"), result.map { it.text })
        assertEquals(listOf(Rect(0, 0, 10, 10), Rect(20, 20, 30, 30)), result.map { it.boundingBox })
    }

    @Test
    fun `translateAll returns empty list for empty input`() = runTest {
        val translator = mock<Translator>()
        val manager = TranslationManager(translator)

        val result = manager.translateAll(emptyList())

        assertEquals(emptyList<TextBlock>(), result)
    }

    @Test
    fun `ensureModelDownloaded returns failure when download fails`() = runTest {
        val translator = mock<Translator> {
            on { downloadModelIfNeeded() } doReturn Tasks.forException(RuntimeException("no network"))
        }
        val manager = TranslationManager(translator)

        val result = manager.ensureModelDownloaded()

        assert(result.isFailure)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*TranslationManagerTest*"`
Expected: FAIL — `TranslationManager` does not exist yet.

- [ ] **Step 3: Write `TranslationManager.kt`**

```kotlin
package com.walesson.screentranslator.translate

import com.google.mlkit.nl.translate.Translator
import com.walesson.screentranslator.ocr.TextBlock
import kotlinx.coroutines.tasks.await

class TranslationManager(private val translator: Translator) {

    suspend fun ensureModelDownloaded(): Result<Unit> {
        return try {
            translator.downloadModelIfNeeded().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun translateAll(blocks: List<TextBlock>): List<TextBlock> {
        return blocks.map { block ->
            val translated = translator.translate(block.text).await()
            block.copy(text = translated)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "*TranslationManagerTest*"`
Expected: PASS (3 tests)

- [ ] **Step 5: Add a factory for the real ML Kit translator (used by Task 8, not unit-tested — ML Kit's builder is a thin wrapper)**

`app/src/main/java/com/walesson/screentranslator/translate/TranslatorFactory.kt`

```kotlin
package com.walesson.screentranslator.translate

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

object TranslatorFactory {
    fun createEnglishToPortuguese() = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.PORTUGUESE)
            .build()
    )
}
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/walesson/screentranslator/translate app/src/test/java/com/walesson/screentranslator/translate
git commit -m "feat: add on-device translation manager"
```
