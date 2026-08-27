package com.m1racle.reader.ui.common

import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.view.Window
import androidx.core.view.WindowInsetsControllerCompat

/**
 * OEM SystemUI draws a contrast scrim behind the status bar when it thinks
 * content is not readable — after immersive hide() that often becomes a
 * solid black block on shade pull. Keep bars transparent and skip the scrim.
 */
@Suppress("DEPRECATION")
fun Window.clearContrastScrim(view: View) {
    statusBarColor = Color.TRANSPARENT
    navigationBarColor = Color.TRANSPARENT
    isStatusBarContrastEnforced = false
    isNavigationBarContrastEnforced = false
    val night = (view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    WindowInsetsControllerCompat(this, view).apply {
        isAppearanceLightStatusBars = !night
        isAppearanceLightNavigationBars = !night
    }
}
