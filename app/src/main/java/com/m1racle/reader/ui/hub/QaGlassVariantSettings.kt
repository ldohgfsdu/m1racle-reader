package com.m1racle.reader.ui.hub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m1racle.reader.ui.components.PillSegmentedControl
import com.m1racle.reader.ui.material.QaCompressOrder
import com.m1racle.reader.ui.material.QaDockNightLens
import com.m1racle.reader.ui.material.QaUnifiedBlur
import com.m1racle.reader.ui.material.QaGlass
import com.m1racle.reader.ui.material.QaGlassVariant

/**
 * QA build only (BuildConfig.QA_GLASS_VARIANTS_ENABLED): picks the glass variant
 * for PDF floating controls + home Dock, and the Dock night selected lens.
 * Applies live; persisted in "qa-glass-variants-v1".
 */
@Composable
internal fun QaGlassVariantSettings(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val selection = QaGlass.selection
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().testTag("qa-glass-variants"),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("QA 玻璃档位（仅 QA 包）",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(4.dp))
            Text("PDF 圆钮 / 胶囊 / ⋯ 与首页底栏。立即生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            PillSegmentedControl(
                labels = QaGlassVariant.entries.map { it.label },
                selectedIndex = selection.variant.ordinal,
                onSelect = { index ->
                    QaGlass.update(context, selection.copy(variant = QaGlassVariant.entries[index]))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (selection.variant == QaGlassVariant.UNIFIED) {
                Spacer(Modifier.height(12.dp))
                Text("档 3 模糊",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                PillSegmentedControl(
                    labels = QaUnifiedBlur.entries.map { it.label },
                    selectedIndex = selection.unifiedBlur.ordinal,
                    onSelect = { index ->
                        QaGlass.update(context, selection.copy(unifiedBlur = QaUnifiedBlur.entries[index]))
                    },
                    modifier = Modifier.fillMaxWidth().testTag("qa-unified-blur"),
                )
                Spacer(Modifier.height(12.dp))
                Text("档 3 压缩顺序",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                PillSegmentedControl(
                    labels = QaCompressOrder.entries.map { it.label },
                    selectedIndex = selection.compressOrder.ordinal,
                    onSelect = { index ->
                        QaGlass.update(context, selection.copy(compressOrder = QaCompressOrder.entries[index]))
                    },
                    modifier = Modifier.fillMaxWidth().testTag("qa-compress-order"),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("底栏夜间选中项",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            PillSegmentedControl(
                labels = QaDockNightLens.entries.map { it.label },
                selectedIndex = selection.dockNightLens.ordinal,
                onSelect = { index ->
                    QaGlass.update(context, selection.copy(dockNightLens = QaDockNightLens.entries[index]))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text("PDF 夜间反色",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            PillSegmentedControl(
                labels = listOf("开（现状）", "关（白页）"),
                selectedIndex = if (selection.pdfNightInvert) 0 else 1,
                onSelect = { index ->
                    QaGlass.update(context, selection.copy(pdfNightInvert = index == 0))
                },
                modifier = Modifier.fillMaxWidth().testTag("qa-pdf-night-invert"),
            )
        }
    }
}
