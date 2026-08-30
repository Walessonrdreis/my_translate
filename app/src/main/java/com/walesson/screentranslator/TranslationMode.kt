package com.walesson.screentranslator

import android.content.Context

private const val PREFS_NAME = "screen_translator_prefs"
private const val KEY_TRANSLATION_MODE = "translation_mode"

enum class TranslationMode {
    /** User taps the bubble to translate the current screen once. */
    MANUAL,

    /** Auto-translates whenever scrolling stops for ~1s (requires the accessibility service). */
    CONTINUOUS;

    companion object {
        fun load(context: Context): TranslationMode {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val name = prefs.getString(KEY_TRANSLATION_MODE, MANUAL.name) ?: MANUAL.name
            return runCatching { valueOf(name) }.getOrDefault(MANUAL)
        }

        fun save(context: Context, mode: TranslationMode) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TRANSLATION_MODE, mode.name)
                .apply()
        }
    }
}
