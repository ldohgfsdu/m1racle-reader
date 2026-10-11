package com.m1racle.reader.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m1racle.reader.BuildConfig
import com.m1racle.reader.R
import com.m1racle.reader.ui.home.HomeDesignTokens

/**
 * Read-only guidance stays visibly distinct from actionable settings.
 * [onOpenGlassLab] opens the Glass Lab in every build, including Release.
 */
@Composable
fun HubSettingsScreen(
    onNavigateHub: (HubDestination) -> Unit,
    onOpenGlassLab: (() -> Unit)?,
    onOpenDocumentTools: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var comparingMaterials by rememberSaveable { mutableStateOf(false) }
    if (comparingMaterials) {
        MaterialComparisonScreen(onBack = { comparingMaterials = false }, modifier = modifier)
        return
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    HubGlassShell(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            HubPageHeader(title = stringResource(R.string.hub_settings_title))
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = HomeDesignTokens.PageHorizontal)
                    .padding(top = 12.dp, bottom = HomeDesignTokens.DockContentClearance + navBottom),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(56.dp).background(
                            MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.hub_settings_version, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(28.dp))
                onOpenDocumentTools?.let { open ->
                    Surface(onClick = open, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(20.dp)) {
                            Text("文件与PDF工具", style = MaterialTheme.typography.titleMedium)
                            Text("扫描本机文档、默认查看器、创建、合并、加密与提取文字", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                SettingsGuidance(
                    section = stringResource(R.string.hub_settings_reading_section),
                    icon = Icons.Outlined.Tune,
                    title = stringResource(R.string.hub_settings_reading_title),
                    description = stringResource(R.string.hub_settings_reader_appearance_hint),
                )
                Spacer(Modifier.height(24.dp))
                com.m1racle.reader.ui.components.ReaderFontPicker()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.material_comparison_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp).semantics { heading() })
                Surface(onClick = { comparingMaterials = true }, shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(stringResource(R.string.material_comparison_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.material_comparison_entry_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (onOpenGlassLab != null) {
                    Spacer(Modifier.height(12.dp))
                    Surface(onClick = onOpenGlassLab, shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(20.dp)) {
                            Text(stringResource(R.string.glass_lab_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.glass_lab_entry_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (BuildConfig.QA_GLASS_VARIANTS_ENABLED) {
                    Spacer(Modifier.height(12.dp))
                    QaGlassVariantSettings()
                }
                Spacer(Modifier.height(24.dp))
                SettingsGuidance(
                    section = stringResource(R.string.hub_settings_privacy_section),
                    icon = Icons.Outlined.Lock,
                    title = stringResource(R.string.hub_settings_privacy_title),
                    description = stringResource(R.string.hub_settings_privacy_description),
                )
            }
        }
    }
}

@Composable
private fun SettingsGuidance(section: String, icon: ImageVector, title: String, description: String) {
    Text(section, style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp).semantics { heading() })
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(8.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
