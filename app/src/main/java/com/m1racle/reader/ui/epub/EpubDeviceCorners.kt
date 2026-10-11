package com.m1racle.reader.ui.epub

import android.view.RoundedCorner
import android.view.WindowInsets
import android.view.ViewTreeObserver
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView

/** Physical pixels, in physical corner order; never substituted with a guessed dp radius. */
internal data class EpubDeviceCorners(
    val topLeft: Float = 0f,
    val topRight: Float = 0f,
    val bottomRight: Float = 0f,
    val bottomLeft: Float = 0f,
) {
    fun shape(): Shape = AbsoluteRoundedCornerShape(
        topLeft = CornerSize(topLeft),
        topRight = CornerSize(topRight),
        bottomRight = CornerSize(bottomRight),
        bottomLeft = CornerSize(bottomLeft),
    )
}

internal fun epubDeviceCorners(insets: WindowInsets?): EpubDeviceCorners {
    fun radius(position: Int) = insets?.getRoundedCorner(position)?.radius?.toFloat() ?: 0f
    return EpubDeviceCorners(
        radius(RoundedCorner.POSITION_TOP_LEFT), radius(RoundedCorner.POSITION_TOP_RIGHT),
        radius(RoundedCorner.POSITION_BOTTOM_RIGHT), radius(RoundedCorner.POSITION_BOTTOM_LEFT),
    )
}

@Composable
internal fun rememberEpubDeviceCorners(): EpubDeviceCorners {
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    var corners by remember(view) { mutableStateOf(epubDeviceCorners(view.rootWindowInsets)) }
    // Do not replace Compose's inset listener. Layout changes include rotation,
    // multi-window and display changes; the initial inset dispatch may be later
    // than composition, so also observe the first attached layout.
    DisposableEffect(view, configuration) {
        fun refresh() { corners = epubDeviceCorners(view.rootWindowInsets) }
        val listener = ViewTreeObserver.OnGlobalLayoutListener { refresh() }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        refresh()
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    return corners
}
