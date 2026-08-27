package com.m1racle.reader.ui.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import com.m1racle.reader.BuildConfig
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.local.DocumentType
import com.m1racle.reader.pdf.PdfReadingMode
import com.m1racle.reader.pdf.PdfViewportSessionState
import com.m1racle.reader.ui.theme.M1racleReaderTheme

/** QA-build-only host for deterministic production horizontal-view interaction acceptance. */
class H1QaHarnessActivity : ComponentActivity() {
    private val interactionProbe = HorizontalInteractionProbe()
    private var pageIndex by mutableIntStateOf(INITIAL_PAGE)
    @Volatile private var viewportSession: PdfViewportSessionState? = null
    private val pageBitmap by lazy {
        Bitmap.createBitmap(720, 996, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }.asImageBitmap()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentInstance = this
        check(BuildConfig.APPLICATION_ID == "com.m1racle.reader.qa") {
            "H1 QA harness must never run in the main package"
        }
        setContent {
            M1racleReaderTheme {
                PdfHorizontalPagedArea(
                    state = readyState(pageIndex),
                    pageJump = null,
                    onToggleChrome = {},
                    onPrevious = { if (pageIndex > 0) pageIndex -= 1 },
                    onNext = { if (pageIndex < PAGE_COUNT - 1) pageIndex += 1 },
                    onPageLabelClick = {},
                    onViewportWidthPx = {},
                    onPageChanged = { pageIndex = it },
                    onZoomScaleSettled = {},
                    onViewportSessionSettled = { viewportSession = it },
                    qaProbe = interactionProbe,
                )
            }
        }
    }

    override fun onDestroy() {
        if (currentInstance === this) currentInstance = null
        super.onDestroy()
    }

    internal fun interactionSnapshot(): HorizontalInteractionSnapshot = interactionProbe.snapshot()
    internal fun viewportSessionSnapshot(): PdfViewportSessionState? = viewportSession

    internal fun contentBoundsOnScreen(): Rect {
        val content = findViewById<android.view.View>(android.R.id.content)
        val location = IntArray(2)
        content.getLocationOnScreen(location)
        return Rect(
            location[0],
            location[1],
            location[0] + content.width,
            location[1] + content.height,
        )
    }

    internal fun densityValue(): Float = resources.displayMetrics.density

    private fun readyState(page: Int): PdfViewerUiState.Ready = PdfViewerUiState.Ready(
        document = DocumentEntity(
            id = "h1-qa-horizontal",
            displayName = "H1 QA PDF",
            uri = "content://h1-qa-horizontal",
            mimeType = "application/pdf",
            fileSize = 1L,
            lastOpenedAt = 0L,
            createdAt = 0L,
            lastScrollIndex = page,
            lastScrollOffset = 0,
            readingProgress = page.toFloat() / (PAGE_COUNT - 1),
            title = null,
            headingCount = 0,
            documentType = DocumentType.PDF.name,
            pageCount = PAGE_COUNT,
        ),
        pageIndex = page,
        pageCount = PAGE_COUNT,
        pageImage = pageBitmap,
        isRendering = false,
        readingMode = PdfReadingMode.HORIZONTAL_PAGED,
        pageBitmaps = (0 until PAGE_COUNT).associateWith { pageBitmap },
        pageAspects = (0 until PAGE_COUNT).associateWith { 1.3833f },
    )

    companion object {
        const val INITIAL_PAGE = 2
        const val PAGE_COUNT = 5
        @Volatile
        internal var currentInstance: H1QaHarnessActivity? = null
    }
}
