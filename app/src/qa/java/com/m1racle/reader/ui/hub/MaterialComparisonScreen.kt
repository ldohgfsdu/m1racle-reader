package com.m1racle.reader.ui.hub

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.m1racle.reader.R
import com.m1racle.reader.ui.components.PillSegmentedControl
import com.m1racle.reader.ui.home.HomeDesignTokens
import com.m1racle.reader.ui.material.*
import com.m1racle.reader.ui.theme.DockSelectedDark
import com.m1racle.reader.ui.theme.DockSelectedLight

/** Samples use production recipes and their own plain source, outside the hub capture. */
@Composable
internal fun MaterialComparisonScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    var backgroundPosition by rememberSaveable { mutableFloatStateOf(0.5f) }
    var textBackground by rememberSaveable { mutableStateOf(false) }
    val moveLabel = stringResource(R.string.material_comparison_move)
    HubGlassShell(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
                Text(stringResource(R.string.material_comparison_title),
                    style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = HomeDesignTokens.PageHorizontal)
                .padding(top = 12.dp, bottom = HomeDesignTokens.DockContentClearance)) {
                Text(stringResource(R.string.material_comparison_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.material_comparison_background),
                    style = MaterialTheme.typography.labelLarge)
                PillSegmentedControl(
                    labels = listOf(stringResource(R.string.material_comparison_pattern),
                        stringResource(R.string.material_comparison_text)),
                    selectedIndex = if (textBackground) 1 else 0,
                    onSelect = { textBackground = it == 1 },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                BackgroundPositionSlider(backgroundPosition, { backgroundPosition = it }, moveLabel)
                listOf(
                    R.string.material_comparison_pdf to GlassMaterialRole.Capsule,
                    R.string.material_comparison_dock to GlassMaterialRole.Dock,
                    R.string.material_comparison_selection to GlassMaterialRole.SelectionLens,
                ).forEach { (title, role) ->
                    Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    MaterialSample(role, backgroundPosition, textBackground)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** Apple-style appearance; Material Slider owns touch, keyboard and accessibility behavior. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackgroundPositionSlider(value: Float, onValueChange: (Float) -> Unit, label: String) {
    val scheme = MaterialTheme.colorScheme
    val colors = SliderDefaults.colors(
        activeTrackColor = if (LocalGlassEnvironment.current.isDark) DockSelectedDark else DockSelectedLight,
        inactiveTrackColor = scheme.onSurface.copy(alpha = 0.16f),
    )
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.material_comparison_position_percent, (value * 100).roundToInt()),
                style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp))
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            colors = colors,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                contentDescription = label
            },
            thumb = {
                Box(Modifier.width(28.dp).height(48.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(28.dp).shadow(3.dp, CircleShape)
                        .background(Color.White, CircleShape)
                        .border(0.5.dp, Color.Black.copy(alpha = 0.08f), CircleShape))
                }
            },
            track = { state ->
                SliderDefaults.Track(
                    sliderState = state,
                    colors = colors,
                    modifier = Modifier.height(4.dp),
                    thumbTrackGapSize = 0.dp,
                    trackInsideCornerSize = 2.dp,
                    drawStopIndicator = null,
                )
            },
        )
    }
}

@Composable
private fun MaterialSample(role: GlassMaterialRole, backgroundPosition: Float, textBackground: Boolean) {
    val backdrop = rememberLayerBackdrop()
    val host = remember(backdrop) { GlassBackdropHost(backdrop, backdrop) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressLabel = stringResource(R.string.material_comparison_press)
    val scheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val backgroundText = stringResource(R.string.material_comparison_background_text)
    val textLayout = textMeasurer.measure(AnnotatedString(backgroundText.repeat(4)),
        style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, color = scheme.onSurface),
        softWrap = false, maxLines = 1)
    CompositionLocalProvider(LocalGlassBackdropHost provides host) {
        Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center) {
            // Capture only the plain chart. The sample is a sibling, so no render graph recursion.
            GlassBackdropSource(backdrop, Modifier.matchParentSize().clearAndSetSemantics {}) {
                Canvas(Modifier.fillMaxSize()) {
                    if (textBackground) {
                        drawRect(scheme.surface)
                        val rowHeight = textLayout.size.height.toFloat().coerceAtLeast(1f)
                        var y = -rowHeight / 3f
                        while (y < size.height) {
                            drawText(textLayout,
                                topLeft = Offset(-backgroundPosition * size.width, y))
                            y += rowHeight
                        }
                    } else {
                        drawRect(Color(0xFFF0F2F5))
                        val shift = (backgroundPosition - 0.5f) * size.width
                        for (i in -2..3) {
                            val x = i * size.width / 2f + shift
                            drawRect(Color(0xFF427CEB), Offset(x, 0f), Size(size.width / 4f, size.height))
                            drawCircle(Color(0xFFFFA56A), size.height * 0.4f,
                                Offset(x + size.width / 3f, size.height / 2f))
                        }
                        for (i in 1..3) {
                            drawLine(Color.Black.copy(alpha = 0.22f),
                                Offset(0f, i * size.height / 4f),
                                Offset(size.width, i * size.height / 4f), 2.dp.toPx())
                        }
                    }
                }
            }
            Box(Modifier.widthIn(max = 200.dp).fillMaxWidth(0.7f).heightIn(min = 48.dp)
                .glassMaterial(role, RoundedCornerShape(50), pressed = pressed, sourceBackdrop = backdrop)
                .clickable(interactionSource = interaction, indication = null, onClick = {})
                .semantics { contentDescription = pressLabel }
                .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.material_comparison_sample),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
