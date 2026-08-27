package com.m1racle.reader.ui.home

import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.local.DocumentType
import com.m1racle.reader.data.repository.ImportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HomeImportEventTest {

    @Test
    fun `successful import opens the imported document`() {
        val document = document("pdf-1", DocumentType.PDF)

        val event = ImportResult.Success(document).toHomeEvent()

        val open = event as HomeEvent.OpenDocument
        assertSame(document, open.document)
    }

    @Test
    fun `import failures remain user-visible events`() {
        assertEquals(
            HomeEvent.UnsupportedFormat,
            ImportResult.UnsupportedFormat.toHomeEvent(),
        )
        assertEquals(
            HomeEvent.ReadFailed,
            ImportResult.ReadFailed.toHomeEvent(),
        )
    }

    private fun document(id: String, type: DocumentType) = DocumentEntity(
        id = id,
        displayName = "$id.pdf",
        uri = "content://provider/$id",
        mimeType = "application/pdf",
        fileSize = 1L,
        lastOpenedAt = 1L,
        createdAt = 1L,
        lastScrollIndex = 0,
        lastScrollOffset = 0,
        readingProgress = 0f,
        title = null,
        headingCount = 0,
        documentType = type.name,
    )
}
