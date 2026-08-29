package com.walesson.screentranslator.ocr

import android.graphics.Rect

data class TextBlock(
    val text: String,
    val boundingBox: Rect
)
