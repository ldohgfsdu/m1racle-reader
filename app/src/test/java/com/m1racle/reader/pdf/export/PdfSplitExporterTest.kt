package com.m1racle.reader.pdf.export

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.m1racle.reader.pdf.PdfBoxRuntime
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class PdfSplitExporterTest {

    private class FileTarget(root: File) : PdfSplitOutputTarget {
        private val directory = root.apply { mkdirs() }

        override fun createPdf(directoryUri: Uri, displayName: String): Uri {
            val file = File(directory, displayName)
            check(file.createNewFile()) { "duplicate output $displayName" }
            return Uri.fromFile(file)
        }

        override fun openInput(uri: Uri): InputStream =
            FileInputStream(requireNotNull(uri.path))

        override fun openOutput(uri: Uri): OutputStream =
            FileOutputStream(requireNotNull(uri.path))

        override fun delete(uri: Uri): Boolean =
            uri.path?.let { File(it).delete() } == true
    }

    private lateinit var app: android.content.Context
    private lateinit var source: File
    private lateinit var outputDir: File

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        check(PdfBoxRuntime.ensureInit(app))
        source = File(app.cacheDir, "split-source-${System.nanoTime()}.pdf").apply {
            PDDocument().use { document ->
                repeat(5) { index ->
                    val page = PDPage(
                        if (index % 2 == 0) PDRectangle(400f, 600f)
                        else PDRectangle(600f, 400f),
                    ).apply {
                        rotation = index * 90
                    }
                    document.addPage(page)
                    PDPageContentStream(document, page).use { content ->
                        content.beginText()
                        content.setFont(PDType1Font.HELVETICA, 14f)
                        content.newLineAtOffset(36f, 500f)
                        content.showText("page ${index + 1}")
                        content.endText()
                    }
                }
                val imagePage = document.getPage(0)
                val raw = ByteArray(16 * 16 * 3) { (it * 17).toByte() }
                val compressed = java.io.ByteArrayOutputStream().use { output ->
                    java.util.zip.DeflaterOutputStream(output).use { it.write(raw) }
                    output.toByteArray()
                }
                val image = PDImageXObject(
                    document,
                    compressed.inputStream(),
                    com.tom_roush.pdfbox.cos.COSName.FLATE_DECODE,
                    16,
                    16,
                    8,
                    PDDeviceRGB.INSTANCE,
                )
                PDPageContentStream(
                    document,
                    imagePage,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true,
                ).use { content ->
                    content.drawImage(image, 48f, 48f, 32f, 32f)
                }
                document.save(this)
            }
        }
        outputDir = File(app.cacheDir, "split-output-${System.nanoTime()}")
    }

    @Test
    fun pageCount_outputsReopenWithPagesResourcesAndShortTail() = runBlocking {
        val plan = PdfSplitPlanner.byPageCount(
            documentDisplayName = "resource-test.pdf",
            sourcePageCount = 5,
            pagesPerPart = 2,
        )
        val target = FileTarget(outputDir)
        val token = ExportCancellationToken()
        val states = mutableListOf<PdfSplitState>()
        val result = PdfSplitExporter(app, target).export(
            sourceUri = Uri.fromFile(source),
            plan = plan,
            outputDirectoryUri = Uri.fromFile(outputDir),
            cancellation = token,
            onState = states::add,
        )

        assertTrue(result is PdfSplitState.Completed)
        val outputs = (result as PdfSplitState.Completed).outputs
        assertEquals(listOf(2, 2, 1), outputs.map { it.pageCount })
        outputs.forEach { output ->
            FileInputStream(File(output.uri.path!!)).use { input ->
                PDDocument.load(input, PdfBoxRuntime.memorySetting(app)).use { document ->
                    assertEquals(output.pageCount, document.numberOfPages)
                    assertTrue(document.getPage(0).hasContents())
                }
            }
        }
        val first = PDDocument.load(
            File(outputs.first().uri.path!!),
            PdfBoxRuntime.memorySetting(app),
        )
        try {
            val box = first.getPage(0).mediaBox
            assertArrayEquals(floatArrayOf(0f, 0f, 400f, 600f),
                floatArrayOf(box.lowerLeftX, box.lowerLeftY, box.upperRightX, box.upperRightY), 0f)
            assertEquals(0, first.getPage(0).rotation)
            assertNotNull(first.getPage(0).resources)
            assertTrue(first.getPage(0).resources!!.getXObjectNames().iterator().hasNext())
        } finally {
            first.close()
        }
        assertTrue(states.any { it is PdfSplitState.VerifyingPart })
        assertTrue(states.last() is PdfSplitState.Completed)
    }

    @Test
    fun cancellationDuringReadback_removesAllCreatedOutputs() = runBlocking {
        val plan = PdfSplitPlanner.byPageCount(
            documentDisplayName = "cancel-test.pdf",
            sourcePageCount = 5,
            pagesPerPart = 1,
        )
        val target = FileTarget(outputDir)
        val token = ExportCancellationToken()
        var terminal: PdfSplitState? = null
        var cancellationThrown = false
        try {
            PdfSplitExporter(app, target).export(
                sourceUri = Uri.fromFile(source),
                plan = plan,
                outputDirectoryUri = Uri.fromFile(outputDir),
                cancellation = token,
                onState = { state ->
                    terminal = state
                    if (state is PdfSplitState.VerifyingPart) token.cancel()
                },
            )
        } catch (_: kotlinx.coroutines.CancellationException) {
            cancellationThrown = true
        }

        assertTrue("cancellation must preserve coroutine cancellation", cancellationThrown)
        assertTrue(terminal is PdfSplitState.Cancelled)
        val cancelled = terminal as PdfSplitState.Cancelled
        assertTrue(cancelled.cleanedOutputs)
        assertTrue(outputDir.listFiles().orEmpty().isEmpty())
        assertFalse(app.cacheDir.listFiles().orEmpty().any {
            it.name.startsWith("pdf_split_")
        })
    }

    @Test
    fun coroutineCancellationStopsAtTheNextPageWithoutCancellingTheToken() = runBlocking {
        val token = ExportCancellationToken()
        val states = mutableListOf<PdfSplitState>()
        val plan = PdfSplitPlanner.byPageCount("cancel-job.pdf", 5, 5)
        lateinit var exportJob: Job
        exportJob = launch(start = CoroutineStart.LAZY) {
            PdfSplitExporter(app, FileTarget(outputDir)).export(
                sourceUri = Uri.fromFile(source), plan = plan,
                outputDirectoryUri = Uri.fromFile(outputDir), cancellation = token,
                onState = { state ->
                    states += state
                    if (state is PdfSplitState.CopyingPart) exportJob.cancel()
                },
            )
        }
        exportJob.start()
        exportJob.join()
        assertTrue(exportJob.isCancelled)
        assertFalse(token.isCancelled())
        assertEquals(1, states.filterIsInstance<PdfSplitState.CopyingPart>().size)
        assertTrue(states.last() is PdfSplitState.Cancelled)
        assertTrue(outputDir.listFiles().orEmpty().isEmpty())
        assertTrue(source.isFile)
    }

    @Test
    fun inheritedParentResources_areMaterializedAndRenderable() = runBlocking {
        val inheritedSource = File(
            app.cacheDir,
            "inherited-resources-${System.nanoTime()}.pdf",
        ).apply {
            PDDocument().use { document ->
                val pageTree = document.documentCatalog.pages
                val resources = PDResources()
                val font = PDType1Font.HELVETICA
                val raw = ByteArray(16 * 16 * 3) { index ->
                    if (index % 3 == 0) 0xff.toByte() else 0x00
                }
                val compressed = java.io.ByteArrayOutputStream().use { output ->
                    java.util.zip.DeflaterOutputStream(output).use { it.write(raw) }
                    output.toByteArray()
                }
                val image = PDImageXObject(
                    document,
                    compressed.inputStream(),
                    COSName.FLATE_DECODE,
                    16,
                    16,
                    8,
                    PDDeviceRGB.INSTANCE,
                )
                resources.put(COSName.getPDFName("F1"), font)
                resources.put(COSName.getPDFName("Im1"), image)
                pageTree.getCOSObject().setItem(COSName.RESOURCES, resources)

                val page = PDPage(PDRectangle(300f, 400f))
                document.addPage(page)
                check(!page.getCOSObject().containsKey(COSName.RESOURCES))
                PDPageContentStream(document, page).use { content ->
                    content.beginText()
                    content.setFont(font, 18f)
                    content.newLineAtOffset(36f, 340f)
                    content.showText("Inherited resource text")
                    content.endText()
                    content.drawImage(image, 36f, 80f, 32f, 32f)
                }
                page.getCOSObject().removeItem(COSName.RESOURCES)
                document.save(this)
            }
        }

        PDDocument.load(inheritedSource, PdfBoxRuntime.memorySetting(app)).use { reopened ->
            val page = reopened.getPage(0)
            assertFalse(page.getCOSObject().containsKey(COSName.RESOURCES))
            assertNotNull(page.resources)
        }

        val plan = PdfSplitPlanner.byPageCount(
            documentDisplayName = "inherited.pdf",
            sourcePageCount = 1,
            pagesPerPart = 1,
        )
        val target = FileTarget(outputDir)
        val result = PdfSplitExporter(app, target).export(
            sourceUri = Uri.fromFile(inheritedSource),
            plan = plan,
            outputDirectoryUri = Uri.fromFile(outputDir),
            cancellation = ExportCancellationToken(),
            onState = {},
        )

        assertTrue(result is PdfSplitState.Completed)
        val output = (result as PdfSplitState.Completed).outputs.single()
        PDDocument.load(
            File(output.uri.path!!),
            PdfBoxRuntime.memorySetting(app),
        ).use { document ->
            val page = document.getPage(0)
            val resources = requireNotNull(page.resources)
            assertTrue(resources.getFontNames().any { it.name == "F1" })
            assertTrue(resources.getXObjectNames().any { it.name == "Im1" })
            assertTrue(PDFTextStripper().getText(document).contains("Inherited resource text"))

            // LEGACY graphics cannot prove raster output. Preserve the exact
            // decoded RGB stream here; instrumentation compares complete pages
            // from the shipped PdfPageRenderer against their source pages.
            val image = resources.getXObject(COSName.getPDFName("Im1")) as PDImageXObject
            val decodedRgb = image.cosObject.createInputStream().use { it.readBytes() }
            val expectedRgb = ByteArray(16 * 16 * 3) { if (it % 3 == 0) 0xff.toByte() else 0 }
            assertArrayEquals(expectedRgb, decodedRgb)
        }
    }

    @Test
    fun createFailure_beforeAnyOutput_doesNotReportCleanupFailure() = runBlocking {
        val plan = PdfSplitPlanner.byPageCount("create-fail.pdf", 5, 1)
        val target = ScriptedTarget(outputDir, failCreateAfter = 0)
        val result = export(plan, target)

        val failed = result as PdfSplitState.Failed
        assertTrue(failed.message.contains("无法在所选目录创建"))
        assertFalse(failed.outputCleanupFailed)
        assertTrue(failed.retainedOutputs.isEmpty())
    }

    @Test
    fun outputOpenFailure_afterSuccessfulDelete_doesNotReportCleanupFailure() = runBlocking {
        val plan = PdfSplitPlanner.byPageCount("open-fail.pdf", 5, 1)
        val target = ScriptedTarget(outputDir, failOpenOutput = true)
        val result = export(plan, target)

        val failed = result as PdfSplitState.Failed
        assertTrue(failed.message.contains("无法写入"))
        assertFalse(failed.outputCleanupFailed)
        assertTrue(failed.retainedOutputs.isEmpty())
        assertTrue(outputDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun readbackOpenFailure_afterSuccessfulDelete_doesNotReportCleanupFailure() = runBlocking {
        val plan = PdfSplitPlanner.byPageCount("readback-fail.pdf", 5, 5)
        val target = ScriptedTarget(outputDir, failOpenInput = true)
        val result = export(plan, target)

        val failed = result as PdfSplitState.Failed
        assertTrue(failed.message.contains("无法校验"))
        assertFalse(failed.outputCleanupFailed)
        assertTrue(failed.retainedOutputs.isEmpty())
        assertTrue(outputDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun refusedDelete_keepsEveryCreatedOutputName() = runBlocking {
        val plan = PdfSplitPlanner.byPageCount("retain-fail.pdf", 5, 1)
        val target = ScriptedTarget(outputDir, failCreateAfter = 3, refuseDelete = true)
        val result = export(plan, target)

        val failed = result as PdfSplitState.Failed
        assertTrue(failed.outputCleanupFailed)
        assertEquals(3, failed.retainedOutputs.size)
        assertEquals(
            failed.retainedOutputs.map { it.displayName }.toSet(),
            outputDir.listFiles().orEmpty().map { it.name }.toSet(),
        )
        failed.retainedOutputs.forEach { residue ->
            assertTrue(File(requireNotNull(residue.uri.path)).isFile)
        }
    }

    @Test
    fun writeInterruption_deletesPartialOutputAndReportsNoCleanupFailure() = runBlocking {
        val plan = PdfSplitPlanner.byPageCount("write-interrupt.pdf", 5, 1)
        val target = ScriptedTarget(outputDir, writeFailureAfterBytes = 1)
        val result = export(plan, target)

        val failed = result as PdfSplitState.Failed
        assertTrue(failed.message.contains("provider write interrupted"))
        assertFalse(failed.outputCleanupFailed)
        assertTrue(failed.retainedOutputs.isEmpty())
        assertEquals(1, target.createdNames.size)
        assertTrue(outputDir.listFiles().orEmpty().isEmpty())
        assertFalse(app.cacheDir.listFiles().orEmpty().any { it.name.startsWith("pdf_split_") })
    }

    @Test
    fun readbackByteMismatch_deletesPartialOutputAndReportsNoCleanupFailure() = runBlocking {
        val plan = PdfSplitPlanner.byPageCount("sha-mismatch.pdf", 5, 5)
        val target = ScriptedTarget(outputDir, appendByteOnClose = true)
        val result = export(plan, target)

        val failed = result as PdfSplitState.Failed
        assertTrue(failed.message.contains("字节不一致"))
        assertFalse(failed.outputCleanupFailed)
        assertTrue(failed.retainedOutputs.isEmpty())
        assertTrue(outputDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun providerDeleteThrows_isReportedAsCleanupFailureAndKeepsName() = runBlocking {
        val plan = PdfSplitPlanner.byPageCount("delete-throws.pdf", 5, 1)
        val target = ScriptedTarget(
            outputDir,
            failCreateAfter = 2,
            throwOnDelete = true,
        )
        val result = export(plan, target)

        val failed = result as PdfSplitState.Failed
        assertTrue(failed.outputCleanupFailed)
        assertEquals(2, failed.retainedOutputs.size)
        assertEquals(target.createdNames.toSet(), failed.retainedOutputs.map { it.displayName }.toSet())
        assertEquals(2, outputDir.listFiles().orEmpty().size)
    }

    @Test
    fun partialDeleteFailure_reportsOnlyUndeletedNames() = runBlocking {
        val plan = PdfSplitPlanner.byPageCount("partial-delete.pdf", 5, 1)
        val names = plan.parts.map { it.outputName }
        val retainedName = names[1]
        val target = ScriptedTarget(
            outputDir,
            failCreateAfter = 3,
            refuseDeleteNames = setOf(retainedName),
        )
        val result = export(plan, target)

        val failed = result as PdfSplitState.Failed
        assertTrue(failed.outputCleanupFailed)
        assertEquals(listOf(retainedName), failed.retainedOutputs.map { it.displayName })
        assertEquals(listOf(retainedName), outputDir.listFiles().orEmpty().map { it.name })
        assertTrue(File(outputDir, retainedName).isFile)
    }

    @Test
    fun providerCreateThrows_reportsFailureWithoutCleanupWarning() = runBlocking {
        val plan = PdfSplitPlanner.byPageCount("create-throws.pdf", 5, 1)
        val target = ScriptedTarget(outputDir, throwOnCreate = true)
        val result = export(plan, target)

        val failed = result as PdfSplitState.Failed
        assertTrue(failed.message.contains("provider create refused"))
        assertFalse(failed.outputCleanupFailed)
        assertTrue(failed.retainedOutputs.isEmpty())
        assertTrue(outputDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun cancellationWithRefusedDelete_reportsResiduesAndRethrows() = runBlocking {
        val plan = PdfSplitPlanner.byPageCount("cancel-delete.pdf", 5, 1)
        val target = ScriptedTarget(outputDir, refuseDelete = true)
        val token = ExportCancellationToken()
        var terminal: PdfSplitState? = null
        var cancellationThrown = false
        try {
            PdfSplitExporter(app, target).export(
                sourceUri = Uri.fromFile(source),
                plan = plan,
                outputDirectoryUri = Uri.fromFile(outputDir),
                cancellation = token,
                onState = { state ->
                    terminal = state
                    if (state is PdfSplitState.VerifyingPart) token.cancel()
                },
            )
        } catch (_: CancellationException) {
            cancellationThrown = true
        }

        assertTrue(cancellationThrown)
        val cancelled = terminal as PdfSplitState.Cancelled
        assertFalse(cancelled.cleanedOutputs)
        assertFalse(cancelled.retainedOutputs.isEmpty())
        assertEquals(
            cancelled.retainedOutputs.map { it.displayName }.toSet(),
            outputDir.listFiles().orEmpty().map { it.name }.toSet(),
        )
    }

    @Test
    fun successfulCleanup_neverReportsCleanupFailure() = runBlocking {
        val plan = PdfSplitPlanner.byPageCount("clean-success.pdf", 5, 1)
        val target = ScriptedTarget(outputDir, failCreateAfter = 3)
        val result = export(plan, target)

        val failed = result as PdfSplitState.Failed
        assertTrue(failed.message.contains("无法在所选目录创建"))
        assertFalse(failed.outputCleanupFailed)
        assertTrue(outputDir.listFiles().orEmpty().isEmpty())
    }

    private fun export(plan: PdfSplitPlan, target: PdfSplitOutputTarget): PdfSplitState =
        runBlocking {
            PdfSplitExporter(app, target).export(
                sourceUri = Uri.fromFile(source),
                plan = plan,
                outputDirectoryUri = Uri.fromFile(outputDir),
                cancellation = ExportCancellationToken(),
                onState = {},
            )
        }

    private class ScriptedTarget(
        root: File,
        private val failCreateAfter: Int = Int.MAX_VALUE,
        private val failOpenOutput: Boolean = false,
        private val failOpenInput: Boolean = false,
        private val refuseDelete: Boolean = false,
        private val refuseDeleteNames: Set<String> = emptySet(),
        private val writeFailureAfterBytes: Int = -1,
        private val appendByteOnClose: Boolean = false,
        private val throwOnDelete: Boolean = false,
        private val throwOnCreate: Boolean = false,
    ) : PdfSplitOutputTarget {
        private val directory = root.apply { mkdirs() }
        val createdNames = mutableListOf<String>()

        override fun createPdf(directoryUri: Uri, displayName: String): Uri? {
            if (throwOnCreate) throw IOException("provider create refused")
            if (directory.listFiles().orEmpty().size >= failCreateAfter) return null
            val file = File(directory, displayName)
            check(file.createNewFile())
            createdNames += displayName
            return Uri.fromFile(file)
        }

        override fun openInput(uri: Uri): InputStream? =
            if (failOpenInput) null else FileInputStream(requireNotNull(uri.path))

        override fun openOutput(uri: Uri): OutputStream? {
            if (failOpenOutput) return null
            val delegate = FileOutputStream(requireNotNull(uri.path))
            return object : FilterOutputStream(delegate) {
                private var written = 0

                override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

                override fun write(b: ByteArray, off: Int, len: Int) {
                    if (writeFailureAfterBytes >= 0 && written + len > writeFailureAfterBytes) {
                        throw IOException("provider write interrupted")
                    }
                    out.write(b, off, len)
                    written += len
                }

                override fun close() {
                    if (appendByteOnClose) out.write(0)
                    super.close()
                }
            }
        }

        override fun delete(uri: Uri): Boolean {
            if (throwOnDelete) throw IOException("provider delete refused")
            val file = File(requireNotNull(uri.path))
            if (refuseDelete || file.name in refuseDeleteNames) return false
            return file.delete()
        }
    }
}
