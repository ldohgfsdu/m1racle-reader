package com.m1racle.reader.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.m1racle.reader.data.local.DocumentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Bounded first-page/declared-cover thumbnail; no network or invented cover art. */
@Composable
fun DocumentCover(document: DocumentEntity, modifier: Modifier, fallback: @Composable () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, document.uri, document.contentSha256) {
        value = withContext(Dispatchers.IO) { runCatching { loadDocumentCover(context, document) }.getOrNull() }
    }
    val image = bitmap
    if (image == null) fallback()
    else Image(image.asImageBitmap(), contentDescription = "文档封面", modifier = modifier, contentScale = ContentScale.Crop)
}

internal fun loadDocumentCover(context: Context, document: DocumentEntity): Bitmap? {
    if (!document.isPdf && !document.isEpub) return null
    val key = document.contentSha256 ?: java.security.MessageDigest.getInstance("SHA-256")
        .digest(document.uri.toByteArray()).joinToString("") { "%02x".format(it) }
    val cache = File(context.cacheDir, "document-covers").apply { mkdirs() }.resolve("$key.png")
    if (cache.isFile) BitmapFactory.decodeFile(cache.path)?.let { return it }
    val bitmap = if (document.isPdf) {
        context.contentResolver.openFileDescriptor(Uri.parse(document.uri), "r")?.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    val height = (200f * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(1, 400)
                    Bitmap.createBitmap(200, height, Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(android.graphics.Color.WHITE)
                        page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    } else {
        val uri = Uri.parse(document.uri)
        if (uri.scheme != "file") return null
        ZipFile(File(requireNotNull(uri.path))).use { zip ->
            fun xml(path: String): org.w3c.dom.Document? {
                val entry = zip.getEntry(path) ?: return null
                if (entry.size > 2 * 1024 * 1024) return null
                val factory = DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                }
                return zip.getInputStream(entry).use { factory.newDocumentBuilder().parse(it) }
            }
            val container = xml("META-INF/container.xml") ?: return null
            val root = container.getElementsByTagNameNS("*", "rootfile").item(0) as? Element ?: return null
            val opfPath = root.getAttribute("full-path")
            val opf = xml(opfPath) ?: return null
            val meta = opf.getElementsByTagNameNS("*", "meta")
            val coverId = (0 until meta.length).map { meta.item(it) as Element }
                .firstOrNull { it.getAttribute("name") == "cover" }?.getAttribute("content")
            val nodes = opf.getElementsByTagNameNS("*", "item")
            val cover = (0 until nodes.length).map { nodes.item(it) as Element }.firstOrNull {
                "cover-image" in it.getAttribute("properties").split(' ') || (coverId != null && it.getAttribute("id") == coverId)
            } ?: return null
            val path = java.net.URI(opfPath).resolve(cover.getAttribute("href")).normalize().path
            if (path.startsWith("/") || path.startsWith("../")) return null
            val entry = zip.getEntry(path) ?: return null
            if (entry.size > 20 * 1024 * 1024) return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, options) }
            options.inSampleSize = (maxOf(options.outWidth, options.outHeight) / 400).coerceAtLeast(1)
            options.inJustDecodeBounds = false
            zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, options) }
        }
    }
    bitmap?.let { image -> cache.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    return bitmap
}
