package com.m1racle.reader.data.repository

import com.m1racle.reader.core.model.OutlineImportDraft
import com.m1racle.reader.core.model.OutlineImportMode
import com.m1racle.reader.core.model.OutlineImportPlan
import com.m1racle.reader.core.model.OutlineImportRules
import com.m1racle.reader.core.model.TextbookOutlineNodeType
import com.m1racle.reader.data.local.OutlineImportPlanner
import com.m1racle.reader.testing.ProductionKotlinRoots
import com.m1racle.reader.testing.ProductionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Data layer accepts only the neutral plan; it does not see PDF parser types. */
class OutlineImportPlanBoundaryTest {

    @Test
    fun repositoryPersistsTheSuppliedPlan() {
        val source = ProductionSource.text("com/m1racle/reader/data/repository/DocumentRepository.kt")
        assertTrue(source.contains("importEmbeddedOutline(plan: com.m1racle.reader.core.model.OutlineImportPlan)"))
        assertTrue(!source.contains("com.m1racle.reader.pdf.PdfEmbeddedOutlineImporter"))
        assertTrue(!source.contains("EmbeddedOutlineNodeDraft"))
    }

    @Test
    fun replaceAndMergeKeepTheExistingPlanContract() {
        val drafts = listOf(
            OutlineImportDraft("One", 0, 2, 0, 0, -1, TextbookOutlineNodeType.CHAPTER, "one"),
            OutlineImportDraft("Two", 3, 5, 0, 1, -1, TextbookOutlineNodeType.CHAPTER, "two"),
        )
        val replaced = OutlineImportPlanner.buildReplace("doc", drafts, now = 7)
        assertEquals(2, replaced.inserted)
        assertEquals(listOf("one", "two"), replaced.nodes.map { it.importKey })
        val again = OutlineImportPlanner.buildMerge("doc", drafts, replaced.nodes, now = 8)
        assertEquals(0, again.inserted)
        assertTrue(OutlineImportRules.isAlreadyImported(drafts, replaced.nodes.map { it.importKey }))
        val plan = OutlineImportPlan("doc", drafts, OutlineImportMode.MERGE)
        assertEquals(OutlineImportMode.MERGE, plan.mode)
    }

    @Test
    fun databaseModuleHasNoPdfParserDependency() {
        val planner = ProductionSource.file("com/m1racle/reader/data/local/OutlineImportPlanner.kt")
        val root = ProductionKotlinRoots.owningRoot(planner)
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence()
                    .filter {
                        it.contains("com.m1racle.reader.pdf") ||
                            it.contains("EmbeddedOutlineNodeDraft")
                    }
                    .map { "${file.relativeTo(root).path}:$it" }
            }
            .toList()
        assertEquals(emptyList<String>(), offenders)
    }
}
