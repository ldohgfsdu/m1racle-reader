package com.m1racle.reader.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single process-wide PDFBox init for outline, text extraction, and page export.
 * Page display never goes through PDFBox ([PdfPageRenderer] / system PdfRenderer).
 *
 * Large files stay on random-access file reads when the URI is a local file.
 * Content streams that cannot be read in place spill into one app-owned scratch
 * directory instead of the process-wide temporary directory.
 */
object PdfBoxRuntime {
    internal const val SCRATCH_DIR_NAME = "pdfbox-scratch"
    private val ready = AtomicBoolean(false)
    @Volatile private var appContext: Context? = null

    /**
     * Initialize PDFBox and prove that the app-owned scratch directory is usable.
     *
     * @return true once initialization succeeds.
     * @throws IOException when the scratch directory is unavailable or PDFBox
     * initialization fails. There is no in-memory fallback.
     */
    fun ensureInit(context: Context): Boolean {
        if (ready.get()) return true
        return synchronized(this) {
            if (ready.get()) return true
            try {
                val resolvedContext = context.applicationContext
                scratchDir(resolvedContext)
                PDFBoxResourceLoader.init(resolvedContext)
                appContext = resolvedContext
                ready.set(true)
                true
            } catch (error: IOException) {
                ready.set(false)
                throw error
            } catch (error: Exception) {
                ready.set(false)
                throw IOException("PDF组件初始化失败", error)
            }
        }
    }

    /**
     * Temp-file backing for content streams. This entry point is only valid after
     * [ensureInit] has stored the application context.
     *
     * @throws IllegalStateException when initialization has not completed.
     * @throws IOException when the app-owned scratch directory is unusable.
     */
    fun memorySetting(): MemoryUsageSetting {
        val context = appContext
            ?: throw IllegalStateException("PdfBoxRuntime has not been initialized")
        return memorySetting(context)
    }

    /**
     * Temp-file backing pinned to `cacheDir/pdfbox-scratch`.
     *
     * @throws IOException when the scratch directory cannot be created or written.
     */
    fun memorySetting(context: Context): MemoryUsageSetting =
        MemoryUsageSetting.setupTempFileOnly().setTempDir(scratchDir(context))

    /**
     * Open [uri] without copying a local file into scratch storage.
     * Non-file URIs still have to be buffered because the provider stream is sequential.
     */
    fun open(context: Context, uri: Uri, password: String = ""): PDDocument {
        if (!ensureInit(context)) throw IOException("PDF组件初始化失败")
        val file = localFile(uri)
        val setting = memorySetting(context)
        return if (file != null) {
            if (password.isEmpty()) PDDocument.load(file, setting)
            else PDDocument.load(file, password, setting)
        } else {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("无法打开文件")
            input.use {
                if (password.isEmpty()) PDDocument.load(it, setting)
                else PDDocument.load(it, password, setting)
            }
        }
    }

    fun open(context: Context, file: File, password: String = ""): PDDocument {
        if (!ensureInit(context)) throw IOException("PDF组件初始化失败")
        val setting = memorySetting(context)
        return if (password.isEmpty()) PDDocument.load(file, setting)
        else PDDocument.load(file, password, setting)
    }

    /**
     * App cache directory used by every temp-file-only PDFBox buffer.
     *
     * The directory is created if needed and probed with a temporary file so a
     * read-only or otherwise unusable cache fails before PDFBox is opened.
     */
    internal fun scratchDir(context: Context): File {
        val directory = File(
            requireNotNull(context.applicationContext) {
                "PDFBox scratch requires an application context"
            }.cacheDir,
            SCRATCH_DIR_NAME,
        )
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("PDFBox scratch directory cannot be created: $directory")
        }
        if (!directory.isDirectory) {
            throw IOException("PDFBox scratch path is not a directory: $directory")
        }
        try {
            val probe = File.createTempFile("probe-", ".tmp", directory)
            if (!probe.delete()) {
                throw IOException("PDFBox scratch probe cannot be deleted: $probe")
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("PDFBox scratch directory is not writable: $directory", e)
        }
        return directory
    }

    private fun localFile(uri: Uri): File? {
        if (uri.scheme?.lowercase(Locale.ROOT) != "file") return null
        val path = uri.path ?: return null
        val file = File(path)
        return file.takeIf { it.isFile }
    }
}
