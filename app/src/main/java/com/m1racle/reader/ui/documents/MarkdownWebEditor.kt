package com.m1racle.reader.ui.documents

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.m1racle.reader.data.settings.ReaderSettings
import org.json.JSONObject
import java.io.ByteArrayInputStream

/** Only this app's bundled editor is reachable; document HTML never becomes a page. */
internal object MarkdownEditorOrigin {
    const val HOST = "reader-markdown.local"
    const val INDEX = "https://$HOST/index.html"

    fun assetPath(url: String): String? {
        val uri = java.net.URI(url)
        if (uri.scheme != "https" || uri.host != HOST || uri.port != -1 || uri.rawUserInfo != null) return null
        val path = uri.path ?: return null
        if (path.split('/').any { it == ".." || it == "." } || '\\' in path || '\u0000' in path) return null
        if (path != "/index.html" && !path.startsWith("/assets/")) return null
        return "markdown-editor$path"
    }
}

internal class MarkdownEditorController {
    var webView: WebView? = null
    private var snapshotPending = false
    var ready by mutableStateOf(false)
        internal set
    fun command(command: String) { if (ready) webView?.evaluateJavascript("window.ReaderMarkdown.$command", null) }
    fun setMode(source: Boolean, accepted: () -> Unit, composing: () -> Unit) {
        val current = webView ?: return
        if (!ready) return
        current.evaluateJavascript("window.ReaderMarkdown.setSourceMode($source)") { result ->
            if (webView !== current) return@evaluateJavascript
            if (result == "true") accepted() else composing()
        }
    }
    fun snapshot(onSnapshot: (String) -> Unit, onComposing: () -> Unit) {
        val current = webView ?: return
        if (!ready || snapshotPending) return
        snapshotPending = true
        current.evaluateJavascript("JSON.stringify({composing:window.ReaderMarkdown.isComposing(),markdown:window.ReaderMarkdown.getMarkdown()})") { encoded ->
            snapshotPending = false
            if (webView !== current) return@evaluateJavascript
            val snapshot = runCatching { JSONObject(org.json.JSONTokener(encoded).nextValue() as String) }.getOrNull() ?: return@evaluateJavascript
            if (snapshot.optBoolean("composing")) onComposing() else onSnapshot(snapshot.getString("markdown"))
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun MarkdownWebEditor(
    id: String,
    initialMarkdown: String,
    sourceMode: Boolean,
    readOnly: Boolean,
    dark: Boolean,
    settings: ReaderSettings,
    controller: MarkdownEditorController,
    onChange: (String) -> Unit,
    onHistory: (Boolean, Boolean) -> Unit,
    onLink: (String) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialLine: Int = 1,
) {
    val changed by rememberUpdatedState(onChange)
    val history by rememberUpdatedState(onHistory)
    val link by rememberUpdatedState(onLink)
    val failed by rememberUpdatedState(onError)
    val initial = remember(id) { initialMarkdown }
    val theme = JSONObject().put("dark", dark).put("fontSize", 17 * settings.fontScale)
        .put("lineHeight", settings.lineHeightScale)
    val currentMode by rememberUpdatedState(sourceMode)
    val currentReadOnly by rememberUpdatedState(readOnly)
    val currentTheme by rememberUpdatedState(theme.toString())
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                controller.webView = this
                setBackgroundColor(if (dark) 0xff0f0f0f.toInt() else 0xffffffff.toInt())
                this.settings.javaScriptEnabled = true
                this.settings.allowFileAccess = false
                this.settings.allowContentAccess = false
                this.settings.blockNetworkLoads = true
                this.settings.textZoom = (context.resources.configuration.fontScale * 100).toInt()
                this.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                this.settings.setSupportMultipleWindows(false)
                isVerticalScrollBarEnabled = false
                isFocusableInTouchMode = true
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun postMessage(message: String) {
                        post {
                            if (controller.webView !== this@apply) return@post
                            val event = runCatching { JSONObject(message) }.getOrNull() ?: return@post
                            when (event.optString("type")) {
                                "ready" -> {
                                    val payload = JSONObject(currentTheme).put("id", id).put("markdown", initial)
                                        .put("sourceMode", currentMode).put("readOnly", currentReadOnly).put("initialLine", initialLine)
                                    evaluateJavascript("window.ReaderMarkdown.load($payload)", null)
                                }
                                "loaded" -> if (event.optString("id") == id) controller.ready = true
                                "change", "history" -> if (event.optString("id") == id) {
                                    if (event.optString("type") == "change") changed(event.getString("markdown"))
                                    history(event.optBoolean("canUndo"), event.optBoolean("canRedo"))
                                }
                                "link" -> link(event.optString("url"))
                                "error" -> failed("编辑器未能载入，请返回后重试")
                            }
                        }
                    }
                }, "Native")
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                        val asset = runCatching { MarkdownEditorOrigin.assetPath(request.url.toString()) }.getOrNull()
                        if (asset != null) {
                            val stream = runCatching { context.assets.open(asset) }.getOrNull()
                            if (stream != null) {
                                val mime = when (asset.substringAfterLast('.')) { "js" -> "application/javascript"; "css" -> "text/css"; "woff2" -> "font/woff2"; "woff" -> "font/woff"; "ttf" -> "font/ttf"; else -> "text/html" }
                                return WebResourceResponse(mime, "UTF-8", stream)
                            }
                        }
                        return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    }
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        // Links are dispatched by the editor; never let navigation acquire the bridge.
                        return true
                    }
                }
                loadUrl(MarkdownEditorOrigin.INDEX)
            }
        },
        onRelease = {
            controller.ready = false
            controller.webView = null
            it.stopLoading()
            it.removeJavascriptInterface("Native")
            it.destroy()
        },
    )
    LaunchedEffect(controller.ready, sourceMode) { controller.command("setSourceMode($sourceMode)") }
    LaunchedEffect(controller.ready, readOnly) { controller.command("setReadOnly($readOnly)") }
    LaunchedEffect(controller.ready, theme.toString()) { controller.command("setTheme($theme)") }
}
