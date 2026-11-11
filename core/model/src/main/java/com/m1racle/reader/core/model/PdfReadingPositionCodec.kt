package com.m1racle.reader.core.model

/** Encodes fractions in the existing PDF-only lastScrollOffset column without a schema change. */
object PdfReadingPositionCodec {
    private const val FRACTION_SCALE = 1_000_000

    fun encodeFraction(fraction: Float): Int =
        (fraction.coerceIn(0f, 1f) * FRACTION_SCALE).toInt()

    fun decodeFraction(encoded: Int): Float =
        (encoded.coerceAtLeast(0).toFloat() / FRACTION_SCALE).coerceIn(0f, 1f)
}
