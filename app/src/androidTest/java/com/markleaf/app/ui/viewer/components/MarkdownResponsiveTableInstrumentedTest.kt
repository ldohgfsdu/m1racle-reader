package com.markleaf.app.ui.viewer.components

import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownResponsiveTableInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MarkdownTableQaActivity>()

    @Test
    fun responsiveTablesCaptureEvidenceAndScrollWithoutRemeasure() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("markdown-table").assertIsDisplayed()
        composeRule.onNodeWithTag("markdown-table-fade-left").assertDoesNotExist()
        composeRule.onNodeWithTag("markdown-table-fade-right").assertDoesNotExist()
        assertEquals(1, composeRule.activity.columnMeasurementCount)
        val shortViewport = composeRule.onNodeWithTag("markdown-table-scroll")
            .getUnclippedBoundsInRoot()
        val shortFirst = composeRule.onNodeWithTag("markdown-table-cell-header-0")
            .getUnclippedBoundsInRoot()
        val shortSecond = composeRule.onNodeWithTag("markdown-table-cell-header-1")
            .getUnclippedBoundsInRoot()
        val shortPr = composeRule.onNodeWithTag("markdown-table-cell-body-1-0")
            .getUnclippedBoundsInRoot()
        val shortHeartRate = composeRule.onNodeWithTag("markdown-table-cell-body-0-0")
            .getUnclippedBoundsInRoot()
        assertTrue(shortFirst.right - shortFirst.left < shortSecond.right - shortSecond.left)
        assertTrue(shortSecond.right <= shortViewport.right)
        assertEquals(
            (shortHeartRate.bottom - shortHeartRate.top).value,
            (shortPr.bottom - shortPr.top).value,
            1f,
        )
        saveRootScreenshot("markdown-table-two-column-short.png")

        composeRule.runOnIdle {
            composeRule.activity.showTable(MarkdownTableQaActivity.CASE_THREE_COLUMN)
        }
        composeRule.waitForIdle()
        val afterWideMeasurement = composeRule.activity.columnMeasurementCount
        assertEquals(2, afterWideMeasurement)
        composeRule.onNodeWithTag("markdown-table-fade-left").assertDoesNotExist()
        composeRule.onNodeWithTag("markdown-table-fade-right").assertDoesNotExist()
        val viewportStart = composeRule.onNodeWithTag("markdown-table-scroll")
            .getUnclippedBoundsInRoot()
        val thirdColumnStart = composeRule.onNodeWithTag("markdown-table-cell-header-2")
            .getUnclippedBoundsInRoot()
        val firstColumnStart = composeRule.onNodeWithTag("markdown-table-cell-header-0")
            .getUnclippedBoundsInRoot()
        val secondColumnHeader = composeRule.onNodeWithTag("markdown-table-cell-header-1")
            .getUnclippedBoundsInRoot()
        val shortMedicalDescription = composeRule.onNodeWithTag("markdown-table-cell-body-0-1")
            .getUnclippedBoundsInRoot()
        val firstColumnSingleLine = composeRule.onNodeWithTag("markdown-table-cell-body-0-0")
            .getUnclippedBoundsInRoot()
        val headerBoundsAtStart = (0..2).map { column ->
            composeRule.onNodeWithTag("markdown-table-cell-header-$column")
                .getUnclippedBoundsInRoot()
        }
        val bodyBoundsAtStart = (0..2).map { column ->
            composeRule.onNodeWithTag("markdown-table-cell-body-0-$column")
                .getUnclippedBoundsInRoot()
        }
        val dividerAtStart = composeRule.onNodeWithTag("markdown-table-header-divider")
            .getUnclippedBoundsInRoot()
        val headerMetadataAtStart = composeRule.activity.headerCellMetadata.toSortedMap().toMap()
        val columnWidthsAtStart = (0..2).map { column: Int ->
            val bounds = composeRule.onNodeWithTag("markdown-table-cell-header-$column")
                .getUnclippedBoundsInRoot()
            bounds.right - bounds.left
        }
        val tableBoundsAtStart = composeRule.onNodeWithTag("markdown-table")
            .getUnclippedBoundsInRoot()
        val tableHeight = tableBoundsAtStart.bottom - tableBoundsAtStart.top
        assertEquals(viewportStart.left.value, firstColumnStart.left.value, 1f)
        assertTrue(firstColumnStart.right <= viewportStart.right)
        assertTrue(secondColumnHeader.right <= viewportStart.right)
        assertEquals(
            (firstColumnSingleLine.bottom - firstColumnSingleLine.top).value,
            (shortMedicalDescription.bottom - shortMedicalDescription.top).value,
            1f,
        )
        assertEquals(3, headerMetadataAtStart.size)
        assertEquals(1, headerMetadataAtStart.values.map { it.fontSizeSp }.distinct().size)
        assertEquals(1, headerMetadataAtStart.values.map { it.fontWeight }.distinct().size)
        assertEquals(1, headerMetadataAtStart.values.map { it.lineHeightSp }.distinct().size)
        assertEquals(1, headerMetadataAtStart.values.map { it.letterSpacingSp }.distinct().size)
        assertEquals(1, headerMetadataAtStart.values.map { it.fontFamily }.distinct().size)
        assertEquals(1, headerMetadataAtStart.values.map { it.platformStyle }.distinct().size)
        assertEquals(1, headerMetadataAtStart.values.map { it.textDirection }.distinct().size)
        assertTrue(headerMetadataAtStart.values.all { it.lineCount == 1 })
        assertEquals(1, headerBoundsAtStart.map { it.top.value }.distinct().size)
        assertEquals(1, headerBoundsAtStart.map { it.bottom.value }.distinct().size)
        val density = composeRule.activity.resources.displayMetrics.density
        val globalBaselines = headerBoundsAtStart.mapIndexed { column, bounds ->
            bounds.top.value + headerMetadataAtStart.getValue(column).firstBaselinePx / density
        }
        assertTrue(globalBaselines.max() - globalBaselines.min() <= 1f)
        headerBoundsAtStart.indices.forEach { column ->
            assertEquals(headerBoundsAtStart[column].left.value, bodyBoundsAtStart[column].left.value, 1f)
        }
        assertTrue(dividerAtStart.left <= headerBoundsAtStart.first().left)
        assertTrue(dividerAtStart.right >= headerBoundsAtStart.last().right)
        assertTrue(thirdColumnStart.left < viewportStart.right)
        assertTrue(thirdColumnStart.right > viewportStart.right)
        saveRootScreenshot("markdown-table-three-column-start.png")

        composeRule.onNodeWithTag("markdown-table-scroll").performTouchInput {
            val c = center
            swipe(
                start = Offset(c.x + 250f, c.y),
                end = Offset(c.x - 250f, c.y),
                durationMillis = 300,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("markdown-table-fade-left").assertDoesNotExist()
        composeRule.onNodeWithTag("markdown-table-fade-right").assertDoesNotExist()
        val thirdColumnMiddle = composeRule.onNodeWithTag("markdown-table-cell-header-2")
            .getUnclippedBoundsInRoot()
        assertTrue(thirdColumnMiddle.left < thirdColumnStart.left)
        val tableBoundsInMiddle = composeRule.onNodeWithTag("markdown-table")
            .getUnclippedBoundsInRoot()
        assertEquals(tableHeight, tableBoundsInMiddle.bottom - tableBoundsInMiddle.top)
        saveRootScreenshot("markdown-table-three-column-middle.png")

        composeRule.onNodeWithTag("markdown-table-scroll").performTouchInput {
            val c = center
            swipe(
                start = Offset(c.x + 500f, c.y),
                end = Offset(c.x - 500f, c.y),
                durationMillis = 300,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("markdown-table-fade-left").assertDoesNotExist()
        composeRule.onNodeWithTag("markdown-table-fade-right").assertDoesNotExist()
        composeRule.onNodeWithTag("markdown-table-cell-body-1-2").assertIsDisplayed()
        val viewportEnd = composeRule.onNodeWithTag("markdown-table-scroll")
            .getUnclippedBoundsInRoot()
        val lastColumnEnd = composeRule.onNodeWithTag("markdown-table-cell-header-2")
            .getUnclippedBoundsInRoot()
        assertTrue(lastColumnEnd.left >= viewportEnd.left)
        assertEquals(viewportEnd.right.value, lastColumnEnd.right.value, 1f)
        val columnWidthsAtEnd = (0..2).map { column: Int ->
            val bounds = composeRule.onNodeWithTag("markdown-table-cell-header-$column")
                .getUnclippedBoundsInRoot()
            bounds.right - bounds.left
        }
        assertEquals(columnWidthsAtStart, columnWidthsAtEnd)
        assertEquals(afterWideMeasurement, composeRule.activity.columnMeasurementCount)
        assertEquals(headerMetadataAtStart, composeRule.activity.headerCellMetadata.toSortedMap())
        val headerBoundsAtEnd = (0..2).map { column ->
            composeRule.onNodeWithTag("markdown-table-cell-header-$column")
                .getUnclippedBoundsInRoot()
        }
        assertEquals(
            headerBoundsAtStart.map { it.bottom - it.top },
            headerBoundsAtEnd.map { it.bottom - it.top },
        )
        saveRootScreenshot("markdown-table-three-column-end.png")
    }

    @Test
    fun verboseTwoColumnFitsBothReadableColumnsWithoutHorizontalScroll() {
        composeRule.runOnIdle {
            composeRule.activity.showTable(MarkdownTableQaActivity.CASE_VERBOSE_TWO_COLUMN)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("markdown-table-fade-left").assertDoesNotExist()
        composeRule.onNodeWithTag("markdown-table-fade-right").assertDoesNotExist()
        val viewport = composeRule.onNodeWithTag("markdown-table-scroll")
            .getUnclippedBoundsInRoot()
        val first = composeRule.onNodeWithTag("markdown-table-cell-header-0")
            .getUnclippedBoundsInRoot()
        val second = composeRule.onNodeWithTag("markdown-table-cell-header-1")
            .getUnclippedBoundsInRoot()
        assertTrue((first.right - first.left).value >= 132f)
        assertTrue((second.right - second.left).value >= 132f)
        assertTrue(first.right <= viewport.right)
        assertTrue(second.right <= viewport.right)
        val secondStart = second.left
        saveRootScreenshot("markdown-table-two-column-verbose.png")

        composeRule.onNodeWithTag("markdown-table-scroll").performTouchInput {
            val c = center
            swipe(
                start = Offset(c.x + 450f, c.y),
                end = Offset(c.x - 450f, c.y),
                durationMillis = 300,
            )
        }
        composeRule.waitForIdle()
        val secondAfterSwipe = composeRule.onNodeWithTag("markdown-table-cell-header-1")
            .getUnclippedBoundsInRoot()
        assertEquals(secondStart.value, secondAfterSwipe.left.value, 1f)
        assertTrue(secondAfterSwipe.right <= viewport.right)
    }

    @Test
    fun landscapeWideViewportShowsAllColumnsWithoutScrollAffordances() {
        composeRule.runOnIdle {
            composeRule.activity.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            composeRule.activity.showTable(MarkdownTableQaActivity.CASE_THREE_COLUMN)
        }
        composeRule.waitUntil(8_000) {
            composeRule.activity.resources.configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("markdown-table-fade-left").assertDoesNotExist()
        composeRule.onNodeWithTag("markdown-table-fade-right").assertDoesNotExist()
        val viewport = composeRule.onNodeWithTag("markdown-table-scroll")
            .getUnclippedBoundsInRoot()
        val lastColumn = composeRule.onNodeWithTag("markdown-table-cell-header-2")
            .getUnclippedBoundsInRoot()
        assertTrue(lastColumn.right <= viewport.right)
        assertFalse((viewport.right - viewport.left).value <= 0f)
        saveRootScreenshot("markdown-table-three-column-landscape.png")
    }

    @Test
    fun verticalPageScrollRemainsAvailableWhenGestureStartsInsideTable() {
        composeRule.runOnIdle { composeRule.activity.showVerticalFixture() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("markdown-table").performTouchInput {
            val c = center
            swipe(
                start = Offset(c.x, c.y + 100f),
                end = Offset(c.x, c.y - 100f),
                durationMillis = 300,
            )
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertTrue(composeRule.activity.pageScrollValue > 0) }
    }

    @Test
    fun tableRendersWithBothMarkLeafThemes() {
        composeRule.onNodeWithTag("markdown-table").assertExists()
        composeRule.runOnIdle { composeRule.activity.setDarkTheme(true) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("markdown-table").assertIsDisplayed()
        saveRootScreenshot("markdown-table-dark.png")
    }

    private fun saveRootScreenshot(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = requireNotNull(context.getExternalFilesDir("table-evidence"))
        directory.mkdirs()
        FileOutputStream(File(directory, name)).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
    }

}
