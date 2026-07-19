package com.markleaf.app.ui.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markleaf.app.ui.theme.MarkLeafTheme

/**
 * QA-only list complexity ladder for R10_HOME_LIST_CARD_PERFORMANCE.
 *
 * Intent extras:
 * - ladder=A fixed-height plain strings (1000)
 * - ladder=B variable-height plain Text (1000)
 * - ladder=C multi-span AnnotatedString (1000)
 * - ladder=D long multi-paragraph AnnotatedString (500) — heavier text
 * - ladder=E home-like multi-line card text (200)
 * - ladder=E_NO_SURFACE E with the per-item Surface shell removed
 * - ladder=E_ONE_TEXT E with three visual lines collapsed into one Text node
 * - ladder=F (not PDF — PDF measured on real viewer)
 */
class MinimalListHarnessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ladder = intent?.getStringExtra(EXTRA_LADDER)?.uppercase() ?: "A"
        title = "Ladder $ladder"
        setContent {
            MarkLeafTheme {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (ladder) {
                        "A" -> LadderA()
                        "B" -> LadderB()
                        "C" -> LadderC()
                        "D" -> LadderD()
                        "E" -> LadderE()
                        "E_NO_SURFACE" -> LadderENoSurface()
                        "E_ONE_TEXT" -> LadderEOneText()
                        else -> LadderA()
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_LADDER = "ladder"
    }
}

@androidx.compose.runtime.Composable
private fun LadderA() {
    val items = remember { List(1000) { i -> "Item #$i fixed height" } }
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        items(items, key = { it }) { line ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(line, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun LadderB() {
    val items = remember {
        List(1000) { i ->
            val n = (i % 5) + 1
            buildString {
                repeat(n) { line ->
                    append("Variable height item #$i line ${line + 1}. ")
                }
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        itemsIndexed(items, key = { i, _ -> i }) { _, line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun LadderC() {
    val items = remember {
        List(1000) { i ->
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))) {
                    append("Annotated #$i ")
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Normal)) {
                    append("normal span with 中文 and more text for layout. ")
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp)) {
                    append("secondary span ${i % 7}")
                }
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        itemsIndexed(items, key = { i, _ -> i }) { _, ann ->
            Text(
                text = ann,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun LadderD() {
    // Heavier multi-paragraph annotated blocks (closer to Markdown body density)
    val items = remember {
        List(500) { i ->
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp)) {
                    append("Heading block $i\n")
                }
                repeat(4) { p ->
                    withStyle(SpanStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp)) {
                        append(
                            "Paragraph $p of block $i with mixed 中文 English content for " +
                                "measure cost similar to markdown paragraphs. ",
                        )
                    }
                    if (p % 2 == 0) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Bold phrase. ")
                        }
                    }
                    append("\n")
                }
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        itemsIndexed(items, key = { i, _ -> i }) { _, ann ->
            Text(
                text = ann,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun LadderE() {
    // Home-card-like multi-line fixed structure (no Room, pure UI cost)
    val items = remember {
        List(200) { i ->
            Triple(
                "文档标题示例 $i 带一点较长文字",
                "filename_$i.md · 12.${i % 9} MB",
                "今天 12:${i % 60} · 已读 ${i % 100}%",
            )
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
        itemsIndexed(items, key = { i, _ -> i }) { _, row ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            ) {
                androidx.compose.foundation.layout.Column(Modifier.padding(14.dp)) {
                    Text(row.first, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(row.second, style = MaterialTheme.typography.bodySmall)
                    Text(row.third, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun LadderENoSurface() {
    // Preserve E text count and spacing; remove only the per-item Surface shell.
    val items = remember {
        List(200) { i ->
            Triple(
                "文档标题示例 $i 带一点较长文字",
                "filename_$i.md · 12.${i % 9} MB",
                "今天 12:${i % 60} · 已读 ${i % 100}%",
            )
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
        itemsIndexed(items, key = { i, _ -> i }) { _, row ->
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .padding(14.dp),
            ) {
                Text(row.first, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(row.second, style = MaterialTheme.typography.bodySmall)
                Text(row.third, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun LadderEOneText() {
    // Preserve E Surface and three visual lines; collapse three Text nodes into one layout.
    val items = remember {
        List(200) { i ->
            Triple(
                "文档标题示例 $i 带一点较长文字",
                "filename_$i.md · 12.${i % 9} MB",
                "今天 12:${i % 60} · 已读 ${i % 100}%",
            )
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
        itemsIndexed(items, key = { i, _ -> i }) { _, row ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    text = "${row.first}\n${row.second}\n${row.third}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
    }
}
