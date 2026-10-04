package com.m1racle.reader.ui.home

import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.testing.ProductionSource
import com.m1racle.reader.ui.theme.M1racleGlassPolicy
import com.m1racle.reader.ui.theme.M1racleGlassUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Home is a reading entry: L2 hero + light list. Glass only on Dock / FAB / Menu. */
class HomeLayoutContractTest {

    @Test
    fun `glass policy still forbids hero list and file items`() {
        assertTrue(M1racleGlassPolicy.allows(M1racleGlassUsage.Toolbar))
        assertTrue(M1racleGlassPolicy.allows(M1racleGlassUsage.Dock))
        assertTrue(M1racleGlassPolicy.allows(M1racleGlassUsage.Menu))
        assertFalse(M1racleGlassPolicy.allowed.any { it.name == "Hero" })
        assertFalse(M1racleGlassPolicy.allowed.any { it.name == "List" })
    }

    @Test
    fun `hero uses L2 surface with one progress line and no glass`() {
        val hero = continueSource()
        assertTrue(hero.contains("M1racleReaderTheme.surface.hero"))
        assertTrue(hero.contains("M1racleReaderTheme.cornerRadius.card"))
        assertFalse(hero.contains("titles.typeLabel"))
        assertTrue(hero.contains("positionLine"))
        assertFalse(hero.contains("glassMaterial"))
        assertFalse(hero.contains("progressPercent"))
        assertFalse(hero.contains("ReadingProgressBar"))
        assertTrue(hero.contains("DocumentActionsButton("))
        assertTrue(hero.contains("DocumentActionsMenu("))
        assertFalse(hero.contains("lastOpenedAt"))
        assertFalse(hero.contains("formatRelativeOpenedAt"))
    }

    @Test
    fun `recent and library rows are light lists not cards`() {
        val row = rowSource()
        val home = homeSource()
        val library = librarySource()
        assertTrue(row.contains("fun DocumentListRow("))
        assertTrue(row.contains("combinedClickable("))
        assertTrue(row.contains("Icons.Outlined.PictureAsPdf"))
        assertTrue(row.contains("formatListMetadata"))
        assertFalse(row.contains("surfaceContainerLow"))
        assertFalse(row.contains("HomeDesignTokens.CardShape"))
        assertFalse(row.contains("ReadingProgressBar"))
        assertFalse(row.contains("OverflowMenuButton"))
        assertTrue(row.contains("DocumentActionsButton("))
        assertTrue(home.contains("DocumentListRow("))
        assertFalse(home.contains("private fun DocumentCard"))
        assertTrue(library.contains("DocumentListRow("))
        assertFalse(library.contains("LibraryDocumentCard"))
        assertFalse(library.contains("ReadingProgressBar"))
    }

    @Test
    fun `list metadata is format and progress without last-opened time`() {
        val pdf = DocumentEntity(
            id = "p",
            displayName = "cell.pdf",
            uri = "content://x/p",
            mimeType = "application/pdf",
            fileSize = 12_000L,
            lastOpenedAt = 1_700_000_000_000L,
            createdAt = 0L,
            lastScrollIndex = 4,
            lastScrollOffset = 0,
            readingProgress = 0.2f,
            title = null,
            headingCount = 0,
            documentType = "PDF",
            pageCount = 20,
        )
        val md = pdf.copy(
            id = "m",
            displayName = "notes.md",
            documentType = "MARKDOWN",
            pageCount = 0,
            readingProgress = 0.37f,
        )
        assertEquals("5 / 20", formatListMetadata(pdf))
        assertEquals("已读 37%", formatListMetadata(md))
        assertFalse(formatListMetadata(pdf).contains("PDF"))
        assertFalse(formatListMetadata(pdf).contains("今天"))
        assertFalse(formatListMetadata(pdf).contains("刚刚"))
        assertFalse(formatListMetadata(pdf).contains("%"))
    }

    @Test
    fun `home uses platform positioned opaque menu and fixed import action`() {
        val row = rowSource()
        val home = homeSource()
        assertTrue(row.contains("DropdownMenu("))
        assertTrue(row.contains("GlassMaterialRole.Dock"))
        assertTrue(row.contains("GlassCapability.SOLID_FALLBACK"))
        assertFalse(row.contains(".glassMaterial("))
        val dock = ProductionSource.text("com/m1racle/reader/ui/hub/GlassDock.kt")
        assertTrue(dock.contains("Icons.Filled.Add"))
        assertTrue(dock.contains("clickable(enabled = !importing"))
        assertTrue(home.contains("Button(onClick = onImport, enabled = !isImporting)"))
        assertFalse(home.contains("floatingActionButton ="))
    }

    @Test
    fun `single content rail is retained`() {
        assertEquals(24f, HomeDesignTokens.PageHorizontal.value)
        assertTrue(homeSource().contains("start = HomeDesignTokens.PageHorizontal"))
        assertTrue(homeSource().contains("end = HomeDesignTokens.PageHorizontal"))
    }

    @Test
    fun `empty copy waits for the first loaded emission`() {
        val home = homeSource()
        val library = librarySource()
        val bookmarks = File("app/src/main/java/com/m1racle/reader/ui/hub/BookmarksScreen.kt")
            .takeIf { it.isFile }
            ?: File("src/main/java/com/m1racle/reader/ui/hub/BookmarksScreen.kt")
        assertTrue(home.contains("uiState.ready && uiState.isEmpty"))
        assertTrue(library.contains("uiState.ready && uiState.documents.isEmpty()"))
        assertTrue(bookmarks.readText().contains("uiState.ready && bookmarks.isEmpty()"))
        val vm = homeSourceVm()
        assertTrue(vm.contains("SharingStarted.Lazily"))
        assertFalse(vm.contains("WhileSubscribed"))
    }

    @Test
    fun `home typography separates current document from list and progress`() {
        assertTrue(continueSource().contains("fontSize = 20.sp"))
        assertTrue(rowSource().contains("fontSize = 16.sp"))
        assertTrue(rowSource().contains("typography.caption"))
        assertTrue(rowSource().contains("maxLines = 2"))
    }

    @Test
    fun `recent list excludes the continue reading document`() {
        val home = homeSource()
        assertTrue(home.contains("documents.filter { it.id != continueReading?.id }"))
        assertFalse(home.contains("progressPercent(continueReading)"))
    }

    @Test
    fun `system insets clear the content tail instead of bounding the viewport and content padding clears the floating dock`() {
        val home = homeSource()
        assertTrue(home.contains(".padding(top = statusTop)"))
        assertFalse(home.contains(".padding(top = statusTop, bottom = navBottom)"))
        assertTrue(home.contains("bottom = HomeDesignTokens.DockContentClearance + navBottom"))
        assertFalse(home.contains("bottom = bottomReserved"))
        assertTrue(home.contains(".clipToBounds().testTag(\"home-list\")"))
        assertFalse(home.contains("item(key = \"home_brand_header\")"))
        assertFalse(home.contains("nestedScroll"))
    }

    @Test
    fun `list uses stable document id keys and no animateItem placeholder`() {
        val home = homeSource()
        val vm = homeSourceVm()
        assertTrue(home.contains("key = { doc -> doc.id }"))
        assertFalse(home.contains("animateItem"))
        assertFalse(home.contains("AnimatedVisibility"))
        assertTrue(vm.contains("pendingDeleteIds"))
        assertTrue(vm.contains("HomeListUpdate.visibleDocuments"))
    }

    @Test
    fun `viewModel delete is optimistic`() {
        val vm = homeSourceVm()
        assertTrue(vm.contains("pendingDeleteIds"))
        assertTrue(vm.contains("HomeListUpdate.visibleDocuments"))
        assertTrue(vm.contains("deleteById"))
    }

    private fun homeSource(): String = source("ui/home/HomeScreen.kt")
    private fun continueSource(): String = source("ui/home/ContinueReadingCard.kt")
    private fun rowSource(): String = source("ui/home/DocumentListRow.kt")
    private fun librarySource(): String = source("ui/hub/LibraryScreen.kt")
    private fun homeSourceVm(): String = source("ui/home/HomeViewModel.kt")

    private fun source(relative: String): String =
        ProductionSource.text("com/m1racle/reader/$relative")
}
