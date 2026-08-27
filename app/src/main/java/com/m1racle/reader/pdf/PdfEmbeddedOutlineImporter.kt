package com.m1racle.reader.pdf

import com.m1racle.reader.data.local.OutlineNodeSource
import com.m1racle.reader.data.local.TextbookOutlineNodeEntity
import java.util.UUID

/**
 * Import policy for embedded outlines → Room entities.
 * Pure logic for tests; no I/O.
 */
object PdfEmbeddedOutlineImporter {

    data class ImportResult(
        val nodes: List<TextbookOutlineNodeEntity>,
        val inserted: Int,
        val skipped: Int,
        val replaced: Int,
    )

    /**
     * Build entities for REPLACE: wipe all previous outline for document, insert all drafts.
     */
    fun buildReplace(
        documentId: String,
        drafts: List<EmbeddedOutlineNodeDraft>,
        now: Long = System.currentTimeMillis(),
    ): ImportResult {
        val nodes = draftsToEntities(documentId, drafts, now)
        return ImportResult(nodes = nodes, inserted = nodes.size, skipped = 0, replaced = nodes.size)
    }

    /**
     * MERGE: insert only drafts whose importKey is not already present.
     * Never duplicates by importKey.
     */
    fun buildMerge(
        documentId: String,
        drafts: List<EmbeddedOutlineNodeDraft>,
        existing: List<TextbookOutlineNodeEntity>,
        now: Long = System.currentTimeMillis(),
    ): ImportResult {
        val existingKeys = existing.mapNotNull { it.importKey }.toSet()
        val toAdd = drafts.filter { it.importKey !in existingKeys }
        if (toAdd.isEmpty()) {
            return ImportResult(nodes = emptyList(), inserted = 0, skipped = drafts.size, replaced = 0)
        }
        // Parent links: if parent draft was skipped (already exists), resolve parentId via importKey map.
        val keyToExistingId = existing.mapNotNull { e ->
            e.importKey?.let { it to e.id }
        }.toMap().toMutableMap()

        val newEntities = ArrayList<TextbookOutlineNodeEntity>()
        val orderToKey = drafts.associate { it.order to it.importKey }
        val orderToDraft = drafts.associateBy { it.order }

        // Process in order so parents (lower order, created first in DFS) exist when children attach.
        for (d in toAdd.sortedBy { it.order }) {
            val id = UUID.randomUUID().toString()
            keyToExistingId[d.importKey] = id
            val parentId = if (d.parentOrder < 0) {
                null
            } else {
                val parentKey = orderToKey[d.parentOrder]
                parentKey?.let { keyToExistingId[it] }
            }
            newEntities.add(
                TextbookOutlineNodeEntity(
                    id = id,
                    documentId = documentId,
                    parentId = parentId,
                    type = d.type.name,
                    title = d.title,
                    startPageIndex = d.pageIndex,
                    endPageIndex = d.endPageIndex,
                    sortOrder = d.order,
                    createdAt = now,
                    updatedAt = now,
                    source = OutlineNodeSource.PDF_EMBEDDED.name,
                    importKey = d.importKey,
                ),
            )
        }
        return ImportResult(
            nodes = newEntities,
            inserted = newEntities.size,
            skipped = drafts.size - newEntities.size,
            replaced = 0,
        )
    }

    /**
     * Idempotent check: if every draft importKey already exists, no write needed.
     */
    fun isAlreadyImported(
        drafts: List<EmbeddedOutlineNodeDraft>,
        existing: List<TextbookOutlineNodeEntity>,
    ): Boolean {
        if (drafts.isEmpty()) return true
        val keys = existing.mapNotNull { it.importKey }.toSet()
        return drafts.all { it.importKey in keys }
    }

    fun draftsToEntities(
        documentId: String,
        drafts: List<EmbeddedOutlineNodeDraft>,
        now: Long,
    ): List<TextbookOutlineNodeEntity> {
        if (drafts.isEmpty()) return emptyList()
        val orderToId = drafts.associate { it.order to UUID.randomUUID().toString() }
        return drafts.map { d ->
            TextbookOutlineNodeEntity(
                id = orderToId[d.order]!!,
                documentId = documentId,
                parentId = if (d.parentOrder < 0) null else orderToId[d.parentOrder],
                type = d.type.name,
                title = d.title,
                startPageIndex = d.pageIndex,
                endPageIndex = d.endPageIndex,
                sortOrder = d.order,
                createdAt = now,
                updatedAt = now,
                source = OutlineNodeSource.PDF_EMBEDDED.name,
                importKey = d.importKey,
            )
        }
    }
}
