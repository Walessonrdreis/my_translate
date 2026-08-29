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
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
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
