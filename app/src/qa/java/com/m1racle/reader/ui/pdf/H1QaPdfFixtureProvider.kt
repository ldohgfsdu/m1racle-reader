package com.m1racle.reader.ui.pdf

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Bundle
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

/** QA-only real PDF source. It never reads main-package files or user storage. */
class H1QaPdfFixtureProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        ensureFixture()
        return true
    }

    override fun getType(uri: Uri): String = "application/pdf"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        if (uri.lastPathSegment == "metrics") {
            val snapshot = HorizontalRealPathTelemetry.snapshot()
            return MatrixCursor(
                arrayOf(
                    "gestureFrames",
                    "tileRequests",
                    "tileReady",
                    "tileSwap",
                    "pageAhead",
                    "pointerDown",
                    "pageStripFrames",
                    "pageStripGain",
                    "pageStripReverseDeltaCount",
                    "pageIndexAtPointerDown",
                    "pageIndexDuringPointerDown",
                    "previousSlotId",
                    "currentSlotId",
                    "nextSlotId",
                    "displayedCurrentPageX",
                    "displayedNeighborPageX",
                    "slotRotationCount",
                    "settleActive",
                    "settleStartCount",
                    "offsetRebaseCount",
                    "pagerProgrammaticScrollCount",
                ),
            ).apply {
                addRow(
                    arrayOf(
                        snapshot.gestureFrames,
                        snapshot.tileRequestsBeforeRelease,
                        snapshot.tileReadyBeforeRelease,
                        snapshot.tileSwapBeforeRelease,
                        snapshot.pageAheadRequestsBeforeRelease,
                        if (snapshot.pointerDown) 1 else 0,
                        snapshot.pageStripFrames,
                        snapshot.pageStripDragGain,
                        snapshot.pageStripReverseDeltaCount,
                        snapshot.pageIndexAtPointerDown,
                        snapshot.pageIndexDuringPointerDown,
                        snapshot.previousSlotId,
                        snapshot.currentSlotId,
                        snapshot.nextSlotId,
                        snapshot.displayedCurrentPageX,
                        snapshot.displayedNeighborPageX,
                        snapshot.slotRotationCount,
                        if (snapshot.settleActive) 1 else 0,
                        snapshot.settleStartCount,
                        snapshot.offsetRebaseCount,
                        snapshot.pagerProgrammaticScrollCount,
                    ),
                )
            }
        }
        val file = ensureFixture()
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf(FIXTURE_NAME, file.length()))
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(ensureFixture(), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == "resetHorizontalTelemetry") {
            HorizontalRealPathTelemetry.reset()
            return Bundle.EMPTY
        }
        return super.call(method, arg, extras)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun ensureFixture(): File {
        val context = checkNotNull(context)
        val file = File(context.filesDir, FIXTURE_NAME)
        if (file.exists() && file.length() > 0L) return file
        val document = PdfDocument()
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 15f }
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 23f
            isFakeBoldText = true
        }
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(110, 110, 110); strokeWidth = 1f }
        repeat(3) { pageIndex ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(720, 996, pageIndex + 1).create())
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            canvas.drawRect(24f, 24f, 696f, 972f, rule)
            canvas.drawText("M1racleReader H1.3 Real Gesture Fixture", 48f, 72f, heading)
            canvas.drawText("Page ${pageIndex + 1} / 3", 48f, 106f, body)
            repeat(18) { line ->
                val y = 146f + line * 34f
                canvas.drawText(
                    "Detail ${line + 1}: pan, pinch, edge handoff, tile clarity 0123456789 ABCDEF",
                    48f,
                    y,
                    body,
                )
                canvas.drawLine(48f, y + 9f, 672f, y + 9f, rule)
            }
            val top = 790f
            canvas.drawRect(48f, top, 672f, 930f, rule)
            canvas.drawLine(48f, top + 45f, 672f, top + 45f, rule)
            canvas.drawLine(48f, top + 90f, 672f, top + 90f, rule)
            canvas.drawLine(220f, top, 220f, 930f, rule)
            canvas.drawLine(455f, top, 455f, 930f, rule)
            canvas.drawText("Metric", 64f, top + 30f, body)
            canvas.drawText("Expected", 236f, top + 30f, body)
            canvas.drawText("Observed", 471f, top + 30f, body)
            document.finishPage(page)
        }
        FileOutputStream(file).use(document::writeTo)
        document.close()
        return file
    }

    companion object {
        const val AUTHORITY = "com.m1racle.reader.qa.h1fixture"
        const val FIXTURE_NAME = "h1-real-gesture-fixture.pdf"
        val URI: Uri = Uri.parse("content://$AUTHORITY/$FIXTURE_NAME")
    }
}
