package com.markleaf.app.pdf

/**
 * Fullscreen-immersive system-bar policy.
 *
 * Transient swipe-to-reveal is not used: a downward swipe on the page would
 * flash a system scrim and jump the PDF. Shade-pull still needs a delayed
 * re-hide because DEFAULT leaves the status bar stuck once shown.
 */
object PdfImmersiveSystemBars {
    const val REHIDE_DELAY_MS = 2_000L

    fun shouldReassertHide(mode: PdfTopBarMode, statusBarVisiblePx: Int): Boolean =
        mode == PdfTopBarMode.IMMERSIVE && statusBarVisiblePx > 0
}
