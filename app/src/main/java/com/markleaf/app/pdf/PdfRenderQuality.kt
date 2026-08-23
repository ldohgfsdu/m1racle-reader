package com.markleaf.app.pdf

import kotlin.math.roundToInt

/**
 * Continuous-mode PDF render quality: always widths in **px** (never dp).
 *
 * Buckets (caps are hard limits for continuous mode — no 4096 whole-page bitmaps):
 * - [QualityTier.SCROLL_PREVIEW]: viewport × 0.5, max 640
 * - [QualityTier.PREVIEW]: readable preview, viewport × 1.0, max 1600
 * - [QualityTier.MEDIUM]:  viewport × 2.0,  max 2304
 * - [QualityTier.HIGH]:    viewport × 2.8,  max 3072
 *
 * Horizontal zoom remains 1.0..3.0; R7 continuous zoom is 1.0..4.0.
 * Tier changes use hysteresis to avoid thrashing near thresholds.
 */
enum class QualityTier {
    SCROLL_PREVIEW,
    PREVIEW,
    MEDIUM,
    HIGH,
}

/** Role of a page relative to the primary visible page. */
enum class PageRenderRole {
    /** Main focused page — may use HIGH. */
    PRIMARY,
    /** Other on-screen pages — at most MEDIUM. */
    VISIBLE_OTHER,
    /** ±1 preload off the focus — PREVIEW only. */
    PRELOAD,
}

/** R7.1 vertical-only readable/HIGH hysteresis; horizontal policy is unchanged. */
object ContinuousQualityHysteresis {
    const val UP_TO_HIGH = 1.65f
    const val DOWN_TO_READABLE = 1.35f

    fun resolve(scale: Float, previous: QualityTier): QualityTier = when {
        previous == QualityTier.HIGH && scale >= DOWN_TO_READABLE -> QualityTier.HIGH
        scale > UP_TO_HIGH -> QualityTier.HIGH
        else -> QualityTier.PREVIEW
    }
}

object PdfRenderQuality {
    const val MIN_ZOOM = 1.0f
    const val MAX_ZOOM = 3.0f
    const val DOUBLE_TAP_ZOOM = 2.0f

    const val SCROLL_PREVIEW_FACTOR = 0.5f
    const val PREVIEW_FACTOR = 1.0f
    const val MEDIUM_FACTOR = 2.0f
    const val HIGH_FACTOR = 2.8f

    const val PREVIEW_MAX_PX = 1600
    const val MEDIUM_MAX_PX = 2304
    const val HIGH_MAX_PX = 3072

    /** Absolute continuous-mode width cap (never 4096). */
    const val MAX_CONTINUOUS_WIDTH_PX = HIGH_MAX_PX
    const val MIN_RENDER_WIDTH_PX = 320
    /** Legacy alias — continuous path must use [MAX_CONTINUOUS_WIDTH_PX]. */
    const val MAX_RENDER_WIDTH_PX = MAX_CONTINUOUS_WIDTH_PX

    // Hysteresis (zoom scale)
    const val UP_TO_MEDIUM = 1.45f
    const val DOWN_TO_PREVIEW = 1.25f
    const val UP_TO_HIGH = 2.15f
    const val DOWN_TO_MEDIUM = 1.85f

    /** Pinch end / scroll idle → primary hi-res only. */
    const val ZOOM_SETTLE_DEBOUNCE_MS = 200L
    const val SCROLL_IDLE_UPGRADE_MS = 200L
    const val FAST_SCROLL_THROTTLE_MS = 60L
    const val FAST_SCROLL_HIDE_MS = 800L

    /** At most one HIGH page in memory at a time. */
    const val MAX_HIGH_PAGES = 1
    /** Neighbor PREVIEW slots (left/right of primary). */
    const val MAX_PREVIEW_NEIGHBORS = 2

    /**
     * Continuous scroll path never uses MEDIUM/HIGH for neighbors.
     * Primary upgrades only after idle debounce when zoomed.
     */
    fun continuousNeedsHiRes(zoomScale: Float): Boolean =
        clampContinuousZoom(zoomScale) > 1.01f

    fun continuousPrimaryUpgradeTier(zoomScale: Float, previous: QualityTier): QualityTier {
        if (!continuousNeedsHiRes(zoomScale)) return QualityTier.PREVIEW
        val resolved = resolveContinuousTier(zoomScale, previous)
        // Prefer HIGH when zoomed enough; MEDIUM for mild zoom.
        return when {
            resolved == QualityTier.PREVIEW && clampContinuousZoom(zoomScale) > 1.01f -> QualityTier.MEDIUM
            else -> resolved
        }
    }

    fun clampZoom(zoomScale: Float): Float =
        zoomScale.coerceIn(MIN_ZOOM, MAX_ZOOM)

    /** R7 continuous uses the product-wide 4× contract; horizontal remains 1×..3×. */
    fun clampContinuousZoom(zoomScale: Float): Float =
        zoomScale.coerceIn(MIN_ZOOM, MAX_CONTINUOUS_SCALE)

    /**
     * Resolve quality tier from [zoomScale] with hysteresis against [previous].
     */
    fun resolveTier(zoomScale: Float, previous: QualityTier): QualityTier {
        return resolveTierAtClampedScale(clampZoom(zoomScale), previous)
    }

    /** R7 continuous tier resolution keeps the full 1x..4x product scale. */
    fun resolveContinuousTier(zoomScale: Float, previous: QualityTier): QualityTier =
        resolveTierAtClampedScale(clampContinuousZoom(zoomScale), previous)

    private fun resolveTierAtClampedScale(z: Float, previous: QualityTier): QualityTier {
        return when (previous) {
            QualityTier.SCROLL_PREVIEW -> when {
                z >= UP_TO_HIGH -> QualityTier.HIGH
                z >= UP_TO_MEDIUM -> QualityTier.MEDIUM
                else -> QualityTier.PREVIEW
            }
            QualityTier.PREVIEW -> when {
                z >= UP_TO_HIGH -> QualityTier.HIGH
                z >= UP_TO_MEDIUM -> QualityTier.MEDIUM
                else -> QualityTier.PREVIEW
            }
            QualityTier.MEDIUM -> when {
                z >= UP_TO_HIGH -> QualityTier.HIGH
                z <= DOWN_TO_PREVIEW -> QualityTier.PREVIEW
                else -> QualityTier.MEDIUM
            }
            QualityTier.HIGH -> when {
                z <= DOWN_TO_PREVIEW -> QualityTier.PREVIEW
                z <= DOWN_TO_MEDIUM -> QualityTier.MEDIUM
                else -> QualityTier.HIGH
            }
        }
    }

    fun factorFor(tier: QualityTier): Float = when (tier) {
        QualityTier.SCROLL_PREVIEW -> SCROLL_PREVIEW_FACTOR
        QualityTier.PREVIEW -> PREVIEW_FACTOR
        QualityTier.MEDIUM -> MEDIUM_FACTOR
        QualityTier.HIGH -> HIGH_FACTOR
    }

    fun maxWidthFor(tier: QualityTier): Int = when (tier) {
        QualityTier.SCROLL_PREVIEW -> SCROLL_PREVIEW_MAX_PX
        QualityTier.PREVIEW -> PREVIEW_MAX_PX
        QualityTier.MEDIUM -> MEDIUM_MAX_PX
        QualityTier.HIGH -> HIGH_MAX_PX
    }

    /**
     * Target PdfRenderer bitmap width in **px**.
     */
    fun targetWidthPx(viewportWidthPx: Int, tier: QualityTier): Int {
        val vp = viewportWidthPx.coerceAtLeast(1)
        val raw = (vp * factorFor(tier)).roundToInt()
        return raw.coerceIn(MIN_RENDER_WIDTH_PX, maxWidthFor(tier))
    }

    /**
     * Convenience for primary page at a given zoom (hysteresis starts from [previousTier]).
     */
    fun targetWidthPx(
        viewportWidthPx: Int,
        zoomScale: Float,
        previousTier: QualityTier = QualityTier.PREVIEW,
    ): Int = targetWidthPx(viewportWidthPx, resolveTier(zoomScale, previousTier))

    /** Cap tier by page role (primary may be high; neighbors cannot). */
    fun cappedTier(desired: QualityTier, role: PageRenderRole): QualityTier {
        return when (role) {
            PageRenderRole.PRIMARY -> desired
            PageRenderRole.VISIBLE_OTHER -> minTier(desired, QualityTier.MEDIUM)
            PageRenderRole.PRELOAD -> QualityTier.SCROLL_PREVIEW
        }
    }

    fun minTier(a: QualityTier, b: QualityTier): QualityTier =
        if (a.ordinal <= b.ordinal) a else b

    fun pageRole(pageIndex: Int, primaryIndex: Int): PageRenderRole = when (pageIndex) {
        primaryIndex -> PageRenderRole.PRIMARY
        primaryIndex - 1, primaryIndex + 1 -> PageRenderRole.PRELOAD
        else -> PageRenderRole.PRELOAD
    }

    /**
     * Pages allowed in continuous cache: primary ±1.
     */
    fun keepRange(center: Int, pageCount: Int): IntRange {
        if (pageCount <= 0) return 0..-1
        val c = center.coerceIn(0, pageCount - 1)
        return (c - 1).coerceAtLeast(0)..(c + 1).coerceAtMost(pageCount - 1)
    }

    fun cacheKey(documentId: String, pageIndex: Int, renderWidthBucket: Int): String =
        "$documentId|$pageIndex|$renderWidthBucket"

    /** ARGB_8888 rough size for a portrait page of [widthPx]. */
    fun estimatedBytes(widthPx: Int, aspectHw: Float = 1.414f): Long {
        val w = widthPx.coerceAtLeast(1).toLong()
        val h = (w * aspectHw.coerceIn(0.4f, 3f)).roundToInt().toLong().coerceAtLeast(1L)
        return w * h * 4L
    }
}
    const val SCROLL_PREVIEW_MAX_PX = 640
