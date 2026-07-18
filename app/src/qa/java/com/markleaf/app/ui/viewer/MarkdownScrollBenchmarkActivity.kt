package com.markleaf.app.ui.viewer

import android.os.Bundle
import android.util.Log
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.markdown.MarkdownBlock
import com.markleaf.app.markdown.MarkdownParser
import com.markleaf.app.markdown.MarkdownPerformance
import com.markleaf.app.markdown.blockContentType
import com.markleaf.app.markdown.blockId
import com.markleaf.app.ui.theme.MarkLeafTheme
import com.markleaf.app.ui.viewer.components.MarkdownBlockView
import com.markleaf.app.ui.viewer.components.TableLayoutCache
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** QA-only host-driven long-document benchmark. Never compiled into the main debug package. */
class MarkdownScrollBenchmarkActivity : ComponentActivity(), Choreographer.FrameCallback {
    private val documentId = "qa-markdown-scroll-performance"
    private val parsed by lazy { MarkdownParser.parse(fixture()) }
    private val compositionMarker = AtomicLong()
    private val recordedFrames = mutableListOf<String>()
    private var recording = false
    private var previousFrameNanos = 0L
    @Volatile private var currentBlockType = "none"
    @Volatile private var visibleTableIds = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MarkLeafTheme {
                BenchmarkDocument()
            }
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        when (intent.getStringExtra(EXTRA_COMMAND)) {
            COMMAND_RESET -> resetRecording()
            COMMAND_STOP -> stopAndFlush()
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (recording && previousFrameNanos != 0L) {
            val durationMs = (frameTimeNanos - previousFrameNanos) / 1_000_000.0
            val budgetMs = 1_000.0 / displayRefreshRate()
            val counters = MarkdownPerformance.snapshot()
            recordedFrames += buildString {
                append("{\"frameStartNanos\":").append(previousFrameNanos)
                append(",\"frameDurationMs\":").append(format(durationMs))
                append(",\"isJank\":").append(durationMs > budgetMs)
                append(",\"currentBlockType\":\"").append(json(currentBlockType)).append('"')
                append(",\"visibleTableIds\":\"").append(json(visibleTableIds)).append('"')
                append(",\"tableSolveCount\":").append(counters.tableSolveCount)
                append(",\"parseCount\":").append(counters.parseCount)
                append(",\"recompositionMarker\":").append(compositionMarker.get())
                append(",\"layoutMarker\":").append(counters.tableSolveCount)
                append('}')
            }
        }
        previousFrameNanos = frameTimeNanos
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDestroy() {
        Choreographer.getInstance().removeFrameCallback(this)
        stopAndFlush()
        super.onDestroy()
    }

    @Composable
    private fun BenchmarkDocument() {
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        val anchorOffsetPx = with(density) { -64.dp.roundToPx() }
        val tableCache = remember { TableLayoutCache(documentId) }
        val blocks = parsed.blocks
        val onLinkClick = remember(listState, blocks) {
            { destination: String ->
                if (destination.startsWith("#")) {
                    val anchor = android.net.Uri.decode(destination.removePrefix("#"))
                    val target = parsed.anchorRegistry.resolve(anchor)
                    if (target == null) {
                        recordAnchorEvent(anchor, null)
                        Log.w("MARKDOWN_TOC", "anchor_miss documentId=$documentId anchor=$anchor")
                    } else {
                        recordAnchorEvent(anchor, target.blockIndex)
                        scope.launch {
                            when (anchorJumpMode(listState.firstVisibleItemIndex, target.blockIndex)) {
                                AnchorJumpMode.Animated -> listState.animateScrollToItem(target.blockIndex, anchorOffsetPx)
                                AnchorJumpMode.Direct -> listState.scrollToItem(target.blockIndex, anchorOffsetPx)
                            }
                        }
                    }
                }
                Unit
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        ) {
            itemsIndexed(
                items = blocks,
                key = { index, block -> "$documentId:${block.blockId(index)}" },
                contentType = { _, block -> block.blockContentType() },
            ) { index, block ->
                compositionMarker.incrementAndGet()
                MarkdownBlockView(
                    block = block,
                    settings = ReaderSettings.Default,
                    stableBlockId = parsed.blockIds[index],
                    tableLayoutCache = tableCache,
                    onLinkClick = onLinkClick,
                )
            }
        }
        LaunchedEffect(listState) {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.map { info ->
                    info.contentType.toString() to info.key.toString()
                }
            }.distinctUntilChanged().collect { visible ->
                currentBlockType = visible.firstOrNull()?.first ?: "none"
                visibleTableIds = visible
                    .filter { it.first == "table" }
                    .joinToString(",") { it.second }
            }
        }
    }

    private fun resetRecording() {
        MarkdownPerformance.reset()
        compositionMarker.set(0)
        recordedFrames.clear()
        previousFrameNanos = 0L
        recording = true
        countersFile().writeText("{}")
        Log.i(TAG, "benchmark_reset package=$packageName refreshRate=${displayRefreshRate()}")
    }

    private fun stopAndFlush() {
        recording = false
        framesFile().writeText(recordedFrames.joinToString(separator = "\n", postfix = "\n"))
        val counters = MarkdownPerformance.snapshot()
        countersFile().writeText(
            """{"parseCount":${counters.parseCount},"tableSolveCount":${counters.tableSolveCount},"tableSolveTotalMs":${format(counters.tableSolveTotalMs)},"cellMeasureCount":${counters.cellMeasureCount},"cacheHitCount":${counters.cacheHitCount},"cacheMissCount":${counters.cacheMissCount},"refreshRate":${format(displayRefreshRate())}}""",
        )
        Log.i(TAG, "benchmark_stop package=$packageName counters=${countersFile().readText()}")
    }

    private fun framesFile() = File(filesDir, "markdown-scroll-frames.jsonl")
    private fun countersFile() = File(filesDir, "markdown-scroll-counters.json")
    private fun recordAnchorEvent(anchor: String, blockIndex: Int?) {
        val anchorBase64 = android.util.Base64.encodeToString(
            anchor.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP,
        )
        File(filesDir, "markdown-anchor-events.jsonl").appendText(
            "{\"anchorBase64\":\"$anchorBase64\",\"blockIndex\":${blockIndex ?: "null"}}\n",
        )
    }
    private fun displayRefreshRate(): Double = display?.refreshRate?.toDouble()?.takeIf { it > 0.0 } ?: 60.0
    private fun format(value: Double): String = String.format(Locale.US, "%.3f", value)
    private fun json(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun fixture(): String = buildString {
        appendLine("# Markdown TOC 与滚动性能")
        appendLine("[第一节](#长文档章节-1)")
        appendLine()
        appendLine("[中间章节](#长文档章节-8)")
        appendLine()
        appendLine("[最后重复章节](#重复章节-3)")
        appendLine()
        appendLine("[不存在的章节](#不存在)")
        repeat(14) { section ->
            appendLine("## 长文档章节 ${section + 1}")
            repeat(4) { paragraph ->
                appendLine("这是第 ${section + 1} 节的稳定长段落 ${paragraph + 1}。用于验证复杂 Markdown block 进入 viewport 时连续滚动，不重新解析文档，也不重复求解已经预热的表格列宽。")
            }
            if (section % 2 == 0) {
                appendLine("| 项目 | 右束支阻滞 | 左束支阻滞 |")
                appendLine("|---|---|---|")
                appendLine("| V1 | rsR′、M 形、兔耳 | QS 或 rS |")
                appendLine("| I、V6 | 宽钝 S 波 | 宽大、切迹 R 波 |")
                appendLine("| 复核 | 结合 QRS 时限和胸前导联形态综合判断 | 滚动到最右端完整查看 |")
            }
            if (section % 3 == 0) {
                appendLine("```kotlin")
                appendLine("val section = $section")
                appendLine("repeat(100) { consume(section + it) }")
                appendLine("```")
            }
        }
        appendLine("## 重复章节")
        appendLine("第一处。")
        appendLine("## 重复章节")
        appendLine("第二处。")
        appendLine("## 重复章节")
        appendLine("最后一处。")
    }

    companion object {
        const val EXTRA_COMMAND = "benchmark_command"
        const val COMMAND_RESET = "reset"
        const val COMMAND_STOP = "stop"
        private const val TAG = "MARKDOWN_BENCHMARK"
    }
}
