package com.m1racle.reader.epub

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubTocSafetyTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun book(nav: Boolean, href: String, toc: String): File {
        val root = tmp.newFolder()
        fun file(path: String, text: String) {
            File(root, path).apply { parentFile.mkdirs(); writeText(text) }
        }
        file("META-INF/container.xml", """<container><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""")
        file("OPS/book.opf", """<package version="3.0"><metadata/><manifest>
            <item id="a" href="text/a.xhtml"/><item id="b" href="text/b.xhtml"/>
            <item id="toc" href="$href" ${if (nav) "properties=\"nav\"" else ""}/>
            </manifest><spine><itemref idref="a"/><itemref idref="b"/></spine></package>""")
        file("OPS/text/a.xhtml", "<html><body><p>A</p></body></html>")
        file("OPS/text/b.xhtml", "<html><body><p id=\"end\">B</p></body></html>")
        if (!href.startsWith("../")) file("OPS/" + href.replace("%20", " "), toc)
        return root
    }

    @Test fun nestedNcxKeepsEveryLevelAndResolvesLinksRelativeToNcx() {
        val root = book(false, "navigation/toc.ncx", """<ncx><navMap>
            <navPoint><navLabel><text>Parent</text></navLabel><content src="../text/a.xhtml"/>
              <navPoint><navLabel><text>Child</text></navLabel><content src="../text/b.xhtml"/>
                <navPoint><navLabel><text>Grandchild</text></navLabel><content src="../text/b.xhtml#end"/></navPoint>
              </navPoint>
            </navPoint></navMap></ncx>""")
        val parent = EpubPackageImporter.parseExtracted(root).toc.single()
        assertEquals(0, parent.spineIndex)
        val child = parent.children.single()
        assertEquals("Child", child.title)
        assertEquals(1, child.spineIndex)
        assertEquals("Grandchild", child.children.single().title)
        assertEquals(1, child.children.single().spineIndex)
    }

    @Test fun navInSubdirectoryWithEscapedNameResolvesDotSegmentsAndFragments() {
        val root = book(true, "navigation/my%20toc.xhtml", """<html><body><nav><ol>
            <li><a href="../text/./b.xhtml#end">End</a></li>
            </ol></nav></body></html>""")
        val node = EpubPackageImporter.parseExtracted(root).toc.single()
        assertEquals(1, node.spineIndex)
        assertEquals("end", node.href.substringAfter('#'))
    }

    @Test fun navAndNcxCannotReadOutsideExtractedBook() {
        for (nav in listOf(true, false)) {
            val root = book(nav, "../../outside.ncx", "")
            File(root.parentFile, "outside.ncx").writeText("<ncx><navMap/></ncx>")
            try {
                EpubPackageImporter.parseExtracted(root)
                fail("Expected path rejection")
            } catch (_: EpubException.UnsafeEntry) { }
        }
    }

    @Test fun oversizedTocIsRejectedBeforeParsing() {
        val root = book(true, "toc.xhtml", " ".repeat(EpubPackageImporter.MAX_XML_BYTES.toInt() + 1))
        try {
            EpubPackageImporter.parseExtracted(root)
            fail("Expected XML limit")
        } catch (_: EpubException.TooLarge) { }
    }
}
