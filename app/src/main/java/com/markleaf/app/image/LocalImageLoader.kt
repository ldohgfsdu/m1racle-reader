package com.markleaf.app.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Locale

sealed interface LocalImageState {
    data object Loading : LocalImageState
    data class Success(val image: ImageBitmap) : LocalImageState
    data class Error(val message: String) : LocalImageState
}

/**
 * Load local images only:
 * - asset://…  (app packaged assets, for offline demos)
 * - content://… (SAF / system content URIs, needs grant)
 * - file://…    (only if the process can open the stream)
 *
 * Never performs network I/O. Samples large bitmaps to reduce OOM risk.
 */
object LocalImageLoader {

    private const val MAX_EDGE_PX = 1600
    private const val ASSET_SCHEME = "asset://"

    /**
     * Classify source and load when allowed. Always returns a terminal Success/Error
     * (caller may show Loading before invoking).
     */
    suspend fun load(context: Context, source: String): LocalImageState =
        withContext(Dispatchers.IO) {
            try {
                val trimmed = source.trim()
                if (trimmed.isEmpty()) {
                    return@withContext LocalImageState.Error("图片地址为空")
                }
                val lower = trimmed.lowercase(Locale.US)
                when {
                    lower.startsWith("http://") || lower.startsWith("https://") ->
                        LocalImageState.Error("网络图片已禁用")

                    lower.startsWith(ASSET_SCHEME) -> {
                        // Case-insensitive scheme; path after "://"
                        val path = trimmed.substringAfter("://", missingDelimiterValue = "")
                        decodeAsset(context, path)
                    }

                    lower.startsWith("content://") ->
                        decodeUri(context, Uri.parse(trimmed))

                    lower.startsWith("file://") ->
                        decodeUri(context, Uri.parse(trimmed))

                    lower.startsWith("/") ->
                        // Absolute path without scheme — no storage permission in MVP.
                        LocalImageState.Error("图片无法读取")

                    else ->
                        // Relative path like images/demo.png
                        LocalImageState.Error("相对图片路径暂未支持")
                }
            } catch (_: Exception) {
                LocalImageState.Error("图片无法读取")
            }
        }

    private fun decodeAsset(context: Context, assetPath: String): LocalImageState {
        val path = assetPath.trim().trimStart('/')
        if (path.isEmpty() || path.contains("..")) {
            return LocalImageState.Error("图片无法读取")
        }
        return try {
            // Bounds pass
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
        } catch (_: Exception) {
            LocalImageState.Error("图片无法读取")
        }
    }

    private fun decodeUri(context: Context, uri: Uri): LocalImageState {
        return try {
            decodeFromStreamFactory {
                context.contentResolver.openInputStream(uri)
            }
        } catch (_: SecurityException) {
            LocalImageState.Error("图片无法读取")
        } catch (_: Exception) {
            LocalImageState.Error("图片无法读取")
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
