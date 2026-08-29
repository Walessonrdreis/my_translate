package com.walesson.screentranslator.overlay

import android.graphics.Rect
import com.walesson.screentranslator.ocr.TextBlock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationOverlayHitTestTest {

    /**
     * Builds a Rect by assigning its public fields rather than via the 4-arg constructor:
     * under the unit-test android.jar (returnDefaultValues) constructor bodies are stubbed out,
     * so only direct field writes reliably carry values. Field reads are unaffected.
     */
    private fun rect(left: Int, top: Int, right: Int, bottom: Int) = Rect().also {
        it.left = left
        it.top = top
        it.right = right
        it.bottom = bottom
    }

    private val blocks = listOf(
        TextBlock("Olá", rect(0, 0, 100, 50)),
        TextBlock("Mundo", rect(200, 200, 300, 250))
    )

    @Test
    fun `point inside a block is not outside all blocks`() {
        assertFalse(isOutsideAllBlocks(50f, 25f, blocks))
    }

    @Test
    fun `point outside every block is outside all blocks`() {
        assertTrue(isOutsideAllBlocks(500f, 500f, blocks))
    }

    @Test
    fun `empty block list means every point is outside`() {
        assertTrue(isOutsideAllBlocks(10f, 10f, emptyList()))
    }
}
