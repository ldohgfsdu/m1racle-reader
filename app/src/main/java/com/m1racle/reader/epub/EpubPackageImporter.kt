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
    private const val MAX_XML_DEPTH = 128
    private const val MAX_XML_NODES = 200_000
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
        val opfRel = findOpfPath(parseXml(readXmlBytes(containerFile), "container")) ?:
            throw EpubException.Corrupt("缺少 OPF")
        val opfFile = File(rootCanon, opfRel)
        if (!opfFile.canonicalFile.path.startsWith(rootCanon.path + File.separator)) {
            throw EpubException.UnsafeEntry("非法 OPF 路径")
        }
        if (!opfFile.isFile) throw EpubException.Corrupt("缺少 OPF 文件")
        val opfBytes = readXmlBytes(opfFile)
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
        val toc = parseToc(rootCanon, opfDir, manifest, spine, navHref)
        val chapters = spine.mapIndexed { index, item ->
            val file = File(opfDir, item.href)
            if (!file.canonicalFile.path.startsWith(rootCanon.path + File.separator) || !file.isFile) {
                throw EpubException.Corrupt("缺章节文件")
            }
            // A single unparseable chapter degrades to a placeholder block so the
            // book still opens and spine indices (TOC/progress) stay aligned.
            val (blocks, anchors) = try {
                val parsed = EpubXhtmlParser.parse(readXmlBytes(file))
                resolveChapterImages(parsed.first, file.parentFile ?: opfDir, rootCanon) to parsed.second
            } catch (_: Exception) {
                listOf(EpubBlock.Unsupported("本章排版异常，已跳过")) to emptyMap<String, Int>()
            }
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
                if (path.isNotEmpty()) return decodeHref(path)
            }
        }
        return null
    }

    private fun readBounded(zip: ZipFile, entry: ZipEntry, cap: Long): ByteArray {
        zip.getInputStream(entry).use { input ->
            return readBounded(input, cap)
        }
    }

    internal fun resolveChapterImages(
        blocks: List<EpubBlock>,
        baseDir: File,
        root: File,
    ): List<EpubBlock> = blocks.map { block ->
        when (block) {
            is EpubBlock.Image -> block.copy(
                resolvedPath = resolvePackagedFile(baseDir, block.src, root)?.canonicalPath,
            )
            is EpubBlock.Quote -> block.copy(blocks = resolveChapterImages(block.blocks, baseDir, root))
            else -> block
        }
    }

    /**
     * Resolve a chapter-relative resource inside the extracted tree.
     * Remote URLs and paths that escape [root] are rejected.
     */
    internal fun resolvePackagedFile(baseDir: File, href: String?, root: File): File? {
        if (href.isNullOrBlank()) return null
        val raw = decodeHref(href.substringBefore('#')).trim()
        if (raw.isEmpty()) return null
        return packagedPath(baseDir, raw, root)?.takeIf { it.isFile }
    }

    private fun packagedPath(baseDir: File, decodedPath: String, root: File): File? {
        if (decodedPath.startsWith("/") || decodedPath.startsWith("\\") ||
            Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(decodedPath)) return null
        val raw = decodedPath
        val rootCanon = root.canonicalFile
        val target = File(baseDir, raw).canonicalFile
        if (target.path != rootCanon.path &&
            !target.path.startsWith(rootCanon.path + File.separator)
        ) {
            return null
        }
        return target
    }

    /** Percent-decode a package href (UTF-8). Unlike form decoding, '+' is kept literal. */
    internal fun decodeHref(href: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < href.length) {
            val c = href[i]
            if (c == '%' && i + 2 < href.length) {
                val hex = href.substring(i + 1, i + 3)
                val v = hex.toIntOrNull(16)
                if (v != null) {
                    // Collect the full UTF-8 run for multibyte sequences.
                    val bytes = mutableListOf<Byte>()
                    var j = i
                    while (j < href.length && href[j] == '%' && j + 2 < href.length) {
                        val w = href.substring(j + 1, j + 3).toIntOrNull(16) ?: break
                        bytes.add(w.toByte())
                        j += 3
                    }
                    out.append(String(bytes.toByteArray(), Charsets.UTF_8))
                    i = j
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
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

    private fun readXmlBytes(file: File): ByteArray = file.inputStream().use { readBounded(it, MAX_XML_BYTES) }

    internal fun parseXml(bytes: ByteArray, what: String): org.w3c.dom.Document {
        if (bytes.size > MAX_XML_BYTES) throw EpubException.TooLarge("XML 过大")
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            factory.isValidating = false
            // XInclude defaults to false. Android's factory does not support
            // setXIncludeAware, even when disabling it; leave the default intact.
            factory.isExpandEntityReferences = false
            // Real-world books commonly ship DOCTYPE (OPF/NCX/chapters), so it
            // stays allowed; XXE is blocked by disabling external entities and
            // capping entity expansion (billion-laughs guard). Each feature is
            // set defensively: Android's built-in parser does not know these
            // Xerces names and throws UnsupportedOperationException for them;
            // its expat core skips external entities by default, and the app
            // holds no INTERNET permission as the backstop.
            fun setFeatureQuietly(name: String, value: Boolean) {
                try {
                    factory.setFeature(name, value)
                } catch (_: Exception) {
                    // Unsupported on this runtime; ignore.
                }
            }
            setFeatureQuietly("http://xml.org/sax/features/external-general-entities", false)
            setFeatureQuietly("http://xml.org/sax/features/external-parameter-entities", false)
            setFeatureQuietly("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            try {
                factory.setAttribute(
                    "http://www.oracle.com/xml/jaxp/properties/entityExpansionLimit", 10_000,
                )
                factory.setAttribute(
                    "http://www.oracle.com/xml/jaxp/properties/totalEntitySizeLimit", 5_000_000,
                )
            } catch (_: Exception) {
                // Attribute unsupported on this runtime; entity limits still
                // enforced by MAX_XML_BYTES on the raw document bytes.
            }
            val document = factory.newDocumentBuilder().parse(bytes.inputStream())
            // Validate iteratively before any recursive DOM lookup/textContent or
            // XHTML/TOC traversal. A small XML file can still have extreme depth.
            var node: Node = document
            var depth = 0
            var count = 0
            while (true) {
                if (++count > MAX_XML_NODES || depth > MAX_XML_DEPTH) {
                    throw EpubException.TooLarge("$what 结构过于复杂")
                }
                val child = node.firstChild
                if (child != null) {
                    node = child
                    depth++
                } else {
                    while (node !== document && node.nextSibling == null) {
                        node = node.parentNode
                        depth--
                    }
                    if (node === document) break
                    node = node.nextSibling
                }
            }
            return document
        } catch (e: EpubException) {
            throw e
        } catch (e: Exception) {
            // Carry the parser cause (class + first line) so device reports are actionable.
            val detail = (e.message ?: "").lineSequence().firstOrNull()?.take(120).orEmpty()
            val cause = e.javaClass.simpleName + (if (detail.isEmpty()) "" else ": $detail")
            throw EpubException.Corrupt("$what 解析失败($cause)")
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
            if (id.isNotEmpty() && href.isNotEmpty()) manifest[id] = decodeHref(href)
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
        root: File,
        opfDir: File,
        manifest: Map<String, String>,
        spine: List<EpubSpineItem>,
        navHref: String?,
    ): List<EpubTocNode> {
        val hrefToSpine = spine.mapIndexed { index, s ->
            packagedPath(opfDir, s.href, root)?.path to index
        }.toMap()
        fun spineFor(base: File): (String) -> Int? = { href ->
            packagedPath(base, href.substringBefore('#'), root)?.path?.let(hrefToSpine::get)
        }
        fun tocFile(href: String): File {
            val file = packagedPath(opfDir, href, root)
                ?: throw EpubException.UnsafeEntry("非法目录路径")
            if (!file.isFile) throw EpubException.Corrupt("缺目录文件")
            return file
        }
        // EPUB3 nav via OPF properties="nav".
        if (navHref != null) {
            val navFile = tocFile(decodeHref(navHref))
            if (navFile.isFile) {
                val nodes = parseNavToc(navFile, spineFor(checkNotNull(navFile.parentFile)))
                if (nodes.isNotEmpty()) return nodes
            }
        }
        // EPUB2 NCX fallback.
        val ncxHref = manifest.entries.firstOrNull { (_, href) ->
            href.endsWith(".ncx", true)
        }?.value
        if (ncxHref != null) {
            val ncxFile = tocFile(ncxHref)
            if (ncxFile.isFile) return parseNcxToc(readXmlBytes(ncxFile), spineFor(checkNotNull(ncxFile.parentFile)))
        }
        // Last resort: flat spine list.
        return spine.mapIndexed { index, s ->
            EpubTocNode("第 ${index + 1} 章", s.href, index, emptyList())
        }
    }

    private fun parseNavToc(navFile: File, spineFor: (String) -> Int?): List<EpubTocNode> {
        val doc = parseXml(readXmlBytes(navFile), "nav")
        val navs = doc.getElementsByTagNameNS("*", "nav")
        for (i in 0 until navs.length) {
            val nav = navs.item(i) as? Element ?: continue
            if (!navHasTocType(nav)) continue
            val lists = nav.getElementsByTagNameNS("*", "ol")
            if (lists.length == 0) continue
            return parseOl(lists.item(0) as Element, spineFor)
        }
        return emptyList()
    }

    private fun navHasTocType(nav: Element): Boolean {
        val type = nav.getAttribute("epub:type")
        if (type.split(" ").contains("toc")) return true
        // Fallback: first nav containing a list of links counts when no epub:type at all.
        return nav.getAttribute("epub:type").isEmpty()
    }

    private fun parseOl(ol: Element, spineFor: (String) -> Int?): List<EpubTocNode> {
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
            val href = decodeHref(link.getAttribute("href"))
            if (href.isEmpty()) continue
            out.add(
                EpubTocNode(
                    title = link.textContent?.trim()?.ifEmpty { href } ?: href,
                    href = href,
                    spineIndex = spineFor(href),
                    children = if (nested != null) parseOl(nested, spineFor) else emptyList(),
                ),
            )
        }
        return out
    }

    private fun parseNcxToc(ncxBytes: ByteArray, spineFor: (String) -> Int?): List<EpubTocNode> {
        val doc = parseXml(ncxBytes, "NCX")
        val maps = doc.getElementsByTagNameNS("*", "navMap")
        if (maps.length == 0) return emptyList()
        return parseNavPoints(maps.item(0) as Element, spineFor)
    }

    private fun parseNavPoints(parent: Element, spineFor: (String) -> Int?): List<EpubTocNode> {
        val out = mutableListOf<EpubTocNode>()
        val kids = parent.childNodes
        for (i in 0 until kids.length) {
            val np = kids.item(i) as? Element ?: continue
            if (!np.tagName.endsWith("navPoint")) continue
            var title: String? = null
            var src: String? = null
            val sub = parseNavPoints(np, spineFor)
            val npKids = np.childNodes
            for (j in 0 until npKids.length) {
                val kid = npKids.item(j) as? Element ?: continue
                when {
                    kid.tagName.endsWith("navLabel") -> {
                        val texts = kid.getElementsByTagNameNS("*", "text")
                        if (texts.length > 0) title = texts.item(0).textContent?.trim()
                    }
                    kid.tagName.endsWith("content") -> src = decodeHref(kid.getAttribute("src"))
                }
            }
            // Nested navPoints may also appear grouped after content; collect recursively.
            if (src == null) continue
            out.add(
                EpubTocNode(
                    title = title?.ifEmpty { src } ?: src,
                    href = src,
                    spineIndex = spineFor(src),
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
