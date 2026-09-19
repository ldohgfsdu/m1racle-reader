#!/usr/bin/env python3
"""Generate minimal EPUB fixtures for unit tests (no DRM, reflowable only).

Output: app/src/test/resources/fixtures/epub/
  sample-epub2.epub  EPUB2 + NCX, 2 chapters
  sample-epub3.epub  EPUB3 + nav, 2 chapters + 1 image ref
  evil-slip.epub     Zip Slip entry (../evil.txt)
  evil-xxe.opf       OPF with external entity (parsed standalone, not zipped)
  evil-encryption.epub  META-INF/encryption.xml present
  not-epub.epub      random bytes with .epub name
"""
import os
import zipfile

OUT = os.path.join("app", "src", "test", "resources", "fixtures", "epub")
os.makedirs(OUT, exist_ok=True)

CONTAINER = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>
"""

CH1 = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>第一章</title></head><body>
<h1>第一章 开始</h1><p>这是第一段，<b>加粗</b>与<i>斜体</i>。</p>
<h2>第一节</h2><p>第二段文字内容。</p><ul><li>条目一</li><li>条目二</li></ul>
</body></html>
"""
CH2 = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>第二章</title></head><body>
<h1>第二章 继续</h1><p>第三段，<code>code()</code> 示例。</p>
<blockquote><p>引用文字。</p></blockquote><hr/><p>尾段。</p>
</body></html>
"""


def write_epub(path, files):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("mimetype", "application/epub+zip")
        for name, data in files.items():
            z.writestr(name, data)


OPF2 = """<?xml version="1.0" encoding="UTF-8"?>
<package version="2.0" xmlns="http://www.idpf.org/2007/opf" unique-identifier="bid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>测试书 EPUB2</dc:title><dc:creator>墨阅</dc:creator>
    <dc:language>zh</dc:language><dc:identifier id="bid">test-epub2</dc:identifier>
  </metadata>
  <manifest>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
    <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
    <item id="ch2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine toc="ncx"><itemref idref="ch1"/><itemref idref="ch2" linear="no"/></spine>
</package>
"""
NCX = """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <navMap>
    <navPoint id="n1" playOrder="1"><navLabel><text>第一章</text></navLabel><content src="ch1.xhtml"/></navPoint>
    <navPoint id="n2" playOrder="2"><navLabel><text>第二章</text></navLabel><content src="ch2.xhtml#sec"/></navPoint>
  </navMap>
</ncx>
"""
write_epub(os.path.join(OUT, "sample-epub2.epub"), {
    "META-INF/container.xml": CONTAINER,
    "OEBPS/content.opf": OPF2,
    "OEBPS/toc.ncx": NCX,
    "OEBPS/ch1.xhtml": CH1,
    "OEBPS/ch2.xhtml": CH2,
})

OPF3 = """<?xml version="1.0" encoding="UTF-8"?>
<package version="3.0" xmlns="http://www.idpf.org/2007/opf" unique-identifier="bid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>测试书 EPUB3</dc:title><dc:creator>墨阅</dc:creator>
    <dc:language>zh</dc:language><dc:identifier id="bid">test-epub3</dc:identifier>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
    <item id="ch2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
    <item id="img" href="img.png" media-type="image/png"/>
  </manifest>
  <spine><itemref idref="ch1"/><itemref idref="ch2"/></spine>
</package>
"""
NAV = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>目录</title></head><body>
<nav epub:type="toc"><h1>目录</h1><ol>
<li><a href="ch1.xhtml">第一章</a></li>
<li><a href="ch2.xhtml">第二章</a><ol><li><a href="ch2.xhtml#tail">尾段</a></li></ol></li>
</ol></nav>
</body></html>
"""
CH2_IMG = CH2.replace("</body>", '<p><img src="img.png" alt="示例图"/></p></body>')
write_epub(os.path.join(OUT, "sample-epub3.epub"), {
    "META-INF/container.xml": CONTAINER,
    "OEBPS/content.opf": OPF3,
    "OEBPS/nav.xhtml": NAV,
    "OEBPS/ch1.xhtml": CH1,
    "OEBPS/ch2.xhtml": CH2_IMG,
    "OEBPS/img.png": bytes.fromhex(
        "89504E470D0A1A0A0000000D4948445200000001000000010802000000907753DE"
        "0000000C4944415478DA6368000000010100 Παρα0000000049454E44AE426082".replace(" Παρα", "")),
})

write_epub(os.path.join(OUT, "evil-slip.epub"), {
    "META-INF/container.xml": CONTAINER,
    "OEBPS/content.opf": OPF2,
    "../evil.txt": "escaped",
})

with open(os.path.join(OUT, "evil-xxe.opf"), "w", encoding="utf-8") as f:
    f.write("""<?xml version="1.0"?>
<!DOCTYPE package [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="b">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>&xxe;</dc:title></metadata>
</package>
""")

write_epub(os.path.join(OUT, "evil-encryption.epub"), {
    "META-INF/container.xml": CONTAINER,
    "META-INF/encryption.xml": "<encryption/>",
    "OEBPS/content.opf": OPF2,
    "OEBPS/ch1.xhtml": CH1,
})

with open(os.path.join(OUT, "not-epub.epub"), "wb") as f:
    f.write(bytes(range(256)) * 4)

print("fixtures written to", OUT)
