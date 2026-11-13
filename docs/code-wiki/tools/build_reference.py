"""Generate current Code Wiki references; validate links without network/builds.

Uses only Python's standard library. Kotlin extraction is lexical, not an AST or call graph.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
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
APP_SOURCE_ROOTS = [
    "app/src/main",
    "app/src/production",
    "app/src/qa",
    "app/src/debug",
]
APP_TEST_ROOTS = [
    "app/src/test",
    "app/src/testQa",
    "app/src/testRelease",
    "app/src/androidTest",
]
CORE_SOURCE_ROOTS = [
    "core/model/src/main",
    "core/database/src/main",
    "core/pdf/src/main",
]
CORE_TEST_ROOTS = [
    "core/model/src/test",
    "core/database/src/test",
    "core/pdf/src/test",
]
TOOL_SOURCE_ROOTS = ["pdf-tool/src/main"]
TOOL_TEST_ROOTS = ["pdf-tool/src/test"]
BENCHMARK_ROOTS = ["benchmark/src"]
SOURCE_ROOTS = (
    APP_SOURCE_ROOTS
    + CORE_SOURCE_ROOTS
    + TOOL_SOURCE_ROOTS
    + BENCHMARK_ROOTS
)
TEST_ROOTS = (
    APP_TEST_ROOTS
    + CORE_TEST_ROOTS
    + TOOL_TEST_ROOTS
    + BENCHMARK_ROOTS
)
ALL_ROOTS = SOURCE_ROOTS + TEST_ROOTS

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
    "ui/pdf/PdfViewerViewModel.kt": ["releaseForEditing", "commitViewportSession", "commitContinuousDocumentState", "beginExportNode", "beginExportCurrent", "runExportToUri", "onContinuousViewportChanged", "requestPredictiveTiles"],
    "pdf/edit/PdfBodyEditor.kt": ["inspect", "writeCopy"],
    "data/repository/PdfSourceWriteStore.kt": ["prepare", "replace", "restore", "recover"],
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

CORE_API_REFERENCES = {
    "core/model/src/main/java/com/m1racle/reader/core/model/DocumentAnchor.kt": [
        "normalized",
        "fromPixel",
    ],
    "core/pdf/src/main/java/com/m1racle/reader/pdf/PdfPageRenderer.kt": [
        "open",
        "pageSizePts",
        "renderPage",
        "renderTile",
        "closeAsync",
    ],
    "core/pdf/src/main/java/com/m1racle/reader/pdf/PdfPageBitmapCache.kt": [
        "putIfCurrent",
    ],
    "core/pdf/src/main/java/com/m1racle/reader/pdf/ContinuousDocumentGeometry.kt": [
        "zoomAt",
        "zoomAndPanFromFocal",
        "restoreViewportSession",
        "remapStatePreservingViewportTop",
    ],
    "core/pdf/src/main/java/com/m1racle/reader/pdf/PdfGestureTransformController.kt": [
        "beginGesture",
        "pointerFrame",
        "endGesture",
    ],
    "app/src/main/java/com/m1racle/reader/pdf/FinalPdfProgressFlusher.kt": [
        "flush",
        "sealAndFlushFinal",
    ],
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
    lines = [
        "# Kotlin 源码索引",
        "",
        "[返回 Wiki](../README.md)",
        "",
        f"生成时间：{now()}（Asia/Shanghai）。本索引直接扫描当前仓库的生产源码与独立工具源码，覆盖 `:app`、`:core:model`、`:core:database`、`:core:pdf`、`:pdf-tool` 和 `:benchmark`。",
        "",
        "声明包含嵌套与内部类型；函数列为词法识别的非 private 函数候选，含局部/内部类成员、override 和重载，不等同于公开 API。行号链接基于生成时的当前文件；复杂泛型或跨行声明可能需打开源码确认。",
        "",
    ]
    for root in SOURCE_ROOTS:
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
    lines += [
        "## 历史移除源码（非当前索引）",
        "",
        "以下路径出现在 74 快照索引中，但当前仓库已无同名源码。它们只作为历史代码路径保留，不设 Markdown 链接，也不属于当前编译输入：",
        "",
        "- `app/src/main/java/com/m1racle/reader/pdf/PdfChromePrefs.kt`",
        "- `app/src/main/java/com/m1racle/reader/ui/common/StatusBarProtection.kt`",
        "",
    ]
    write("source-index.md", lines)


def test_index():
    lines = [
        "# 测试源码索引",
        "",
        "[返回 Wiki](../README.md)",
        "",
        f"生成时间：{now()}（Asia/Shanghai）。列出当前仓库 app、core、pdf-tool 与 benchmark 的测试及辅助 Kotlin 文件。",
        "",
        "数量按去注释代码中的 `@Test` 文本标记统计，不等于参数化展开后的 case 数、执行数或通过数。benchmark 单列为隔离测量入口。",
        "",
    ]
    for root in TEST_ROOTS:
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
    files = [
        path
        for root in APP_SOURCE_ROOTS + CORE_SOURCE_ROOTS + TOOL_SOURCE_ROOTS
        for path in kotlin_files(root)
    ]
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
             f"生成时间：{now()}（Asia/Shanghai）。扫描 app、core 与 pdf-tool 的生产 Kotlin 源。",
             "",
             "只统计显式内部 import，按最长已知 package 前缀归类。数字为 import 语句数，不是调用次数；不包含同包引用、完全限定名调用、回调、反射或外部库依赖。依赖可以形成环，本表不宣称严格分层。", "",
             "| 发起包 | 被导入包（语句数） |", "|---|---|"]
    for origin, destinations in sorted(edges.items()):
        dest = "<br>".join(f"`{k}` ({v})" for k, v in sorted(destinations.items())) or "无显式跨包 import"
        lines.append(f"| `{origin}` | {dest} |")
    write("package-dependencies.md", lines)


def schema_path() -> Path:
    src = read(REPO / "core/database/src/main/java/com/m1racle/reader/data/local/AppDatabase.kt")
    version = int(re.search(r"version\s*=\s*(\d+)", src)[1])
    return REPO / f"core/database/schemas/com.m1racle.reader.data.local.AppDatabase/{version}.json"


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
    inputs.update((REPO / "core/database/schemas").rglob("*.json"))
    for name in [
        "settings.gradle.kts",
        "build.gradle.kts",
        "app/build.gradle.kts",
        "benchmark/build.gradle.kts",
        "core/model/build.gradle.kts",
        "core/database/build.gradle.kts",
        "core/pdf/build.gradle.kts",
        "pdf-tool/build.gradle.kts",
        "app/version.properties",
        "gradle.properties",
        "gradle/wrapper/gradle-wrapper.properties",
        "app/proguard-rules.pro",
        "pdf-tool/proguard-rules.pro",
    ]:
        inputs.add(REPO / name)
    inputs.update(
        path
        for path in (REPO / "scripts").iterdir()
        if path.is_file()
    )
    digest = hashlib.sha256()
    for path in sorted(inputs, key=lambda p: p.as_posix()):
        name = path.relative_to(REPO)
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
    generated_at = now()
    lines = ["# 扫描基准与证据边界", "", "[返回 Wiki](../README.md)", "",
             f"- 生成时间：{generated_at}（Asia/Shanghai）。", f"- 版本：{name.strip()} / {code.strip()}。", f"- Git HEAD：`{head}`；包含未提交工作树，HEAD 不是全部内容的快照。",
             f"- Room schema：{json.loads(read(schema_path()))['database']['version']}。",
             f"- 当前仓库根：`{REPO.as_posix()}`。", f"- 摘要覆盖 {count} 个仓库内输入：当前 app/core/pdf-tool/benchmark 的 Kotlin 与源集 XML、导出 schema、构建配置与脚本。仓库外宿主 helper 只作为普通路径说明，不计入源码摘要。未读取签名秘密。",
             f"- 输入集合 SHA-256：`{fingerprint}`。", f"<!-- source-manifest-sha256: {fingerprint} -->", "",
             "| 源集 | Kotlin 文件数 | 文本行数 |", "|---|---:|---:|"]
    for root in ALL_ROOTS:
        files = kotlin_files(root)
        lines.append(f"| `{root}` | {len(files)} | {sum(len(read(p).splitlines()) for p in files):,} |")
    lines += ["", "本生成器只执行静态索引、链接/行号/API 名称/源摘要校验，不执行应用构建、JUnit、lint、安装或设备验收。", "",
              "当前校验记录见 [validation-current.json](validation-current.json)。历史 [validation.json](validation.json) 原样保留，仅描述 2026-10-14 的 74 快照，不代表当前仓库或本轮结果。"]
    write("snapshot.md", lines)


def now() -> str:
    return datetime.now(timezone(timedelta(hours=8))).isoformat(timespec="seconds")


def check():
    REF.mkdir(parents=True, exist_ok=True)
    report = REF / "validation-current.json"
    report.write_text('{"status": "checking"}\n', encoding="utf-8")
    errors = []
    count = 0
    source_links = 0
    outside_repository_links = []
    forbidden_source_targets = []
    files = sorted(WIKI.rglob("*.md"))
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
            if not target.is_relative_to(REPO):
                outside_repository_links.append(
                    {
                        "from": relative(path),
                        "target": target.as_posix(),
                    }
                )
            else:
                repository_target = target.relative_to(REPO).as_posix()
                if repository_target.startswith(("output/", "reader/")) or (
                    repository_target.startswith("reader-audit-fixes-")
                ):
                    forbidden_source_targets.append(
                        {
                            "from": relative(path),
                            "target": raw,
                        }
                    )
            if target.suffix in {".kt", ".py", ".ps1", ".kts"}:
                source_links += 1
            if re.fullmatch(r"L\d+", anchor):
                line = int(anchor[1:])
                source_lines = read(target).splitlines()
                if not 1 <= line <= len(source_lines):
                    errors.append(f"{relative(path)}: invalid line {raw}")
                elif target.suffix == ".kt" and re.fullmatch(r"\w+(?:\(\))?", label):
                    symbol = label.removesuffix("()")
                    declaration_window = "\n".join(
                        source_lines[max(0, line - 2):min(len(source_lines), line + 2)]
                    )
                    if not re.search(
                        r"\b" + re.escape(symbol) + r"\b",
                        declaration_window,
                    ):
                        errors.append(f"{relative(path)}: symbol {label} is not at {raw}")
    api_count = 0
    app_base = REPO / "app/src/main/java/com/m1racle/reader"
    for rel, names in API_REFERENCES.items():
        text = code_only(read(app_base / rel))
        for name in names:
            api_count += 1
            if not re.search(r"\bfun\s+(?:<[^\n]+?>\s*)?(?:[\w?.<>,]+\.)?" + re.escape(name) + r"\s*\(", text):
                errors.append(f"missing API {rel}: {name}")
    for rel, names in CORE_API_REFERENCES.items():
        text = code_only(read(REPO / rel))
        for name in names:
            api_count += 1
            if not re.search(r"\bfun\s+(?:<[^\n]+?>\s*)?(?:[\w?.<>,]+\.)?" + re.escape(name) + r"\s*\(", text):
                errors.append(f"missing API {rel}: {name}")
    _, fingerprint = input_fingerprint()
    snapshot_file = REF / "snapshot.md"
    marker = re.search(r"source-manifest-sha256: ([0-9a-f]{64})", read(snapshot_file)) if snapshot_file.is_file() else None
    reference_stale = not marker or marker[1] != fingerprint
    if forbidden_source_targets:
        errors.append(
            "Current Wiki contains links to output/, old worktrees or the "
            "retired reader/ root."
        )
    link_checks_pass = not any(
        ": missing link " in error
        or "invalid line " in error
        or "symbol " in error
        or error.startswith("Current Wiki contains links")
        for error in errors
    )
    api_checks_pass = not any(
        error.startswith("missing API ")
        for error in errors
    )
    try:
        head = subprocess.check_output(
            ["git", "rev-parse", "--short", "HEAD"],
            cwd=REPO,
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        head = "unavailable"
    result = {
        "status": "pass" if link_checks_pass and api_checks_pass else "fail",
        "checks": {
            "links": "pass" if link_checks_pass else "fail",
            "api_names": "pass" if api_checks_pass else "fail",
            "reference_freshness": "stale" if reference_stale else "pass",
        },
        "checked_at": now(),
        "repository_root": REPO.as_posix(),
        "git_head": head,
        "markdown_files": len(files),
        "relative_links": count,
        "source_links": source_links,
        "missing_links": sum(
            ": missing link " in error
            for error in errors
        ),
        "outside_repository_links": outside_repository_links,
        "forbidden_source_targets": forbidden_source_targets,
        "curated_api_names": api_count,
        "input_sha256": fingerprint,
        "reference_fingerprint_stale": reference_stale,
        "historical_validation_sha256": hashlib.sha256(
            (REF / "validation.json").read_bytes()
        ).hexdigest(),
        "repaired_baseline": {
            "recorded_at": "2026-11-13",
            "missing_relative_links": 872,
            "source_index_missing": 755,
            "test_index_missing": 106,
            "handwritten_missing": 11,
            "unique_migrated_source_files": 59,
            "unique_missing_targets": 112,
            "historical_removed_sources": [
                "app/src/main/java/com/m1racle/reader/pdf/PdfChromePrefs.kt",
                "app/src/main/java/com/m1racle/reader/ui/common/StatusBarProtection.kt",
            ],
        },
        "scope": (
            "Static current-repository Code Wiki checks only; no build, tests, "
            "lint, install or device verification. The historical "
            "validation.json is not reused."
        ),
        "boundary": (
            "Current links target the checked working tree. Historical "
            "validation.json remains an unmodified 74-snapshot record. "
            "output/, old worktrees and prior APK artifacts are not current "
            "source targets. The outside-repository host helper "
            "reader-env-local.ps1 is documented as plain code text and is "
            "not a source link."
        ),
        "errors": errors,
    }
    report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
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
    print("Generated five current Markdown reference files in docs/code-wiki/reference.")
    return check()


if __name__ == "__main__":
    raise SystemExit(main())
