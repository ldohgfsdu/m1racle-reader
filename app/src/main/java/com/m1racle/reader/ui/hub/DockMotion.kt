package com.m1racle.reader.ui.hub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/** Navigation is the selection owner; RiseDiaryBottomTabs owns only its optical animation. */
@Immutable
internal class HubDockMotion(val selected: HubDestination)
internal val LocalHubDockMotion = staticCompositionLocalOf<HubDockMotion> {
    error("GlassDock must be inside HubDockMotionProvider")
}
@Composable
internal fun HubDockMotionProvider(selected: HubDestination, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHubDockMotion provides HubDockMotion(selected), content = content)
}
