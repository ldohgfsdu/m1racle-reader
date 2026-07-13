package com.markleaf.app.pdf.export

import com.markleaf.app.data.local.TextbookOutlineNodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.io.path.createTempDirectory

/**
 * P0.4A acceptance for textbook node:
 * 第二篇 呼吸系统疾病 → 第一章 总论 → PDF 41–48 (8 pages).
 *
 * Metadata + packaging validated on JVM; real page extract is done offline
 * against 内科学（第10版）.pdf (see tools / outbox acceptance notes).
 */
class ChapterPackageP04aAcceptanceTest {

    private fun node(
        id: String,
        parent: String?,
        title: String,
        type: String,
        start0: Int,
        end0: Int,
        sort: Int,
    ) = TextbookOutlineNodeEntity(
        id = id,
        documentId = "doc-neike-10",
        parentId = parent,
        type = type,
        title = title,
        startPageIndex = start0,
        endPageIndex = end0,
        sortOrder = sort,
        createdAt = 0L,
        updatedAt = 0L,
    )

    /** Outline graph matching the real 内科学 sample navigation. */
    private fun sampleNodes(): List<TextbookOutlineNodeEntity> {
        val part = node(
            id = "part-resp",
            parent = null,
            title = "第二篇 呼吸系统疾病",
            type = "PART",
            start0 = 40,
            end0 = 198,
            sort = 0,
        )
        val ch1 = node(
            id = "ch1-zonglun",
            parent = "part-resp",
            title = "第一章 总论",
            type = "CHAPTER",
            start0 = 40,
            end0 = 40, // stored start; end derived from next sibling
            sort = 0,
        )
        val ch2 = node(
            id = "ch2-next",
            parent = "part-resp",
            title = "第二章 急性上呼吸道感染和急性气管-支气管炎",
            type = "CHAPTER",
            start0 = 48, // PDF page 49 → end of 总论 = 47 (page 48)
            end0 = 60,
            sort = 1,
        )
        val otherPart = node(
            id = "part-other",
            parent = null,
            title = "第三篇 循环系统疾病",
            type = "PART",
            start0 = 199,
            end0 = 400,
            sort = 1,
        )
        return listOf(part, ch1, ch2, otherPart)
    }

    @Test
    fun range_ch1_is_pdf_41_to_48() {
        val nodes = sampleNodes()
        val ch1 = nodes.first { it.id == "ch1-zonglun" }
        val (end, src) = ExportRangeResolver.computeEndPage(ch1, nodes, 982)
        assertEquals(47, end) // PDF page 48
        assertEquals(EndPageSource.NEXT_SIBLING, src)

        val plan = ExportRangeResolver.plan(ch1, nodes, 982)
        assertEquals(40, plan.startPageIndex) // PDF 41
        assertEquals(47, plan.endPageIndex) // PDF 48
        assertEquals(8, plan.pageCount)
        assertEquals(41, plan.pdfPageStart1Based)
        assertEquals(48, plan.pdfPageEnd1Based)
        assertEquals(
            listOf("第二篇 呼吸系统疾病", "第一章 总论"),
            plan.pathTitles,
        )
        assertEquals(EndPageSource.NEXT_SIBLING, plan.endSource)
    }

    @Test
    fun manifest_selectedNodeAndPath() {
        val nodes = sampleNodes()
        val ch1 = nodes.first { it.id == "ch1-zonglun" }
        val plan = ExportRangeResolver.plan(ch1, nodes, 982)
        val json = ChapterPackageMetadata.manifestJson(
            documentId = "doc-neike-10",
            documentDisplayName = "内科学（第10版）.pdf",
            sourceSha256 = "abc123",
            plan = plan,
            createdAtIso = "2026-07-10T00:00:00Z",
        )
        assertTrue(json.contains("\"package_type\": \"medlearn-source-chapter\""))
        assertTrue(json.contains("\"schema_version\": \"1.0.0\""))
        assertTrue(json.contains("doc-neike-10"))
        assertTrue(json.contains("内科学（第10版）.pdf"))
        assertTrue(json.contains("第一章 总论"))
        assertTrue(json.contains("第二篇 呼吸系统疾病"))
        assertTrue(json.contains("\"pdf_page_start\": 41"))
        assertTrue(json.contains("\"pdf_page_end\": 48"))
        assertTrue(json.contains("\"page_count\": 8"))
        assertTrue(json.contains("source_page_index_start\": 40"))
        assertTrue(json.contains("source_page_index_end\": 47"))
        assertTrue(json.contains("NEXT_SIBLING") || json.contains("下一同级"))
        assertTrue(json.contains("abc123"))
        assertTrue(json.contains("0-based") || json.contains("1-based"))
    }

    @Test
    fun outline_rootIsCh1_noOtherParts() {
        val nodes = sampleNodes()
        val ch1 = nodes.first { it.id == "ch1-zonglun" }
        val plan = ExportRangeResolver.plan(ch1, nodes, 982)
        val json = ChapterPackageMetadata.outlineJson(plan, nodes)
        assertTrue(json.contains("第一章 总论"))
        assertTrue(json.contains("\"exported_pdf_page\": 1"))
        assertTrue(json.contains("\"source_pdf_page\": 41"))
        assertFalse(json.contains("第三篇"))
        assertFalse(json.contains("第二章 急性"))
        // path titles are only in manifest; root is ch1
        assertTrue(json.contains("\"root\""))
    }

    @Test
    fun pageMap_eightEntries_nullPrintedWithoutAnchors() {
        val nodes = sampleNodes()
        val ch1 = nodes.first { it.id == "ch1-zonglun" }
        val plan = ExportRangeResolver.plan(ch1, nodes, 982)
        val json = ChapterPackageMetadata.pageMapJson(plan, emptyList())
        assertEquals(8, Regex("\"exported_pdf_page\"").findAll(json).count())
        assertTrue(json.contains("\"exported_pdf_page\": 1"))
        assertTrue(json.contains("\"exported_pdf_page\": 8"))
        assertTrue(json.contains("\"source_pdf_page\": 41"))
        assertTrue(json.contains("\"source_pdf_page\": 48"))
        // all printed_page null when no anchors
        assertEquals(8, Regex("\"printed_page\": null").findAll(json).count())
    }

    @Test
    fun validateRange_rejectsEndBeforeStart() {
        val err = ExportRangeResolver.validateRange(47, 40, 982)
        assertNotNull(err)
        assertTrue(err!!.contains("起始") || err.contains("结束") || err.contains("大于"))
    }

    @Test
    fun checksums_coverFourFiles_andMatch() {
        val dir = createTempDirectory("p04a_ck").toFile()
        try {
            val files = listOf(
                "source.pdf" to "%PDF-1.4 mock",
                "manifest.json" to "{\"ok\":true}",
                "outline.json" to "{\"root\":{}}",
                "page_map.json" to "[]",
            )
            val hashes = linkedMapOf<String, String>()
            for ((name, body) in files) {
                val f = File(dir, name)
                f.writeText(body)
                hashes[name] = sha256(f)
            }
            val checksumsBody = ChapterPackageMetadata.checksumsJson(hashes)
            val checksumsFile = File(dir, "checksums.json")
            checksumsFile.writeText(checksumsBody)

            // Must list exactly the four payload files
            assertTrue(checksumsBody.contains("source.pdf"))
            assertTrue(checksumsBody.contains("manifest.json"))
            assertTrue(checksumsBody.contains("outline.json"))
            assertTrue(checksumsBody.contains("page_map.json"))
            assertFalse(
                "checksums must not self-reference with mutable hash",
                checksumsBody.contains("\"checksums.json\""),
            )

            // Re-hash each and compare
            for ((name, expected) in hashes) {
                assertEquals(expected, sha256(File(dir, name)))
                assertTrue(checksumsBody.contains(expected))
            }

            // ZIP only fixed entries
            val zip = File(dir, "pkg.zip")
            ChapterPackageExporter.zipFiles(
                zip,
                files.map { File(dir, it.first) to it.first } +
                    listOf(checksumsFile to "checksums.json"),
            )
            ZipFile(zip).use { zf ->
                val names = zf.entries().asSequence().map { it.name }.toSet()
                assertEquals(
                    setOf(
                        "source.pdf",
                        "manifest.json",
                        "outline.json",
                        "page_map.json",
                        "checksums.json",
                    ),
                    names,
                )
                assertFalse(names.any { it.contains("tmp") || it.startsWith("_") })
            }
        } finally {
            ChapterPackageExporter.cleanupDir(dir)
        }
    }

    @Test
    fun defaultZipName_forCh1() {
        val name = ExportRangeResolver.defaultZipName(
            "内科学（第10版）.pdf",
            listOf("第二篇 呼吸系统疾病", "第一章 总论"),
        )
        assertTrue(name.endsWith(".zip"))
        assertFalse(name.contains("/"))
        assertTrue(name.contains("总论") || name.contains("第一章") || name.contains("内科学"))
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { inp ->
            val buf = ByteArray(8192)
            while (true) {
                val n = inp.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
