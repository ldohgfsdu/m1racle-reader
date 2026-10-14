"""Generate repository-local Code Wiki references; validate links without network/builds.

Uses only Python's standard library. Kotlin extraction is lexical, not an AST or call graph.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from datetime import datetime, timezone, timedelta
import hashlib
from functools import lru_cache
import json
from pathlib import Path
import re
import subprocess
from urllib.parse import unquote, quote

REPO = Path(__file__).resolve().parents[3]
WIKI = REPO / "docs/code-wiki"
REF = WIKI / "reference"
BASE = "app/src/main/java/com/m1racle/reader/"
SOURCE_ROOTS = ["app/src/main", "app/src/production", "app/src/qa", "app/src/debug"]
TEST_ROOTS = ["app/src/test", "app/src/testQa", "app/src/testRelease", "app/src/androidTest"]
BENCHMARK = "benchmark/src"
ALL_ROOTS = SOURCE_ROOTS + TEST_ROOTS + [BENCHMARK]

# Curated API references used by the handwritten chapters. Validate names in code,
# not in comments. This supplements link checks; it does not verify prose semantics.
API_REFERENCES = {
    "data/repository/DocumentRepository.kt": ["observeRecentDocuments", "importDocument", "commitResolvedImport", "readDocumentText", "renameDocument", "exportDocument", "deleteById", "updateReadingPosition", "updateEpubReadingPosition", "updatePdfProgressSnapshotAtomic", "saveDocumentEdits", "recoverPdfSourceWrites", "openEpubBook"],
    "data/repository/ReadingRepository.kt": ["createBookmark", "addHighlight", "addNote", "removeHighlight", "removeNote", "updateNoteContent"],
    "data/repository/SearchRepository.kt": ["index", "search"],
    "identity/ContentIdentityResolver.kt": ["resolve", "findExistingDocument"],
    "share/ExternalOpenCoordinator.kt": ["onIncomingIntent", "confirmPending", "cancelPending", "onViewerOpened", "onViewerClosed", "ackNavigation", "clearEventHandledForNewDelivery"],
    "share/ExternalDocumentImporter.kt": ["sanitizeFileName"],
    "packageimport/ZipPackageImporter.kt": ["extract", "resolveEntrypoint", "readManifestEntrypoint", "extractFirstH1", "resolvePackageImage"],
    "markdown/MarkdownParser.kt": ["parse", "stripHtmlComments"],
    "markdown/MarkdownAssetResolver.kt": ["normalizeRelativeSegments", "percentDecodeOnce", "resolve"],
    "markdown/MarkdownLinkRouter.kt": ["classify"],
    "markdown/AnchorRegistry.kt": ["resolve"],
    "search/DocumentSearch.kt": ["search"],
    "ui/viewer/ViewerViewModel.kt": ["onSearchQueryChange", "goToNextMatch", "goToPreviousMatch", "saveReadingPosition"],
    "ui/viewer/ReadingPositionSaver.kt": ["save", "flushFinal"],
    "ui/viewer/ReaderGradient.kt": ["readerGradient"],
    "ui/viewer/ProgressiveChromeBlur.kt": ["apply"],
    "data/anchor/BookmarkTarget.kt": ["encode", "decode", "applyTo"],
    "data/anchor/DocumentAnchor.kt": ["normalized", "fromPixel"],
    "pdf/PdfPageRenderer.kt": ["open", "pageSizePts", "renderPage", "renderTile", "closeAsync"],
    "pdf/PdfPageBitmapCache.kt": ["putIfCurrent"],
    "pdf/ContinuousDocumentGeometry.kt": ["zoomAt", "zoomAndPanFromFocal", "restoreViewportSession", "remapStatePreservingViewportTop"],
    "pdf/PdfGestureTransformController.kt": ["beginGesture", "pointerFrame", "endGesture"],
    "pdf/FinalPdfProgressFlusher.kt": ["flush", "sealAndFlushFinal"],
    "ui/pdf/PdfViewerViewModel.kt": ["releaseForEditing", "commitViewportSession", "commitContinuousDocumentState", "beginExportNode", "beginExportCurrent", "runExportToUri", "onContinuousViewportChanged", "requestPredictiveTiles"],
    "pdf/edit/PdfBodyEditor.kt": ["inspect", "writeCopy"],
    "pdf/edit/PdfSourceWriteStore.kt": ["prepare", "replace", "restore", "recover"],
    "pdf/export/ChapterPackageExporter.kt": ["export"],
    "pdf/export/PdfAnnotatedExporter.kt": ["export"],
    "epub/EpubPackageImporter.kt": ["readMetadata", "open", "openCached", "parseExtracted", "resolvePackagedFile"],
    "epub/EpubXhtmlParser.kt": ["parse"],
    "epub/EpubReadingAnchors.kt": ["anchorAt", "restore", "resolve"],
    "epub/EpubRestoreSession.kt": ["anchorForSave"],
    "ui/epub/EpubPagination.kt": ["paginateEpub", "pageForText", "pageForPosition", "epubPageTurnLayer", "epubFitImage"],
    "ui/epub/EpubReaderViewModel.kt": ["setReadingMode", "saveReadingPosition", "flushReadingPosition"],
    "ui/material/GlassMaterial.kt": ["resolve", "glassMaterial"],
    "ui/material/DockRefraction.kt": ["dockRefraction"],
    "ui/material/GlassAuthority.kt": ["detect", "forSurface"],
    "documents/DocumentTools.kt": ["createPdf", "merge", "encrypt", "toMarkdown"],
    "documents/DocumentDiscovery.kt": ["scan"],
}

CLASS_RE = re.compile(r"^\s*(?:(?:public|internal|private|protected|data|sealed|enum|open|abstract|value|annotation|fun|actual|expect)\s+)*(class|interface|object)\s+([\w]+)", re.M)
FUN_RE = re.compile(r"^\s*(?:@[\w.]+(?:\([^\n]*?\))?\s+)*(?P<mods>(?:(?:public|internal|private|protected|override|open|final|abstract|suspend|inline|tailrec|operator|infix|external|actual|expect)\s+)*)fun\s+(?:<[^\n]+?>\s*)?(?:[\w?.<>,]+\.)?(?P<name>`[^`]+`|\w+)\s*\(", re.M)


@lru_cache(maxsize=None)
def read(path: Path) -> str:
    return path.read_text(encoding="utf-8-sig")


def code_only(text: str) -> str:
    # Preserve newlines/offsets for line references. Strings are deliberately not
    # evaluated; this is a declaration inventory with documented limitations.
    return re.sub(r"/\*[\s\S]*?\*/|//[^\n]*", lambda m: "".join("\n" if c == "\n" else " " for c in m[0]), text)


def kotlin_files(root: str) -> list[Path]:
    return sorted((REPO / root).rglob("*.kt"))


def relative(path: Path) -> str:
    return path.relative_to(REPO).as_posix()


def link(path: Path, label: str, line: int | None = None) -> str:
    url = "../../../" + quote(relative(path), safe="/.")
    return f"[{label}]({url}" + (f"#L{line}" if line else "") + ")"


def declarations(path: Path):
    text = code_only(read(path))
    classes = [(m[2], text.count("\n", 0, m.start(2)) + 1) for m in CLASS_RE.finditer(text)]
    functions = [(m["name"].strip("`"), text.count("\n", 0, m.start("name")) + 1)
                 for m in FUN_RE.finditer(text) if "private" not in m["mods"].split()]
    return classes, functions


def package(path: Path) -> str:
    found = re.search(r"^package\s+([\w.]+)", read(path), re.M)
    return found[1] if found else "(无 package)"


def write(name: str, lines: list[str]):
    REF.mkdir(parents=True, exist_ok=True)
    (REF / name).write_text("\n".join(lines) + "\n", encoding="utf-8")


def source_index():
    lines = ["# Kotlin 源码索引", "", "[返回 Wiki](../README.md)", "",
             "自动扫描全部 main/production/qa/debug 及 benchmark Kotlin 文件。声明包含嵌套与内部类型；函数列为词法识别的非 private 函数候选，含局部/内部类成员、override 和重载，不等同于公开 API。行号链接基于当前文件；复杂泛型或跨行声明可能需打开源码确认。", ""]
    for root in SOURCE_ROOTS + [BENCHMARK]:
        lines += [f"## `{root}`", ""]
        groups = defaultdict(list)
        for path in kotlin_files(root):
            groups[package(path)].append(path)
        for pkg, files in sorted(groups.items()):
            lines += [f"### `{pkg}`", "", "| 文件 | 行数 | 类型声明 | 非 private 函数定位 |", "|---|---:|---|---|"]
            for path in files:
                classes, funcs = declarations(path)
                row = [link(path, path.name), str(len(read(path).splitlines())),
                       " · ".join(link(path, name, line) for name, line in classes) or "—",
                       " · ".join(link(path, name + "()", line) for name, line in funcs) or "—"]
                lines.append("| " + " | ".join(row) + " |")
            lines.append("")
    write("source-index.md", lines)


def test_index():
    lines = ["# 测试源码索引", "", "[返回 Wiki](../README.md)", "",
             "列出全部测试与辅助 Kotlin 文件；数量按去注释代码中的 `@Test` 文本标记统计，不等于参数化展开后的 case 数、执行数或通过数。benchmark 单列为隔离测量入口。", ""]
    for root in TEST_ROOTS + [BENCHMARK]:
        files = kotlin_files(root)
        count = lambda p: len(re.findall(r"@Test\b", code_only(read(p))))
        lines += [f"## `{root}`", "", f"{len(files)} 个 Kotlin 文件，{sum(count(p) for p in files)} 个 `@Test` 标记。", ""]
        groups = defaultdict(list)
        for path in files:
            groups[package(path)].append(path)
        for pkg, paths in sorted(groups.items()):
            lines += [f"### `{pkg}`", "", "| 文件 | 类型声明 | @Test 标记数 |", "|---|---|---:|"]
            for path in paths:
                classes, _ = declarations(path)
                lines.append(f"| {link(path, path.name)} | " + " · ".join(link(path, n, l) for n, l in classes) + f" | {count(path)} |")
            lines.append("")
    write("test-index.md", lines)


def package_dependencies():
    edges = defaultdict(Counter)
    files = kotlin_files("app/src/main")
    pkgs = {package(path) for path in files}
    for path in files:
        origin = package(path)
        edges[origin]  # Also retain packages with no cross-package imports.
        for target in re.findall(r"^import\s+(com\.m1racle\.reader[\w.*]+)", code_only(read(path)), re.M):
            choices = [pkg for pkg in pkgs if target.startswith(pkg + ".")]
            if choices:
                destination = max(choices, key=len)
                if destination != origin:
                    edges[origin][destination] += 1
    lines = ["# 显式包依赖参考", "", "[返回 Wiki](../README.md)", "",
             "只统计 app/main 中显式内部 import，按最长已知 package 前缀归类。数字为 import 语句数，不是调用次数；不包含同包引用、完全限定名调用、回调、反射或外部库依赖。依赖可以形成环，本表不宣称严格分层。", "",
             "| 发起包 | 被导入包（语句数） |", "|---|---|"]
    for origin, destinations in sorted(edges.items()):
        dest = "<br>".join(f"`{k}` ({v})" for k, v in sorted(destinations.items())) or "无显式跨包 import"
        lines.append(f"| `{origin}` | {dest} |")
    write("package-dependencies.md", lines)


def schema_path() -> Path:
    src = read(REPO / (BASE + "data/local/AppDatabase.kt"))
    version = int(re.search(r"version\s*=\s*(\d+)", src)[1])
    return REPO / f"app/schemas/com.m1racle.reader.data.local.AppDatabase/{version}.json"


def database_schema():
    path = schema_path()
    db = json.loads(read(path))["database"]
    lines = [f"# Room v{db['version']} 数据库字典", "", "[返回 Wiki](../README.md)", "",
             f"自动来源：{link(path, path.name)}。共有 {len(db['entities'])} 张表；字段类型/空值/主键/索引/外键以导出 JSON 为准，业务解释见数据章节。", ""]
    for table in db["entities"]:
        pk = table["primaryKey"]
        lines += [f"## `{table['tableName']}`", "",
                  "主键：" + ", ".join(f"`{x}`" for x in pk["columnNames"]) + f"；autoGenerate={str(pk['autoGenerate']).lower()}。", "",
                  "| 字段 | SQLite 类型 | NOT NULL | 默认值 |", "|---|---|---|---|"]
        for field in table["fields"]:
            default = str(field.get("defaultValue", "—")).replace("|", "\\|")
            lines.append(f"| `{field['columnName']}` | {field['affinity']} | {'是' if field.get('notNull', False) else '否'} | `{default}` |")
        lines += ["", "索引："]
        for index in table.get("indices", []):
            lines += ["", f"- `{index['name']}`：{', '.join(index['columnNames'])}；unique={str(index['unique']).lower()}。"]
        if not table.get("indices"):
            lines += ["", "除主键外无导出索引。"]
        lines += ["", "外键："]
        for fk in table.get("foreignKeys", []):
            lines += ["", f"- `{', '.join(fk['columns'])}` → `{fk['table']}({', '.join(fk['referencedColumns'])})`；ON DELETE {fk['onDelete']}；ON UPDATE {fk['onUpdate']}。"]
        if not table.get("foreignKeys"):
            lines += ["", "无导出外键。"]
        lines.append("")
    write("database-schema.md", lines)


def input_fingerprint():
    inputs = set()
    for root in ALL_ROOTS:
        inputs.update((REPO / root).rglob("*.kt"))
        inputs.update((REPO / root).rglob("*.xml"))
    inputs.update((REPO / "app/schemas").rglob("*.json"))
    for name in ["settings.gradle.kts", "build.gradle.kts", "app/build.gradle.kts", "benchmark/build.gradle.kts", "app/version.properties", "gradle.properties", "gradle/wrapper/gradle-wrapper.properties", "app/proguard-rules.pro"]:
        inputs.add(REPO / name)
    inputs.update(path for path in (REPO / "scripts").iterdir() if path.is_file())
    for name in ["reader-env-local.ps1", "reader-build-local.ps1", "reader-emulator-local.ps1"]:
        path = REPO.parent / name
        if path.is_file():
            inputs.add(path)
    digest = hashlib.sha256()
    for path in sorted(inputs, key=lambda p: p.as_posix()):
        name = path.relative_to(REPO) if path.is_relative_to(REPO) else Path("..") / path.name
        digest.update(name.as_posix().encode() + b"\0" + hashlib.sha256(path.read_bytes()).digest())
    return len(inputs), digest.hexdigest()


def snapshot():
    count, fingerprint = input_fingerprint()
    version = read(REPO / "app/version.properties")
    code = re.search(r"^versionCode=(.+)$", version, re.M)[1]
    name = re.search(r"^versionName=(.+)$", version, re.M)[1]
    try:
        head = subprocess.check_output(["git", "rev-parse", "--short", "HEAD"], cwd=REPO, text=True).strip()
    except (OSError, subprocess.CalledProcessError):
        head = "不可用"
    now = datetime.now(timezone(timedelta(hours=8))).isoformat(timespec="seconds")
    lines = ["# 扫描基准与证据边界", "", "[返回 Wiki](../README.md)", "",
             f"- 生成时间：{now}（Asia/Shanghai）。", f"- 版本：{name.strip()} / {code.strip()}。", f"- Git HEAD：`{head}`；包含未提交工作树，HEAD 不是全部内容的快照。",
             f"- Room schema：{json.loads(read(schema_path()))['database']['version']}。",
             f"- 摘要覆盖 {count} 个输入：Kotlin/源集 XML、导出 schema、构建配置、脚本与存在的外围本机 helper。未读取签名秘密。",
             f"- 输入集合 SHA-256：`{fingerprint}`。", f"<!-- source-manifest-sha256: {fingerprint} -->", "",
             "| 源集 | Kotlin 文件数 | 文本行数 |", "|---|---:|---:|"]
    for root in ALL_ROOTS:
        files = kotlin_files(root)
        lines.append(f"| `{root}` | {len(files)} | {sum(len(read(p).splitlines()) for p in files):,} |")
    lines += ["", "本生成器只执行静态索引、链接/行号/API 名称/源摘要校验，不执行应用构建、JUnit、lint、安装或设备验收。应用运行验证另见 [稳定性核查](../../STABILIZATION_20261013.md)，不计入本文静态校验结果。", "",
              "静态校验结果见 [validation.json](validation.json)。该文件只描述文档引用校验，不代表功能通过。"]
    write("snapshot.md", lines)


def check():
    REF.mkdir(parents=True, exist_ok=True)
    report = REF / "validation.json"
    report.write_text('{"status": "checking"}\n', encoding="utf-8")
    errors = []
    count = 0
    source_links = 0
    files = sorted(WIKI.rglob("*.md")) + [REPO / "CODE_WIKI.md"]
    for path in files:
        # Avoid treating shell/code examples as actual Markdown links.
        body = re.sub(r"```[\s\S]*?```", "", read(path))
        for match in re.finditer(r"\[([^\]\n]*)\]\(([^)\n]+)\)", body):
            label = match[1]
            raw = match[2].strip().strip("<>")
            if re.match(r"^[a-zA-Z][\w+.-]*:", raw):
                continue
            url, _, anchor = raw.partition("#")
            target = (path.parent / unquote(url)).resolve() if url else path
            count += 1
            if not target.exists():
                errors.append(f"{relative(path)}: missing link {raw}")
                continue
            if target.suffix in {".kt", ".py", ".ps1", ".kts"}:
                source_links += 1
            if re.fullmatch(r"L\d+", anchor):
                line = int(anchor[1:])
                source_lines = read(target).splitlines()
                if not 1 <= line <= len(source_lines):
                    errors.append(f"{relative(path)}: invalid line {raw}")
                elif target.suffix == ".kt" and re.fullmatch(r"\w+(?:\(\))?", label):
                    symbol = label.removesuffix("()")
                    if not re.search(r"\b" + re.escape(symbol) + r"\b", source_lines[line - 1]):
                        errors.append(f"{relative(path)}: symbol {label} is not at {raw}")
    api_count = 0
    for rel, names in API_REFERENCES.items():
        text = code_only(read(REPO / (BASE + rel)))
        for name in names:
            api_count += 1
            if not re.search(r"\bfun\s+(?:<[^\n]+?>\s*)?(?:[\w?.<>,]+\.)?" + re.escape(name) + r"\s*\(", text):
                errors.append(f"missing API {rel}: {name}")
    _, fingerprint = input_fingerprint()
    snapshot_file = REF / "snapshot.md"
    marker = re.search(r"source-manifest-sha256: ([0-9a-f]{64})", read(snapshot_file)) if snapshot_file.is_file() else None
    if not marker or marker[1] != fingerprint:
        errors.append("Reference source fingerprint is stale; regenerate references and review prose.")
    result = {"status": "pass" if not errors else "fail", "checked_at": datetime.now(timezone(timedelta(hours=8))).isoformat(timespec="seconds"),
              "markdown_files": len(files), "relative_links": count, "source_links": source_links,
              "curated_api_names": api_count, "input_sha256": fingerprint,
              "scope": "Static documentation checks only; no build, tests, lint, install or device verification.", "errors": errors}
    REF.mkdir(parents=True, exist_ok=True)
    (REF / "validation.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if not errors else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Validate references without regenerating them")
    args = parser.parse_args()
    if args.check:
        return check()
    for generate in [source_index, test_index, package_dependencies, database_schema, snapshot]:
        generate()
    print("Generated five Markdown reference files in docs/code-wiki/reference.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
