#!/usr/bin/env python3
"""Generate fixed optical acceptance inputs, outside app assets.

Host-only dependencies: reportlab==5.0.1, pillow==12.3.0.
Example: python generate-r36-optical-fixtures.py --font /system/fonts/MiSansVF.ttf \
    --output /storage/emulated/0/inbox/M1racleReader/r36-2
The font is embedded as a subset in the local fixture, never shipped in the APK.
"""
import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageDraw
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas

PAGE = (595, 842)
TEXT = ("清晨的光线落在书页上，窗外的树影缓缓移动。阅读时请观察文字与空白之间的关系，"
        "远处的山峰和近处的河流呈现不同层次。每一段内容都包含具体信息，"
        "玻璃下面可以保留明暗变化，但不应再辨认完整汉字、数字或表格内容。")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def paragraphs(c, top, size, leading, width=48):
    c.setFont("Fixture", size)
    c.setFillColorRGB(0, 0, 0)
    words = TEXT * 100
    rows = int((top - 24) / leading)
    for row in range(rows):
        text = words[row * width:(row + 1) * width]
        c.drawString(32, top - row * leading, text)


def image_fixture(color):
    image = Image.new("RGB", (800, 960), "white")
    pixels = image.load()
    for y in range(960):
        for x in range(800):
            if color:
                bands = [(161, 185, 199), (191, 169, 146), (154, 180, 160), (187, 157, 164)]
                base = bands[min(x // 200, 3)]
                shade = .45 + .55 * y / 959
                pixels[x, y] = tuple(round(v * shade) for v in base)
            else:
                value = round(255 * x / 799)
                pixels[x, y] = (value, value, value)
    draw = ImageDraw.Draw(image)
    for i in range(12):
        x = 20 + i * 65
        value = round(i * 255 / 11)
        draw.rectangle((x, 80, x + 38, 430), fill=(value,) * 3)
    draw.ellipse((170, 510, 630, 920), fill=(210, 100, 55) if color else (70, 70, 70))
    for y in range(460, 960, 12):
        draw.line((0, y, 800, y), fill=(20, 20, 20), width=2)
    return image


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--font", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    pdfmetrics.registerFont(TTFont("Fixture", str(args.font)))
    output = args.output / "R36-2-optical-A-E.pdf"
    c = canvas.Canvas(str(output), pagesize=PAGE, invariant=1, pageCompression=1)
    c.setTitle("R36.2 optical fixtures A-E")
    c.setAuthor("M1racle Reader acceptance fixture")
    for name in "ABCDE":
        c.setFillColorRGB(1, 1, 1)
        c.rect(0, 0, *PAGE, fill=1, stroke=0)
        c.setFillColorRGB(0, 0, 0)
        c.setFont("Fixture", 10)
        c.drawString(32, 823, "R36.2  Fixture " + name + "  595 x 842 pt")
        if name == "A":
            paragraphs(c, 799, 10.5, 15, 50)
        elif name == "B":
            c.setFont("Fixture", 26)
            c.drawString(32, 780, "光线、页面与阅读的层次")
            paragraphs(c, 735, 14, 22, 37)
        elif name == "C":
            c.setFont("Fixture", 11)
            for row in range(32):
                y = 799 - row * 24
                c.setStrokeColorRGB(.18, .18, .18)
                c.setLineWidth(.5)
                c.line(32, y, 563, y)
                if row < 31:
                    for column, label in enumerate(("章节", "实验参数", "测量记录", "阅读观察")):
                        text = label if row == 0 else f"{label} {row:02d}-{column + 1}"
                        c.drawString(38 + column * 132.75, y - 17, text)
            for column in range(5):
                x = 32 + column * 132.75
                c.line(x, 55, x, 799)
        else:
            image = image_fixture(name == "E")
            image.save(args.output / f"fixture-{name}.png")
            c.drawImage(ImageReader(image), 32, 35, width=531, height=755)
            c.setFont("Fixture", 13)
            for row in range(7):
                c.setFillColorRGB(1 if row % 2 else 0, 1 if row % 2 else 0, 1 if row % 2 else 0)
                c.drawString(45, 100 + row * 95, "灰阶与色块边界  0123456789  明暗层次")
        c.showPage()
    c.save()
    markdown = "# R36.2 光学验收\n\n" + "\n\n".join(TEXT for _ in range(12))
    markdown += "\n\n|章节|实验参数|测量记录|\n|---|---|---|\n"
    markdown += "\n".join(f"|第{i}章|正文与玻璃|{i * 17}|" for i in range(1, 18))
    markdown += "\n\n![灰阶](fixture-D.png)\n\n![彩色](fixture-E.png)\n"
    (args.output / "R36-2-optical.md").write_text(markdown, encoding="utf-8")
    manifest = {
        "page_size_pt": PAGE, "page_count": 5,
        "pages": {"A": "dense Chinese 10.5pt", "B": "title 26pt / body 14pt", "C": "table 11pt / rule .5pt", "D": "grayscale", "E": "color"},
        "font_sha256": digest(args.font),
        "files": {p.name: digest(p) for p in sorted(args.output.iterdir()) if p.name != "manifest.json" and p.is_file()},
        "device_acceptance": "NOT_RUN; lock viewport, theme, zoom, scroll position and overlay state for each pair",
    }
    (args.output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    print(output)
    print("SHA256", digest(output))


if __name__ == "__main__":
    main()
