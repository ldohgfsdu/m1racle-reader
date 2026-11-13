package com.m1racle.reader.ui.annotation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.m1racle.reader.core.model.NormalizedRect
import com.m1racle.reader.design.RadixColors
import com.m1racle.reader.pdf.PdfSelectableGlyph
import com.m1racle.reader.pdf.PdfSelectableLine
import com.m1racle.reader.pdf.PdfSelectablePage
import com.m1racle.reader.pdf.PdfTextSelectionRange
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "w480dp-h640dp-mdpi")
class PdfSelectionReferenceRenderTest {
    @get:Rule val rule = createComposeRule()

    private data class RenderedHandle(
        val point: PdfSelectionHandlePoint,
        val center: Offset,
        val tip: Offset,
    )

    private data class SelectionCapture(
        val bitmap: Bitmap,
        val handles: Map<PdfSelectionHandle, RenderedHandle>,
    )

    private val page = PdfSelectablePage(
        page = 0,
        width = 300f,
        height = 400f,
        lines = listOf(
            PdfSelectableLine(
                glyphs = listOf(
                    PdfSelectableGlyph("中", NormalizedRect(.16f, .23f, .065f, .06f)),
                    PdfSelectableGlyph("文", NormalizedRect(.235f, .23f, .065f, .06f)),
                    PdfSelectableGlyph("阅", NormalizedRect(.31f, .23f, .065f, .06f)),
                    PdfSelectableGlyph("读", NormalizedRect(.385f, .23f, .065f, .06f)),
                ),
                flowLeft = .16f,
                flowRight = .45f,
            ),
            PdfSelectableLine(
                glyphs = listOf(
                    PdfSelectableGlyph("这", NormalizedRect(.16f, .32f, .065f, .06f)),
                    PdfSelectableGlyph("是", NormalizedRect(.235f, .32f, .065f, .06f)),
                    PdfSelectableGlyph("一", NormalizedRect(.31f, .32f, .065f, .06f)),
                    PdfSelectableGlyph("页", NormalizedRect(.385f, .32f, .065f, .06f)),
                ),
                flowLeft = .16f,
                flowRight = .45f,
            ),
        ),
    )
    private val range = PdfTextSelectionRange(
        anchor = PdfSelectablePage.Hit(0, 0),
        focus = PdfSelectablePage.Hit(1, 3),
    )

    @Test
    fun `renders day night and zoomed selection handles from the production layer`() {
        val dark = mutableStateOf(false)
        val scale = mutableFloatStateOf(1f)
        val session = PdfTextSelectionSession()
        lateinit var view: View
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bundledFont = "fonts/NotoSerifCJKsc-Regular.otf"
        val referenceTypeface = android.graphics.Typeface.createFromAsset(context.assets, bundledFont)
        val resolvedTypeface = requireNotNull(referenceTypeface)
        val familyName = referenceTypeface.toString()
        rule.setContent {
            M1racleReaderTheme(darkTheme = dark.value) {
                // A fixed viewport captures the zoomed page beyond its unscaled layout box.
                Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .size(300.dp, 400.dp)
                        .graphicsLayer {
                            scaleX = scale.floatValue
                            scaleY = scale.floatValue
                            transformOrigin = TransformOrigin(0f, 0f)
                        },
                ) {
                    Canvas(
                        Modifier
                            .matchParentSize()
                            .background(if (dark.value) Color(0xFF151515) else Color.White)
                            .testTag("reference-page"),
                    ) {
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            typeface = resolvedTypeface
                            textSize = size.height * .055f
                            color = if (dark.value) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                        }
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                "中文阅读",
                                size.width * .16f,
                                size.height * .285f,
                                paint,
                            )
                            canvas.nativeCanvas.drawText(
                                "这是一页",
                                size.width * .16f,
                                size.height * .375f,
                                paint,
                            )
                        }
                    }
                    view = LocalView.current
                    PdfHighlightLayer(
                        pageIndex = 0,
                        items = emptyList(),
                        selectionEnabled = true,
                        textPage = page,
                        pageScale = scale.floatValue,
                        selectionSession = session,
                        paperColor = if (dark.value) RadixColors.Black else RadixColors.White,
                        darkPaper = dark.value,
                        onSelectCompleted = {},
                        modifier = Modifier.testTag("selection-reference-layer"),
                    )
                }
                }
            }
        }

        rule.runOnIdle { session.setSelection(0, range) }
        rule.waitForIdle()
        val outputDirectory = System.getProperty("m1racle.selection.artifacts")
            ?.takeIf(String::isNotBlank)
            ?.let(::File)
            ?.apply(File::mkdirs)
            ?: java.nio.file.Files.createTempDirectory("m1racle-selection-").toFile()
        val captures = linkedMapOf<String, SelectionCapture>()
        for ((name, isDark, pageScale) in listOf(
            Triple("selection-day-1x", false, 1f),
            Triple("selection-night-1x", true, 1f),
            Triple("selection-day-2_5x", false, 2.5f),
        )) {
            rule.runOnIdle {
                dark.value = isDark
                scale.floatValue = pageScale
            }
            rule.waitForIdle()
            val pageNode = rule.onNodeWithTag("reference-page").fetchSemanticsNode()
            val pageCoordinates = pageNode.layoutInfo.coordinates
            val layerWidthPx = pageCoordinates.size.width.toFloat()
            val layerHeightPx = pageCoordinates.size.height.toFloat()
            val inverseScale = 1f / pageScale
            val geometryHandles = PdfTextSelectionGeometry.handles(
                page,
                range,
                layerWidthPx,
                layerHeightPx,
                12.5f * rule.density.density * inverseScale,
            ).associateBy(PdfSelectionHandlePoint::handle)
            val renderedHandles = PdfSelectionHandle.entries.associateWith { handle ->
                val tag = when (handle) {
                    PdfSelectionHandle.ANCHOR -> "pdf-selection-handle-anchor"
                    PdfSelectionHandle.FOCUS -> "pdf-selection-handle-focus"
                }
                val touchBounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                assertEquals(
                    "$name ${handle.name.lowercase()} touch target stays 44dp",
                    44f,
                    touchBounds.width / rule.density.density,
                    .5f,
                )
                val point = requireNotNull(geometryHandles[handle])
                val center = touchBounds.center
                val geometryCenter = pageCoordinates.localToRoot(Offset(point.x, point.y))
                assertEquals("$name ${handle.name.lowercase()} semantics centre x",
                    geometryCenter.x, center.x, .75f)
                assertEquals("$name ${handle.name.lowercase()} semantics centre y",
                    geometryCenter.y, center.y, .75f)
                RenderedHandle(
                    point = point,
                    center = center,
                    tip = pageCoordinates.localToRoot(Offset(point.tipX, point.tipY)),
                )
            }
            rule.runOnIdle {
                val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(full))
                val file = File(outputDirectory, "$name.png")
                file.outputStream().use { full.compress(Bitmap.CompressFormat.PNG, 100, it) }
                assertTrue("$name must contain rendered pixels", file.length() > 2_000)
                captures[name] = SelectionCapture(full, renderedHandles)
            }
        }
        val day = captures.getValue("selection-day-1x")
        val night = captures.getValue("selection-night-1x")
        val zoomed = captures.getValue("selection-day-2_5x")
        val bodyRadiusPx = 12.5f * rule.density.density
        day.handles.forEach { (handle, rendered) ->
            assertBlueHandle(day.bitmap, rendered, bodyRadiusPx, "day ${handle.name.lowercase()}")
        }
        night.handles.forEach { (handle, rendered) ->
            assertBlueHandle(night.bitmap, rendered, bodyRadiusPx, "night ${handle.name.lowercase()}")
        }
        zoomed.handles.forEach { (handle, rendered) ->
            assertBlueHandle(zoomed.bitmap, rendered, bodyRadiusPx, "zoomed ${handle.name.lowercase()}")
        }
        assertEquals(day.bitmap.width, night.bitmap.width)
        assertEquals(day.bitmap.height, night.bitmap.height)
        assertEquals(day.bitmap.width, zoomed.bitmap.width)
        assertEquals(day.bitmap.height, zoomed.bitmap.height)
        assertFalse("night paper must invert", sameRaster(day.bitmap, night.bitmap))
        assertTrue("zoom must render a different raster", !sameRaster(day.bitmap, zoomed.bitmap))

        println("PDF_SELECTION_REFERENCE_RENDER artifacts=${outputDirectory.absolutePath}")
        println("PDF_SELECTION_REFERENCE_RENDER typeface=assets/$bundledFont resolved=$familyName")
        println("PDF_SELECTION_REFERENCE_RENDER day=${day.bitmap.width}x${day.bitmap.height} " +
            "night=${night.bitmap.width}x${night.bitmap.height} " +
            "zoomed=${zoomed.bitmap.width}x${zoomed.bitmap.height}")
        captures.values.forEach { it.bitmap.recycle() }
    }

    private fun assertBlueHandle(
        bitmap: Bitmap,
        rendered: RenderedHandle,
        radiusPx: Float,
        label: String,
    ) {
        fun pixelAt(x: Float, y: Float): Color {
            require(x >= 0 && x < bitmap.width && y >= 0 && y < bitmap.height) {
                "$label sample ($x,$y) must fit the ${bitmap.width}x${bitmap.height} viewport"
            }
            return Color(bitmap.getPixel(x.toInt(), y.toInt()))
        }
        fun opaqueBlueAt(x: Float, y: Float): Boolean {
            val pixel = pixelAt(x, y)
            val expected = PdfSelectionHandleFill
            return kotlin.math.abs(pixel.red - expected.red) < .12f &&
                kotlin.math.abs(pixel.green - expected.green) < .12f &&
                kotlin.math.abs(pixel.blue - expected.blue) < .12f
        }
        val center = rendered.center
        assertEquals("$label body centre must be the solid blue fill", PdfSelectionHandleFill.blue,
            pixelAt(center.x, center.y).blue, .08f)
        listOf(-.65f, 0f, .65f).forEach { fraction ->
            val dx = fraction * radiusPx
            // Sample inside the silhouette; the exact circumference is antialiased.
            val dy = kotlin.math.sqrt((radiusPx * radiusPx - dx * dx).coerceAtLeast(0f)) * .88f
            assertTrue("$label blue circle must fill its 25dp body",
                opaqueBlueAt(center.x + dx, center.y + dy))
            assertTrue("$label blue circle must fill its 25dp body",
                opaqueBlueAt(center.x + dx, center.y - dy))
        }
        listOf(.25f, .7f).forEach { fraction ->
            val x = rendered.tip.x + (center.x - rendered.tip.x) * fraction
            val y = rendered.tip.y + (center.y - rendered.tip.y) * fraction
            assertTrue("$label corner must be blue through the text tip", opaqueBlueAt(x, y))
        }
        val squareMidX = center.x + (rendered.tip.x - center.x) * .45f
        val squareMidY = center.y + (rendered.tip.y - center.y) * .5f
        assertTrue("$label must be a filled quarter square, not a thin stem",
            opaqueBlueAt(squareMidX, squareMidY))

        val outward = if (rendered.point.isLeft) -1f else 1f
        val outsideX = center.x + outward * (radiusPx + 3f)
        if (outsideX in 0f..bitmap.width.toFloat()) {
            assertFalse("$label must not be a 44dp filled disk",
                opaqueBlueAt(outsideX, center.y))
        }
    }

    private fun sameRaster(first: Bitmap, second: Bitmap): Boolean {
        if (first.width != second.width || first.height != second.height) return false
        for (y in 1 until 10) {
            for (x in 1 until 10) {
                val px = first.width * x / 10
                val py = first.height * y / 10
                if (first.getPixel(px, py) != second.getPixel(px, py)) return false
            }
        }
        return true
    }
}
