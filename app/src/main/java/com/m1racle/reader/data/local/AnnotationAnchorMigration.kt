package com.m1racle.reader.data.local

/** v17 data-only repair. Room runs this once, transactionally, before any reader opens. */
internal object AnnotationAnchorMigration {
    val statements: List<String> = listOf("highlights", "notes").flatMap { table ->
        listOf(
            """
            UPDATE $table SET anchor = CASE
              WHEN locationKind = 'PAGE_INDEX' THEN 'pdf:' || location || '|-'
              WHEN locationKind = 'SCROLL_POSITION' THEN 'md:' || location || '|' ||
                CASE WHEN anchorStart >= 0 AND anchorEnd >= anchorStart
                  THEN anchorStart || ',' || anchorEnd ELSE '-' END
            END
            WHERE anchor IS NULL AND location >= 0
              AND locationKind IN ('PAGE_INDEX', 'SCROLL_POSITION')
            """.trimIndent(),
            """
            UPDATE search_index SET anchor = (
              SELECT anchor FROM $table WHERE documentId = search_index.documentId
                AND CAST(id AS TEXT) = search_index.sourceId
            ) WHERE contentType = '${if (table == "highlights") "HIGHLIGHT" else "NOTE"}'
              AND EXISTS (SELECT 1 FROM $table WHERE documentId = search_index.documentId
                AND CAST(id AS TEXT) = search_index.sourceId AND anchor IS NOT NULL)
            """.trimIndent(),
        )
    }
}
