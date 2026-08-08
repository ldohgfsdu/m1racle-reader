package com.markleaf.app.ui.home

import com.markleaf.app.data.local.DocumentEntity

/**
 * Pure list-identity helpers for Home recent files.
 *
 * Optimistic remove prevents a visible residual card/blank slot while Room
 * Flow catches up after [DocumentRepository.deleteById].
 */
object HomeListUpdate {

    /**
     * Visible list = source minus ids pending delete.
     * Order of [source] is preserved (already lastOpenedAt DESC from Room).
     */
    fun visibleDocuments(
        source: List<DocumentEntity>,
        pendingDeleteIds: Set<String>,
    ): List<DocumentEntity> {
        if (pendingDeleteIds.isEmpty()) return source
        return source.filter { it.id !in pendingDeleteIds }
    }

    /**
     * After a source emit, drop pending ids that are no longer in [source]
     * (delete committed) so the pending set cannot grow forever.
     * Ids still present stay pending (delete in flight / failed).
     */
    fun reconcilePending(
        pendingDeleteIds: Set<String>,
        source: List<DocumentEntity>,
    ): Set<String> {
        if (pendingDeleteIds.isEmpty()) return pendingDeleteIds
        val present = source.mapTo(HashSet(source.size)) { it.id }
        return pendingDeleteIds.filterTo(HashSet()) { it in present }
    }

    /** True when [id] would be shown after applying [pendingDeleteIds]. */
    fun isVisible(id: String, pendingDeleteIds: Set<String>): Boolean =
        id !in pendingDeleteIds
}
