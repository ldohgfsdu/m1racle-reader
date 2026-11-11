package com.m1racle.reader.pdf

/** Top-of-reader presentation mode (user-visible setting). */
enum class PdfTopBarMode {
    /** Status bar stays visible; edge-to-edge reader, no scrim/gradient painted. */
    NORMAL,

    /** Hide only the status bar; keep the transparent gesture navigation bar usable. */
    IMMERSIVE,
}

