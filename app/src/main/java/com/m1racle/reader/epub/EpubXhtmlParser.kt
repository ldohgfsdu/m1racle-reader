package com.m1racle.reader.epub

import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * XHTML subset → [EpubBlock]. Script/style dropped, remote resources rejected,
 * unknown layout elements degrade to [EpubBlock.Unsupported] placeholders.
 */
object EpubXhtmlParser {
    const val MAX_BLOCKS = 20_000

    fun parse(bytes: ByteArray): Pair<List<EpubBlock>, Map<String, Int>> {
        val doc = EpubPackageImporter.parseXml(bytes, "章节")
        val roots = doc.getElementsByTagNameNS("*", "body")
        val body = if (roots.length > 0) roots.item(0) as Element else doc.documentElement
        val out = mutableListOf<EpubBlock>()
        val anchors = mutableMapOf<String, Int>()
        val kids = body.childNodes
        for (i in 0 until kids.length) {
            blockFor(kids.item(i), out, anchors)
            if (out.size > MAX_BLOCKS) break
        }
        if (out.isEmpty()) out.add(EpubBlock.Unsupported("空章节"))
        return out to anchors
    }

    private fun blockFor(node: Node, out: MutableList<EpubBlock>, anchors: MutableMap<String, Int>) {
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
                        val items = mutableListOf<List<EpubInline>>()
                        eachElement(el) { li, ltag ->
                            if (ltag == "li") {
                                val inlines = inlineKids(li)
                                if (inlines.isNotEmpty()) items.add(inlines)
                            }
                        }
                        if (items.isNotEmpty()) out.add(EpubBlock.ListBlock(tag == "ol", items))
                    }
                    "blockquote" -> {
                        val inner = mutableListOf<EpubBlock>()
                        eachElement(el) { kid, _ -> blockFor(kid, inner, anchors) }
                        if (inner.isNotEmpty()) out.add(EpubBlock.Quote(inner))
                    }
                    "pre" -> out.add(EpubBlock.Code(clean(el.textContent).take(8000)))
                    "hr" -> out.add(EpubBlock.Divider)
                    "br" -> Unit // line break inside paragraphs is whitespace
                    "img" -> {
                        val src = el.getAttribute("src")
                        if (src.startsWith("http:", true) || src.startsWith("https:", true)) {
                            out.add(EpubBlock.Unsupported("在线图片已忽略"))
                        } else {
                            out.add(EpubBlock.Image(el.getAttribute("alt").ifEmpty { null }))
                        }
                    }
                    "table" -> out.add(EpubBlock.Unsupported("表格"))
                    "audio", "video", "canvas", "svg", "object", "embed", "iframe" ->
                        out.add(EpubBlock.Unsupported("不支持的媒体"))
                    "a", "span", "b", "strong", "i", "em", "code", "small", "sub", "sup", "u", "s", "q", "cite" -> {
                        val inlines = inlineKids(el)
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
    private fun paragraphKids(el: Element, out: MutableList<EpubBlock>, anchors: MutableMap<String, Int>) {
        val pending = mutableListOf<EpubInline>()
        fun flush() {
            if (pending.isNotEmpty()) {
                out.add(EpubBlock.Paragraph(pending.toList()))
                pending.clear()
            }
        }
        val kids = el.childNodes
        for (i in 0 until kids.length) {
            val kid = kids.item(i)
            when (kid.nodeType) {
                Node.TEXT_NODE -> {
                    val text = clean(kid.textContent)
                    if (text.isNotEmpty()) pending.add(EpubInline(text))
                }
                Node.ELEMENT_NODE -> {
                    val tag = (kid as Element).tagName.substringAfter(":").lowercase()
                    if (tag == "br") {
                        pending.add(EpubInline(" "))
                    } else if (tag == "img") {
                        flush()
                        blockFor(kid, out, anchors)
                    } else if (tag in BLOCK_TAGS) {
                        flush()
                        blockFor(kid, out, anchors)
                    } else {
                        pending.addAll(inlineKids(kid))
                    }
                }
                else -> Unit
            }
        }
        flush()
    }

    private val BLOCK_TAGS = setOf(
        "h1", "h2", "h3", "h4", "h5", "h6", "p", "div", "ul", "ol",
        "blockquote", "pre", "hr", "table", "section", "article",
    )

    private fun inlineKids(el: Element): List<EpubInline> {
        val out = mutableListOf<EpubInline>()
        collectInline(el, out, bold = false, italic = false, code = false)
        return out.filter { it.text.isNotEmpty() }
    }

    private fun collectInline(node: Node, out: MutableList<EpubInline>, bold: Boolean, italic: Boolean, code: Boolean) {
        when (node.nodeType) {
            Node.TEXT_NODE -> {
                val text = clean(node.textContent)
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
