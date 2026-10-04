package com.m1racle.reader.epub

/**
 * Reading text of one block as styled runs. This is the single source of the
 * characters a reader shows for a block, and therefore of in-block character
 * offsets: the layout builds its styled text from these runs and text anchors
 * count offsets in [readingText].
 */
fun EpubBlock.readingRuns(): List<EpubInline> = buildList { appendRuns(this@readingRuns) }

/** Plain reading text; character offsets of EPUB text anchors index into this string. */
fun EpubBlock.readingText(): String = readingRuns().joinToString("") { it.text }

private fun MutableList<EpubInline>.appendRuns(block: EpubBlock) {
    when (block) {
        is EpubBlock.Heading -> add(EpubInline(block.text))
        is EpubBlock.Paragraph -> addAll(block.inlines)
        is EpubBlock.ListBlock -> block.items.forEachIndexed { index, item ->
            if (index > 0) add(EpubInline("\n"))
            add(EpubInline(if (block.ordered) "${index + 1}. " else "• "))
            addAll(item)
        }
        is EpubBlock.Quote -> block.blocks.forEachIndexed { index, child ->
            if (index > 0) add(EpubInline("\n"))
            appendRuns(child)
        }
        is EpubBlock.Code -> add(EpubInline(block.text, code = true))
        is EpubBlock.Image -> add(EpubInline(block.alt ?: if (block.resolvedPath == null) "本书图片暂不支持显示" else ""))
        is EpubBlock.Divider -> add(EpubInline("────────"))
        is EpubBlock.Unsupported -> add(EpubInline(block.label))
    }
}
