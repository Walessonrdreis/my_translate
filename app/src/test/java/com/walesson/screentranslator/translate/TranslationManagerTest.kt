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
