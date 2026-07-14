package com.markleaf.app

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.markleaf.app.share.ExternalFileIntake
import com.markleaf.app.share.ExternalIntentUri
import com.markleaf.app.ui.navigation.MarkLeafNavGraph
import com.markleaf.app.ui.navigation.Routes
import com.markleaf.app.ui.theme.MarkLeafTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    /** documentId to open after external import; cleared after navigation. */
    private val pendingDocumentId = MutableStateFlow<String?>(null)
    private val pendingIsPdf = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighestRefreshRate()

        val app = application as MarkLeafApplication
        handleIncomingIntent(intent)

        setContent {
            MarkLeafTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    val openId by pendingDocumentId.collectAsStateWithLifecycle()
                    val openPdf by pendingIsPdf.collectAsStateWithLifecycle()

                    LaunchedEffect(openId) {
                        val id = openId ?: return@LaunchedEffect
                        pendingDocumentId.value = null
                        val route = if (openPdf) Routes.pdfViewer(id) else Routes.viewer(id)
                        navController.navigate(route) {
                            launchSingleTop = true
                        }
                    }

                    MarkLeafNavGraph(
                        navController = navController,
                        repository = app.documentRepository,
                        readerSettingsRepository = app.readerSettingsRepository,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        requestHighestRefreshRate()
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

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (!ExternalIntentUri.isExternalOpenAction(intent.action)) return
        val uri = ExternalIntentUri.extract(intent) ?: return

        val app = application as MarkLeafApplication
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                ExternalFileIntake.importSharedUri(
                    context = applicationContext,
                    repository = app.documentRepository,
                    sourceUri = uri,
                    intentType = intent.type,
                )
            }
            when (outcome) {
                is ExternalFileIntake.Outcome.Success -> {
                    pendingIsPdf.value = outcome.isPdf
                    pendingDocumentId.value = outcome.documentId
                }
                ExternalFileIntake.Outcome.Unsupported ->
                    toast(getString(R.string.error_unsupported_format))
                ExternalFileIntake.Outcome.TooLarge ->
                    toast(getString(R.string.error_file_too_large))
                ExternalFileIntake.Outcome.Failed ->
                    toast(getString(R.string.error_read_failed))
                ExternalFileIntake.Outcome.Cancelled -> Unit
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
