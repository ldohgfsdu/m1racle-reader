package com.m1racle.reader.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.m1racle.reader.data.local.DocumentType
import com.m1racle.reader.markdown.MarkdownAssetResolver
import com.m1racle.reader.markdown.MarkdownAssetResult
import com.m1racle.reader.markdown.MarkdownDocumentBase
import com.m1racle.reader.packageimport.ZipPackageImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.Locale

sealed interface LocalImageState {
    data object Loading : LocalImageState
    data class Success(val image: ImageBitmap) : LocalImageState
    data class Error(val message: String) : LocalImageState
}

/**
 * Load local images only (no network).
 * Relative refs go through [MarkdownAssetResolver] (single resolution entry).
 */
object LocalImageLoader {

    private const val TAG = "M1racleReaderImage"
    private const val MAX_EDGE_PX = 1600

    suspend fun load(
        context: Context,
        source: String,
        documentUri: String? = null,
        documentType: DocumentType? = null,
    ): LocalImageState =
        withContext(Dispatchers.IO) {
            try {
                val app = context.applicationContext
                val base = MarkdownAssetResolver.fromDocumentUri(
                    context = app,
                    documentUri = documentUri,
                    documentType = documentType,
                )
                when (val result = MarkdownAssetResolver.resolve(base, source)) {
                    is MarkdownAssetResult.Absolute -> loadAbsolute(app, result.uri)
                    is MarkdownAssetResult.Resolved -> loadResolved(app, result.uri)
                    is MarkdownAssetResult.Inaccessible,
                    is MarkdownAssetResult.Invalid,
                    -> LocalImageState.Error(MarkdownAssetResolver.userMessage(result))
                }
            } catch (e: Exception) {
                Log.d(TAG, "decode failed: ${e.javaClass.simpleName}: ${e.message}")
                LocalImageState.Error("图片无法读取")
            }
        }

    private fun loadAbsolute(context: Context, uriString: String): LocalImageState {
        val lower = uriString.trim().lowercase(Locale.ROOT)
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") ->
                LocalImageState.Error("网络图片已禁用")
            lower.startsWith("asset://") -> {
                val path = uriString.substringAfter("://", missingDelimiterValue = "")
                decodeAsset(context, path)
            }
            lower.startsWith("content://") ->
                decodeUri(context, Uri.parse(uriString))
            lower.startsWith("file:") -> {
                val path = filePathFromUri(uriString)
                    ?: return LocalImageState.Error("图片无法读取")
                decodeFile(File(path))
            }
            lower.startsWith("data:") -> LocalImageState.Error("图片无法读取")
            lower.startsWith("android.resource://") ->
                decodeUri(context, Uri.parse(uriString))
            else -> LocalImageState.Error("图片无法读取")
        }
    }

    private fun loadResolved(context: Context, uriString: String): LocalImageState {
        val lower = uriString.trim().lowercase(Locale.ROOT)
        return when {
            lower.startsWith("file:") || !lower.contains("://") -> {
                val path = filePathFromUri(uriString)
                    ?: uriString.takeIf { it.startsWith("/") }
                    ?: return LocalImageState.Error("图片无法读取")
                decodeFile(File(path))
            }
            lower.startsWith("content://") -> decodeUri(context, Uri.parse(uriString))
            else -> decodeUri(context, Uri.parse(uriString))
        }
    }

    /**
     * Resolve relative image against document entry URI (file://).
     * Uses [MarkdownAssetResolver] with an explicit [MarkdownDocumentBase.FileDirectory].
     */
    fun resolveRelativeImage(documentUri: String, relativePath: String): File? {
        val path = filePathFromUri(documentUri) ?: return null
        val entry = try {
            File(path).canonicalFile
        } catch (_: Exception) {
            return null
        }
        val root = try {
            ZipPackageImporter.findPackageRoot(entry).canonicalFile
        } catch (_: Exception) {
            entry.parentFile?.canonicalFile ?: return null
        }
        val base = MarkdownDocumentBase.FileDirectory(documentUri, entry, root)
        return when (val result = MarkdownAssetResolver.resolve(base, relativePath)) {
            is MarkdownAssetResult.Resolved -> {
                val p = filePathFromUri(result.uri) ?: return null
                File(p).takeIf { it.isFile }
            }
            else -> null
        }
    }

    fun filePathFromUri(documentUri: String): String? =
        MarkdownAssetResolver.filePathFromDocumentUri(documentUri)

    private fun decodeAsset(context: Context, assetPath: String): LocalImageState {
        val path = assetPath.trim().trimStart('/')
        if (path.isEmpty() || path.contains("..")) {
            return LocalImageState.Error("图片无法读取")
        }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(path).use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return LocalImageState.Error("图片无法读取")
            }
            val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE_PX)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = context.assets.open(path).use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            } ?: return LocalImageState.Error("图片无法读取")
            LocalImageState.Success(bitmap.asImageBitmap())
        } catch (e: Exception) {
            Log.d(TAG, "asset decode: ${e.javaClass.simpleName}")
            LocalImageState.Error("图片无法读取")
        }
    }

    private fun decodeUri(context: Context, uri: Uri): LocalImageState {
        return try {
            if (uri.scheme?.lowercase(Locale.ROOT) == "file") {
                val path = uri.path ?: return LocalImageState.Error("图片无法读取")
                return decodeFile(File(path))
            }
            decodeFromStreamFactory {
                context.contentResolver.openInputStream(uri)
            }
        } catch (e: Exception) {
            Log.d(TAG, "uri decode: ${e.javaClass.simpleName}")
            LocalImageState.Error("图片无法读取")
        }
    }

    /**
     * Decode App-private real files via absolute path (not InputStream adapters).
     * content:// still uses [decodeFromStreamFactory].
     */
    private fun decodeFile(file: File): LocalImageState {
        val canonical = try {
            file.canonicalFile
        } catch (e: Exception) {
            Log.d(TAG, "canonical failed: ${e.javaClass.simpleName}")
            return LocalImageState.Error("图片无法读取")
        }

        Log.d(
            TAG,
            "decode file path=${canonical.absolutePath} " +
                "exists=${canonical.exists()} " +
                "isFile=${canonical.isFile} " +
                "canRead=${canonical.canRead()} " +
                "length=${canonical.length()}",
        )

        if (!canonical.isFile || !canonical.canRead() || canonical.length() <= 0L) {
            return LocalImageState.Error("图片无法读取")
        }

        return try {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(canonical.absolutePath, bounds)

            Log.d(
                TAG,
                "bounds width=${bounds.outWidth} " +
                    "height=${bounds.outHeight} mime=${bounds.outMimeType}",
            )

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return LocalImageState.Error("图片无法读取")
            }

            val sample = calculateInSampleSize(
                bounds.outWidth,
                bounds.outHeight,
                MAX_EDGE_PX,
            )

            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val bitmap = BitmapFactory.decodeFile(
                canonical.absolutePath,
                options,
            ) ?: decodeWithImageDecoder(canonical)

            if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) {
                bitmap?.recycle()
                LocalImageState.Error("图片无法读取")
            } else {
                LocalImageState.Success(bitmap.asImageBitmap())
            }
        } catch (e: Exception) {
            Log.d(
                TAG,
                "file decode failed: ${e.javaClass.simpleName}: ${e.message}",
            )
            LocalImageState.Error("图片无法读取")
        }
    }

    /** Android 9+ fallback when BitmapFactory.decodeFile returns null. */
    private fun decodeWithImageDecoder(file: File): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null

        return try {
            val source = ImageDecoder.createSource(file)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (e: Exception) {
            Log.d(
                TAG,
                "ImageDecoder failed: ${e.javaClass.simpleName}: ${e.message}",
            )
            null
        }
    }

    private fun decodeFromStreamFactory(open: () -> InputStream?): LocalImageState {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: return LocalImageState.Error("图片无法读取")

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return LocalImageState.Error("图片无法读取")
        }

        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE_PX)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = open()?.use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)
        } ?: return LocalImageState.Error("图片无法读取")

        if (bitmap.width <= 0 || bitmap.height <= 0) {
            bitmap.recycle()
            return LocalImageState.Error("图片无法读取")
        }
        return LocalImageState.Success(bitmap.asImageBitmap())
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var inSampleSize = 1
        var w = width
        var h = height
        while (w / 2 >= maxEdge || h / 2 >= maxEdge) {
            w /= 2
            h /= 2
            inSampleSize *= 2
        }
        while (w > maxEdge || h > maxEdge) {
            w /= 2
            h /= 2
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }
}
