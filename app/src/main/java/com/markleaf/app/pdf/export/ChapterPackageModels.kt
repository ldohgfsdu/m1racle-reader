package com.markleaf.app.pdf.export

import com.markleaf.app.data.local.PdfPrintPageAnchorEntity
import com.markleaf.app.data.local.TextbookOutlineNodeEntity
import com.markleaf.app.pdf.PrintPageResolver
import com.markleaf.app.pdf.TextbookOutlineResolver

/**
 * Build metadata JSON for chapter packages without org.json
 * (works on JVM unit tests and Android).
 *
 * External page numbers are **1-based**; 0-based indices are also stored.
 */
object ChapterPackageMetadata {

    fun manifestJson(
        documentId: String,
        documentDisplayName: String,
        sourceSha256: String,
        plan: ExportRangePlan,
        createdAtIso: String,
    ): String {
        val pathArr = plan.pathTitles.joinToString(",") { "\"${escape(it)}\"" }
        val printBlock = if (plan.printPageStart != null && plan.printPageEnd != null) {
            """,
    "printed_page_range": {
      "start": ${plan.printPageStart},
      "end": ${plan.printPageEnd}
    }"""
        } else {
            ""
        }
        return """
{
  "package_type": "medlearn-source-chapter",
  "schema_version": "1.0.0",
  "document": {
    "id": "${escape(documentId)}",
    "display_name": "${escape(documentDisplayName)}",
    "source_sha256": "$sourceSha256"
  },
  "selected_node": {
    "id": "${escape(plan.node.id)}",
    "type": "${escape(plan.node.type)}",
    "title": "${escape(plan.node.title)}",
    "path": [$pathArr]
  },
  "source_range": {
    "page_numbering": "1-based for pdf_page_*; source_page_index is 0-based",
    "pdf_page_start": ${plan.pdfPageStart1Based},
    "pdf_page_end": ${plan.pdfPageEnd1Based},
    "source_page_index_start": ${plan.startPageIndex},
    "source_page_index_end": ${plan.endPageIndex},
    "page_count": ${plan.pageCount},
    "end_page_source": "${plan.endSource.name}",
    "end_page_source_label_zh": "${escape(plan.endSource.labelZh())}"$printBlock
  },
  "created_at": "$createdAtIso"
}
""".trimIndent()
    }

    fun outlineJson(
        plan: ExportRangePlan,
        allNodes: List<TextbookOutlineNodeEntity>,
    ): String {
        val subtreeIds = TextbookOutlineResolver.subtreeIds(plan.node.id, allNodes)
        val inRange = allNodes.filter { n ->
            n.id in subtreeIds &&
                n.startPageIndex <= plan.endPageIndex &&
                n.endPageIndex >= plan.startPageIndex
        }
        val byParent = inRange.groupBy { it.parentId }

        fun exportPage1(sourceIndex0: Int): Int =
            (sourceIndex0 - plan.startPageIndex + 1).coerceAtLeast(1)

        fun buildNode(n: TextbookOutlineNodeEntity): String {
            val children = byParent[n.id].orEmpty()
                .sortedWith(
                    compareBy<TextbookOutlineNodeEntity> { it.sortOrder }
                        .thenBy { it.startPageIndex }
                        .thenBy { it.title },
                )
            val srcStart = n.startPageIndex.coerceIn(plan.startPageIndex, plan.endPageIndex)
            val srcEnd = n.endPageIndex.coerceIn(plan.startPageIndex, plan.endPageIndex)
            val kids = children.joinToString(",\n") { buildNode(it) }
            return """
{
  "id": "${escape(n.id)}",
  "title": "${escape(n.title)}",
  "type": "${escape(n.type)}",
  "source_pdf_page": ${srcStart + 1},
  "source_page_index": $srcStart,
  "exported_pdf_page": ${exportPage1(srcStart)},
  "source_pdf_page_end": ${srcEnd + 1},
  "exported_pdf_page_end": ${exportPage1(srcEnd)},
  "children": [${if (kids.isEmpty()) "" else "\n$kids\n"}]
}
""".trimIndent()
        }

        val root = buildNode(plan.node)
        return """
{
  "page_numbering": "1-based for *_pdf_page; source_page_index is 0-based",
  "root": $root
}
""".trimIndent()
    }

    fun pageMapJson(
        plan: ExportRangePlan,
        printAnchors: List<PdfPrintPageAnchorEntity>,
    ): String {
        val entries = (0 until plan.pageCount).joinToString(",\n") { i ->
            val source0 = plan.startPageIndex + i
            val printed = PrintPageResolver.resolve(source0, printAnchors)
            val printedJson = printed?.toString() ?: "null"
            """
  {
    "exported_pdf_page": ${i + 1},
    "exported_page_index": $i,
    "source_pdf_page": ${source0 + 1},
    "source_page_index": $source0,
    "printed_page": $printedJson
  }""".trimEnd()
        }
        return "[\n$entries\n]"
    }

    fun checksumsJson(files: Map<String, String>): String {
        val body = files.entries.joinToString(",\n") { (k, v) ->
            "  \"${escape(k)}\": \"$v\""
        }
        return "{\n$body\n}"
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
}
