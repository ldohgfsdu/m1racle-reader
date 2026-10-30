package com.m1racle.reader.data.repository

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.m1racle.reader.data.local.AppDatabase
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.local.DocumentType
import com.m1racle.reader.packageimport.ZipPackageImporter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class MarkdownPackageExportTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: DocumentRepository
    private val image = byteArrayOf(1, 7, 12, 31)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = repository()
    }

    private fun repository(runner: RepositoryTransactionRunner = RoomRepositoryTransactionRunner(db)) =
        DocumentRepository(context, db, db.documentDao(), db.pdfPrintPageAnchorDao(),
            db.textbookOutlineNodeDao(), SearchRepository(db.searchIndexDao()), importTransactionRunner = runner)

    @After fun tearDown() { db.close() }

    private fun zip(): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { out ->
            out.setComment("retain original ZIP metadata and bytes")
            for ((name, data) in listOf(
                "package-manifest.json" to "{\"entrypoint\":\"chapter/start.md\"}".toByteArray(),
                "chapter/start.md" to "# Book\n\n![image](image.png)".toByteArray(),
                "chapter/image.png" to image,
            )) {
                out.putNextEntry(ZipEntry(name)); out.write(data); out.closeEntry()
            }
        }
    }.toByteArray()

    private suspend fun importZip(): Pair<File, DocumentEntity> {
        val source = temporary.newFile("source.zip").apply { writeBytes(zip()) }
        val result = repository.importDocument(Uri.fromFile(source))
        assertTrue("Expected package import, got $result", result is ImportResult.Success)
        return source to (result as ImportResult.Success).document
    }

    @Test fun newImportExportsOriginalBytesAndRoundTripsAllResources() = runBlocking {
        val (source, document) = importZip()
        val destination = temporary.newFile("export.zip")
        assertEquals(DocumentExportKind.ORIGINAL, repository.exportDocument(document.id, Uri.fromFile(destination)))
        assertArrayEquals(source.readBytes(), destination.readBytes())
        ZipFile(destination).use { archive ->
            assertArrayEquals(image, archive.getInputStream(archive.getEntry("chapter/image.png")).use { it.readBytes() })
            assertNotNull(archive.getEntry("package-manifest.json"))
        }
        val importedAgain = repository.importDocument(Uri.fromFile(destination)) as ImportResult.Success
        assertEquals(document.id, importedAgain.document.id)
        val entry = File(Uri.parse(importedAgain.document.uri).path!!)
        assertArrayEquals(image, ZipPackageImporter.resolvePackageImage(entry, "image.png")!!.readBytes())
        assertEquals("application/zip", document.mimeType)
    }

    @Test fun previousImportsWithoutOriginalAreRebuiltWithResourcesAndExplicitResult() = runBlocking {
        val root = File(context.filesDir, "packages/previous-import").apply { mkdirs() }
        val chapter = File(root, "chapter").apply { mkdirs() }
        val entry = File(chapter, "start.md").apply { writeText("# Old\n\n![image](image.png)") }
        File(root, "package-manifest.json").writeText("{\"entrypoint\":\"chapter/start.md\"}")
        File(chapter, "image.png").writeBytes(image)
        val document = DocumentEntity("previous-import", "Previous book", Uri.fromFile(entry).toString(),
            "application/zip", entry.length(), 1L, 1L, 0, 0, 0f, "Old", 1,
            documentType = DocumentType.MARKDOWN_PACKAGE.name, contentSha256 = "a".repeat(64))
        db.documentDao().insertDocument(document)
        val before = entry.readBytes()
        val destination = temporary.newFile("previous-export.zip")
        assertEquals(DocumentExportKind.REPACKAGED, repository.exportDocument(document.id, Uri.fromFile(destination)))
        val unpacked = ZipPackageImporter.extract(destination.inputStream(), temporary.newFolder("unpacked"))
        assertEquals("chapter/start.md", unpacked.entryRelativePath)
        assertArrayEquals(image, File(unpacked.packageRoot, "chapter/image.png").readBytes())
        assertArrayEquals(before, entry.readBytes())
        assertFalse(context.cacheDir.listFiles().orEmpty().any { it.name.startsWith("package-export-") })
    }

    @Test fun corruptedRetainedOriginalIsRejectedBeforeOpeningDestination() = runBlocking {
        val (source, document) = importZip()
        val before = source.readBytes()
        File(context.filesDir, "package-archives/${document.id}/original.zip").writeText("damaged")
        val destination = temporary.newFile("unchanged.zip").apply { writeText("do not truncate") }
        val result = runCatching { repository.exportDocument(document.id, Uri.fromFile(destination)) }
        assertTrue(result.isFailure)
        assertEquals("do not truncate", destination.readText())
        assertArrayEquals(before, source.readBytes())
    }

    @Test fun originalPackageCannotBeSelectedAsExportDestination() = runBlocking {
        val (source, document) = importZip()
        val before = source.readBytes()
        assertEquals(Uri.fromFile(source).toString(), document.sourceUri)
        assertTrue(runCatching { repository.exportDocument(document.id, Uri.fromFile(source)) }.isFailure)
        assertArrayEquals(before, source.readBytes())
    }

    @Test fun allVerifiedSourcesStayProtectedAfterSamePackageIsOpenedFromAnotherFile() = runBlocking {
        val (first, document) = importZip()
        val second = temporary.newFile("second.zip").apply { writeBytes(first.readBytes()) }
        val reused = (repository.importDocument(Uri.fromFile(second)) as ImportResult.Success).document
        assertEquals(document.id, reused.id)
        assertEquals(Uri.fromFile(second).toString(), reused.sourceUri)
        for (source in listOf(first, second)) {
            val before = source.readBytes()
            assertTrue(runCatching { repository.exportDocument(reused.id, Uri.fromFile(source)) }.isFailure)
            assertArrayEquals(before, source.readBytes())
        }
    }

    @Test fun deletingPackageRemovesRetainedOriginalButPreservesUserSource() = runBlocking {
        val (source, document) = importZip()
        val original = source.readBytes()
        val archive = File(context.filesDir, "package-archives/${document.id}")
        assertTrue(archive.isDirectory)
        repository.deleteById(document.id)
        assertFalse(archive.exists())
        assertFalse(File(context.filesDir, "packages/${document.id}").exists())
        assertArrayEquals(original, source.readBytes())
    }

    @Test fun failedIdentityCommitCleansBothExtractAndRetainedArchive() = runBlocking {
        var commits = 0
        repository = repository(object : RepositoryTransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T {
                if (++commits == 2) throw IOException("commit failed")
                return block()
            }
        })
        val source = temporary.newFile("failed.zip").apply { writeBytes(zip()) }
        val before = source.readBytes()
        assertEquals(ImportResult.ReadFailed, repository.importDocument(Uri.fromFile(source)))
        assertTrue(db.documentDao().getAll().isEmpty())
        assertTrue(File(context.filesDir, "packages").listFiles().orEmpty().isEmpty())
        assertTrue(File(context.filesDir, "package-archives").listFiles().orEmpty().isEmpty())
        assertArrayEquals(before, source.readBytes())
    }
}
