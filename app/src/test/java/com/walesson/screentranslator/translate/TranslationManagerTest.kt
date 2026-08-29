package com.walesson.screentranslator.translate

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.translate.Translator
import com.walesson.screentranslator.ocr.TextBlock
import android.graphics.Rect
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class TranslationManagerTest {

    @Test
    fun `translateAll replaces text but keeps bounding box`() = runTest {
        val translator = mock<Translator> {
            on { translate("Hello") } doReturn Tasks.forResult("Olá")
            on { translate("World") } doReturn Tasks.forResult("Mundo")
        }
        val manager = TranslationManager(translator)
        val firstBox = Rect(0, 0, 10, 10)
        val secondBox = Rect(20, 20, 30, 30)
        val blocks = listOf(
            TextBlock("Hello", firstBox),
            TextBlock("World", secondBox)
        )

        val result = manager.translateAll(blocks)

        assertEquals(listOf("Olá", "Mundo"), result.map { it.text })
        // Identity, not equals(): Rect.equals() is a stubbed no-op under the unit-test android.jar.
        assertSame(firstBox, result[0].boundingBox)
        assertSame(secondBox, result[1].boundingBox)
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
