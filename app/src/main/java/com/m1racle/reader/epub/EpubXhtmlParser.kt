package com.m1racle.reader.epub

import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * XHTML subset → [EpubBlock]. Script/style dropped, remote resources rejected,
 * unknown layout elements degrade to [EpubBlock.Unsupported] placeholders.
 */
object EpubXhtmlParser {
    const val MAX_BLOCKS = 20_000

    private class BlockBudget(var count: Int = 0)

    /** Both the chapter and nested quotes spend the same output budget. */
    private class Blocks(val budget: BlockBudget) : ArrayList<EpubBlock>() {
        override fun add(element: EpubBlock): Boolean {
            if (++budget.count > MAX_BLOCKS) throw EpubException.TooLarge("章节段落过多")
            return super.add(element)
        }
    }

    private val VOID_TAGS = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr",
    )

    private val HTML_ENTITIES = mapOf(
        "nbsp" to 160, "copy" to 169, "reg" to 174, "hellip" to 8230,
        "mdash" to 8212, "ndash" to 8211, "ldquo" to 8220, "rdquo" to 8221,
        "lsquo" to 8216, "rsquo" to 8217, "middot" to 183, "bull" to 8226,
        "emsp" to 8195, "ensp" to 8194, "thinsp" to 8201, "dagger" to 8224,
        "Dagger" to 8225, "laquo" to 171, "raquo" to 187, "sect" to 167,
        "para" to 182, "times" to 215, "divide" to 247, "minus" to 8722,
        "plusmn" to 177, "sup2" to 178, "sup3" to 179, "frac12" to 189,
        "frac14" to 188, "trade" to 8482, "euro" to 8364, "pound" to 163,
        "yen" to 165, "cent" to 162, "deg" to 176,
    )

    private val VoidPattern =
        Regex("<(area|base|br|col|embed|hr|img|input|link|meta|param|source|track|wbr)\\b((?:[^>\"']|\"[^\"]*\"|'[^']*')*)>")
    private val EntityPattern =
        Regex("&([A-Za-z][A-Za-z0-9]*);")

    /**
     * Real-world chapters are often HTML tag-soup (unclosed voids, HTML-only
     * entities). Normalize the two common cases before strict XML parsing;
     * anything else still fails loudly with the chapter href attached.
     */
    internal fun sanitizeTagSoup(raw: String): String {
        val withEntities = EntityPattern.replace(raw) { match ->
            val code = HTML_ENTITIES[match.groupValues[1]]
            if (code != null) "&#$code;" else match.value
        }
        return VoidPattern.replace(withEntities) { match ->
            val tag = match.groupValues[1].lowercase()
            val attrs = match.groupValues[2]
            if (tag in VOID_TAGS && !attrs.trimEnd().endsWith("/")) {
                "<$tag$attrs/>"
            } else {
                match.value
            }
        }
    }

    fun parse(bytes: ByteArray): Pair<List<EpubBlock>, Map<String, Int>> {
        if (bytes.size > EpubPackageImporter.MAX_XML_BYTES) throw EpubException.TooLarge("章节过大")
        val text = sanitizeTagSoup(bytes.toString(Charsets.UTF_8))
        val doc = EpubPackageImporter.parseXml(text.toByteArray(Charsets.UTF_8), "章节")
        val roots = doc.getElementsByTagNameNS("*", "body")
        val body = if (roots.length > 0) roots.item(0) as Element else doc.documentElement
        val out = Blocks(BlockBudget())
        val anchors = mutableMapOf<String, Int>()
        val kids = body.childNodes
        for (i in 0 until kids.length) {
            blockFor(kids.item(i), out, anchors)
        }
        if (out.isEmpty()) out.add(EpubBlock.Unsupported("空章节"))
        return out to anchors
    }

    private fun blockFor(node: Node, out: Blocks, anchors: MutableMap<String, Int>) {
        when (node.nodeType) {
            Node.TEXT_NODE -> {
                val text = clean(node.textContent)
                if (text.isNotEmpty()) out.add(EpubBlock.Paragraph(listOf(EpubInline(text))))
            }
            Node.ELEMENT_NODE -> {
                val el = node as Element
                val tag = el.tagName.substringAfter(":").lowercase()
                if (tag == "script" || tag == "style" || tag == "head" ||
                    tag == "title" || tag == "meta" || tag == "link"
                ) {
                    return
                }
                val id = el.getAttribute("id")
                if (id.isNotEmpty() && id !in anchors) anchors[id] = out.size
                when (tag) {
                    "h1", "h2", "h3", "h4", "h5", "h6" -> {
                        val text = clean(el.textContent)
                        if (text.isNotEmpty()) {
                            out.add(EpubBlock.Heading(tag[1] - '0', text))
                        }
                    }
                    "p", "div", "section", "article", "header", "footer" -> {
                        paragraphKids(el, out, anchors)
                    }
                    "ul", "ol" -> {
                        registerInlineAnchors(el, out.size, anchors)
                        val items = mutableListOf<List<EpubInline>>()
                        eachElement(el) { li, ltag ->
                            if (ltag == "li") {
                                val inlines = normalizeInlines(inlineKids(li))
                                if (inlines.isNotEmpty()) items.add(inlines)
                            }
                        }
                        if (items.isNotEmpty()) out.add(EpubBlock.ListBlock(tag == "ol", items))
                    }
                    "blockquote" -> {
                        val inner = Blocks(out.budget)
                        val innerAnchors = mutableMapOf<String, Int>()
                        eachElement(el) { kid, _ -> blockFor(kid, inner, innerAnchors) }
                        innerAnchors.keys.forEach { anchors.putIfAbsent(it, out.size) }
                        if (inner.isNotEmpty()) out.add(EpubBlock.Quote(inner))
                    }
                    "pre" -> {
                        registerInlineAnchors(el, out.size, anchors)
                        out.add(EpubBlock.Code(el.textContent.orEmpty()))
                    }
                    "hr" -> out.add(EpubBlock.Divider)
                    "br" -> Unit // line break inside paragraphs is whitespace
                    "img", "image" -> addImage(el, out)
                    "svg" -> addSvgImages(el, out)
                    "figure", "aside" -> paragraphKids(el, out, anchors)
                    "table" -> {
                        val lines = tableLines(el)
                        if (lines.isEmpty()) out.add(EpubBlock.Unsupported("表格"))
                        else lines.forEach { line ->
                            out.add(EpubBlock.Paragraph(listOf(EpubInline(line))))
                        }
                    }
                    "audio", "video", "canvas", "object", "embed", "iframe" ->
                        out.add(EpubBlock.Unsupported("不支持的媒体"))
                    "a", "span", "b", "strong", "i", "em", "code", "small", "sub", "sup", "u", "s", "q", "cite" -> {
                        registerInlineAnchors(el, out.size, anchors)
                        val inlines = normalizeInlines(inlineKids(el))
                        if (inlines.isNotEmpty()) out.add(EpubBlock.Paragraph(inlines))
                    }
                    else -> {
                        // Unknown container: descend, but never silently drop text.
                        val before = out.size
                        eachElement(el) { kid, _ -> blockFor(kid, out, anchors) }
                        if (out.size == before) {
                            val text = clean(el.textContent)
                            if (text.isNotEmpty()) {
                                out.add(EpubBlock.Paragraph(listOf(EpubInline(text))))
                            }
                        }
                    }
                }
            }
            else -> Unit
        }
    }

    /** Flow children of p/div-like elements: split nested blocks, keep inline runs. */
    private fun paragraphKids(el: Element, out: Blocks, anchors: MutableMap<String, Int>) {
        val pending = mutableListOf<EpubInline>()
        fun flush() {
            val normalized = normalizeInlines(pending)
            if (normalized.isNotEmpty()) out.add(EpubBlock.Paragraph(normalized))
            pending.clear()
        }
        val kids = el.childNodes
        for (i in 0 until kids.length) {
            val kid = kids.item(i)
            when (kid.nodeType) {
                Node.TEXT_NODE -> {
                    val text = kid.textContent.orEmpty()
                    if (text.isNotEmpty()) pending.add(EpubInline(text))
                }
                Node.ELEMENT_NODE -> {
                    val tag = (kid as Element).tagName.substringAfter(":").lowercase()
                    if (tag == "br") {
                        pending.add(EpubInline(" "))
                    } else if (tag == "img" || tag == "image" || tag == "svg") {
                        flush()
                        blockFor(kid, out, anchors)
                    } else if (tag in BLOCK_TAGS) {
                        flush()
                        blockFor(kid, out, anchors)
                    } else {
                        registerInlineAnchors(kid, out.size, anchors)
                        pending.addAll(inlineKids(kid))
                    }
                }
                else -> Unit
            }
        }
        flush()
    }

    private fun registerInlineAnchors(el: Element, block: Int, anchors: MutableMap<String, Int>) {
        val id = el.getAttribute("id")
        if (id.isNotEmpty()) anchors.putIfAbsent(id, block)
        eachElement(el) { child, _ -> registerInlineAnchors(child, block, anchors) }
    }

    /** Collapse whitespace across styled runs, trimming only the whole block. */
    private fun normalizeInlines(runs: List<EpubInline>): List<EpubInline> {
        val result = mutableListOf<EpubInline>()
        var space: EpubInline? = null
        var hasText = false
        runs.forEach { run ->
            val text = StringBuilder()
            fun flush() {
                if (text.isNotEmpty()) {
                    result.add(run.copy(text = text.toString()))
                    text.clear()
                }
            }
            for (ch in run.text) {
                if (ch.isWhitespace() || ch == '\u00a0' || ch == '\u2060') {
                    flush()
                    space = run
                } else {
                    if (space != null && hasText) {
                        result.add(space!!.copy(text = " "))
                    }
                    space = null
                    hasText = true
                    text.append(ch)
                }
            }
            flush()
        }
        return result
    }

    private val BLOCK_TAGS = setOf(
        "h1", "h2", "h3", "h4", "h5", "h6", "p", "div", "ul", "ol",
        "blockquote", "pre", "hr", "table", "section", "article",
        "svg", "figure", "aside",
    )

    private fun addSvgImages(el: Element, out: MutableList<EpubBlock>) {
        val before = out.size
        val images = el.getElementsByTagNameNS("*", "image")
        for (i in 0 until images.length) {
            addImage(images.item(i) as Element, out)
        }
        if (out.size == before) out.add(EpubBlock.Unsupported("不支持的媒体"))
    }

    private fun addImage(el: Element, out: MutableList<EpubBlock>) {
        val src = imageHref(el)
        if (src.startsWith("http:", true) || src.startsWith("https:", true)) {
            out.add(EpubBlock.Unsupported("在线图片已忽略"))
        } else {
            val alt = el.getAttribute("alt").ifEmpty { null }
            out.add(EpubBlock.Image(alt = alt, src = src.ifEmpty { null }))
        }
    }

    private fun imageHref(el: Element): String {
        val src = el.getAttribute("src")
        if (src.isNotEmpty()) return src
        val xlink = el.getAttributeNS("http://www.w3.org/1999/xlink", "href")
        if (xlink.isNotEmpty()) return xlink
        val prefixed = el.getAttribute("xlink:href")
        if (prefixed.isNotEmpty()) return prefixed
        return el.getAttribute("href")
    }

    private fun tableLines(table: Element): List<String> {
        val lines = mutableListOf<String>()
        val rows = table.getElementsByTagNameNS("*", "tr")
        for (i in 0 until rows.length) {
            val tr = rows.item(i) as? Element ?: continue
            val cells = mutableListOf<String>()
            val kids = tr.childNodes
            for (j in 0 until kids.length) {
                val cell = kids.item(j) as? Element ?: continue
                val tag = cell.tagName.substringAfter(":").lowercase()
                if (tag == "td" || tag == "th") {
                    val text = clean(cell.textContent)
                    if (text.isNotEmpty()) cells.add(text)
                }
            }
            if (cells.isNotEmpty()) lines.add(cells.joinToString("  ·  "))
        }
        return lines
    }

    private fun inlineKids(el: Element): List<EpubInline> {
        val out = mutableListOf<EpubInline>()
        collectInline(el, out, bold = false, italic = false, code = false)
        return out.filter { it.text.isNotEmpty() }
    }

    private fun collectInline(node: Node, out: MutableList<EpubInline>, bold: Boolean, italic: Boolean, code: Boolean) {
        when (node.nodeType) {
            Node.TEXT_NODE -> {
                val text = node.textContent.orEmpty()
                if (text.isNotEmpty()) out.add(EpubInline(text, bold, italic, code))
            }
            Node.ELEMENT_NODE -> {
                val el = node as Element
                val tag = el.tagName.substringAfter(":").lowercase()
                if (tag == "script" || tag == "style") return
                if (tag == "br") {
                    out.add(EpubInline(" "))
                    return
                }
                if (tag == "img") return // images are block-level placeholders
                collectInlineKids(
                    el, out,
                    bold || tag == "b" || tag == "strong",
                    italic || tag == "i" || tag == "em",
                    code || tag == "code",
                )
            }
            else -> Unit
        }
    }

    private fun collectInlineKids(el: Element, out: MutableList<EpubInline>, bold: Boolean, italic: Boolean, code: Boolean) {
        val kids = el.childNodes
        for (i in 0 until kids.length) collectInline(kids.item(i), out, bold, italic, code)
    }

    private fun eachElement(parent: Element, fn: (Element, String) -> Unit) {
        val kids = parent.childNodes
        for (i in 0 until kids.length) {
            val kid = kids.item(i) as? Element ?: continue
            fn(kid, kid.tagName.substringAfter(":").lowercase())
        }
    }

    private fun clean(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        val sb = StringBuilder()
        var pendingSpace = false
        for (ch in raw) {
            if (ch.isWhitespace() || ch == ' ' || ch == '⁠') {
                pendingSpace = true
            } else {
                if (pendingSpace && sb.isNotEmpty()) sb.append(' ')
                pendingSpace = false
                sb.append(ch)
            }
        }
        return sb.toString()
    }
}
