package com.markleaf.app.ui.pdf

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R22.5 胶囊交互契约（冻结当前正确行为，禁止生产改动）。
 *
 * `PdfInfoCapsule` 是 private composable：morph 派生逻辑无法脱离生产文件执行，
 * 因此本文件按仓库既有范式（ReaderChromeMaterialTest）用源码结构契约锁定
 * 「单一 fraction 驱动」的实现形态；可执行的公开模型（PdfCapsuleData）单独直测。
 * 任何命中断言的改动都必须是有意识的契约修订，而不是悄悄回归。
 */
class CapsuleInteractionContractTest {

    // ── 可执行部分：结构化内容模型（公开 API） ──────────────────────────────

    @Test
    fun `pdf capsule data defaults print detail to null and round-trips copies`() {
        val collapsed = PdfCapsuleData(
            title = "第三章 动力学",
            positionLine = "78 / 982",
            progressPercent = 8,
        )
        assertEquals(null, collapsed.printDetail)
        assertEquals(collapsed, collapsed.copy())
        val expanded = collapsed.copy(printDetail = "教材页 47（锚点）")
        assertEquals("教材页 47（锚点）", expanded.printDetail)
        assertFalse(collapsed == expanded)
        // 进度与位置是独立可空维度（Loading/Error 态均为 null）。
        assertEquals(null, PdfCapsuleData(title = "doc.pdf", null, null).positionLine)
    }

    // ── Ready 态派生：单一事实源 ────────────────────────────────────────────

    @Test
    fun `ready state derives title from deepest outline node with document fallback`() {
        val d = readyDerivation()
        assertTrue(d.contains("state.currentOutlineNode?.title?.takeIf { it.isNotBlank() }"))
        assertTrue(d.contains("?: state.document.displayName"))
    }

    @Test
    fun `ready state derives position line and rounded clamped progress only when page count positive`() {
        val d = readyDerivation()
        assertTrue(d.contains("positionLine = state.pageLabel.takeIf { state.pageCount > 0 }"))
        assertTrue(d.contains("(((state.pageIndex + 1) * 100.0) / state.pageCount)"))
        assertTrue(d.contains(".toInt()"))
        assertTrue(d.contains(".coerceIn(0, 100)"))
        // pageCount == 0 时进度不可知（null），不渲染 0%。
        assertTrue(d.contains("} else {") && d.contains("null,"))
    }

    @Test
    fun `print detail surfaces only when textbook anchor is bound`() {
        assertTrue(readyDerivation().contains("printDetail = printLabel.takeIf { printNum != null }"))
    }

    @Test
    fun `jump card receives human page index one-based`() {
        assertTrue(source("PdfViewerScreen.kt").contains("currentHumanPage = state.pageIndex + 1"))
    }

    @Test
    fun `loading and error states pass title-only capsule payloads`() {
        val s = source("PdfViewerScreen.kt")
        assertTrue(s.contains("title = stringResource(R.string.viewer_loading)"))
        assertTrue(
            s.contains("title = state.displayName ?: stringResource(R.string.viewer_title_fallback)"),
        )
        // Loading 与 Error 各一份 title-only payload；无第三处。
        assertEquals(2, Regex("positionLine = null,").findAll(norm(s)).count())
        assertEquals(2, Regex("progressPercent = null,").findAll(norm(s)).count())
        // 两个状态都不提供胶囊点击目标。
        assertEquals(2, Regex("onCapsuleToggle = null,").findAll(norm(s)).count())
    }

    // ── morph 状态行为：单一 spring 时钟是唯一驱动源 ────────────────────────

    /** PdfInfoCapsule 区域（至 JumpPageBody 前）：所有胶囊 morph 实现都在这里。 */
    private val capsuleRegion: String by lazy {
        val s = source("PdfViewerScreen.kt")
        val start = s.indexOf("private fun PdfInfoCapsule")
        val end = s.indexOf("private fun JumpPageBody")
        assertTrue(start >= 0 && end > start)
        s.substring(start, end)
    }

    @Test
    fun `one spring clock owns every animated dimension - no second clock exists`() {
        val r = norm(capsuleRegion)
        assertTrue(r.contains("remember { Animatable(if (expanded) 1f else 0f) }"))
        assertTrue(r.contains("LaunchedEffect(expanded)"))
        assertTrue(r.contains("morph.animateTo(if (expanded) 1f else 0f, CapsuleMorphSpring)"))
        // 单一时钟：整个区域只有一个 Animatable、一次 animateTo、一次读取 morph.value，
        // 其余全部从派生的 fraction 计算 —— 不存在第二个状态源。
        assertEquals(1, Regex("Animatable\\(").findAll(capsuleRegion).count())
        assertEquals(1, Regex("animateTo\\(").findAll(r).count())
        assertEquals(1, Regex("morph\\.value").findAll(r).count())
        assertTrue(r.contains("val fraction = morph.value"))
        // 中断续播靠 animateTo 从当前值出发；snap/tween 会制造跳变或第二时钟。
        assertFalse(r.contains("tween("))
        assertFalse(r.contains("snapTo"))
        assertFalse(r.contains("animateFloatAsState"))
    }

    @Test
    fun `width band interpolates linearly from pill to popover under the cap`() {
        val r = norm(capsuleRegion)
        assertTrue(r.contains("val collapsedWidth = 200.dp.coerceAtMost(widthCap)"))
        assertTrue(r.contains("val expandedWidth = 240.dp.coerceAtMost(widthCap)"))
        assertTrue(
            r.contains("val surfaceWidthDp = collapsedWidth + (expandedWidth - collapsedWidth) * fraction"),
        )
        assertTrue(r.contains(".width(surfaceWidthDp)"))
    }

    @Test
    fun `silhouette switches once and top edge stays the pill width`() {
        val r = norm(capsuleRegion)
        // 仅在真正变宽时用梯形轮廓；两端都是普通圆角矩形，中间只让 body 外扩。
        assertTrue(r.contains("if (fraction > 0f && expandedWidth > collapsedWidth)"))
        assertTrue(r.contains("topWidth = collapsedWidth"))
        assertTrue(r.contains("bottomWidth = surfaceWidthDp"))
        assertTrue(r.contains("} else { RoundedCornerShape(22.dp) }"))
        // 锚点稳定性的另一半：header 行永远钉在收起态宽度上。
        assertTrue(r.contains(".width(collapsedWidth)"))
        assertTrue(r.contains(".height(44.dp)"))
    }

    @Test
    fun `header crossfade is fraction-driven with no second alpha clock`() {
        val r = norm(capsuleRegion)
        assertTrue(r.contains("graphicsLayer { alpha = 1f - fraction }"))
        assertTrue(r.contains("if (fraction > 0f) {"))
        assertTrue(r.contains("graphicsLayer { alpha = fraction }"))
    }

    @Test
    fun `body reveal follows the same fraction with early content fade-in`() {
        val r = norm(capsuleRegion)
        // 内容可见性与区域高度都由同一 fraction 推生：宽度展开时 body 必然已合成，
        // 禁止「宽了但内容没长」的非法组合（shouldComposeBody 含 fraction > 0 分支）。
        assertTrue(r.contains("val shouldComposeBody = expanded || fraction > 0f"))
        assertTrue(r.contains("(bodyNaturalHeightPx * fraction).toDp()"))
        assertTrue(r.contains("(fraction * 1.5f).coerceAtMost(1f)"))
        // 展开态材质随 expanded 切换，但仍是同一 surface（GlassMaterialRole.Capsule）。
        assertTrue(r.contains("role = GlassMaterialRole.Capsule"))
        assertTrue(r.contains("interaction = expanded"))
    }

    @Test
    fun `marquee applies only via the shared slow helper on both titles`() {
        val s = source("PdfViewerScreen.kt")
        val helperCount = Regex("private fun Modifier.capsuleTitleMarquee").findAll(s).count()
        assertEquals(1, helperCount)
        val n = norm(s)
        assertTrue(n.contains("iterations = Int.MAX_VALUE"))
        assertTrue(n.contains("initialDelayMillis = 1500"))
        assertTrue(n.contains("repeatDelayMillis = 1200"))
        // 收起态标题与展开态标题各挂一次（排除 helper 定义自身）。
        val usages = Regex("(?<!fun Modifier)\\.capsuleTitleMarquee\\(\\)").findAll(s).count()
        assertEquals(2, usages)
    }

    @Test
    fun `frozen interaction specs stay untouched`() {
        val n = norm(source("PdfViewerScreen.kt"))
        assertTrue(
            n.contains("private val CapsuleMorphSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 450f)"),
        )
        assertTrue(n.contains("private const val MENU_CLOSE_MORPH_MS = 200L"))
    }

    private fun readyDerivation(): String = norm(source("PdfViewerScreen.kt"))

    private fun source(name: String): String = appSource(
        "com/markleaf/app/ui/pdf/$name",
    ).readText()

    private fun appSource(relative: String): File = listOf(
        File("src/main/java/$relative"),
        File("app/src/main/java/$relative"),
        File("/root/code/MarkLeaf/app/src/main/java/$relative"),
    ).first { it.isFile }

    private fun norm(s: String): String = s.replace(Regex("\\s+"), " ")
}
