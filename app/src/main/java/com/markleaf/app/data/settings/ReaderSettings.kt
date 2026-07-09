package com.markleaf.app.data.settings

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * User-adjustable reading display settings (persisted via DataStore).
 */
data class ReaderSettings(
    val fontScale: Float = DEFAULT_FONT_SCALE,
    val lineHeightScale: Float = DEFAULT_LINE_HEIGHT_SCALE,
    val contentPaddingLevel: ContentPaddingLevel = ContentPaddingLevel.Standard,
) {
    companion object {
        const val MIN_FONT_SCALE = 0.9f
        const val MAX_FONT_SCALE = 1.2f
        const val DEFAULT_FONT_SCALE = 1.0f

        const val MIN_LINE_HEIGHT_SCALE = 0.9f
        const val MAX_LINE_HEIGHT_SCALE = 1.25f
        const val DEFAULT_LINE_HEIGHT_SCALE = 1.0f

        /** UI presets for font size chips. */
        const val FONT_SMALL = 0.9f
        const val FONT_DEFAULT = 1.0f
        const val FONT_LARGE = 1.15f

        /** UI presets for line height chips. */
        const val LINE_COMPACT = 0.95f
        const val LINE_DEFAULT = 1.0f
        const val LINE_RELAXED = 1.15f

        val Default = ReaderSettings()

        fun clampFontScale(value: Float): Float {
            if (!value.isFinite()) return DEFAULT_FONT_SCALE
            return value.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        }

        fun clampLineHeightScale(value: Float): Float {
            if (!value.isFinite()) return DEFAULT_LINE_HEIGHT_SCALE
            return value.coerceIn(MIN_LINE_HEIGHT_SCALE, MAX_LINE_HEIGHT_SCALE)
        }
    }
}

enum class ContentPaddingLevel {
    Compact,
    Standard,
    Relaxed,
    ;

    val horizontalDp: Dp
        get() = when (this) {
            Compact -> 16.dp
            Standard -> 24.dp
            Relaxed -> 32.dp
        }

    companion object {
        fun fromStorage(name: String?): ContentPaddingLevel {
            if (name.isNullOrBlank()) return ContentPaddingLevel.Standard
            return entries.find { it.name == name } ?: ContentPaddingLevel.Standard
        }
    }
}
