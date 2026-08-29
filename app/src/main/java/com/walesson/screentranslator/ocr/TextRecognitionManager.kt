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
