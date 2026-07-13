package com.markleaf.app.ui.pdf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.markleaf.app.data.local.DocumentEntity
import com.markleaf.app.data.local.DocumentType
import com.markleaf.app.pdf.ContinuousDocumentState
import com.markleaf.app.pdf.ContinuousEntryPhase
import com.markleaf.app.pdf.ContinuousEntryState

/** Debug-only host used to exercise a real Activity recreation around the R7 viewport. */
class R7ViewportTestActivity : ComponentActivity() {
    lateinit var model: R7ViewportTestViewModel
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[R7ViewportTestViewModel::class.java]
        setContent {
            MaterialTheme {
                ContinuousDocumentViewport(
                    state = model.ready,
                    pageJump = null,
                    onPageJumpConsumed = {},
                    onViewportWidthPx = {},
                    onContinuousGeometryReady = {},
                    onContinuousEntryRestoreApplied = { _, _ -> },
                    onDocumentStateSettled = model::commitStable,
                    onZoomGestureActive = model::onGestureActiveChanged,
                    onPageSettled = {},
                    onViewportChanged = {},
                    onEnsureAround = {},
                    onRetryPage = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

class R7ViewportTestViewModel : ViewModel() {
    private val restoredTransform = ContinuousDocumentState(
        scale = 2.5f,
        scrollX = 80f,
        scrollY = 900f,
    )

    var ready by mutableStateOf(testReady(restoredTransform))
        private set
    var gestureActive by mutableStateOf(false)
        private set
    val gestureEvents = mutableListOf<Boolean>()
    val stableCommits = mutableListOf<ContinuousDocumentState>()

    fun onGestureActiveChanged(active: Boolean) {
        gestureActive = active
        gestureEvents += active
    }

    fun commitStable(state: ContinuousDocumentState, pageIndex: Int?) {
        stableCommits += state
        ready = ready.copy(
            continuousDocumentState = state,
            zoomScale = state.scale,
            pageIndex = pageIndex ?: ready.pageIndex,
        )
    }

    private fun testReady(stable: ContinuousDocumentState): PdfViewerUiState.Ready =
        PdfViewerUiState.Ready(
            document = DocumentEntity(
                id = "r7-recreate-test",
                displayName = "R7 recreate fake PDF",
                uri = "content://r7-recreate-test",
                mimeType = "application/pdf",
                fileSize = 1L,
                lastOpenedAt = 0L,
                createdAt = 0L,
                lastScrollIndex = 0,
                lastScrollOffset = 0,
                readingProgress = 0f,
                title = null,
                headingCount = 0,
                documentType = DocumentType.PDF.name,
                pageCount = 30,
            ),
            pageIndex = 0,
            pageCount = 30,
            pageImage = null,
            isRendering = false,
            pageAspects = (0 until 30).associateWith { 1f },
            zoomScale = stable.scale,
            continuousDocumentState = stable,
            continuousEntryState = ContinuousEntryState(phase = ContinuousEntryPhase.READY),
        )
}
