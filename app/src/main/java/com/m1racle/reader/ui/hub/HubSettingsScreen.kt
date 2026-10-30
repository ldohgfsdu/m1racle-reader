package com.m1racle.reader.ui.hub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m1racle.reader.BuildConfig
import com.m1racle.reader.M1racleReaderApplication
import com.m1racle.reader.R
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.ui.home.HomeDesignTokens
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun HubSettingsScreen(
    onNavigateHub: (HubDestination) -> Unit,
    onOpenDocumentTools: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as? M1racleReaderApplication
    val repository = app?.readerSettingsRepository
    val settings = repository?.settingsFlow?.collectAsState(initial = ReaderSettings.Default)?.value
        ?: ReaderSettings.Default
    val scope = rememberCoroutineScope()
    var comparingMaterials by rememberSaveable { mutableStateOf(false) }
    var appearance by rememberSaveable { mutableStateOf(false) }
    var privacy by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    fun update(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                saveError = "设置未能保存，请重试。"
            }
        }
    }
    if (BuildConfig.QA_GLASS_VARIANTS_ENABLED && comparingMaterials) {
        QaMaterialComparisonScreen(onBack = { comparingMaterials = false }, modifier = modifier)
        return
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scroll = rememberScrollState()
    HubGlassShell(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
            val brand = rememberSettingsBrandMetrics(
                width = maxWidth,
                horizontalPadding = HomeDesignTokens.PageHorizontal,
            )
            val viewportHeight = (maxHeight - brand.collapsedHeight).coerceAtLeast(1.dp)
            val minimumContentHeight = viewportHeight + brand.collapseDistance

            Box(
                Modifier.fillMaxSize()
                    .padding(top = brand.collapsedHeight)
                    .clipToBounds(),
            ) {
                Column(
                    Modifier.fillMaxSize()
                        .testTag("settings-brand-scroll")
                        .verticalScroll(scroll),
                ) {
                    Column(
                        Modifier.fillMaxWidth()
                            .heightIn(min = minimumContentHeight)
                            .padding(horizontal = HomeDesignTokens.PageHorizontal)
                            .padding(bottom = HomeDesignTokens.DockContentClearance + navBottom),
                    ) {
                        Spacer(Modifier.height(brand.collapseDistance))
                        SettingsSections(
                            repositoryAvailable = repository != null,
                            onOpenDocumentTools = onOpenDocumentTools,
                            onAppearance = { appearance = true },
                            onPrivacy = { privacy = true },
                            onAbout = { about = true },
                            onCompareMaterials = { comparingMaterials = true },
                        )
                    }
                }
            }

            SettingsBrandHeader(
                scroll = scroll,
                metrics = brand,
                width = maxWidth,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    if (appearance && repository != null) {
        com.m1racle.reader.ui.viewer.components.ReaderSettingsSheet(
            settings = settings,
            onFontScale = { update { repository.updateFontScale(it) } },
            onLineHeightScale = { update { repository.updateLineHeightScale(it) } },
            onPaddingLevel = { update { repository.updateContentPaddingLevel(it) } },
            onReset = { update { repository.resetToDefaults() } },
            onDismiss = { appearance = false },
        )
    }
    if (privacy) {
        SettingsInformation(
            "数据与隐私",
            stringResource(R.string.hub_settings_privacy_description),
        ) { privacy = false }
    }
    if (about) {
        SettingsInformation(
            "关于 Reader",
            "M1racle Reader\n版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）\n\n" +
                "支持 Markdown、PDF 与 EPUB。",
        ) { about = false }
    }
    saveError?.let { SettingsInformation("设置未保存", it) { saveError = null } }
}

@Composable
private fun SettingsSections(
    repositoryAvailable: Boolean,
    onOpenDocumentTools: (() -> Unit)?,
    onAppearance: () -> Unit,
    onPrivacy: () -> Unit,
    onAbout: () -> Unit,
    onCompareMaterials: () -> Unit,
) {
    SettingsSection("阅读") {
        com.m1racle.reader.ui.components.ReaderFontPicker(entry = { name, open ->
            SettingsRow(Icons.Outlined.FontDownload, "阅读字体", name ?: "默认字体", onClick = open)
        })
        SettingsDivider()
        SettingsRow(
            Icons.Outlined.Tune,
            "阅读显示",
            enabled = repositoryAvailable,
            onClick = onAppearance,
        )
    }
    Spacer(Modifier.height(24.dp))
    onOpenDocumentTools?.let { open ->
        SettingsSection("文件") {
            SettingsRow(
                Icons.Outlined.FolderOpen,
                "文件与 PDF 工具",
                onClick = open,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
    SettingsSection("关于") {
        SettingsRow(Icons.Outlined.Lock, "数据与隐私", onClick = onPrivacy)
        SettingsDivider()
        SettingsRow(Icons.Outlined.Info, "关于 Reader", onClick = onAbout)
    }
    if (BuildConfig.QA_GLASS_VARIANTS_ENABLED) {
        Spacer(Modifier.height(24.dp))
        SettingsSection(stringResource(R.string.material_comparison_section)) {
            SettingsRow(
                Icons.Outlined.Tune,
                stringResource(R.string.material_comparison_title),
                stringResource(R.string.material_comparison_entry_hint),
                onClick = onCompareMaterials,
            )
        }
        Spacer(Modifier.height(12.dp))
        QaGlassVariantSettings()
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp).semantics { heading() },
    )
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, enabled = enabled, color = Color.Transparent) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Normal)
                subtitle?.let { Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                ) }
            }
            Spacer(Modifier.size(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        Modifier.padding(start = 52.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = M1racleReaderTheme.hairline,
    )
}

@Composable
private fun SettingsInformation(title: String, text: String, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = { Text(text, modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = dismiss) { Text("关闭") } },
    )
}
