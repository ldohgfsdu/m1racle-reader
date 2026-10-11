package com.m1racle.reader.ui.epub

import android.app.Activity
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.ViewCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.m1racle.reader.ui.common.clearContrastScrim
import com.m1racle.reader.ui.theme.LocalReaderDarkTheme

/** Match PDF immersion: hide status only, retain the native navigation affordance. */
@Composable
internal fun EpubSystemBars(visible: Boolean) {
    val view = LocalView.current
    val activity = view.context as? Activity ?: return
    val window = activity.window
    val originalCutoutMode = remember(window) { window.attributes.layoutInDisplayCutoutMode }
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentVisible by rememberUpdatedState(visible)
    val darkTheme = LocalReaderDarkTheme.current

    fun apply() {
        if (window.attributes.layoutInDisplayCutoutMode != WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        window.clearContrastScrim(view)
        WindowInsetsControllerCompat(window, view).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            if (currentVisible) show(WindowInsetsCompat.Type.systemBars())
            else {
                show(WindowInsetsCompat.Type.navigationBars())
                hide(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    LaunchedEffect(view, visible, darkTheme) {
        apply()
        if (!visible) while (isActive) {
            // DEFAULT can leave the status bar showing after a shade pull,
            // even without a focus change. Match the PDF's two-second re-hide.
            delay(2_000L)
            if (ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.statusBars()) == true) apply()
        }
    }
    DisposableEffect(view, lifecycleOwner) {
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (focused) apply()
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) apply()
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = originalCutoutMode }
            window.clearContrastScrim(view)
            WindowInsetsControllerCompat(window, view).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
