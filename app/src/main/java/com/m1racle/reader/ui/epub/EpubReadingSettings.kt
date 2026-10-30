package com.m1racle.reader.ui.epub

import com.m1racle.reader.ui.components.readerHairlineBorder
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m1racle.reader.data.settings.ContentPaddingLevel
import com.m1racle.reader.data.settings.ReaderSettings
import kotlin.math.abs

/** Reader-specific compact card, grounded in the supplied reference's visual hierarchy. */
@Composable
internal fun EpubReadingSettings(
    settings: ReaderSettings,
    onFontScale: (Float) -> Unit,
    onLineHeightScale: (Float) -> Unit,
    onPaddingLevel: (ContentPaddingLevel) -> Unit,
    onReset: () -> Unit,
    onFontFamily: (com.m1racle.reader.data.settings.EpubFontFamily) -> Unit = {},
    fontLibrary: @Composable () -> Unit = {},
    onReadingMode: (com.m1racle.reader.data.settings.EpubReadingMode) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text("字体", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        com.m1racle.reader.ui.components.PillSegmentedControl(
            labels = listOf("默认", "宋体", "系统"),
            selectedIndex = if (settings.importedFontId == null) settings.epubFontFamily.ordinal else -1,
            onSelect = { onFontFamily(com.m1racle.reader.data.settings.EpubFontFamily.entries[it]) },
            modifier = Modifier.fillMaxWidth().testTag("epub-font-family"),
        )
        fontLibrary()
        Spacer(Modifier.height(12.dp))
        Text("字号", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val values = listOf(ReaderSettings.FONT_SMALL, ReaderSettings.FONT_DEFAULT, ReaderSettings.FONT_LARGE)
            listOf("小", "默认", "大").forEachIndexed { i, label ->
                val active = abs(settings.fontScale - values[i]) < 0.02f
                Column(Modifier.weight(1f).heightIn(min = 48.dp).testTag("epub-font-$i")
                    .background(if (active) scheme.primaryContainer else scheme.surfaceContainer, RoundedCornerShape(8.dp))
                    .semantics { selected = active }
                    .clickable(role = Role.RadioButton) { onFontScale(values[i]) }
                    .padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, style = MaterialTheme.typography.labelLarge,
                        color = if (active) scheme.primary else scheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 18.dp), color = scheme.outlineVariant.copy(alpha = .35f), thickness = M1racleReaderTheme.hairline)
        var linePreview by remember(settings.lineHeightScale) { mutableFloatStateOf(settings.lineHeightScale) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("行距", style = MaterialTheme.typography.labelLarge)
            Text("%.2f".format(linePreview), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        }
        Slider(value = linePreview, onValueChange = { linePreview = it },
            onValueChangeFinished = { onLineHeightScale(linePreview) },
            valueRange = ReaderSettings.MIN_LINE_HEIGHT_SCALE..ReaderSettings.MAX_LINE_HEIGHT_SCALE,
            modifier = Modifier.fillMaxWidth().testTag("epub-line-slider"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("紧凑", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Text("宽松", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
        HorizontalDivider(Modifier.padding(vertical = 18.dp), color = scheme.outlineVariant.copy(alpha = .35f), thickness = M1racleReaderTheme.hairline)
        Text("页边距", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ContentPaddingLevel.entries.forEachIndexed { i, value ->
                val active = settings.contentPaddingLevel == value
                val label = listOf("紧凑", "标准", "宽松")[i]
                Column(Modifier.weight(1f).testTag("epub-margin-$i")
                    .semantics { selected = active }
                    .clickable(role = Role.RadioButton) { onPaddingLevel(value) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(56.dp)
                        .background(if (active) scheme.primaryContainer else scheme.surfaceContainer, RoundedCornerShape(8.dp))
                        .readerHairlineBorder(if (active) scheme.primary else Color.Transparent, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Article, null, modifier = Modifier.size((28 - i * 4).dp, 32.dp),
                            tint = if (active) scheme.primary else scheme.onSurfaceVariant)
                    }
                    Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp),
                        color = if (active) scheme.primary else scheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("阅读方式", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        com.m1racle.reader.ui.components.PillSegmentedControl(
            labels = listOf("滑动", "连续", "卷页"),
            selectedIndex = settings.epubReadingMode.ordinal,
            onSelect = { onReadingMode(com.m1racle.reader.data.settings.EpubReadingMode.entries[it]) },
            modifier = Modifier.fillMaxWidth().testTag("epub-reading-mode"),
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(Modifier.padding(top = 18.dp, bottom = 4.dp), color = scheme.outlineVariant.copy(alpha = .35f), thickness = M1racleReaderTheme.hairline)
        TextButton(onClick = onReset, modifier = Modifier.align(Alignment.End)) { Text("恢复默认") }
    }
}
