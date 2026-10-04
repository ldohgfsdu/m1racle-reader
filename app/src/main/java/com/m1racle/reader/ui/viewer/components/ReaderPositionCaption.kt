package com.m1racle.reader.ui.viewer.components

/** The secondary line adds position, rather than repeating the document title. */
internal fun readerPositionCaption(title: String, chapter: String?, progressPercent: Int): String {
    val distinctChapter = chapter?.trim()?.takeIf { it.isNotEmpty() && it != title.trim() }
    return listOfNotNull(distinctChapter, "$progressPercent%").joinToString(" · ")
}
