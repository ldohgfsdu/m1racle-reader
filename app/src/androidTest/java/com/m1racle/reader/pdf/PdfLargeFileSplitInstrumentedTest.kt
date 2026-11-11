@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.m1racle.reader.pdf

import android.app.Application
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.m1racle.reader.data.local.AppDatabase
import com.m1racle.reader.data.local.DocumentType
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.data.repository.ImportResult
import com.m1racle.reader.data.repository.SearchRepository
import com.m1racle.reader.pdf.export.ExportCancellationToken
import com.m1racle.reader.pdf.export.PdfSplitChapterSource
import com.m1racle.reader.pdf.export.PdfSplitExporter
import com.m1racle.reader.pdf.export.PdfSplitOutputTarget
import com.m1racle.reader.pdf.export.PdfSplitPageRange
import com.m1racle.reader.pdf.export.PdfSplitPlanner
import com.m1racle.reader.pdf.export.PdfSplitState
import com.m1racle.reader.share.ExternalDocumentImporter
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.DeflaterOutputStream

/**
 * Native coverage for the release's 2 GiB PDF intake policy and split export.
 *
 * The large fixture has a real 2,000-page page tree, a mixed-aspect final page,
 * and one valid stream object larger than 200 MiB. It is copied by the shipped
 * importer in 64 KiB chunks; neither fixture creation nor verification
 * materializes the whole PDF in memory.
 */
@RunWith(AndroidJUnit4::class)
class PdfLargeFileSplitInstrumentedTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var repository: DocumentRepository
    private val generatedFiles = CopyOnWriteArrayList<File>()
    private val generatedDirectories = CopyOnWriteArrayList<File>()

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        assertEquals("com.m1racle.reader.qa", app.packageName)
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        repository = DocumentRepository(
            app,
            db,
            db.documentDao(),
            db.pdfPrintPageAnchorDao(),
            db.textbookOutlineNodeDao(),
            SearchRepository(db.searchIndexDao()),
        )
    }

    @After
    fun tearDown() {
        generatedDirectories.forEach { directory ->
            directory.deleteRecursively()
        }
        generatedFiles.forEach { file ->
            file.delete()
        }
        runBlocking { db.close() }
    }

    @Test(timeout = LARGE_TEST_TIMEOUT_MS)
    fun largeSparsePdfImportsAndOpensFinalMixedSizePageInRenderer(): Unit = runBlocking {
        val source = newGeneratedFile("large-2000-pages.pdf")
        LargeSparsePdfFixture.create(
            file = source,
            pageCount = LARGE_PAGE_COUNT,
            finalAspect = LARGE_FINAL_ASPECT,
            minimumBytes = MINIMUM_LARGE_PDF_BYTES,
        )
        assertTrue(source.length() > ExternalDocumentImporter.MAX_BYTES)
        assertTrue(source.length() < ExternalDocumentImporter.PDF_MAX_BYTES)
        assertPdfTailIsValid(source)

        val imported = repository.importDocument(Uri.fromFile(source))
        val success = imported as? ImportResult.Success
            ?: error("PDF import failed for ${source.length()} bytes: $imported")
        assertEquals(DocumentType.PDF, success.document.type)
        assertTrue(success.document.fileSize!! > ExternalDocumentImporter.MAX_BYTES)

        val managedFile = managedFile(success.document.uri)
        generatedFiles += managedFile
        assertTrue(managedFile.isFile)
        assertTrue(managedFile.length() > ExternalDocumentImporter.MAX_BYTES)

        val renderer = PdfPageRenderer(app)
        try {
            val open = renderer.open(success.document.uri.toString())
            assertTrue("large imported PDF must open through PdfRenderer: $open",
                open is PdfPageRenderer.OpenResult.Success)
            assertEquals(LARGE_PAGE_COUNT, (open as PdfPageRenderer.OpenResult.Success).pageCount)
            val finalSize = renderer.pageSizePts(LARGE_PAGE_COUNT - 1)
            requireNotNull(finalSize)
            assertEquals(
                LARGE_FINAL_ASPECT,
                finalSize.second.toFloat() / finalSize.first,
                0.000001f,
            )
            val finalRender = renderer.renderPage(
                pageIndex = LARGE_PAGE_COUNT - 1,
                targetWidthPx = 240,
            )
            assertTrue(
                "large imported PDF final page must render: $finalRender",
                finalRender is PdfPageRenderer.RenderResult.Success,
            )
            val rendered = finalRender as PdfPageRenderer.RenderResult.Success
            assertTrue(rendered.widthPx > 0)
            assertTrue(rendered.heightPx > 0)
            assertEquals(
                LARGE_FINAL_ASPECT,
                rendered.pageSize.height.toFloat() / rendered.pageSize.width,
                0.000001f,
            )
        } finally {
            renderer.close()
        }

        runCatching { repository.deleteById(success.document.id) }
        source.delete()
    }

    @Test
    fun splitByPageCountAndChaptersReadsBackEveryOutputAndCancellationCleansPartialFiles(): Unit = runBlocking {
        val source = newGeneratedFile("split-mixed-sizes.pdf")
        createAndroidPdf(source, MIXED_PAGE_COUNT)
        val outputDirectory = newGeneratedDirectory("split-output")
        val target = FileSplitOutputTarget(outputDirectory)
        val exporter = PdfSplitExporter(app, target)
        val completedFiles = mutableSetOf<File>()

        val byPages = PdfSplitPlanner.byPageCount(
            documentDisplayName = source.name,
            sourcePageCount = MIXED_PAGE_COUNT,
            pagesPerPart = 40,
        )
        assertEquals(5, byPages.parts.size)
        val pageStates = CopyOnWriteArrayList<PdfSplitState>()
        val pageResult = exporter.export(
            sourceUri = Uri.fromFile(source),
            plan = byPages,
            outputDirectoryUri = target.directoryUri,
            cancellation = ExportCancellationToken(),
            onState = pageStates::add,
        )
        assertTrue(pageResult is PdfSplitState.Completed)
        val pageOutputs = (pageResult as PdfSplitState.Completed).outputs
        assertEquals(byPages.parts.size, pageOutputs.size)
        assertEquals(MIXED_PAGE_COUNT, pageOutputs.sumOf { it.pageCount })
        pageOutputs.forEach { output ->
            verifyOutput(output, source, byPages.parts.first { it.outputName == output.displayName })
        }
        assertTrue(pageStates.any { it is PdfSplitState.CopyingPart })
        assertTrue(pageStates.any { it is PdfSplitState.VerifyingPart })
        assertTrue(pageStates.last() is PdfSplitState.Completed)
        target.createdFiles.removeAll { file ->
            !pageOutputs.any { output -> managedFile(output.uri.toString()).canonicalFile == file.canonicalFile }
        }
        completedFiles += pageOutputs.map { managedFile(it.uri.toString()).canonicalFile }

        val byChapters = PdfSplitPlanner.byChapters(
            documentDisplayName = source.name,
            sourcePageCount = MIXED_PAGE_COUNT,
            chapters = listOf(
                PdfSplitChapterSource(
                    id = "chapter-a",
                    title = "第一章",
                    pathTitles = listOf("第一章"),
                    range = PdfSplitPageRange(0, 49),
                ),
                PdfSplitChapterSource(
                    id = "chapter-b",
                    title = "第二章",
                    pathTitles = listOf("第二章"),
                    range = PdfSplitPageRange(50, 119),
                ),
                PdfSplitChapterSource(
                    id = "chapter-c",
                    title = "第三章",
                    pathTitles = listOf("第三章"),
                    range = PdfSplitPageRange(120, MIXED_PAGE_COUNT - 1),
                ),
            ),
        )
        assertEquals(3, byChapters.parts.size)
        val chapterResult = exporter.export(
            sourceUri = Uri.fromFile(source),
            plan = byChapters,
            outputDirectoryUri = target.directoryUri,
            cancellation = ExportCancellationToken(),
            onState = {},
        )
        assertTrue(chapterResult is PdfSplitState.Completed)
        val chapterOutputs = (chapterResult as PdfSplitState.Completed).outputs
        assertEquals(MIXED_PAGE_COUNT, chapterOutputs.sumOf { it.pageCount })
        chapterOutputs.forEach { output ->
            verifyOutput(output, source, byChapters.parts.first { it.outputName == output.displayName })
        }
        target.createdFiles.removeAll { file ->
            !chapterOutputs.any { output -> managedFile(output.uri.toString()).canonicalFile == file.canonicalFile }
        }
        completedFiles += chapterOutputs.map { managedFile(it.uri.toString()).canonicalFile }

        val cancelPlan = PdfSplitPlanner.byPageCount(
            documentDisplayName = "cancel.pdf",
            sourcePageCount = MIXED_PAGE_COUNT,
            pagesPerPart = 2,
        )
        val cancellation = ExportCancellationToken()
        val cancelledStates = CopyOnWriteArrayList<PdfSplitState>()
        val cancellationFilesBefore = target.createdFiles.toSet()
        val cancelled = runCatching {
            exporter.export(
                sourceUri = Uri.fromFile(source),
                plan = cancelPlan,
                outputDirectoryUri = target.directoryUri,
                cancellation = cancellation,
                onState = { state ->
                    cancelledStates += state
                    if (state is PdfSplitState.CopyingPart && state.partIndex >= 2) {
                        cancellation.cancel()
                    }
                },
            )
        }
        assertTrue(cancelled.exceptionOrNull() is CancellationException)
        val terminal = cancelledStates.last()
        assertTrue(terminal is PdfSplitState.Cancelled)
        assertTrue((terminal as PdfSplitState.Cancelled).cleanedOutputs)
        val cancellationFiles = target.createdFiles.filter { it !in cancellationFilesBefore }
        assertTrue(cancellationFiles.isNotEmpty())
        assertTrue(cancellationFiles.none { it.exists() })
        assertTrue(completedFiles.all { it.isFile })
        source.delete()
    }

    @Test
    fun inheritedParentFontAndImageResourcesRenderIdenticallyAfterSplit(): Unit = runBlocking {
        val source = newGeneratedFile("inherited-parent-resources.pdf")
        createInheritedResourcePdf(source)
        assertInheritedResourcesResolveFromParent(source)
        val outputDirectory = newGeneratedDirectory("inherited-resource-split")
        val target = FileSplitOutputTarget(outputDirectory)
        val plan = PdfSplitPlanner.byPageCount(
            documentDisplayName = source.name,
            sourcePageCount = INHERITED_RESOURCE_PAGE_ROTATIONS.size,
            pagesPerPart = 1,
        )

        val result = PdfSplitExporter(app, target).export(
            sourceUri = Uri.fromFile(source),
            plan = plan,
            outputDirectoryUri = target.directoryUri,
            cancellation = ExportCancellationToken(),
            onState = {},
        )
        assertTrue("production split failed: $result", result is PdfSplitState.Completed)
        val outputs = (result as PdfSplitState.Completed).outputs
        assertEquals(INHERITED_RESOURCE_PAGE_ROTATIONS.size, outputs.size)
        assertEquals(plan.parts.size, target.createdFiles.size)

        val sourceRenderer = PdfPageRenderer(app)
        val outputRenderer = PdfPageRenderer(app)
        try {
            assertTrue(
                sourceRenderer.open(Uri.fromFile(source).toString())
                    is PdfPageRenderer.OpenResult.Success,
            )
            PDDocument.load(source, PdfBoxRuntime.memorySetting(app)).use { sourceDocument ->
                outputs.forEachIndexed { sourcePageIndex, output ->
                    val outputFile = managedFile(output.uri.toString())
                    assertTrue(outputFile.isFile)
                    assertEquals(1, output.pageCount)
                    assertTrue(
                        outputRenderer.open(output.uri.toString())
                            is PdfPageRenderer.OpenResult.Success,
                    )

                    PDDocument.load(outputFile, PdfBoxRuntime.memorySetting(app)).use { outputDocument ->
                        assertEquals(1, outputDocument.numberOfPages)
                        val sourcePage = sourceDocument.getPage(sourcePageIndex)
                        val outputPage = outputDocument.getPage(0)
                        assertFalse(
                            "fixture page must inherit resources from its parent Pages dictionary",
                            sourcePage.getCOSObject().containsKey(COSName.RESOURCES),
                        )
                        assertNotNull(sourcePage.resources)
                        assertTrue(
                            "split output must materialize inherited resources on the page",
                            outputPage.getCOSObject().containsKey(COSName.RESOURCES),
                        )

                        assertEquals(
                            "page size and rotation must survive split",
                            pageGeometry(sourcePage),
                            pageGeometry(outputPage),
                        )
                        assertEquals(sourcePage.rotation, outputPage.rotation)
                        assertEquals(
                            sourcePage.mediaBox.width,
                            outputPage.mediaBox.width,
                            0f,
                        )
                        assertEquals(
                            sourcePage.mediaBox.height,
                            outputPage.mediaBox.height,
                            0f,
                        )
                        assertEquals(
                            resourceNames(sourcePage.resources?.fontNames),
                            resourceNames(outputPage.resources?.fontNames),
                        )
                        assertEquals(
                            resourceNames(sourcePage.resources?.xObjectNames),
                            resourceNames(outputPage.resources?.xObjectNames),
                        )
                        val sourceImage = sourcePage.resources?.getXObject(
                            COSName.getPDFName("Im1"),
                        )
                        val outputImage = outputPage.resources?.getXObject(
                            COSName.getPDFName("Im1"),
                        )
                        assertTrue(sourceImage is PDImageXObject)
                        assertTrue(outputImage is PDImageXObject)
                        assertNotNull(
                            outputPage.resources?.getFont(COSName.getPDFName("F1")),
                        )
                    }

                    val sourcePointSize = requireNotNull(
                        sourceRenderer.pageSizePts(sourcePageIndex),
                    )
                    val outputPointSize = requireNotNull(outputRenderer.pageSizePts(0))
                    assertEquals(sourcePointSize, outputPointSize)

                    val sourceRender = sourceRenderer.renderPage(
                        pageIndex = sourcePageIndex,
                        targetWidthPx = INHERITED_RESOURCE_RENDER_WIDTH,
                        minEdgePx = 1,
                    )
                    val outputRender = outputRenderer.renderPage(
                        pageIndex = 0,
                        targetWidthPx = INHERITED_RESOURCE_RENDER_WIDTH,
                        minEdgePx = 1,
                    )
                    assertTrue(sourceRender is PdfPageRenderer.RenderResult.Success)
                    assertTrue(outputRender is PdfPageRenderer.RenderResult.Success)
                    val sourceBitmap =
                        (sourceRender as PdfPageRenderer.RenderResult.Success).image.asAndroidBitmap()
                    val outputBitmap =
                        (outputRender as PdfPageRenderer.RenderResult.Success).image.asAndroidBitmap()
                    try {
                        assertEquals(sourceBitmap.width, outputBitmap.width)
                        assertEquals(sourceBitmap.height, outputBitmap.height)
                        assertTrue(
                            "source page $sourcePageIndex must contain rendered content",
                            bitmapHasNonWhitePixel(sourceBitmap),
                        )
                        assertTrue(
                            "source page $sourcePageIndex must contain a colored pixel",
                            bitmapHasColoredPixel(sourceBitmap),
                        )
                        assertTrue(
                            "source page $sourcePageIndex and split output must match pixel-for-pixel",
                            sourceBitmap.sameAs(outputBitmap),
                        )
                    } finally {
                        sourceBitmap.recycle()
                        outputBitmap.recycle()
                    }
                }
            }
        } finally {
            sourceRenderer.close()
            outputRenderer.close()
        }
    }

    @Test
    fun productionSafTargetCreatesAndReadsBackPageCountSplit(): Unit = runBlocking {
        val treeUri = Uri.parse(
            requireNotNull(
                InstrumentationRegistry.getArguments()
                    .getString("readerSafTreeUri")
                    ?.takeIf { it.isNotBlank() },
            ) {
                "Instrumentation argument readerSafTreeUri is required"
            },
        )
        require(DocumentsContract.isTreeUri(treeUri)) {
            "readerSafTreeUri must be an ACTION_OPEN_DOCUMENT_TREE URI: $treeUri"
        }
        val resolver = app.contentResolver
        val persistedPermission = resolver.persistedUriPermissions.firstOrNull {
            it.uri.toString() == treeUri.toString()
        }
        assertNotNull(
            "readerSafTreeUri must have a persisted URI permission: $treeUri",
            persistedPermission,
        )
        assertTrue(
            "Persisted URI permission must include read access: $treeUri",
            persistedPermission!!.isReadPermission,
        )
        assertTrue(
            "Persisted URI permission must include write access: $treeUri",
            persistedPermission.isWritePermission,
        )

        val runId = UUID.randomUUID().toString()
        val source = newGeneratedFile("saf-$runId.pdf")
        createAndroidPdf(source, SAF_PAGE_COUNT)
        val completedOutputUris = CopyOnWriteArrayList<Uri>()
        try {
            val plan = PdfSplitPlanner.byPageCount(
                documentDisplayName = "saf-$runId.pdf",
                sourcePageCount = SAF_PAGE_COUNT,
                pagesPerPart = 20,
            )
            assertEquals(2, plan.parts.size)
            val result = PdfSplitExporter(app).export(
                sourceUri = Uri.fromFile(source),
                plan = plan,
                outputDirectoryUri = treeUri,
                cancellation = ExportCancellationToken(),
                onState = {},
            )
            assertTrue(result is PdfSplitState.Completed)
            val outputs = (result as PdfSplitState.Completed).outputs
            completedOutputUris += outputs.map { it.uri }
            assertEquals(plan.parts.size, outputs.size)
            outputs.forEach { output ->
                assertEquals("content", output.uri.scheme)
                assertTrue(
                    "Output name must include this run's UUID: ${output.displayName}",
                    output.displayName.contains(runId),
                )
                assertEquals(
                    plan.parts.first { it.outputName == output.displayName }.sourceRange.pageCount,
                    output.pageCount,
                )
                resolver.openInputStream(output.uri)?.use { input ->
                    PDDocument.load(input).use { document ->
                        assertEquals(output.pageCount, document.numberOfPages)
                    }
                } ?: error("SAF output must be readable: ${output.uri}")
            }
        } finally {
            val cleanupFailures = mutableListOf<String>()
            completedOutputUris.forEach { outputUri ->
                runCatching {
                    DocumentsContract.deleteDocument(resolver, outputUri)
                }.onSuccess { deleted ->
                    if (!deleted) cleanupFailures += "delete returned false: $outputUri"
                }.onFailure { error ->
                    cleanupFailures += "delete failed: $outputUri (${error.message})"
                }
            }
            if (!source.delete()) {
                cleanupFailures += "source delete returned false: $source"
            }
            assertTrue(
                "Completed output cleanup failed: ${cleanupFailures.joinToString()}",
                cleanupFailures.isEmpty(),
            )
        }
    }

    private fun assertInheritedResourcesResolveFromParent(file: File) {
        PDDocument.load(file, PdfBoxRuntime.memorySetting(app)).use { document ->
            assertNotNull(
                document.documentCatalog.pages.getCOSObject()
                    .getDictionaryObject(COSName.RESOURCES),
            )
            assertEquals(INHERITED_RESOURCE_PAGE_ROTATIONS.size, document.numberOfPages)
            INHERITED_RESOURCE_PAGE_ROTATIONS.forEachIndexed { index, rotation ->
                val page = document.getPage(index)
                assertFalse(
                    "reopened source page must not carry its own Resources",
                    page.getCOSObject().containsKey(COSName.RESOURCES),
                )
                val inherited = requireNotNull(page.resources) {
                    "reopened source page must resolve inherited resources"
                }
                assertEquals(rotation, page.rotation)
                val fonts = resourceNames(inherited.fontNames)
                val images = resourceNames(inherited.xObjectNames)
                assertTrue(fonts.contains("F1"))
                assertTrue(images.contains("Im1"))
                val operators = page.contents?.use { input ->
                    input.readBytes().toString(Charsets.ISO_8859_1)
                }.orEmpty()
                assertTrue(fonts.any { name -> operators.contains("/$name") })
                assertTrue(images.any { name -> operators.contains("/$name") })
            }
        }
    }

    private fun createInheritedResourcePdf(file: File) {
        PDDocument().use { document ->
            val pageTree = document.documentCatalog.pages
            val resources = PDResources()
            val font = PDType1Font.HELVETICA
            val image = inheritedRgbImage(document)
            resources.put(COSName.getPDFName("F1"), font)
            resources.put(COSName.getPDFName("Im1"), image)
            pageTree.getCOSObject().setItem(COSName.RESOURCES, resources)
            val mediaBox = PDRectangle(
                INHERITED_RESOURCE_RENDER_WIDTH.toFloat(),
                INHERITED_RESOURCE_PAGE_HEIGHT,
            )
            INHERITED_RESOURCE_PAGE_ROTATIONS.forEachIndexed { index, rotation ->
                val page = PDPage(mediaBox)
                page.rotation = rotation
                document.addPage(page)
                check(!page.getCOSObject().containsKey(COSName.RESOURCES))
                PDPageContentStream(document, page).use { content ->
                    content.beginText()
                    content.setFont(font, 18f)
                    content.newLineAtOffset(36f, 150f)
                    content.showText("Inherited $index")
                    content.endText()
                    content.drawImage(image, 36f, 36f, 96f, 72f)
                }
                page.getCOSObject().removeItem(COSName.RESOURCES)
                check(!page.getCOSObject().containsKey(COSName.RESOURCES))
            }
            val parent = PDResources(
                pageTree.getCOSObject().getCOSDictionary(COSName.RESOURCES),
            )
            check(resourceNames(parent.fontNames).contains("F1"))
            check(resourceNames(parent.xObjectNames).contains("Im1"))
            document.save(file)
        }
    }

    private fun inheritedRgbImage(document: PDDocument): PDImageXObject {
        val width = 24
        val height = 18
        val raw = ByteArray(width * height * 3) { index ->
            val x = (index / 3) % width
            when (index % 3) {
                0 -> if (x < width / 2) 0xE2.toByte() else 0x14.toByte()
                1 -> if (x < width / 2) 0x2A.toByte() else 0x9A.toByte()
                else -> 0x18.toByte()
            }
        }
        val compressed = ByteArrayOutputStream().use { output ->
            DeflaterOutputStream(output).use { deflater -> deflater.write(raw) }
            output.toByteArray()
        }
        return PDImageXObject(
            document,
            compressed.inputStream(),
            COSName.FLATE_DECODE,
            width,
            height,
            8,
            PDDeviceRGB.INSTANCE,
        )
    }

    private fun resourceNames(names: Iterable<COSName>?): Set<String> =
        names?.mapTo(linkedSetOf()) { it.name } ?: emptySet()

    private fun bitmapHasNonWhitePixel(bitmap: android.graphics.Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.any { pixel ->
            Color.red(pixel) != 255 || Color.green(pixel) != 255 || Color.blue(pixel) != 255
        }
    }

    private fun bitmapHasColoredPixel(bitmap: android.graphics.Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.any { pixel ->
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)
            maxOf(red, green, blue) - minOf(red, green, blue) >= 32
        }
    }

    private suspend fun verifyOutput(
        output: com.m1racle.reader.pdf.export.PdfSplitOutput,
        sourceFile: File,
        part: com.m1racle.reader.pdf.export.PdfSplitPart,
    ) {
        val outputFile = managedFile(output.uri.toString())
        assertTrue(outputFile.isFile)
        assertEquals(part.sourceRange.pageCount, output.pageCount)
        PDDocument.load(outputFile).use { outputDocument ->
            assertEquals(part.sourceRange.pageCount, outputDocument.numberOfPages)
            PDDocument.load(sourceFile).use { sourceDocument ->
                repeat(part.sourceRange.pageCount) { offset ->
                    val sourcePage = sourceDocument.getPage(part.sourceRange.startPageIndex + offset)
                    val outputPage = outputDocument.getPage(offset)
                    assertEquals(
                        pageGeometry(sourcePage),
                        pageGeometry(outputPage),
                    )
                    assertEquals(
                        contentDigest(sourcePage),
                        contentDigest(outputPage),
                    )
                }
            }
        }
    }

    private fun pageGeometry(page: PDPage): String =
        listOf(
            page.mediaBox,
            page.cropBox,
            page.bleedBox,
            page.trimBox,
            page.artBox,
            page.userUnit,
            page.rotation,
        ).joinToString("|")

    private fun contentDigest(page: PDPage): Long {
        var digest = 1125899906842597L
        if (page.hasContents()) {
            page.contents.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    repeat(n) { index ->
                        digest = 31 * digest + buffer[index]
                    }
                }
            }
        }
        return digest
    }

    private fun assertPdfTailIsValid(file: File) {
        val tail = file.readTailText(64 * 1024)
        assertTrue(tail.contains("%%EOF"))
        val startXref = Regex("""startxref\s+(\d+)\s+%%EOF""")
            .findAll(tail)
            .lastOrNull()
            ?: error("Missing startxref in the final 64 KiB")
        val xrefOffset = startXref.groupValues[1].toLong()
        FileInputStream(file).use { input ->
            input.channel.position(xrefOffset)
            val prefix = ByteArray(8)
            assertEquals(8, input.read(prefix))
            assertTrue(String(prefix, Charsets.US_ASCII).startsWith("xref"))
        }
    }

    private fun File.readTailText(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        FileInputStream(this).use { input ->
            input.channel.position(length() - byteCount)
            var offset = 0
            while (offset < byteCount) {
                val n = input.read(bytes, offset, byteCount - offset)
                if (n < 0) break
                offset += n
            }
            assertEquals(byteCount, offset)
        }
        return String(bytes, Charsets.US_ASCII)
    }

    private fun createAndroidPdf(file: File, pageCount: Int) {
        val document = PdfDocument()
        try {
            repeat(pageCount) { index ->
                val (width, height) = mixedPageSize(index)
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(width, height, index + 1).create(),
                )
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawText(
                    "Split page ${index + 1}",
                    24f,
                    42f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = 16f
                    },
                )
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
    }

    private fun managedFile(uri: String): File =
        requireNotNull(Uri.parse(uri).path?.let(::File)) {
            "Expected a file URI from the QA output seam: $uri"
        }

    private fun newGeneratedFile(name: String): File {
        val file = File(app.cacheDir, "${UUID.randomUUID()}-$name")
        generatedFiles += file
        return file
    }

    private fun newGeneratedDirectory(name: String): File {
        val directory = File(app.cacheDir, "${UUID.randomUUID()}-$name")
        require(directory.mkdirs()) { "Unable to create $directory" }
        generatedDirectories += directory
        return directory
    }

    private fun mixedPageSize(index: Int): Pair<Int, Int> =
        when (index % 6) {
            0 -> 900 to 400
            1 -> 600 to 840
            2 -> 840 to 600
            3 -> 400 to 1_000
            4 -> 720 to 720
            else -> 612 to 792
        }

    private class FileSplitOutputTarget(
        directory: File,
    ) : PdfSplitOutputTarget {
        private val root = directory.canonicalFile
        val directoryUri: Uri = Uri.fromFile(root)
        val createdFiles = CopyOnWriteArrayList<File>()

        init {
            check(root.isDirectory)
        }

        override fun createPdf(directoryUri: Uri, displayName: String): Uri? {
            val file = File(root, displayName)
            if (!file.canonicalFile.toPath().startsWith(root.toPath())) return null
            if (!file.createNewFile()) return null
            createdFiles += file
            return Uri.fromFile(file)
        }

        override fun openInput(uri: Uri): InputStream? =
            uri.path?.let(::File)?.takeIf(File::isFile)?.let(::FileInputStream)

        override fun openOutput(uri: Uri): OutputStream? =
            uri.path?.let(::File)?.takeIf(File::isFile)?.let(::FileOutputStream)

        override fun delete(uri: Uri): Boolean =
            uri.path?.let(::File)?.delete() ?: false
    }

    private companion object {
        const val MIXED_PAGE_COUNT = 200
        const val SAF_PAGE_COUNT = 40
        const val LARGE_PAGE_COUNT = 2_000
        const val MINIMUM_LARGE_PDF_BYTES = 224L * 1024L * 1024L
        const val LARGE_FINAL_ASPECT = 1_000f / 400f
        const val LARGE_TEST_TIMEOUT_MS = 240_000L
        const val INHERITED_RESOURCE_RENDER_WIDTH = 360
        const val INHERITED_RESOURCE_PAGE_HEIGHT = 240f
        val INHERITED_RESOURCE_PAGE_ROTATIONS = listOf(0, 90, 180)
    }

    private object LargeSparsePdfFixture {
        fun create(
            file: File,
            pageCount: Int,
            finalAspect: Float,
            minimumBytes: Long,
        ) {
            require(finalAspect > 0f)
            writePdfWithLargeContentStream(file, pageCount, minimumBytes)
            check(file.length() > minimumBytes) {
                "Large PDF fixture must exceed $minimumBytes bytes, actual=${file.length()}"
            }
        }

        private fun writePdfWithLargeContentStream(
            file: File,
            pageCount: Int,
            minimumBytes: Long,
        ) {
            val layout = FixtureLayout(pageCount)
            val offsets = HashMap<Int, Long>()
            RandomAccessFile(file, "rw").use { raf ->
                raf.setLength(0)
                write(raf, "%PDF-1.4\n")
                writeObject(raf, offsets, 1) {
                    "<< /Type /Catalog /Pages 2 0 R >>"
                }
                writeObject(raf, offsets, 2) {
                    "<< /Type /Pages /Count $pageCount /Kids [${layout.pagesRootId} 0 R] >>"
                }
                layout.nodes.forEach { node ->
                    writeObject(raf, offsets, node.id) {
                        if (node.firstPage == node.lastPage) {
                            val pageIndex = node.firstPage
                            val box = if (pageIndex == pageCount - 1) {
                                "[0 0 400 1000]"
                            } else {
                                "[0 0 612 792]"
                            }
                            "<< /Type /Page /Parent ${node.parentId} 0 R " +
                                "/MediaBox $box /Resources << >> >>"
                        } else {
                            val kids = node.children.joinToString(" ") { "${it} 0 R" }
                            "<< /Type /Pages /Parent ${node.parentId} 0 R " +
                                "/Count ${node.lastPage - node.firstPage + 1} " +
                                "/Kids [$kids] >>"
                        }
                    }
                }
                val contentOffset = raf.filePointer
                write(raf, "${layout.contentId} 0 obj\n<< /Length $minimumBytes >>\nstream\n")
                val contentDataOffset = raf.filePointer
                // setLength creates a filesystem hole on the instrumentation
                // device. The declared stream length remains valid without
                // materializing 224 MiB in Java heap or copying filler bytes.
                raf.setLength(contentDataOffset + minimumBytes)
                raf.seek(contentDataOffset + minimumBytes)
                write(raf, "\nendstream")
                write(raf, "\nendobj\n")
                offsets[layout.contentId] = contentOffset
                val xrefOffset = raf.filePointer
                val objectCount = layout.contentId
                write(raf, "xref\n0 ${objectCount + 1}\n")
                write(raf, "0000000000 65535 f \n")
                for (id in 1..objectCount) {
                    val offset = offsets[id]
                        ?: error("Fixture object $id has no xref offset")
                    write(raf, "%010d 00000 n \n".format(offset))
                }
                write(raf, "trailer\n<< /Size ${objectCount + 1} /Root 1 0 R >>\n")
                write(raf, "startxref\n$xrefOffset\n%%EOF\n")
                raf.fd.sync()
            }
        }

        private fun writeObject(
            raf: RandomAccessFile,
            offsets: MutableMap<Int, Long>,
            id: Int,
            body: () -> String,
        ) {
            offsets[id] = raf.filePointer
            write(raf, "$id 0 obj\n${body()}\nendobj\n")
        }

        private fun write(raf: RandomAccessFile, text: String) {
            raf.write(text.toByteArray(Charsets.US_ASCII))
        }

        private data class FixtureNode(
            val id: Int,
            val parentId: Int,
            val firstPage: Int,
            val lastPage: Int,
            val children: List<Int>,
        )

        private class FixtureLayout(pageCount: Int) {
            private var rootId: Int = -1
            private val leafPageIds: List<Int>
            val nodes: List<FixtureNode>
            val contentId: Int

            init {
                val result = ArrayList<FixtureNode>()
                val pageIds = ArrayList<Int>()
                val childrenByNode = HashMap<Int, MutableList<Int>>()
                fun addTree(firstPage: Int, lastPage: Int, parentId: Int): Int {
                    val id = 3 + result.size
                    val children = childrenByNode.getOrPut(id) { mutableListOf() }
                    val node = FixtureNode(id, parentId, firstPage, lastPage, children)
                    result += node
                    if (firstPage == lastPage) {
                        pageIds += id
                    } else {
                        val mid = (firstPage + lastPage) / 2
                        children += addTree(firstPage, mid, id)
                        children += addTree(mid + 1, lastPage, id)
                    }
                    return id
                }
                rootId = addTree(0, pageCount - 1, 2)
                nodes = result
                contentId = 3 + result.size
                leafPageIds = pageIds
            }

            val pagesRootId: Int
                get() = rootId

            fun leafId(pageIndex: Int): Int = leafPageIds[pageIndex]
        }
    }
}
