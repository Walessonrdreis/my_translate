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
