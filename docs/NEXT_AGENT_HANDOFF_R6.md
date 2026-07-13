# MarkLeaf 下一位 Agent 技术交接手册（R6）

**交接任务：** `MARKLEAF_NEXT_AGENT_HANDOFF_R6`  
**当前日期：** 2026-07-11  
**下一 active object：** `R6_2_ZOOM_SESSION_AND_TARGET_HIT_FIX`  
**原则：** 本文记录真实代码和真实证据。`BUILD PASS`、单元测试通过、代码看起来正确，都不等于真机体验通过。

---

# 1. 项目概览

## 1.1 产品定位

MarkLeaf（墨阅）是 Android 本地优先阅读器。仓库的原始 MVP 定位是本地 Markdown 阅读：无账号、无广告、无统计 SDK、无云同步、无联网依赖；`AndroidManifest.xml` 当前仍明确没有 `INTERNET` 权限。

当前脏工作树在 Markdown MVP 之上增加了较完整的 PDF 教材阅读工作台，包括本地 PDF 位图阅读、连续/左右阅读模式、印刷页映射、PDF 内置目录导入、教材目录编目、缩略图和章节包导出。这些 PDF 能力尚未形成干净提交，当前核心工作不是继续扩功能，而是先把连续模式缩放和页面身份稳定性真机收口。

## 1.2 技术栈

- Kotlin 2.0.21、JVM 17
- Android Gradle Plugin 8.7.3、Gradle wrapper 8.11.1
- Jetpack Compose + Material 3（Compose BOM 2024.12.01）
- Navigation Compose
- Room 2.6.1 + KSP
- DataStore Preferences
- Coroutines / Flow
- Android SAF、`PdfRenderer`
- `pdfbox-android` 2.0.27.0：只用于 PDF 元数据/目录和章节 PDF 处理，不替换页面位图渲染器
- `compileSdk/targetSdk=35`，`minSdk=31`

## 1.3 主要模块

| 模块 | 真实入口/目录 | 说明 |
|---|---|---|
| App/导航 | `MainActivity.kt`、`ui/navigation/NavGraph.kt` | 根主题、外部 intent、主页/Markdown/PDF 路由 |
| Markdown 阅读 | `ui/viewer/`、`markdown/`、`highlight/` | 块解析、inline、搜索、TOC、代码、表格 |
| 本地图片 | `image/LocalImageLoader.kt` | asset/content/file/package-relative 图片；禁用网络图片 |
| ZIP 教材包 | `packageimport/ZipPackageImporter.kt` | 安全解压、入口 Markdown、相对资源 |
| PDF Viewer UI | `ui/pdf/` | Reader chrome、连续/左右模式、缩略图、目录、导出对话框 |
| PDF 核心 | `pdf/` | Renderer、缓存、质量、viewport policy、Zoom session、页码/目录纯逻辑 |
| PDF/目录持久化 | `data/local/`、`DocumentRepository.kt` | 文档、印刷页锚点、教材目录 Room 数据 |
| 分享导入 | `share/` | ACTION_VIEW/ACTION_SEND 单文件进入 app private inbox |

## 1.4 PDF、Markdown 包、教材目录和页码映射的关系

- `DocumentEntity.type` 区分普通 Markdown、PDF、`MARKDOWN_PACKAGE`。
- PDF 页面由 Android `PdfRenderer` 本地渲染成 `ImageBitmap`；PDFBox 不参与页面显示。
- PDF 内置 bookmark 经 `PdfEmbeddedOutlineParser` 解析成 draft，再由 importer/repository 写入统一的 `TextbookOutlineNodeEntity` 表。
- 教材目录节点绑定 0-based inclusive PDF 页范围；点击节点最终调用 `PdfViewerViewModel.goToPage(node.startPageIndex)`。
- 印刷页不是目录节点字段。它由 `PdfPrintPageAnchorEntity` 稀疏锚点 + `PrintPageSection` 分段规则独立解析。
- 章节导出按教材目录节点决定页范围，同时把印刷页和 outline metadata 写入 `medlearn-source-chapter` ZIP。
- Markdown ZIP 包是另一路本地阅读输入；解压后入口 Markdown 仍走 Markdown reader，并用 package root 解析相对图片。

## 1.5 与未来 Medlearn 的关系

MarkLeaf 当前只负责本地可信阅读、教材目录编目和可验证的 source chapter 包。`medlearn-source-chapter` 可以作为未来 Medlearn 的上游材料，但 AI、OCR、知识点、云端处理、账号和同步均不属于当前 MarkLeaf active scope。不要借此扩大 R6.2。

---

# 2. 启动与工作环境

## 2.1 固定位置

- 项目根目录：`/root/code/MarkLeaf`
- Android inbox：`/storage/emulated/0/inbox/MarkLeaf/`
- Android outbox：`/storage/emulated/0/outbox/MarkLeaf/`
- Debug APK 构建产物：`app/build/outputs/apk/debug/app-debug.apk`
- 当前 arm64 aapt2 wrapper：`/root/code/MarkLeaf/tools/aapt2`

## 2.2 Termux / PRoot 结构

当前 shell 在 PRoot Ubuntu 的 `/root/code/MarkLeaf`，Android SDK 实体可位于 Termux home。仓库 `tools/aapt2` 默认调用：

```text
/data/data/com.termux/files/home/Android/Sdk/build-tools/36.0.0/aapt2
```

arm64 时 wrapper 优先通过 `qemu-x86_64` 运行 SDK 的 x86_64 aapt2。`gradle.properties` 当前包含绝对路径：

```properties
android.aapt2FromMavenOverride=/root/code/MarkLeaf/tools/aapt2
```

在普通 x86_64 Android Studio 环境不要盲目沿用这个路径；先确认本机 SDK 和架构。

## 2.3 bwrap / apply_patch 已知故障

本轮及前序开发多次遇到：

```text
bwrap: fchdir to oldroot: No such file or directory
```

它会随机影响 `rg`、`ls`、`rm`、普通 workspace write，甚至 `apply_patch` 的读文件阶段。已观察到的直接原因是 Codex sandbox/bwrap 的 old-root 工作目录句柄失效；这是执行环境故障，不是 Gradle 或项目源码错误。不能把它误判为文件不存在，也不要用 Python、`cat > file`、heredoc、`sed -i` 等方式绕过正常编辑约束。

推荐做法：

1. 先用只读 `pwd`、`sed -n` 验证路径仍存在。
2. 如果重要命令因同一 bwrap 错误失败，按工具规范申请 scoped escalation 后重试。
3. 外部 `/storage/emulated/0` 的读写通常需要明确审批。
4. 编辑仍优先使用 `apply_patch`；大型机械改写也必须先确认只影响目标范围。
5. 环境问题持续时停止修改并向用户报告，不要扩大权限或清理环境。

## 2.4 推荐权限与最小写入探针

- 启动时优先使用 workspace-write；只读审计不需要额外写权限。
- 只在 bwrap 失败、Gradle build output、或 Android inbox/outbox 访问确实需要时申请 scoped escalation。
- 不请求宽泛 shell、Python 或破坏性前缀。
- 用户确认进入修改阶段后，可用 `apply_patch` 在仓库根创建一个唯一命名的小探针（例如 `.codex_write_probe`），立即再用 `apply_patch` 删除。探针失败就停止；不要改 production 文件来测试权限。

## 2.5 命令输出注意事项

- Gradle 首次启动可能只返回 session id 或暂时无输出；必须持续 poll 直到明确 `BUILD SUCCESSFUL`/`BUILD FAILED`，不能把安静输出当成功。
- 组合命令中任一 `sed` 找不到文件会阻断后续 `&&`/shell 链。此项目没有 `gradle/libs.versions.toml`，依赖直接写在 `build.gradle.kts`。
- 长输出可能被截断；对关键文件分段 `sed -n`，对 symbol 用 `rg -n`。
- 稳定 CI 式验证优先：

```bash
./gradlew :app:lintDebug --no-daemon --console=plain
./gradlew :app:assembleDebug --no-daemon --console=plain
```

单测也可加 `--no-daemon --console=plain`。不要擅自执行任何 usage reset、额度重置或与项目任务无关的环境重置。

## 2.6 APK 与签名注意

Debug/本地 acceptance APK 由 AGP 正常签名和 zipalign。`app/build.gradle.kts` 明确警告：不要再用 `jarsigner` 重签，否则可能破坏 16 KB page-aligned 未压缩 native `.so`，导致 `INSTALL_FAILED_INVALID_APK`。

---

# 3. 仓库当前状态

## 3.1 分支和最近提交

- 分支：`master`
- 最近 8 个 commit：

```text
4ce2822 chore: prepare v0.1.0-alpha release notes
fa33b97 docs: finalize MVP release notes
d7f1e8e feat: add local markdown image support
b776e63 feat: add markdown table support
b6cd155 feat: add lightweight code syntax highlighting
0cb1d2e feat: add in-document search
5a6c200 feat: enhance code block copy and toolbar
8d57b64 feat: add reader display settings
```

## 3.2 tracked diff

交接读取时 `git diff --stat`：

```text
38 files changed, 3039 insertions(+), 605 deletions(-)
```

这只统计 tracked diff；整个 PDF 目录和大量测试是 untracked，不在上述数字内。

## 3.3 `git status --short` 快照

```text
 M AGENTS.md
 M app/build.gradle.kts
 M app/src/main/AndroidManifest.xml
 M app/src/main/java/com/markleaf/app/MainActivity.kt
 M app/src/main/java/com/markleaf/app/MarkLeafApplication.kt
 M app/src/main/java/com/markleaf/app/data/local/AppDatabase.kt
 M app/src/main/java/com/markleaf/app/data/local/DocumentDao.kt
 M app/src/main/java/com/markleaf/app/data/local/DocumentEntity.kt
 M app/src/main/java/com/markleaf/app/data/repository/DocumentRepository.kt
 M app/src/main/java/com/markleaf/app/data/settings/ReaderSettings.kt
 M app/src/main/java/com/markleaf/app/highlight/CodeHighlighter.kt
 M app/src/main/java/com/markleaf/app/highlight/HighlightTheme.kt
 M app/src/main/java/com/markleaf/app/image/LocalImageLoader.kt
 M app/src/main/java/com/markleaf/app/markdown/InlineMarkdownParser.kt
 M app/src/main/java/com/markleaf/app/markdown/MarkdownBlock.kt
 M app/src/main/java/com/markleaf/app/markdown/MarkdownParser.kt
 M app/src/main/java/com/markleaf/app/ui/home/HomeScreen.kt
 M app/src/main/java/com/markleaf/app/ui/navigation/NavGraph.kt
 M app/src/main/java/com/markleaf/app/ui/theme/Color.kt
 M app/src/main/java/com/markleaf/app/ui/theme/Theme.kt
 M app/src/main/java/com/markleaf/app/ui/theme/Type.kt
 M app/src/main/java/com/markleaf/app/ui/viewer/ViewerScreen.kt
 M app/src/main/java/com/markleaf/app/ui/viewer/components/CodeBlockView.kt
 M app/src/main/java/com/markleaf/app/ui/viewer/components/HeadingBlockView.kt
 M app/src/main/java/com/markleaf/app/ui/viewer/components/ImageBlockView.kt
 M app/src/main/java/com/markleaf/app/ui/viewer/components/InlineMarkdownText.kt
 M app/src/main/java/com/markleaf/app/ui/viewer/components/ListBlockView.kt
 M app/src/main/java/com/markleaf/app/ui/viewer/components/MarkdownBlockView.kt
 M app/src/main/java/com/markleaf/app/ui/viewer/components/ParagraphBlockView.kt
 M app/src/main/java/com/markleaf/app/ui/viewer/components/QuoteBlockView.kt
 M app/src/main/java/com/markleaf/app/ui/viewer/components/ReaderSettingsSheet.kt
 M app/src/main/java/com/markleaf/app/ui/viewer/components/ReaderTypography.kt
 M app/src/main/java/com/markleaf/app/ui/viewer/components/SearchHighlightStyle.kt
 M app/src/main/java/com/markleaf/app/ui/viewer/components/TableBlockView.kt
 M app/src/main/java/com/markleaf/app/ui/viewer/components/TocDrawerContent.kt
 M app/src/main/res/values/strings.xml
 D dist/markleaf-v0.1.0-alpha-debug.apk.sha256
 M settings.gradle.kts
?? .kotlin/
?? app/src/main/java/com/markleaf/app/data/local/DocumentType.kt
?? app/src/main/java/com/markleaf/app/data/local/OutlineNodeSource.kt
?? app/src/main/java/com/markleaf/app/data/local/PdfPrintPageAnchorDao.kt
?? app/src/main/java/com/markleaf/app/data/local/PdfPrintPageAnchorEntity.kt
?? app/src/main/java/com/markleaf/app/data/local/TextbookOutlineNodeDao.kt
?? app/src/main/java/com/markleaf/app/data/local/TextbookOutlineNodeEntity.kt
?? app/src/main/java/com/markleaf/app/data/local/TextbookOutlineNodeType.kt
?? app/src/main/java/com/markleaf/app/markdown/DocumentListTitle.kt
?? app/src/main/java/com/markleaf/app/packageimport/
?? app/src/main/java/com/markleaf/app/pdf/
?? app/src/main/java/com/markleaf/app/share/
?? app/src/main/java/com/markleaf/app/ui/pdf/
?? app/src/main/java/com/markleaf/app/ui/theme/DesignSystem.kt
?? app/src/main/java/com/markleaf/app/ui/viewer/ReaderTopBarScroll.kt
?? app/src/test/
?? benchmark/
?? dist/APK_DELIVERY.md
?? dist/markleaf-release.apk.sha256
?? dist/performance/
?? docs/PDF_P0_1.md
?? docs/PDF_P0_2A.md
?? docs/PDF_P0_2B.md
?? docs/PDF_P0_3A.md
?? docs/PDF_P0_3B.md
?? docs/PDF_P0_4A.md
?? docs/PDF_VIEWER_HANDOFF.md
?? docs/SCROLL_PERF.md
?? docs/sample-stress.md
```

## 3.4 强制保护规则

这是大型脏工作树。PDF 相关源码、UI 和测试整体仍为 untracked；它们不是可随意删除的生成物，而是用户长期开发成果。绝对禁止用以下方式“整理”：

- `git reset --hard`
- `git clean`（任何变体）
- `git checkout --` 覆盖文件
- 删除并重新生成 `app/src/main/java/com/markleaf/app/pdf/`、`ui/pdf/` 或 `app/src/test/`
- 用模板/脚手架覆盖现有模块
- 删除不属于当前 narrow task 的 modified/untracked 文件

不应提交：inbox/outbox PDF/ZIP/APK、`.kotlin/`、Gradle/cache、图片缓存、临时抽取目录、大型 fixture、性能采样二进制。`dist/` 只按明确 release 任务处理。

## 3.5 当前最后 APK

```text
路径：/storage/emulated/0/outbox/MarkLeaf/markleaf-debug-r6-1.apk
大小：74,289,947 bytes
SHA-256：7380deaf68a47ef0c348b711e42ca3a5edcbc0b58507d6c33a6774a9cf628b7c
```

该 APK 来自当前 R6.1 production code 最后一次四项 Gradle PASS。其连续 zoom 真机验收仍是 **PENDING**；最新已知录像是 R6（非 R6.1 修复后）在 pinch 松手时 overlay 消失，并出现过底层/overlay 同页重复。

---

# 4. Agent 固定启动顺序

下一位 Agent 每次开始必须依次执行：

1. `AGENTS.md`
2. `.memory/active_object.yaml`
3. `.memory/CURRENT_STATE.md`
4. `docs/NEXT_AGENT_HANDOFF_R6.md`
5. `docs/PDF_VIEWER_HANDOFF.md`（历史手册，内容早于 R6，不能覆盖当前代码事实）
6. `git status --short`
7. 只读目标相关文件
8. 先向用户输出窄范围审计、真实根因和准备修改的 symbol
9. 经任务授权/确认后才修改

真实 active object 文件名是：

```text
.memory/active_object.yaml
```

不是 `.memory/ACTIVE_OBJECT.md`。

---

# 5. 当前真实架构图

> 旧 handoff 中的 `PdfVerticalContinuousArea` 已被 R6 删除。当前真实 symbol 是 `PdfVerticalZoomLockArea`。当前也没有独立名为 `ZoomPageOverlay` 的 composable；overlay 代码内联在连续区域，状态/几何在 `ZoomPageOverlayMath.kt`。

## 5.1 核心 symbol 表

| 文件 / symbol | 职责 | 状态所有者 | 调用与权威来源 |
|---|---|---|---|
| `ui/pdf/PdfViewerScreen.kt` / `PdfViewerScreen` | 收集 VM StateFlow，组装 chrome、连续/左右区域、跳页/印刷页/目录/导出 UI | Screen-local dialog/chrome state；业务状态来自 VM | `NavGraph` 创建 screen；`PdfViewerUiState.Ready` 是页面业务 UI 权威快照 |
| 同文件 / `PdfChrome` | 固定 TopBar、reading viewport、底部区域 | 参数驱动 | R6 后 `topBarVisible=true`；horizontal controls 是 viewport 内 overlay |
| 同文件 / `PdfVerticalZoomLockArea` | 固定 geometry LazyColumn、viewport snapshot、手势、Zoom session、内联 overlay、fast scroller | `LazyListState` + local `ContinuousZoomSession` | VM 提供 bitmaps/aspects/pageIndex；UI 回报 viewport/page settled/zoom gesture |
| 同文件内联 overlay block | session active 时绘制唯一变换页 | `zoomSession` | `state.pageBitmaps[lockedPage] ?: state.pageImage`；这是 R6.2 需要审计的身份风险 |
| 同文件 / `PdfHorizontalPagedArea` | HorizontalPager、单页 transform、点击热区、controls overlay | `PagerState`；`scale/offset` keyed by `state.pageIndex` | Pager settled 调 `onHorizontalPageSettled`；外部 `PageJumpRequest.id` 才 scrollToPage |
| `ui/pdf/PdfViewerViewModel.kt` / `PdfViewerViewModel` | 打开文档、VM state、页面导航、缓存/渲染调度、目录/印刷页/导出 | `_uiState` StateFlow + renderer/cache/jobs + Room-loaded lists | repository/renderer 为数据源；UI 不直接写 DB/renderer |
| 同文件 / `PdfViewerUiState.Ready` | 页面、bitmap map、aspect、zoom quality、mode、outline、print 等 UI 快照 | VM | Screen 只消费；`displayPageIndex` 等为派生 |
| `pdf/PdfPageRenderer.kt` / `PdfPageRenderer` | Android `PdfRenderer` 本地页面位图 | Renderer 实例、PFD、Mutex | VM 唯一主页面调用者；Mutex 保证一次只开一页 |
| `pdf/PdfPageBitmapCache.kt` / `PdfPageBitmapCache` | last-good bitmap、tier/bucket/generation/aspect | VM 私有 cache | `putIfCurrent` 防 stale；`publishCache` 原子发布 snapshot |
| `pdf/PdfRenderQuality.kt` / `PdfRenderQuality` | 1×–3×、SCROLL_PREVIEW/PREVIEW/MEDIUM/HIGH、宽度 cap、hysteresis | 纯函数/常量 | VM 调度权威规则；HIGH 最大宽 3072、最多 1 页 |
| `pdf/ContinuousViewportPolicy.kt` / `ContinuousViewportPolicy` | 根据 visible/primary/direction/phase 生成有界预览集合/顺序 | 纯函数 | UI viewport snapshot → VM → policy |
| `pdf/ContinuousRenderScheduling.kt` / `ContinuousRenderScheduling` | phase/tier 允许规则和 idle upgrade 集合 | 纯函数 | 测试存在；VM 也有直接 job 调度逻辑 |
| `pdf/ZoomPageOverlayMath.kt` / `ContinuousZoomSession` | active、lockedPage、scale、offset、bounds、gesture/exit/session/list snapshot | 连续 UI 顶层 `remember` | 不 keyed by bitmap/page/quality；R6.1 build/unit 已验证，device 未验 |
| 同文件 / `ContinuousZoomSessionReducer` | begin/update/gestureEnd/exit、底层显隐、overlay count | 纯 reducer | gesture end 只在 explicit shrink request 时 exit；double tap 直接 exit |
| 同文件 / `ZoomPageOverlayMath` | centroid offset、clamp、fit-center、tap policy | 纯函数 | 连续 overlay 和横向 UI 使用 |
| `pdf/PrintPageResolver.kt` / `PrintPageResolver` | document/section scoped 印刷页解析 | 纯函数 | VM Ready 派生显示；Room anchors + catalog sections 是输入 |
| `pdf/PrintPageSection.kt` / `PrintPageSectionCatalog` | 分段编号；内科学 front/body seed | 纯数据/函数 | body 从 PDF index 31 映射 printed 1 |
| `ui/pdf/TextbookOutlineSheet.kt` / `TextbookOutlineSheet` | 当前一级目录 UI（浏览+管理混合） | local expanded/delete；nodes 来自 VM | 点击节点 → `goToPage(startPageIndex)`；P1 尚未拆分 |
| `ui/pdf/OutlineEditDialog.kt` / `OutlineEditDialog` | 目录节点编辑 | dialog local fields | VM save；repository upsert |
| `pdf/TextbookOutlineResolver.kt` / `TextbookOutlineResolver` | flatten/current/path/subtree/range | 纯函数 | UI、VM、repository、export 共用 |
| `data/repository/DocumentRepository.kt` | 文档、目录、印刷页、ZIP import 的 I/O 边界 | Room DAO + app files | VM/Home/Viewers 调用；目录 DB 的权威写入口 |
| `data/local/TextbookOutlineNodeDao.kt` | 目录 Room DAO | Room | repository 唯一业务入口 |
| `pdf/PdfEmbeddedOutlineParser.kt` | PDFBox 只读解析内置 bookmarks | 无持久状态 | VM 首屏后约 400 ms 后调用 |
| `pdf/PdfEmbeddedOutlineImporter.kt` | REPLACE/MERGE/idempotency | 纯计划 | repository 执行 DB 写入 |

## 5.2 连续模式当前数据流

```text
LazyColumn layoutInfo
  → ContinuousViewportSnapshot(visible, primary, direction, phase)
  → PdfViewerViewModel.onContinuousViewportChanged
  → ContinuousViewportPolicy
  → ensurePreviewAround / ensureReadablePreviews / schedulePrimaryHiRes
  → requestPageRender
  → PdfPageRenderer (Mutex, IO)
  → PdfPageBitmapCache.putIfCurrent(generation)
  → publishCache
  → Ready.pageBitmaps/pageAspects
  → LazyColumn / inline Zoom overlay
```

当前 zoom lock 流：

```text
pointerInput 双指
  → 当前代码 enterLock(state.pageIndex)
  → 使用单个 primaryBounds
  → ContinuousZoomSessionReducer.begin
  → LazyColumn lockedPage 内容隐藏但 item 不移除
  → inline overlay 绘制/graphicsLayer
  → gestureEnd 保存 session（R6.1）
```

`enterLock(state.pageIndex)` 是 R6.2 的核心错误候选：VM pageIndex 是 viewport center/settled 状态，不保证是双指命中的页面。

## 5.3 横向模式当前数据流

```text
用户单指拖动(1×)
  → HorizontalPager 原生 fling/snap
  → pagerState.settledPage distinct
  → PdfViewerViewModel.onHorizontalPageSettled
  → VM 更新 pageIndex + requestRender
```

反向只允许明确外部请求：

```text
页码/目录/缩略图/点击热区
  → VM goToPage
  → PageJumpRequest(id, target, horizontalExternal)
  → Pager 消费新 id 一次并 scrollToPage
```

普通 VM `pageIndex` 更新不会再通过 `LaunchedEffect(state.pageIndex)` 拉 Pager；不要恢复双向反馈。

## 5.4 目录权威数据流

```text
Room textbook_outline_nodes
  → TextbookOutlineNodeDao
  → DocumentRepository.list/import/upsert/delete
  → PdfViewerViewModel.outlineNodes
  → Ready.outlineNodes
  → TextbookOutlineSheet / current outline path
```

内置目录：

```text
PDF content URI
  → PdfEmbeddedOutlineParser (PDFBox metadata only)
  → EmbeddedOutlineBuilder drafts
  → PdfEmbeddedOutlineImporter plan
  → DocumentRepository
  → Room textbook_outline_nodes
```

---

# 6. 已稳定功能与验证等级

标记说明：`BUILD VERIFIED` 指最后 R6.1 工程构建通过；不代表该功能真机通过。`DEVICE` 只记录明确证据。

| 功能 | Implemented | Build | Unit | Device / 当前结论 |
|---|---|---|---|---|
| PDF 打开（PdfRenderer） | YES | VERIFIED | policy/helper tests only | 历史上可打开；R6.1 当前包未单独复验，PENDING |
| PDF 内置目录解析/导入 | YES | VERIFIED | Builder/Importer VERIFIED | parser 真 PDF/当前包真机 PENDING |
| 教材目录节点与跳转 | YES | VERIFIED | Resolver VERIFIED | 历史实现；当前包 DEVICE PENDING |
| 印刷页分段映射 | YES | VERIFIED | VERIFIED | UI/device PENDING |
| UI 33→2、330→299、633→602 | YES | VERIFIED | `PrintPageResolverTest` VERIFIED | DEVICE PENDING |
| Markdown ZIP 包导入 | YES | VERIFIED | ZIP/package tests VERIFIED | DEVICE 未在本轮确认 |
| Package 相对图片 | YES | VERIFIED | integration/resolve VERIFIED | DEVICE 未在本轮确认 |
| Markdown 表格 | YES | VERIFIED | 无专门 UI 单测 | DEVICE 未在本轮确认 |
| ACTION_VIEW / ACTION_SEND 单文件 | YES | VERIFIED | allowlist evidence | 端到端 DEVICE PENDING；无 SEND_MULTIPLE |
| 深色模式 | YES | VERIFIED | 无主题单测 | DEVICE 未在本轮确认 |
| 连续 R3 SCROLL_PREVIEW 调度 | YES | VERIFIED | policy/scheduling/cache VERIFIED | 真机曾明显改善，但完整稳定验收仍 PENDING |
| HorizontalPager 当前实现 | YES | VERIFIED | R6 policy tests | 最新 R6.1 真机验收 PENDING |
| R6 自动 chrome hide 移除 | YES（代码无常量/定时器） | VERIFIED | source/policy test | DEVICE PENDING |
| R6.1 持久 ZoomSession | 代码已实现 | VERIFIED | R6.1 tests VERIFIED | **未真机复验；不得写 PASS** |
| 同页底层隐藏 + 单 overlay | 代码已实现 | VERIFIED | reducer/source tests | **未真机复验；P0 仍需身份对齐** |

最近一次四项 Gradle（R6.1 APK 构建轮）均 PASS：PDF test group、full unit test、lintDebug、assembleDebug。当前交接轮按 `change.md` 明确没有重跑完整 Gradle。

---

# 7. PDF Viewer 演进史与失败经验

## R2：可见页保护与 pending commit

- 引入页面 bitmap 保留思路：重渲染开始不能先把旧 bitmap 置空。
- 使用 `pendingZoomCommit`/本地 UI 与 VM commit 之间的桥接，尝试避免 zoom 状态被 VM 旧值覆盖。
- 仅保护“当前/附近”仍不够：快速滚动时预取窗口落后，`retainOnly`/map publish 可能让刚进入 viewport 的页面无 bitmap，UI 落入 Loading/白页。
- 预取若仍以普通 PREVIEW/HIGH 串行排队，`PdfRenderer` 一次只能开一页，滚动速度会超过渲染队列。
- 结果：build/unit 可过，真机仍可能闪白；不能只靠扩大预取或更多高清图解决。

## R3：SCROLL_PREVIEW 与调度收敛

- 增加低成本 `SCROLL_PREVIEW`（viewport ×0.5，cap 640）用于拖动/fling。
- `PdfPageRenderer` 用 Mutex 串行；VM 用 `ContinuousRenderKey(documentId,page,tier,width)` 和 per-page generation 去重/拒绝 stale result。
- 引入 viewport direction/phase 和 bounded protected pages；滚动期间只允许 SCROLL_PREVIEW，idle 后 visible 升 PREVIEW，primary 再升级。
- `PdfPageBitmapCache.putIfCurrent` 原子替换，last-good bitmap 保留到成功结果。
- 早期 `retainOnly` 错误会删掉仍在屏幕或即将进入屏幕的页；当前实现改为 bounded `continuousProtectedPages`，但这段仍是闪白审计重点。
- 真机滚动明显改善，但 committed zoom 仍曾在 layout/graphicsLayer 之间分裂；高清稳定不代表交互稳定。

## R4：centroid zoom + 页面内纵向控制的失败

- 引入以双指 centroid 为锚点的 scale、`offsetX/offsetY`。
- 为在放大状态继续纵向阅读，曾加入页面消费纵向 pan、`dispatchRawDelta` 把 remainder 注入 LazyColumn、residual velocity 和人工 fling。
- 多层手势消费者与 LazyColumn 原生 nested scroll 竞争，造成阻尼怪、松手后残余速度、自动快速滑动。
- 结论：单指纵向阅读必须优先交回 LazyColumn；不要人工模拟 list drag/fling。

## R5：全书 layout zoom 与 anchor handoff 的失败

- committed zoom 乘到所有 LazyColumn page item 宽高，zoom 会重测整份文档。
- pinch end 需要同时切 committed layout、清 GPU transient、重算纵向 list anchor 和横向 offset。
- `PendingLayoutAnchor`、normalizedX/Y、requestId 试图把这些步骤原子化。
- 实际上 Compose layout 可用时机、VM commit、graphics frame 和 LazyList item measurement 不在一个原子事务中；会先/后看到旧 offset、新 width 或旧 bounds。
- 结果是真机松手后横向跳边、大面积灰、跳页或整份文档 geometry 失稳。
- 结论：连续阅读不能再用 zoom 改全书 item geometry；pinch end 不能 `scrollToItem` 修锚点。

## R6：固定 LazyColumn + 当前页 overlay

- 放弃全书 layout zoom；连续列表永久适宽、固定 item geometry。
- pinch 时锁定一页，在 reading viewport 顶层用 GPU `ZoomPageOverlay` 思路缩放/pan；底层列表冻结。
- R6 首版把 session 生命周期和 gesture end 耦合，录像中连续三次出现 pinch 过程正常、松手立即恢复 1×。
- 同时底层 lockedPage Image 仍绘制，overlay 又绘制相同 bitmap；位置/尺寸轻微不一致时出现两张相同页面。
- R6.1 当前代码增加 `ContinuousZoomSession`/reducer，把 gesture end 与 explicit session exit 分开，并在 active 时隐藏底层 lockedPage 内容。它已 unit/build verified，但没有 post-R6.1 真机录像，不能认为已闭环。
- 当前更深的身份错误仍在：session 创建使用 `state.pageIndex` 和只属于 current page 的 `primaryBounds`。双指可能位于另一个可见页，VM pageIndex 又是 debounce/center 概念，因此 lockedPage 可能锁错。
- overlay bitmap 使用 `pageBitmaps[lockedPage] ?: pageImage`；`pageImage` 可能对应另一页。隐藏项、overlay bitmap、bounds 必须由同一个 hit-tested pageIndex 决定。
- 当前连续 cold page 分支是白色静态 Box + `LaunchedEffect(onEnsureAround)`，Horizontal 仍有 `CircularProgressIndicator`。`change.md` 明确要求 R6.2 再审计“连续 spinner 遗漏分支”，不能仅凭当前一个分支看起来静态就宣布完成。

## 为什么 gesture end 与 session end 必须分开

`gestureActive` 只表示当前是否有手指正在变换。`session.active` 表示用户仍处于锁页放大阅读。松手后用户还需要看放大结果、单指 pan、等待清晰 bitmap；因此 `gestureActive=false` 必须保留 `active/lockedPage/scale/offset/bounds`。只有主动缩回 1×或 active session 双击才能 exit。

## 为什么 lockedPage 必须来自手势命中页

连续 viewport 常同时显示上一页尾部、当前页和下一页头部。`state.pageIndex` 是 VM 的 settled/center page，可能滞后 150ms，也不代表双指位置。正确流程必须保存所有 visible page 的精确 viewport rect，用 pinch centroid 做 hit-test；命中的 pageIndex、rect、last-good bitmap、底层隐藏 id 必须一次性进入 session。

## 禁止重新引入

- committed zoom 修改全书 LazyColumn item 尺寸
- 持久 `offsetY` 作为页面内纵向阅读模型
- `dispatchRawDelta`
- 人工 residual velocity / drag / fling 注入
- pinch end `scrollToItem` anchor correction
- `PendingLayoutAnchor` 全局布局交接
- 直接用 `primaryPage`、`state.pageIndex` 猜 pinch lockedPage
- `gestureActive=false` 即退出 session
- overlay 与底层同页 PDF 内容同时绘制
- 自动 chrome hide 或系统栏自动沉浸
- Pager settled → VM → 普通 pageIndex → Pager 的双向反馈循环
- 为追求高清而先清 last-good bitmap

## 当前源码中的历史残留（不是 active architecture）

下列文件仍在 production source tree，但当前 UI/VM 没有调用它们的旧交互路径：`ContinuousLayoutMath.kt`（R5 全书 layout helper）、`ContinuousTransformMath.kt`（含 R4 `consumePan/listScrollDelta`）、`ContinuousZoomHandoff.kt`（旧 pending bridge）。`ZoomLockSession/replaceBitmap` 和 `ContinuousRenderScheduling` 的多数 helper 目前也主要由旧测试覆盖。它们的存在不表示可以恢复旧架构。

另一个必须诚实记录的当前实现事实：`schedulePrimaryHiRes` 的注释说“if zoomed”，但代码直接选择 `QualityTier.HIGH`，没有调用 `continuousNeedsHiRes`，所以 idle primary 当前会无条件请求 HIGH。`setZoomGestureActive(true)` 的注释说只取消 HIGH，但实际 `cancelNonPreviewJobs` 清空全部 `cacheJobs`（缓存位图仍保留）。这些是 R3 调度审计事实；R6.2 不得顺手重构，但也不能在报告里声称“1×永不 HIGH”或“pinch 只取消 HIGH job”。

---

# 8. 当前最高优先级缺陷

## P0-1：持久 ZoomSession 真机尚未闭环

R6 录像连续三次显示：双指过程中 overlay 能正常出现/缩放；手指离开后 overlay 立即消失，页面恢复适宽。R6.1 代码随后引入持久 reducer并构建 APK，但没有新的真机回传。因此当前状态是：**代码尝试修复 + unit/build verified，device PENDING**。

复现与验收：

1. 连续模式打开同时可见多页的区域，在某页双指放大到明显大于 1×。
2. 松开双指；overlay、lockedPage、scale/offset/bounds 必须继续存在，仅 `gestureActive=false`。
3. LazyColumn index/offset 不变并继续冻结；松手后单指四向 pan，offset 再次松手仍保持，无 fling。
4. 仅主动缩回 1×或 active 双击退出。

注意：session owner 当前是 `PdfVerticalZoomLockArea` 内 unkeyed `remember`。它能跨普通 recompose 保持，但离开 Ready、切换 reading mode 或 composable 从 composition 移除时会重置；它不是 ViewModel/document-level durable owner，也不是 `rememberSaveable`。首次 pinch 还通过 `scope.launch` 异步 stopScroll + enterLock，存在首帧丢失/旧闭包/sessionId 未用于拒绝过期 launch 的 orchestration 风险。

## P0-2：pinch 必须 hit-test 真实页面

当前 pinch 和 double tap 都调用 `enterLock(state.pageIndex)`；`primaryBounds` 只在 `page == state.pageIndex` 且 unlocked 时更新，Rect 自身没有 page identity。双指落在上一页/下一页时，会把 VM center/settled pageIndex 与可能陈旧的单个 Rect 配对。

验收：维护 `pageIndex → exact viewport Rect` 的 visible bounds；pinch/tap centroid 命中 rect 后才激活；未命中应忽略而非猜 nearest/primary；active 后 target immutable。

## P0-3：overlay bitmap、隐藏 item、lockedPage 身份必须一致

当前底层 bitmap 是 `state.pageBitmaps[page]`，底层隐藏按 `zoomSession.lockedPage`，但进入资格和 overlay source 允许无 page identity 的 `state.pageImage` fallback：

```text
lockedPage = A
底层隐藏 A
pageBitmaps[A] 缺失
overlay fallback 绘制裸 pageImage（可能是 B）
```

R6.2 必须只使用 identity 化的 `(pageIndex, bitmap)`，优先严格读取 `pageBitmaps[lockedPage]`。激活前 pageIndex、rect、last-good bitmap 必须全部准备好；PREVIEW→HIGH 只替换同一个 overlay source。

此外 VM `commitZoom(scale)` 当前把 continuous quality upgrade target 指向 `ready.pageIndex`。即使 UI hit-test 修成 lockedPage，如果二者不同，高清仍会升级错页。R6.2 要窄范围传递 frozen target 或明确同步目标，但不能借机重构整个 R3 scheduler。

## P0-4：连续冷页静态 placeholder / spinner 遗漏审计

当前源码事实：`PdfVerticalZoomLockArea` 冷页是固定尺寸白色 Box + `LaunchedEffect(onEnsureAround)`，没有 `CircularProgressIndicator`；spinner 只在全局 Loading 和 horizontal cold page。`change.md` 仍记录连续冷页 spinner/遗漏风险，因此 R6.2 必须全局搜索共享/mode switch 分支并真机慢滚冷页。不要写成当前 continuous 源码已确认有 spinner，也不要未经真机就宣布问题关闭。

## P0-5：同一个 lockedPage 只能有一个可见 PDF 层

R6 录像出现两张相同页面。R6.1 当前代码 active 时让 locked item 只画固定尺寸白底，再顶层画一个 overlay；identity 正确时只有一份 PDF。但若隐藏 A、fallback overlay 画 B，底层 B 仍可见，就仍可能看到同一实际页 B 两份。

验收：inactive 底层可见/overlay 0；active 只隐藏 locked item 内容、布局/key 不变、overlay 1；quality replace 始终 1；exit 在同一 snapshot 恢复底层并移除 overlay，无双层/空白帧。

---

# 9. 下一位 Agent 的第一项任务

## `R6.2_ZOOM_SESSION_AND_TARGET_HIT_FIX`

这是窄范围修复。不要重新改 Pager、R3 缓存队列、目录、Markdown 或全局 layout zoom。

## A. ZoomSession 生命周期

先核对 `PdfVerticalZoomLockArea`、local `zoomSession`、`enterLock/exitLock`、`ContinuousZoomSessionReducer.begin/updateTransform/gestureEnd/exit`。搜索全部 `zoomSession =`、`exitLock(`、`gestureActive`、`lockedPage`、scale/offset reset 写入点。验证 pointer count 变 0 不会因 recompose、bitmap、quality 或 pageIndex 重建 session；审计首次 `scope.launch` 和 stale capture/sessionId 竞态。

## B. pinch hit-test

预计主要修改 `PdfViewerScreen.kt`，必要时在 `ZoomPageOverlayMath.kt` 增加纯 hit-test。建议身份化模型：

```kotlin
data class VisiblePageTarget(val pageIndex: Int, val bounds: OverlayRect)
```

- 在每个 visible item 的实际 PDF content Box 上维护带 pageIndex 的 viewport rect。
- 手势首次满足 pinch 条件时以 centroid 命中，不用 `state.pageIndex`/primary 猜。
- stop list 后原子捕获 pageIndex + rect + bitmap slot + entry index/offset。
- 未命中/bitmap 未准备好则忽略；active target 不再变化。

## C. bitmap / hidden item identity alignment

- 删除连续 overlay 对裸 `state.pageImage` 的 fallback，或先把 fallback 改成显式 `(pageIndex,bitmap)` 并严格校验；优先只用 `pageBitmaps[lockedPage]`。
- bottom hidden、overlay key/source、session lockedPage 使用同一 frozen target。
- 用稳定 `sessionId/lockedPage` 作 overlay identity，不 key bitmap/tier/map，不引入 Crossfade/AnimatedContent 双 Image。
- 让 zoom settle/quality request 指向 frozen lockedPage，而不是错误的 VM center page；只做最小接口调整。

## D. 连续 spinner 遗漏分支

- 搜索 `CircularProgressIndicator|onEnsureAround|pageBitmaps|pageImage`。
- continuous cold branch 保持 fixed aspect + static placeholder；不改变 item key/height，不显示全页动画 spinner，不动 R3 queue。

## E. 针对性测试

至少覆盖：release 后 session/transform 保持；centroid 命中不同 visible page；未命中不建 session；VM primary 改变不影响 locked target；bounds/bitmap/hidden index 一致；wrong `pageImage` 不可兜底；inactive/active/quality/exit 的同页可见层唯一；item key/height/list index/offset 不变；continuous cold page 无 spinner；旧 global layout/dispatchRawDelta/pinch-end scrollToItem 不回归。

纯 Kotlin identity/hit-test/reducer test 优先；source-string test 只能兜底，不能替代 Compose runtime/真机。

## F. 构建和 APK

修改完成后运行第 13 节四项命令。全部 PASS 后生成：

```text
/storage/emulated/0/outbox/MarkLeaf/markleaf-debug-r6-2.apk
```

报告 bytes 和 SHA-256；不提交。

## G. 真机验收

录制：同时可见两页时 pinch 非 primary 页；松手保留；单指四向 pan；PREVIEW→HIGH 不跳/不双页；主动缩回 1× 后列表位置完全不变；冷页无 spinner/尺寸塌陷。任何一项失败都停在 R6.2，不开始 P1。

---

# 10. 目录 P1 待办（R6.2 真机通过后单独实施）

一级“教材目录”只负责：展开/收起、当前章节定位、点击跳转、搜索。

二级“目录管理”负责：PDF 内置目录导入、从当前页新建、编辑、排序、分享、删除。

一级目录删除：每行分享、每行编辑、每行删除、巨型加号、大型导入入口。

当前 `TextbookOutlineSheet` 已有 `LazyColumn` 和 stable key `node.id`，`flatAll` 由 `remember(nodes){flattenTree(nodes)}` 计算；但仍混有所有管理入口，无搜索/独立管理/排序，也没有 `contentType`。`visibleRows` 祖先检查还反复 `nodes.firstOrNull`。

P1 性能硬要求：LazyColumn、stable key、contentType、flatten tree 纯函数、只在 outline/expanded ids 变化时重算、不在 row 递归整棵树、不在滚动中做 DB/PDF 解析。必须等 R6.2 真机收口后另开任务。

---

# 11. 工作习惯与用户偏好

- 先结论，再证据，再下一步；判断直接、明确、可执行。
- 一次只处理一个问题，不喜欢宽泛分析和无休止重构。
- 真机表现优先；Build/Unit PASS 不等于 Feature/Device PASS。
- 必须诚实标记 PENDING/NOT VERIFIED/FAILED。
- 不扩大 scope、不擅自进下一阶段、不自行提交。
- 不清理/覆盖大型脏工作树。
- 每次 APK 报路径、bytes、SHA-256。
- 最终 Markdown 报告含根因、修改、测试、APK、真机状态。
- 用户最重视跟手、稳定、不闪烁、不自动跳；低清稳定优先于高清闪白。
- 环境问题阻断时立即停止并报告，不静默绕过。
- 下一位 Agent 不要重复全工程审计；读本手册后只审 active path。

---

# 12. 推荐执行节奏与停止条件

1. **只读确认 R6.1/R6.2：** 未确认 owner/exit/target/source/hidden 任一项就不修改。
2. **只修 session + target hit：** 若发现必须改 Pager、R3 queue 或全书 layout 才能继续，立即停，说明方案偏离。
3. **PDF 定向单测：** 失败不得进入 full build。
4. **full test/lint/assemble：** 任一失败不生成交付 APK，不跳过验证。
5. **生成 APK：** size/hash 未核对不报告完成。
6. **真机录像：** 松手退出、锁错页、双层、spinner、跳动任一存在即 R6.2 未通过。
7. **目录 P1：** 只有用户明确确认 R6.2 device pass 并下达 P1 才开始。

---

# 13. 构建与验收命令

```bash
./gradlew :app:testDebugUnitTest --tests 'com.markleaf.app.pdf.*'
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug --no-daemon --console=plain
./gradlew :app:assembleDebug --no-daemon --console=plain
```

```bash
cp app/build/outputs/apk/debug/app-debug.apk \
  /storage/emulated/0/outbox/MarkLeaf/markleaf-debug-r6-2.apk
ls -l /storage/emulated/0/outbox/MarkLeaf/markleaf-debug-r6-2.apk
stat -c '%n %s bytes' \
  /storage/emulated/0/outbox/MarkLeaf/markleaf-debug-r6-2.apk
sha256sum \
  /storage/emulated/0/outbox/MarkLeaf/markleaf-debug-r6-2.apk
```

外部 storage 访问可能需要 scoped approval；不要把 APK 加入 git。

---

# 14. 最终交接检查表

- [ ] 已读 `AGENTS.md`
- [ ] 已读 `.memory/active_object.yaml`
- [ ] 已读 `.memory/CURRENT_STATE.md`
- [ ] 已读 `docs/NEXT_AGENT_HANDOFF_R6.md`
- [ ] 知道 `docs/PDF_VIEWER_HANDOFF.md` 是 R6 前历史资料
- [ ] 已运行 `git status --short`，未清理工作树
- [ ] 当前 APK path/size/SHA 与 memory 一致
- [ ] 当前 continuous symbol 是 `PdfVerticalZoomLockArea`
- [ ] Session owner/所有 exit-reset writers 已确认
- [ ] overlay 显示依赖 session active，而非 gesture active
- [ ] lockedPage 当前仍来自 `state.pageIndex`，待 R6.2 修
- [ ] visible page bounds/hit-test 已确认
- [ ] overlay bitmap/hidden item identity 已确认
- [ ] continuous placeholder/spinner 所有分支已搜索
- [ ] 未触碰 Pager/R3/目录/Markdown
- [ ] 未恢复 global layout zoom/人工 vertical fling
- [ ] 真机验收仍为 PENDING
- [ ] 未自行提交

## 第一条命令与第一项任务

```bash
cd /root/code/MarkLeaf && \
sed -n '1,220p' .memory/active_object.yaml && \
sed -n '1,260p' .memory/CURRENT_STATE.md && \
sed -n '1,320p' docs/NEXT_AGENT_HANDOFF_R6.md && \
git status --short
```

第一项任务：只读审计 `PdfVerticalZoomLockArea` 的 `enterLock(state.pageIndex)`、裸 `primaryBounds`、`state.pageBitmaps[page] ?: state.pageImage`、bottom hidden predicate，以及 `commitZoom` 的 quality target；先报告 page hit/identity 的精确错配，再执行 R6.2。
