package com.m1racle.reader.data.local

import com.m1racle.reader.core.model.OutlineImportDraft
import com.m1racle.reader.core.model.OutlineImportRules
import com.m1racle.reader.core.model.OutlineNodeSource
import java.util.UUID

/**
 * Turns a neutral outline import plan into Room nodes.
 * Pure logic; no I/O and no PDF parser types.
 */
object OutlineImportPlanner {

    data class Result(
        val nodes: List<TextbookOutlineNodeEntity>,
        val inserted: Int,
        val skipped: Int,
        val replaced: Int,
    )

    fun buildReplace(
        documentId: String,
        drafts: List<OutlineImportDraft>,
        now: Long = System.currentTimeMillis(),
    ): Result {
        val nodes = draftsToEntities(documentId, drafts, now)
        return Result(nodes = nodes, inserted = nodes.size, skipped = 0, replaced = nodes.size)
    }

    fun buildMerge(
        documentId: String,
        drafts: List<OutlineImportDraft>,
        existing: List<TextbookOutlineNodeEntity>,
        now: Long = System.currentTimeMillis(),
    ): Result {
        val toAdd = OutlineImportRules.newDrafts(
            drafts,
            existing.map { it.importKey },
        )
        if (toAdd.isEmpty()) {
            return Result(nodes = emptyList(), inserted = 0, skipped = drafts.size, replaced = 0)
        }
        val keyToExistingId = existing.mapNotNull { entity ->
            entity.importKey?.let { it to entity.id }
        }.toMap().toMutableMap()
        val orderToKey = drafts.associate { it.order to it.importKey }
        val newEntities = ArrayList<TextbookOutlineNodeEntity>()
        for (draft in toAdd.sortedBy { it.order }) {
            val id = UUID.randomUUID().toString()
            keyToExistingId[draft.importKey] = id
            val parentId = if (draft.parentOrder < 0) {
                null
            } else {
                orderToKey[draft.parentOrder]?.let { keyToExistingId[it] }
            }
            newEntities.add(entity(documentId, id, parentId, draft, draft.order, now))
        }
        return Result(
            nodes = newEntities,
            inserted = newEntities.size,
            skipped = drafts.size - newEntities.size,
            replaced = 0,
        )
    }

    private fun draftsToEntities(
        documentId: String,
        drafts: List<OutlineImportDraft>,
        now: Long,
    ): List<TextbookOutlineNodeEntity> {
        if (drafts.isEmpty()) return emptyList()
        val orderToId = drafts.associate { it.order to UUID.randomUUID().toString() }
        return drafts.map { draft ->
            entity(
                documentId = documentId,
                id = orderToId.getValue(draft.order),
                parentId = if (draft.parentOrder < 0) null else orderToId[draft.parentOrder],
                draft = draft,
                sortOrder = draft.order,
                now = now,
            )
        }
    }

    private fun entity(
        documentId: String,
        id: String,
        parentId: String?,
        draft: OutlineImportDraft,
        sortOrder: Int,
        now: Long,
    ) = TextbookOutlineNodeEntity(
        id = id,
        documentId = documentId,
        parentId = parentId,
        type = draft.type.name,
        title = draft.title,
        startPageIndex = draft.pageIndex,
        endPageIndex = draft.endPageIndex,
        sortOrder = sortOrder,
        createdAt = now,
        updatedAt = now,
        source = OutlineNodeSource.PDF_EMBEDDED.name,
        importKey = draft.importKey,
    )
}
