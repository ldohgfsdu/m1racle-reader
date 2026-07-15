package com.markleaf.app.ui.viewer.components

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.markdown.MarkdownBlock
import com.markleaf.app.markdown.MarkdownParser
import com.markleaf.app.ui.theme.MarkLeafTheme

/** QA-only responsive-table preview and Compose instrumentation host. */
class MarkdownTableQaActivity : ComponentActivity() {
    private var tableCase by mutableStateOf(CASE_TWO_COLUMN)
    private var dark by mutableStateOf(false)
    private var verticalFixture by mutableStateOf(false)
    var columnMeasurementCount: Int = 0
        private set
    var pageScrollValue: Int = 0
        private set
    val headerCellMetadata: MutableMap<Int, TableHeaderCellLayoutMetadata> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tableCase = intent.getStringExtra(EXTRA_CASE) ?: CASE_TWO_COLUMN
        dark = intent.getBooleanExtra(EXTRA_DARK, false)
        setContent {
            MarkLeafTheme(darkTheme = dark) {
                if (verticalFixture) {
                    val pageScroll = rememberScrollState()
                    pageScrollValue = pageScroll.value
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .verticalScroll(pageScroll)
                            .padding(horizontal = 16.dp),
                    ) {
                        Spacer(Modifier.height(80.dp))
                        ResponsiveTable()
                        Spacer(Modifier.height(1200.dp))
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(16.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        ResponsiveTable()
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun ResponsiveTable() {
        TableBlockView(
            block = tableFor(tableCase),
            settings = ReaderSettings.Default,
            onColumnMetricsCalculated = { columnMeasurementCount++ },
            onHeaderCellLayout = { column, metadata -> headerCellMetadata[column] = metadata },
        )
    }

    fun showTable(tableCase: String) {
        headerCellMetadata.clear()
        this.tableCase = tableCase
        verticalFixture = false
    }

    fun showVerticalFixture() {
        verticalFixture = true
    }

    fun setDarkTheme(dark: Boolean) {
        this.dark = dark
    }

    private fun tableFor(tableCase: String): MarkdownBlock.Table = parseSingleTable(
        when (tableCase) {
            CASE_VERBOSE_TWO_COLUMN -> """
                | 核心心电图表现 | 速记与判断 |
                |---|---|
                | 每个 P 波后都有 QRS；PR 间期固定延长，成人通常大于 0.20 秒，需要结合心率和临床症状综合判断。 | 只延长，不漏搏；长文本应自然换行，不能出现省略号。 |
                | 连续观察多个导联，记录节律、间期和波形变化，并与既往心电图进行比较。 | 第二列承担同等重要的正文比较内容。 |
            """.trimIndent()
            CASE_THREE_COLUMN -> """
                | 项目 | 右束支阻滞 | 左束支阻滞 |
                |---|---|---|
                | V1 | rsR′、M 形、兔耳 | QS 或 rS |
                | I、V6 | 宽钝 S 波 | 宽大、切迹 R 波 |
                | 复核 | 结合 QRS 时限和胸前导联形态综合判断 | 滚动到最右侧时本列必须完整可见 |
            """.trimIndent()
            else -> """
                | 项目 | 结果 |
                |---|---|
                | 心率 | 正常 |
                | PR 间期 | 0.20 秒 |
                | QRS 波群 | 未见明显增宽 |
                | RR 间期 | 规则 |
            """.trimIndent()
        },
    )

    private fun parseSingleTable(source: String): MarkdownBlock.Table =
        MarkdownParser.parse(source).blocks.filterIsInstance<MarkdownBlock.Table>().single()

    companion object {
        const val EXTRA_CASE = "table_case"
        const val EXTRA_DARK = "dark"
        const val CASE_TWO_COLUMN = "two"
        const val CASE_VERBOSE_TWO_COLUMN = "two_verbose"
        const val CASE_THREE_COLUMN = "three"
    }
}
