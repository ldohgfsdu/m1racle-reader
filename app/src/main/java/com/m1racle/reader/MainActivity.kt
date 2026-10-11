package com.m1racle.reader

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.m1racle.reader.share.ExternalOpenPhase
import com.m1racle.reader.share.ExternalOpenUiEffect
import com.m1racle.reader.share.FileSizeFormatter
import com.m1racle.reader.share.PendingExternalOpen
import com.m1racle.reader.ui.common.clearContrastScrim
import com.m1racle.reader.ui.material.GlassAlertDialog
import com.m1racle.reader.ui.navigation.M1racleReaderNavGraph
import com.m1racle.reader.ui.navigation.Routes
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import com.m1racle.reader.ui.viewer.ReaderPerformanceExperiments
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyEdgeToEdge()
        requestHighestRefreshRate()
        ReaderPerformanceExperiments.configure(intent)
        // QA build only; a no-op elsewhere.
        com.m1racle.reader.ui.material.QaGlass.load(this)

        val app = application as M1racleReaderApplication
        val coordinator = app.externalOpenCoordinator

        // Always dispatch the launch Intent. A restored Activity can belong either
        // to the same process (rotation: coordinator already handled it) or a new
        // process (coordinator is empty and must recover it). The coordinator owns
        // that distinction; skipping here loses external opens after process death.
        lifecycleScope.launch {
            coordinator.onIncomingIntent(
                intent,
                allowActivityRecreateReplay = savedInstanceState == null,
            )
        }

        setContent {
            M1racleReaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    val pending by coordinator.pending.collectAsStateWithLifecycle()
                    val busy by coordinator.busy.collectAsStateWithLifecycle()

                    LaunchedEffect(Unit) {
                        coordinator.effects.collectLatest { effect ->
                            when (effect) {
                                is ExternalOpenUiEffect.Toast -> toast(effect.message)
                                is ExternalOpenUiEffect.ToastUnsupported -> {
                                    toast(
                                        getString(
                                            R.string.error_unsupported_format_detail,
                                            effect.fileName,
                                            effect.mime ?: "unknown",
                                        ),
                                    )
                                }
                                ExternalOpenUiEffect.ToastSendMultiple ->
                                    toast(getString(R.string.error_send_multiple_not_supported))
                                ExternalOpenUiEffect.ToastTextRequiresFile ->
                                    toast(getString(R.string.error_send_text_requires_file))
                            }
                        }
                    }

                    // Replay-latest navigation: survives the no-subscriber window of a
                    // rotation. ack even if navigation throws, so a request never wedges.
                    LaunchedEffect(Unit) {
                        coordinator.pendingNavigation.collectLatest { req ->
                            if (req != null) {
                                try {
                                    val current = navController.currentBackStackEntry
                                    if (current?.destination?.route == Routes.PDF_EDITOR) {
                                        if (current.arguments?.getString("documentId") == req.documentId) {
                                            return@collectLatest
                                        }
                                        toast("文档已导入，退出正文编辑后打开")
                                        navController.currentBackStackEntryFlow.first {
                                            it.destination.route != Routes.PDF_EDITOR
                                        }
                                    }
                                    navigateReplaceViewer(
                                        navController = navController,
                                        documentId = req.documentId,
                                        documentType = req.documentType,
                                        bringToFrontOnly = req.bringToFrontOnly,
                                    )
                                } finally {
                                    coordinator.ackNavigation(req.requestId)
                                }
                            }
                        }
                    }

                    M1racleReaderNavGraph(
                        navController = navController,
                        repository = app.documentRepository,
                        readerSettingsRepository = app.readerSettingsRepository,
                        externalOpenCoordinator = coordinator,
                    )

                    pending?.let { p ->
                        when (p.phase) {
                            ExternalOpenPhase.AWAITING_CONFIRM -> {
                                ExternalOpenConfirmDialog(
                                    pending = p,
                                    onCancel = {
                                        lifecycleScope.launch { coordinator.cancelPending() }
                                    },
                                    onConfirm = {
                                        // Do NOT pop HOME here — prepare keeps old viewer;
                                        // commit emits a single atomic navigate-replace.
                                        lifecycleScope.launch { coordinator.confirmPending() }
                                    },
                                )
                            }
                            ExternalOpenPhase.PREPARING,
                            ExternalOpenPhase.COMMITTING,
                            ExternalOpenPhase.READY_TO_COMMIT,
                            -> {
                                if (busy) {
                                    GlassAlertDialog(
                                        onDismissRequest = {},
                                        title = { Text(stringResource(R.string.external_import_loading_title)) },
                                        text = {
                                            Column {
                                                Text(stringResource(R.string.external_import_loading_body))
                                                Spacer(Modifier.height(16.dp))
                                                CircularProgressIndicator()
                                            }
                                        },
                                        confirmButton = {},
                                    )
                                }
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val app = application as M1racleReaderApplication
        // Fresh external delivery: allow same URI again after cancel/success.
        app.externalOpenCoordinator.clearEventHandledForNewDelivery(intent)
        lifecycleScope.launch {
            app.externalOpenCoordinator.onIncomingIntent(intent, allowActivityRecreateReplay = true)
        }
    }

    override fun onResume() {
        super.onResume()
        requestHighestRefreshRate()
    }

    /**
     * Night mode must not recreate the activity: PDF/Markdown would remount
     * at page 0 (list/pager state is not saveable; restore can be clobbered
     * by the first viewport callback). Compose already swaps [M1racleReaderTheme]
     * via [androidx.compose.foundation.isSystemInDarkTheme].
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyEdgeToEdge()
        requestHighestRefreshRate()
    }

    private fun applyEdgeToEdge() {
        // Fully transparent system bars so reader content can draw behind status icons (X-style).
        // Zero light/dark scrims — no OEM "status bar protection" band from Activity defaults.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = AndroidColor.TRANSPARENT,
                darkScrim = AndroidColor.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = AndroidColor.TRANSPARENT,
                darkScrim = AndroidColor.TRANSPARENT,
            ),
        )
        window.clearContrastScrim(window.decorView)
    }

    /**
     * Single navigation transaction: pop existing viewer destinations under HOME, then open
     * the target. Avoids intermediate empty-HOME-only state before import completes.
     */
    private fun navigateReplaceViewer(
        navController: NavHostController,
        documentId: String,
        documentType: com.m1racle.reader.data.local.DocumentType,
        bringToFrontOnly: Boolean,
    ) {
        val route = when (documentType) {
            com.m1racle.reader.data.local.DocumentType.PDF -> Routes.pdfViewer(documentId)
            com.m1racle.reader.data.local.DocumentType.EPUB -> Routes.epubViewer(documentId)
            else -> Routes.viewer(documentId)
        }
        if (bringToFrontOnly) {
            val current = navController.currentBackStackEntry?.destination?.route
            if (current == Routes.PDF_VIEWER || current == Routes.VIEWER || current == Routes.EPUB_VIEWER ||
                current?.startsWith("pdf/") == true || current?.startsWith("viewer/") == true ||
                current?.startsWith("epub/") == true
            ) {
                // Already on a viewer; re-navigate with singleTop + same id restores foreground.
                navController.navigate(route) {
                    launchSingleTop = true
                    restoreState = true
                }
                return
            }
        }
        navController.navigate(route) {
            popUpTo(Routes.HOME) {
                inclusive = false
                saveState = false
            }
            launchSingleTop = true
        }
    }

    private fun requestHighestRefreshRate() {
        val targetDisplay = display ?: return
        val currentMode = targetDisplay.mode
        val preferred = selectHighestRefreshMode(
            currentWidth = currentMode.physicalWidth,
            currentHeight = currentMode.physicalHeight,
            candidates = targetDisplay.supportedModes.map { mode ->
                DisplayModeCandidate(
                    modeId = mode.modeId,
                    width = mode.physicalWidth,
                    height = mode.physicalHeight,
                    refreshRate = mode.refreshRate,
                )
            },
        ) ?: return
        val attributes = window.attributes
        if (attributes.preferredDisplayModeId == preferred.modeId &&
            attributes.preferredRefreshRate == preferred.refreshRate
        ) {
            return
        }
        attributes.preferredDisplayModeId = preferred.modeId
        attributes.preferredRefreshRate = preferred.refreshRate
        if (Build.VERSION.SDK_INT >= 35) {
            attributes.setFrameRateBoostOnTouchEnabled(true)
            attributes.setFrameRatePowerSavingsBalanced(false)
        }
        window.attributes = attributes
        if (com.m1racle.reader.BuildConfig.READER_PERFORMANCE_EXPERIMENTS_ENABLED) {
            Log.i(
                "MARKDOWN_REFRESH",
                "preferredModeId=${preferred.modeId} refreshRate=${preferred.refreshRate}",
            )
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun ExternalOpenConfirmDialog(
    pending: PendingExternalOpen,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val current = pending.current
    val incoming = pending.incoming
    GlassAlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.external_open_confirm_title)) },
        text = {
            Column {
                Text(stringResource(R.string.external_open_confirm_current_label))
                Text(
                    text = current?.displayName ?: stringResource(R.string.unnamed_document),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = FileSizeFormatter.formatForConfirmDialog(
                        current?.sizeBytes,
                        sizeUnknownUntilOpen = false,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.external_open_confirm_new_label))
                Text(
                    text = incoming.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = FileSizeFormatter.formatForConfirmDialog(
                        incoming.sizeBytes,
                        incoming.sizeUnknownUntilOpen,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.external_open_confirm_hint))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.external_open_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.external_open_confirm_action))
            }
        },
    )
}
