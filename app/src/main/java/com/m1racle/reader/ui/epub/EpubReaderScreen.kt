package com.m1racle.reader.ui.epub

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.data.settings.ReaderSettingsRepository
import com.m1racle.reader.epub.EpubSavedPosition
import com.m1racle.reader.ui.viewer.components.ReaderSettingsSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubReaderScreen(
    documentId: String,
    repository: DocumentRepository,
    readerSettingsRepository: ReaderSettingsRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EpubReaderViewModel = viewModel(
        factory = EpubReaderViewModel.Factory(documentId, repository, readerSettingsRepository),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.readerSettings.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    when (val state = uiState) {
        is EpubUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is EpubUiState.Error -> Scaffold(
            topBar = {
                TopAppBar(title = { Text(state.displayName ?: "电子书") }, navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                })
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(state.message)
                TextButton(onClick = onBack) { Text("返回") }
            }
        }
        is EpubUiState.Success -> {
            val readySettings = settings
            if (readySettings == null) {
                Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                EpubReaderContent(
                    book = state.book,
                    settings = readySettings,
                    title = state.book.metadata.title ?: state.document.displayName,
                    initial = remember(state.document) { EpubSavedPosition.of(state.document) },
                    onPosition = viewModel::saveReadingPosition,
                    onFlush = viewModel::flushReadingPosition,
                    onBack = onBack,
                    onSettings = { showSettings = true },
                    modifier = modifier,
                    restoreSession = requireNotNull(viewModel.restoreSession),
                )
                if (showSettings) ReaderSettingsSheet(
                    settings = readySettings,
                    onFontScale = viewModel::setFontScale,
                    onLineHeightScale = viewModel::setLineHeightScale,
                    onPaddingLevel = viewModel::setContentPaddingLevel,
                    onReset = viewModel::resetReaderSettings,
                    onDismiss = { showSettings = false },
                )
            }
        }
    }
}
