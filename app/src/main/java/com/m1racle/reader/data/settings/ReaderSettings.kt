package com.m1racle.reader.data.settings

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * User-adjustable reading display settings (persisted via DataStore).
 *
 * [lineHeightScale] is a **line-height / font-size ratio** applied to body (and
 * heading) text — not a mild multiplier of a fixed sp constant.
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

        /** UI presets for font size chips. */
        const val FONT_SMALL = 0.9f
        const val FONT_DEFAULT = 1.0f
        const val FONT_LARGE = 1.15f

        /**
         * Line-height presets (ratio of lineHeight.sp / fontSize.sp).
         * Compact 1.35 · Default 1.55 · Relaxed 1.75
         */
        const val LINE_COMPACT = 1.35f
        const val LINE_DEFAULT = 1.55f
        const val LINE_RELAXED = 1.75f

        /** Ratios are lh/fontSize; must differ enough to be visible. */
        const val MIN_LINE_HEIGHT_SCALE = 1.20f
        const val MAX_LINE_HEIGHT_SCALE = 2.00f
        const val DEFAULT_LINE_HEIGHT_SCALE = LINE_DEFAULT

        /** Legacy DataStore values from the old mild multiplier scheme. */
        private const val LEGACY_LINE_COMPACT = 0.95f
        private const val LEGACY_LINE_DEFAULT = 1.0f
        private const val LEGACY_LINE_RELAXED = 1.15f

        val Default = ReaderSettings()

        fun clampFontScale(value: Float): Float {
            if (!value.isFinite()) return DEFAULT_FONT_SCALE
            return value.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        }

        /**
         * Map stored value to a current preset ratio, including migration from
         * the previous 0.95 / 1.0 / 1.15 mild scales.
         */
        fun clampLineHeightScale(value: Float): Float {
            if (!value.isFinite()) return DEFAULT_LINE_HEIGHT_SCALE
            // Migrate legacy chip values.
            when {
                near(value, LEGACY_LINE_COMPACT) -> return LINE_COMPACT
                near(value, LEGACY_LINE_DEFAULT) -> return LINE_DEFAULT
                near(value, LEGACY_LINE_RELAXED) -> return LINE_RELAXED
            }
            // Snap to nearest known preset if close.
            when {
                near(value, LINE_COMPACT) -> return LINE_COMPACT
                near(value, LINE_DEFAULT) -> return LINE_DEFAULT
                near(value, LINE_RELAXED) -> return LINE_RELAXED
            }
            return value.coerceIn(MIN_LINE_HEIGHT_SCALE, MAX_LINE_HEIGHT_SCALE)
        }

        private fun near(a: Float, b: Float, eps: Float = 0.03f): Boolean =
            abs(a - b) < eps
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
            /** GPT-style reading stream default side inset. */
            Standard -> 20.dp
            Relaxed -> 28.dp
        }

    companion object {
        fun fromStorage(name: String?): ContentPaddingLevel {
            if (name.isNullOrBlank()) return ContentPaddingLevel.Standard
            return entries.find { it.name == name } ?: ContentPaddingLevel.Standard
        }
    }
}
