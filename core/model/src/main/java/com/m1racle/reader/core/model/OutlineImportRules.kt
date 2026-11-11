package com.m1racle.reader.core.model

/** Shared idempotency rule for outline import across orchestration and persistence. */
object OutlineImportRules {
    fun newDrafts(
        drafts: List<OutlineImportDraft>,
        existingImportKeys: Iterable<String?>,
    ): List<OutlineImportDraft> {
        val existingKeys = existingImportKeys.filterNotNull().toSet()
        return drafts.filter { it.importKey !in existingKeys }
    }

    fun isAlreadyImported(
        drafts: List<OutlineImportDraft>,
        existingImportKeys: Iterable<String?>,
    ): Boolean = newDrafts(drafts, existingImportKeys).isEmpty()
}
