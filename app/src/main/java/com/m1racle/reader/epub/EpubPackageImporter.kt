package com.m1racle.reader.epub

import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/** EPUB container/OPF/TOC parsing over an extracted tree or directly from the zip. */
object EpubPackageImporter {
    const val MAX_ENTRIES = 20_000
    const val MAX_TOTAL_BYTES = 400L * 1024 * 1024
    const val MAX_ENTRY_BYTES = 32L * 1024 * 1024
    const val MAX_XML_BYTES = 8L * 1024 * 1024
    const val EXTRACTOR_VERSION = 1

    private const val CONTAINER_PATH = "META-INF/container.xml"
    private const val ENCRYPTION_PATH = "META-INF/encryption.xml"

    /** Light metadata read straight from the .epub (no extraction). For import-time title. */
    fun readMetadata(epubFile: File): EpubMetadata {
        openZip(epubFile).use { zip ->
            if (zip.getEntry(ENCRYPTION_PATH) != null) {
                return EpubMetadata(null, emptyList(), null, null, null, false, true)
            }
            val opfPath = findOpfPath(zip)
            val opfEntry = zip.getEntry(opfPath) ?: throw EpubException.Corrupt("缺少 OPF")
            val opfBytes = readBounded(zip, opfEntry, MAX_XML_BYTES)
            return parseOpfMetadata(opfBytes, opfPath)
        }
    }

    /** Full open: extract with limits, then parse. */
    fun open(epubFile: File, destDir: File): EpubBook {
        extract(epubFile, destDir)
        return parseExtracted(destDir)
    }

    fun extract(epubFile: File, destDir: File) {
        destDir.mkdirs()
        val destCanon = destDir.canonicalFile
        openZip(epubFile).use { zip ->
            val entries = zip.entries().asSequence().toList()
            if (entries.size > MAX_ENTRIES) throw EpubException.TooLarge("条目过多")
            var total = 0L
            for (entry in entries) {
                if (entry.isDirectory) continue
                val size = entry.size
                if (size > MAX_ENTRY_BYTES) throw EpubException.TooLarge("单文件过大")
                val target = File(destDir, entry.name).canonicalFile
                if (!target.path.startsWith(destCanon.path + File.separator)) {
                    throw EpubException.UnsafeEntry("非法路径")
                }
                val name = entry.name
                if (name.contains("__MACOSX/") || name.endsWith(".DS_Store")) continue
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var entryBytes = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            entryBytes += n
                            total += n
                            if (entryBytes > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) {
                                throw EpubException.TooLarge("解压体积超限")
                            }
                            out.write(buf, 0, n)
                        }
                    }
                }
            }
        }
    }

    fun parseExtracted(rootDir: File): EpubBook {
        val rootCanon = rootDir.canonicalFile
        if (File(rootCanon, ENCRYPTION_PATH).isFile) {
            throw EpubException.Protected("本书受保护，无法打开")
        }
        val containerFile = File(rootCanon, CONTAINER_PATH)
        if (!containerFile.isFile) throw EpubException.Corrupt("缺少 container")
        val opfRel = findOpfPath(parseXml(containerFile.readBytes(), "container")) ?:
            throw EpubException.Corrupt("缺少 OPF")
        val opfFile = File(rootCanon, opfRel)
        if (!opfFile.canonicalFile.path.startsWith(rootCanon.path + File.separator)) {
            throw EpubException.UnsafeEntry("非法 OPF 路径")
        }
        if (!opfFile.isFile) throw EpubException.Corrupt("缺少 OPF 文件")
        val opfBytes = opfFile.readBytes()
        val metadata = parseOpfMetadata(opfBytes, opfRel)
        if (metadata.hasEncryption) throw EpubException.Protected("本书受保护，无法打开")
        if (metadata.isFixedLayout) throw EpubException.FixedLayout("暂不支持固定版式")
        val opfDir = opfFile.parentFile ?: rootCanon
        val (manifest, spineRefs, navHref) = parseOpfManifestSpine(opfBytes)
        val spine = spineRefs.mapIndexed { index, (idref, linear) ->
            val href = manifest[idref] ?: throw EpubException.Corrupt("spine 缺 manifest")
            EpubSpineItem(idref, href, linear, index)
        }
        if (spine.isEmpty()) throw EpubException.Corrupt("空 spine")
        val toc = parseToc(opfDir, manifest, spine, navHref)
        val chapters = spine.mapIndexed { index, item ->
            val file = File(opfDir, item.href)
            if (!file.canonicalFile.path.startsWith(rootCanon.path + File.separator) || !file.isFile) {
                throw EpubException.Corrupt("缺章节文件")
            }
            val (blocks, anchors) = EpubXhtmlParser.parse(file.readBytes())
            EpubChapter(index, item.href, tocTitleFor(toc, index), blocks, anchors)
        }
        return EpubBook(metadata, spine, toc, chapters)
    }

    // ── internals ──

    private fun openZip(file: File): ZipFile {
        try {
            return ZipFile(file)
        } catch (_: Exception) {
            throw EpubException.NotAnEpub("不是有效的 EPUB")
        }
    }

    private fun findOpfPath(zip: ZipFile): String {
        val entry = zip.getEntry(CONTAINER_PATH) ?: throw EpubException.Corrupt("缺少 container")
        return findOpfPath(parseXml(readBounded(zip, entry, MAX_XML_BYTES), "container"))
            ?: throw EpubException.Corrupt("缺少 OPF")
    }

    private fun findOpfPath(containerDoc: org.w3c.dom.Document): String? {
        val roots = containerDoc.getElementsByTagNameNS("*", "rootfile")
        for (i in 0 until roots.length) {
            val el = roots.item(i) as? Element ?: continue
            val type = el.getAttribute("media-type")
            if (type.isEmpty() || type == "application/oebps-package+xml") {
                val path = el.getAttribute("full-path")
                if (path.isNotEmpty()) return path
            }
        }
        return null
    }

    private fun readBounded(zip: ZipFile, entry: ZipEntry, cap: Long): ByteArray {
        zip.getInputStream(entry).use { input ->
            return readBounded(input, cap)
        }
    }

    internal fun readBounded(input: InputStream, cap: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > cap) throw EpubException.TooLarge("XML 过大")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    internal fun parseXml(bytes: ByteArray, what: String): org.w3c.dom.Document {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            factory.isValidating = false
            factory.isXIncludeAware = false
            factory.isExpandEntityReferences = false
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            return factory.newDocumentBuilder().parse(bytes.inputStream())
        } catch (e: EpubException) {
            throw e
        } catch (_: Exception) {
            throw EpubException.Corrupt("$what 解析失败")
        }
    }

    private fun parseOpfMetadata(opfBytes: ByteArray, opfPath: String): EpubMetadata {
        val doc = parseXml(opfBytes, "OPF")
        val pkg = doc.documentElement
        val version = pkg.getAttribute("version").ifEmpty { null }
        fun firstText(ns: String, tag: String): String? {
            val nodes = doc.getElementsByTagNameNS(ns, tag)
            if (nodes.length == 0) return null
            return nodes.item(0).textContent?.trim()?.ifEmpty { null }
        }
        val dc = "http://purl.org/dc/elements/1.1/"
        val creators = mutableListOf<String>()
        val cn = doc.getElementsByTagNameNS(dc, "creator")
        for (i in 0 until cn.length) {
            cn.item(i).textContent?.trim()?.ifEmpty { null }?.let { creators.add(it) }
        }
        var fixed = false
        val metas = doc.getElementsByTagNameNS("*", "meta")
        for (i in 0 until metas.length) {
            val el = metas.item(i) as? Element ?: continue
            if (el.getAttribute("property") == "rendition:layout" &&
                el.textContent?.trim() == "pre-paginated"
            ) {
                fixed = true
            }
        }
        // encryption.xml presence is checked by callers via zip/tree; OPF alone can't tell.
        return EpubMetadata(
            title = firstText(dc, "title"),
            creators = creators,
            language = firstText(dc, "language"),
            identifier = firstText(dc, "identifier"),
            version = version,
            isFixedLayout = fixed,
            hasEncryption = false,
        )
    }

    private fun parseOpfManifestSpine(opfBytes: ByteArray): Triple<Map<String, String>, List<Pair<String, Boolean>>, String?> {
        val doc = parseXml(opfBytes, "OPF")
        val manifest = mutableMapOf<String, String>()
        var navHref: String? = null
        val items = doc.getElementsByTagNameNS("*", "item")
        for (i in 0 until items.length) {
            val el = items.item(i) as? Element ?: continue
            val id = el.getAttribute("id")
            val href = el.getAttribute("href")
            if (id.isNotEmpty() && href.isNotEmpty()) manifest[id] = href
            if (el.getAttribute("properties").split(" ").contains("nav")) navHref = href
        }
        val refs = mutableListOf<Pair<String, Boolean>>()
        val itemrefs = doc.getElementsByTagNameNS("*", "itemref")
        for (i in 0 until itemrefs.length) {
            val el = itemrefs.item(i) as? Element ?: continue
            val idref = el.getAttribute("idref")
            if (idref.isNotEmpty()) refs.add(idref to (el.getAttribute("linear") != "no"))
        }
        return Triple(manifest, refs, navHref)
    }

    private fun parseToc(
        opfDir: File,
        manifest: Map<String, String>,
        spine: List<EpubSpineItem>,
        navHref: String?,
    ): List<EpubTocNode> {
        val hrefToSpine = spine.mapIndexed { index, s -> s.href to index }.toMap()
        // EPUB3 nav via OPF properties="nav".
        if (navHref != null) {
            val navFile = File(opfDir, navHref)
            if (navFile.isFile) {
                val nodes = parseNavToc(navFile, hrefToSpine)
                if (nodes.isNotEmpty()) return nodes
            }
        }
        // EPUB2 NCX fallback.
        val ncxHref = manifest.entries.firstOrNull { (_, href) ->
            href.endsWith(".ncx", true)
        }?.value
        if (ncxHref != null) {
            val ncxFile = File(opfDir, ncxHref)
            if (ncxFile.isFile) return parseNcxToc(ncxFile.readBytes(), hrefToSpine)
        }
        // Last resort: flat spine list.
        return spine.mapIndexed { index, s ->
            EpubTocNode("第 ${index + 1} 章", s.href, index, emptyList())
        }
    }

    private fun parseNavToc(navFile: File, hrefToSpine: Map<String, Int>): List<EpubTocNode> {
        val doc = parseXml(navFile.readBytes(), "nav")
        val navs = doc.getElementsByTagNameNS("*", "nav")
        for (i in 0 until navs.length) {
            val nav = navs.item(i) as? Element ?: continue
            if (!navHasTocType(nav)) continue
            val lists = nav.getElementsByTagNameNS("*", "ol")
            if (lists.length == 0) continue
            return parseOl(lists.item(0) as Element, hrefToSpine)
        }
        return emptyList()
    }

    private fun navHasTocType(nav: Element): Boolean {
        val type = nav.getAttribute("epub:type")
        if (type.split(" ").contains("toc")) return true
        // Fallback: first nav containing a list of links counts when no epub:type at all.
        return nav.getAttribute("epub:type").isEmpty()
    }

    private fun parseOl(ol: Element, hrefToSpine: Map<String, Int>): List<EpubTocNode> {
        val out = mutableListOf<EpubTocNode>()
        val children = ol.childNodes
        for (i in 0 until children.length) {
            val li = children.item(i) as? Element ?: continue
            if (!li.tagName.endsWith("li")) continue
            var link: Element? = null
            var nested: Element? = null
            val liKids = li.childNodes
            for (j in 0 until liKids.length) {
                val kid = liKids.item(j) as? Element ?: continue
                when {
                    kid.tagName.endsWith("a") && link == null -> link = kid
                    (kid.tagName.endsWith("ol") || kid.tagName.endsWith("ul")) && nested == null -> nested = kid
                }
            }
            if (link == null) continue
            val href = link.getAttribute("href")
            if (href.isEmpty()) continue
            out.add(
                EpubTocNode(
                    title = link.textContent?.trim()?.ifEmpty { href } ?: href,
                    href = href,
                    spineIndex = hrefToSpine[href.substringBefore("#")],
                    children = if (nested != null) parseOl(nested, hrefToSpine) else emptyList(),
                ),
            )
        }
        return out
    }

    private fun parseNcxToc(ncxBytes: ByteArray, hrefToSpine: Map<String, Int>): List<EpubTocNode> {
        val doc = parseXml(ncxBytes, "NCX")
        val maps = doc.getElementsByTagNameNS("*", "navMap")
        if (maps.length == 0) return emptyList()
        return parseNavPoints(maps.item(0) as Element, hrefToSpine)
    }

    private fun parseNavPoints(parent: Element, hrefToSpine: Map<String, Int>): List<EpubTocNode> {
        val out = mutableListOf<EpubTocNode>()
        val kids = parent.childNodes
        for (i in 0 until kids.length) {
            val np = kids.item(i) as? Element ?: continue
            if (!np.tagName.endsWith("navPoint")) continue
            var title: String? = null
            var src: String? = null
            val sub = mutableListOf<EpubTocNode>()
            val npKids = np.childNodes
            for (j in 0 until npKids.length) {
                val kid = npKids.item(j) as? Element ?: continue
                when {
                    kid.tagName.endsWith("navLabel") -> {
                        val texts = kid.getElementsByTagNameNS("*", "text")
                        if (texts.length > 0) title = texts.item(0).textContent?.trim()
                    }
                    kid.tagName.endsWith("content") -> src = kid.getAttribute("src")
                    kid.tagName.endsWith("navPoint") -> sub.addAll(parseNavPoints(kid, hrefToSpine))
                }
            }
            // Nested navPoints may also appear grouped after content; collect recursively.
            if (src == null) continue
            out.add(
                EpubTocNode(
                    title = title?.ifEmpty { src } ?: src,
                    href = src,
                    spineIndex = hrefToSpine[src.substringBefore("#")],
                    children = sub,
                ),
            )
        }
        return out
    }

    private fun tocTitleFor(toc: List<EpubTocNode>, spineIndex: Int): String? {
        val queue = ArrayDeque(toc)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.spineIndex == spineIndex) return n.title
            queue.addAll(n.children)
        }
        return null
    }
}
