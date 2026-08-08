package com.markleaf.app

import android.content.Intent
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
import androidx.compose.material3.AlertDialog
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
import com.markleaf.app.share.ExternalOpenPhase
import com.markleaf.app.share.ExternalOpenUiEffect
import com.markleaf.app.share.FileSizeFormatter
import com.markleaf.app.share.PendingExternalOpen
import com.markleaf.app.ui.navigation.MarkLeafNavGraph
import com.markleaf.app.ui.navigation.Routes
import com.markleaf.app.ui.theme.MarkLeafTheme
import com.markleaf.app.ui.viewer.ReaderPerformanceExperiments
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        requestHighestRefreshRate()
        ReaderPerformanceExperiments.configure(intent)

        val app = application as MarkLeafApplication
        val coordinator = app.externalOpenCoordinator

        // Only cold-start / process recreate with a fresh task should consume the
        // launch Intent. Rotation (savedInstanceState != null) must not re-fire.
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                coordinator.onIncomingIntent(intent, allowActivityRecreateReplay = true)
            }
        }

        setContent {
            MarkLeafTheme {
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
                                is ExternalOpenUiEffect.NavigateToDocument -> {
                                    navigateReplaceViewer(
                                        navController = navController,
                                        documentId = effect.documentId,
                                        isPdf = effect.isPdf,
                                        bringToFrontOnly = effect.bringToFrontOnly,
                                    )
                                }
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
                            }
                        }
                    }

                    MarkLeafNavGraph(
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
                                    AlertDialog(
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
        val app = application as MarkLeafApplication
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
     * Single navigation transaction: pop existing viewer destinations under HOME, then open
     * the target. Avoids intermediate empty-HOME-only state before import completes.
     */
    private fun navigateReplaceViewer(
        navController: NavHostController,
        documentId: String,
        isPdf: Boolean,
        bringToFrontOnly: Boolean,
    ) {
        val route = if (isPdf) Routes.pdfViewer(documentId) else Routes.viewer(documentId)
        if (bringToFrontOnly) {
            val current = navController.currentBackStackEntry?.destination?.route
            if (current == Routes.PDF_VIEWER || current == Routes.VIEWER ||
                current?.startsWith("pdf/") == true || current?.startsWith("viewer/") == true
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
        Log.i(
            "MARKDOWN_REFRESH",
            "preferredModeId=${preferred.modeId} refreshRate=${preferred.refreshRate}",
        )
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
    AlertDialog(
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
