"""Build Reader's outlined wordmark and Android adaptive foreground from brand sources."""
from pathlib import Path
import hashlib
import json
import xml.etree.ElementTree as ET
from PIL import ImageFont
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'branding/m1racle-reader/v1'
ANDROID_TILE = 72
ANDROID_SCALE = ANDROID_TILE / 256
ANDROID_INSET = (108 - ANDROID_TILE) / 2
NS = '{http://www.w3.org/2000/svg}'
mark = ET.parse(BASE / 'source/master-trace.svg').getroot()
paths = [p.attrib['d'] for p in mark.iter(NS + 'path')]
assert len(paths) == 1
font_path = BASE / 'fonts/Inter-Variable.ttf'
font = TTFont(font_path)
units = font['head'].unitsPerEm
axes = {a.axisTag: a.defaultValue for a in font['fvar'].axes}
axes.update(wght=500, opsz=32)
glyphs, cmap = font.getGlyphSet(location=axes), font.getBestCmap()
layout = ImageFont.truetype(str(font_path), units)
layout.set_variation_by_axes([axes[a.axisTag] for a in font['fvar'].axes])
size, baseline, label = 256 * .19, 256 * .82, 'Reader'
scale = size / units
width = layout.getlength(label) * scale
left = (256 - width) / 2
letters = []
for i, char in enumerate(label):
    x = (layout.getlength(label[:i + 1]) - layout.getlength(char)) * scale + left
    pen = SVGPathPen(glyphs)
    glyphs[cmap[ord(char)]].draw(TransformPen(pen, (scale, 0, 0, -scale, x, baseline)))
    letters.append(pen.getCommands())
master = '<g transform="translate(35.84 46.08) scale(0.205714285714) translate(-172 -399)"><path fill-rule="evenodd" d="' + paths[0] + '"/></g>'
word = ''.join('<path d="' + p + '"/>' for p in letters)
content = '<g fill="#181818">' + master + word + '</g>'
svg = '<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 256 256"><title>M1racle Reader</title><rect width="256" height="256" rx="51.2" fill="#ffffff"/>' + content + '</svg>'
(BASE / 'svg/icon-512-day.svg').write_text(svg, encoding='utf-8')
circle = '<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" viewBox="18 18 72 72"><defs><clipPath id="mask"><circle cx="54" cy="54" r="36"/></clipPath></defs><g clip-path="url(#mask)"><rect x="18" y="18" width="72" height="72" fill="white"/>' + f'<g transform="translate({ANDROID_INSET} {ANDROID_INSET}) scale({ANDROID_SCALE})">' + content + '</g></g></svg>'
(BASE / 'svg/adaptive-circle-preview.svg').write_text(circle, encoding='utf-8')
path = lambda d, rule='nonZero': '<path android:fillColor="#181818" android:fillType="' + rule + '" android:pathData="' + d + '"/>'
vector = '<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n' + f'<group android:translateX="{ANDROID_INSET}" android:translateY="{ANDROID_INSET}" android:scaleX="{ANDROID_SCALE}" android:scaleY="{ANDROID_SCALE}">\n' + '<group android:translateX="35.84" android:translateY="46.08" android:scaleX="0.205714285714" android:scaleY="0.205714285714"><group android:translateX="-172" android:translateY="-399">' + path(paths[0], "evenOdd") + '</group></group>\n' + '\n'.join(path(p) for p in letters) + '\n</group>\n</vector>\n'
(ROOT / 'app/src/main/res/drawable/ic_reader_mark.xml').write_text(vector, encoding='utf-8')
provenance = {'product': 'M1racle Reader', 'master': 'User-supplied original, identical SHA256 to formal Note source; existing vector trace reused without redrawing', 'masterSha256': hashlib.sha256((BASE / 'source/m1racle-master-original.png').read_bytes()).hexdigest(), 'traceSha256': hashlib.sha256((BASE / 'source/master-trace.svg').read_bytes()).hexdigest(), 'fontSha256': hashlib.sha256(font_path.read_bytes()).hexdigest(), 'fontAxes': axes, 'lettering': 'Actual Inter glyph outlines; Medium 500, opsz 32', 'labelWidthAt256': width, 'android': {'viewport': 108, 'background': '#FFFFFF', 'artboardScale': ANDROID_SCALE, 'artboardTranslation': [ANDROID_INSET, ANDROID_INSET], 'artboardTileDp': ANDROID_TILE, 'visibleMask': [18, 18, 90, 90]}, 'generator': 'scripts/branding/build_reader_icon.py'}
(BASE / 'source/provenance.json').write_text(json.dumps(provenance, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(provenance, ensure_ascii=False, indent=2))
