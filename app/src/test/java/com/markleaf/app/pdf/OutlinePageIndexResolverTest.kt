package com.markleaf.app.pdf

import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.pdmodel.PDDocumentNameDestinationDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Behavioral regression tests for outline page-index resolution.
 *
 * Root cause under test: pdfbox-android 2.0.27 returns a **fresh PDPage
 * wrapper on every access** (`getPage(i)`, destination retrieval), so
 * `IdentityHashMap<PDPage, Int>` can never hit and every bookmark degraded to
 * an O(pages) `indexOf` — or, worse, resolved as -1 if dictionary instances
 * were not shared. [OutlinePageIndexResolver] keys on the stable underlying
 * COSDictionary instead.
 */
class OutlinePageIndexResolverTest {

    private fun buildOutlinePdf(): File {
        val dir = File("build/tmp/outline-resolver-test").apply { mkdirs() }
        val pdf = File(dir, "outline-fixture.pdf")
        PDDocument().use { doc ->
            repeat(4) { doc.addPage(PDPage()) }

            // Named destination "named-last" → page 3, stored in the catalog's
            // direct /Dests dictionary (second lookup path of
            // findNamedDestinationPage, after the /Names name tree).
            val destsDict = PDDocumentNameDestinationDictionary(COSDictionary())
            destsDict.cosObject.setItem(
                COSName.getPDFName("named-last"),
                PDPageXYZDestination().apply { setPage(doc.getPage(3)) }.cosObject,
            )
            doc.documentCatalog.cosObject.setItem(COSName.getPDFName("Dests"), destsDict.cosObject)

            val outline = PDDocumentOutline()
            doc.documentCatalog.documentOutline = outline

            fun addItem(
                title: String,
                dest: com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination?,
                action: com.tom_roush.pdfbox.pdmodel.interactive.action.PDAction? = null,
                parent: PDOutlineNode = outline,
            ): PDOutlineItem {
                val item = PDOutlineItem().apply { this.title = title }
                if (dest != null) item.destination = dest
                if (action != null) item.action = action
                parent.addLast(item)
                return item
            }

            // 1) explicit destination → first page (0)
            addItem("first", PDPageXYZDestination().apply { setPage(doc.getPage(0)) })
            // 2) explicit destination → middle page (2)
            addItem("middle", PDPageXYZDestination().apply { setPage(doc.getPage(2)) })
            // 3) named destination → last page (3)
            addItem("named-last", PDNamedDestination("named-last"))
            // 4) PDActionGoTo → page 1
            val goto = addItem(
                "goto",
                null,
                PDActionGoTo().apply {
                    destination = PDPageXYZDestination().apply { setPage(doc.getPage(1)) }
                },
            )
            // 5) nested child under "goto": truly unresolved (no dest, no action)
            addItem("unresolved-child", null, parent = goto)
            // 6) named destination that does not exist → unresolved
            addItem("missing-name", PDNamedDestination("does-not-exist"))

            doc.save(pdf)
        }
        return pdf
    }

    /** DFS order of outline items, matching the parser's walk order. */
    private fun collectItems(outline: PDDocumentOutline): List<PDOutlineItem> {
        val items = mutableListOf<PDOutlineItem>()
        fun collect(node: PDOutlineItem) {
            items.add(node)
            var child = node.firstChild
            while (child != null) {
                collect(child)
                child = child.nextSibling
            }
        }
        var item = outline.firstChild
        while (item != null) {
            collect(item)
            item = item.nextSibling
        }
        return items
    }

    @Test
    fun resolvesAllDestinationKinds_first_middle_last_named_goto_nested() {
        val pdf = buildOutlinePdf()
        PDDocument.load(pdf).use { doc ->
            val resolver = OutlinePageIndexResolver(doc)
            val items = collectItems(doc.documentCatalog.documentOutline)
            val expected = listOf(0, 2, 3, 1, -1, -1) // last two unresolved
            assertEquals(6, items.size)
            items.forEachIndexed { i, item ->
                assertEquals(
                    "item[$i]=${item.title}",
                    expected[i],
                    resolver.resolvePageIndex(item),
                )
            }
        }
    }

    @Test
    fun freshWrapperForSamePage_stillResolves() {
        // Core regression: pdfbox returns a NEW PDPage wrapper for the same
        // underlying page. Wrapper identity must not matter.
        val pdf = buildOutlinePdf()
        PDDocument.load(pdf).use { doc ->
            // Prove the failure mode is real: two fetches of page 0 are
            // different Java instances...
            assertNotSame("getPage() must return fresh wrappers", doc.getPage(0), doc.getPage(0))
            // ...but the resolver (keyed on COSDictionary) resolves regardless.
            val resolver = OutlinePageIndexResolver(doc)
            val item = doc.documentCatalog.documentOutline.firstChild
            assertEquals(0, resolver.resolvePageIndex(item))
            // Also resolve via a freshly fetched wrapper path: destinations of
            // other items still hit the dictionary map.
            assertEquals(3, resolver.resolvePageIndex(collectItems(doc.documentCatalog.documentOutline)[2]))
        }
    }

    @Test
    fun wrapperInstancesAreNotStable_butDictionariesAre() {
        // Documents the pdfbox-android contract the fix relies on:
        // wrappers unstable, underlying COSDictionary stable per page.
        val pdf = buildOutlinePdf()
        PDDocument.load(pdf).use { doc ->
            val w1 = doc.getPage(0)
            val w2 = doc.getPage(0)
            assertNotSame("PDPage wrapper is re-created", w1, w2)
            assertTrue("underlying COSDictionary is shared", w1.cosObject === w2.cosObject)
        }
    }

    // ── Failure policy (PdfEmbeddedOutlineParser.decideOutcome) ──────────────

    private fun entry(title: String = "t") = EmbeddedOutlineEntry(
        title = title,
        pageIndex = 0,
        depth = 0,
        order = 0,
        parentOrder = -1,
    )

    @Test
    fun decideOutcome_allResolved_isSuccess() {
        val r = PdfEmbeddedOutlineParser.decideOutcome(List(4) { entry() }, unresolved = 0)
        assertTrue(r is PdfEmbeddedOutlineParser.ParseResult.Success)
        assertEquals(0, (r as PdfEmbeddedOutlineParser.ParseResult.Success).unresolvedDestinations)
    }

    @Test
    fun decideOutcome_minorUnresolved_keepsOutline() {
        // 2 of 6 unresolved: 2*2=4 < 6 → outline stays usable, entries kept.
        val r = PdfEmbeddedOutlineParser.decideOutcome(List(6) { entry() }, unresolved = 2)
        assertTrue(r is PdfEmbeddedOutlineParser.ParseResult.Success)
        assertEquals(2, (r as PdfEmbeddedOutlineParser.ParseResult.Success).unresolvedDestinations)
    }

    @Test
    fun decideOutcome_majorityUnresolved_isFailed() {
        // 3 of 4 unresolved: 3*2=6 >= 4 → outline untrusted, surfaced as failure.
        val r = PdfEmbeddedOutlineParser.decideOutcome(List(4) { entry() }, unresolved = 3)
        assertTrue(r is PdfEmbeddedOutlineParser.ParseResult.Failed)
    }

    @Test
    fun decideOutcome_empty_isNone() {
        assertTrue(
            PdfEmbeddedOutlineParser.decideOutcome(emptyList(), unresolved = 0) is
                PdfEmbeddedOutlineParser.ParseResult.None,
        )
    }
}
