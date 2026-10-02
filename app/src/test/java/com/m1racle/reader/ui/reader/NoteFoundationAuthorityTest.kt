package com.m1racle.reader.ui.reader

import com.m1racle.reader.testing.ProductionSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** R27 结构红线：Note UI 只消费控制器，Repository 是唯一持久化门面。 */
class NoteFoundationAuthorityTest {

    @Test
    fun `pdf reader persists notes through controller instead of dao or repository`() {
        val screen = source("app/src/main/java/com/m1racle/reader/ui/pdf/PdfViewerScreen.kt")
        assertTrue(screen.contains("ReaderNoteController"))
        assertTrue(screen.contains("notes.create(target.anchor, content)"))
        assertTrue(screen.contains("notes.update(target.note.id, content)"))
        assertTrue(screen.contains("noteController?.delete(note.id)"))
        assertFalse(screen.contains("data.local.NoteDao"))
        assertFalse(screen.contains("ReadingRepository"))

        val controller = source("app/src/main/java/com/m1racle/reader/ui/reader/ReaderNoteController.kt")
        assertTrue(controller.contains("readingRepository.addNote("))
        assertTrue(controller.contains("readingRepository.updateNoteContent("))
        assertTrue(controller.contains("readingRepository.removeNote("))
        assertFalse(controller.contains("NoteDao"))
    }

    @Test
    fun `annotation management exposes highlight and note sections with note actions`() {
        val sheet = source(
            "app/src/main/java/com/m1racle/reader/ui/annotation/AnnotationsManagementSheet.kt",
        )
        assertTrue(sheet.contains("reader_highlights_section"))
        assertTrue(sheet.contains("reader_notes_section"))
        assertTrue(sheet.contains("onAddNoteForHighlight"))
        assertTrue(sheet.contains("onEditNote"))
        assertTrue(sheet.contains("onDeleteNote"))
    }

    private fun source(relative: String): String = ProductionSource.projectText(relative)
}
