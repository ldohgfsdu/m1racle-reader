package com.m1racle.reader.core.model

/**
 * Neutral outline import request. The caller computes drafts and mode;
 * persistence only writes the resulting nodes.
 */
data class OutlineImportPlan(
    val documentId: String,
    val drafts: List<OutlineImportDraft>,
    val mode: OutlineImportMode,
)

enum class OutlineImportMode { REPLACE, MERGE }

data class OutlineImportDraft(
    val title: String,
    val pageIndex: Int,
    val endPageIndex: Int,
    val depth: Int,
    val order: Int,
    val parentOrder: Int,
    val type: TextbookOutlineNodeType,
    val importKey: String,
)
