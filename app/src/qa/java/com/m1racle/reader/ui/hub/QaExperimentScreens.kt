package com.m1racle.reader.ui.hub

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable internal fun QaMaterialComparisonScreen(onBack: () -> Unit, modifier: Modifier = Modifier) =
    MaterialComparisonScreen(onBack, modifier)

@Composable internal fun QaGlassLabScreen(onBack: () -> Unit) =
    com.m1racle.reader.ui.hub.glasslab.GlassLabScreen(onBack)
