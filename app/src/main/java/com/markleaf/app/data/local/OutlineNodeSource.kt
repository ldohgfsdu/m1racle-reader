package com.markleaf.app.data.local

/**
 * Provenance of a textbook outline node.
 * MANUAL = P0.3A hand catalog; PDF_EMBEDDED = imported from PDF bookmarks.
 */
enum class OutlineNodeSource {
    MANUAL,
    PDF_EMBEDDED,
    ;

    companion object {
        fun fromStorage(value: String?): OutlineNodeSource {
            return when (value?.uppercase()) {
                PDF_EMBEDDED.name -> PDF_EMBEDDED
                else -> MANUAL
            }
        }
    }
}
