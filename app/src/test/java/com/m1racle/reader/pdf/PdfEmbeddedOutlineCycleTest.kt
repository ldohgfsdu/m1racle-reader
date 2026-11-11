package com.m1racle.reader.pdf

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Regression for cyclic PDF bookmarks.
 *
 * Root cause: `walk` relied on a per-level `guard++ < 50_000` and had no
 * identity check, so a /Next pointer back at the same COS dictionary produced
 * 50,000 entries that `decideOutcome` reported as Success — and 93 imports
 * embedded outlines silently on first open. Evidence:
 * output/comprehensive-audit-20261031/audit-outline-probe.log
 * (`uniqueBookmarks=1 parsedEntries=50000 resultType=Success`).
 *
 * Contract under test: a malformed tree is rejected, never imported; a
 * well-formed tree is still parsed in full.
 */
@RunWith(RobolectricTestRunner::class)
class PdfEmbeddedOutlineCycleTest {

    @get:Rule val folder = TemporaryFolder()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun init() { check(PdfBoxRuntime.ensureInit(context)) }

    @Test
    fun nextSiblingSelfCycleIsRejectedInsteadOfImportingFiftyThousandRows() = runBlocking {
        val file = writeOutline { page, root ->
            val item = PDOutlineItem().apply {
                title = "Only bookmark"
                destination = PDPageXYZDestination().apply { setPage(page) }
            }
            root.addLast(item)
            item.cosObject.setItem(COSName.NEXT, item.cosObject)
        }

        val result = PdfEmbeddedOutlineParser.parse(context, Uri.fromFile(file).toString())

        assertTrue(
            "A cyclic outline must not be reported as a successful import: $result",
            result is PdfEmbeddedOutlineParser.ParseResult.Failed,
        )
        val count = (result as? PdfEmbeddedOutlineParser.ParseResult.Success)?.nodeCount ?: 0
        assertTrue("A cyclic bookmark must be visited at most once, got $count", count <= 1)
    }

    @Test
    fun firstChildSelfCycleIsRejectedAcrossLevels() = runBlocking {
        val file = writeOutline { page, root ->
            val item = PDOutlineItem().apply {
                title = "Parent"
                destination = PDPageXYZDestination().apply { setPage(page) }
            }
            root.addLast(item)
            item.cosObject.setItem(COSName.getPDFName("First"), item.cosObject)
            item.cosObject.setItem(COSName.getPDFName("Last"), item.cosObject)
        }

        val result = PdfEmbeddedOutlineParser.parse(context, Uri.fromFile(file).toString())

        assertTrue(
            "A child cycle must not be reported as a successful import: $result",
            result is PdfEmbeddedOutlineParser.ParseResult.Failed,
        )
    }

    @Test
    fun healthyOutlineStillParsesEveryEntry() = runBlocking {
        val file = writeOutline { page, root ->
            repeat(3) { index ->
                root.addLast(PDOutlineItem().apply {
                    title = "Chapter $index"
                    destination = PDPageXYZDestination().apply { setPage(page) }
                })
            }
        }

        val result = PdfEmbeddedOutlineParser.parse(context, Uri.fromFile(file).toString())

        assertTrue(
            "A well-formed outline must not be rejected: $result",
            result is PdfEmbeddedOutlineParser.ParseResult.Success,
        )
        val success = result as PdfEmbeddedOutlineParser.ParseResult.Success
        assertEquals(3, success.nodeCount)
        assertEquals(
            listOf("Chapter 0", "Chapter 1", "Chapter 2"),
            success.entries.map { it.title },
        )
    }

    private fun writeOutline(build: (PDPage, PDDocumentOutline) -> Unit): File {
        val file = folder.newFile("outline-cycle-fixture.pdf")
        PDDocument().use { doc ->
            val page = PDPage().also(doc::addPage)
            val root = PDDocumentOutline().also { doc.documentCatalog.documentOutline = it }
            build(page, root)
            doc.save(file)
        }
        return file
    }
}
