# 未使用代码清理证据 2026-11-06

本报告先于删除创建。分支：`cleanup/unused-code-20261106`；基线是实际工作树 705 项构建输入，不是旧 Git HEAD。既有 WIP 和空暂存区保留，不提交、不推送、不合并。

## 范围与判定

扫描 app、core:model、core:database、core:pdf、benchmark、Markdown 编辑器、Manifest、资源、Gradle、脚本及所有单测和原生测试。搜索包含隐藏文件和未跟踪源码，排除 Git 内部、构建输出、依赖缓存及本报告；历史文档命中与调用分开记录。`initial.zip` 和逐文件 SHA-256 存在 `output/unused-code-20261106/`。

正向入口：主 Manifest → M1racleReaderApplication / MainActivity → M1racleReaderTheme → M1racleReaderNavGraph → 首页、书库、设置、Markdown、PDF、EPUB、编辑及文档工具；QA Manifest → harness / provider；JUnit / AndroidJUnitRunner → 测试；benchmark runner → ReaderScrollBenchmark；MarkdownEditorOrigin → assets/markdown-editor/index.html → bundle / reader-markdown-editor/src/main.mjs。Gradle、package.json 和独立脚本均作为可手动运行的入口。

core 模块只被本仓库 Gradle 项目消费，未配置发布插件或对外服务接口。下列候选均为本地普通成员；它们不是 Room DAO / Entity、序列化字段、Android override、反射入口或 @JavascriptInterface。搜索结果不是单独的删除理由，还核对了声明语义、调用方向、框架注册和构建变体。

## 第一批 删除前清单

| # | 候选 | 删除前位置 | 全仓搜索 | 测试引用 | 入口判定 |
|---|---|---|---|---|---|
| 1 | `pageBitmapPresent` | `app/src/qa/java/com/m1racle/reader/ui/pdf/PdfLatePageCountHarnessActivity.kt:136` | 1 行（定义及本地注释），旧文档 0 行 | 0 | 无调用，成员不可达 |
| 2 | `clearInstanceForTests` | `core/database/src/main/java/com/m1racle/reader/data/local/AppDatabase.kt:573` | 1 行（定义及本地注释），旧文档 1 行 | 0 | 无调用，成员不可达 |
| 3 | `pageRole` | `core/model/src/main/java/com/m1racle/reader/core/model/PdfRenderQuality.kt:185` | 1 行（定义及本地注释），旧文档 1 行 | 0 | 无调用，成员不可达 |
| 4 | `forMode` | `core/model/src/main/java/com/m1racle/reader/core/model/PdfViewportSessionState.kt:42` | 1 行（定义及本地注释），旧文档 1 行 | 0 | 无调用，成员不可达 |
| 5 | `clearCache` | `core/pdf/src/main/java/com/m1racle/reader/pdf/PdfThumbnailLoader.kt:115` | 1 行（定义及本地注释），旧文档 1 行 | 0 | 无调用，成员不可达 |
| 6 | `intersects` | `core/pdf/src/main/java/com/m1racle/reader/pdf/PdfTileRendering.kt:16` | 2 行（定义及本地注释），旧文档 2 行 | 0 | 无调用，成员不可达 |
| 7 | `shouldPublishSettled` | `core/pdf/src/main/java/com/m1racle/reader/pdf/ZoomPageOverlayMath.kt:40` | 1 行（定义及本地注释），旧文档 1 行 | 0 | 无调用，成员不可达 |
| 8 | `touchLastOpened` | `app/src/main/java/com/m1racle/reader/data/repository/DocumentRepository.kt:321` | 1 行（定义及本地注释），旧文档 1 行 | 0 | 无调用，成员不可达 |
| 9 | `clearAllOutlineNodes` | `app/src/main/java/com/m1racle/reader/data/repository/DocumentRepository.kt:771` | 1 行（定义及本地注释），旧文档 1 行 | 0 | 无调用，成员不可达 |
| 10 | `fromDocument` | `app/src/main/java/com/m1racle/reader/markdown/MarkdownAssetResolver.kt:109` | 2 行（定义及本地注释），旧文档 4 行 | 0 | 无调用，成员不可达 |
| 11 | `isInPageAnchor` | `app/src/main/java/com/m1racle/reader/markdown/MarkdownLinkRouter.kt:17` | 1 行（定义及本地注释），旧文档 1 行 | 0 | 无调用，成员不可达 |
| 12 | `hintZh` | `app/src/main/java/com/m1racle/reader/pdf/export/ExportRangeResolver.kt:258` | 1 行（定义及本地注释），旧文档 2 行 | 0 | 无调用，成员不可达 |
| 13 | `dismissError` | `app/src/main/java/com/m1racle/reader/share/ExternalOpenCoordinator.kt:352` | 1 行（定义及本地注释），旧文档 1 行 | 0 | 无调用，成员不可达 |
| 14 | `StatusBarProtection` | `app/src/main/java/com/m1racle/reader/ui/common/StatusBarProtection.kt:35` | 1 行（定义及本地注释），旧文档 2 行 | 0 | 无调用，成员不可达 |
| 15 | `formatMeta` | `app/src/main/java/com/m1racle/reader/ui/home/HomeScreen.kt:296` | 1 行（定义及本地注释），旧文档 5 行 | 0 | 无调用，成员不可达 |
| 16 | `endHover` | `app/src/main/java/com/m1racle/reader/ui/material/GlassInteraction.kt:107` | 1 行（定义及本地注释），旧文档 1 行 | 0 | 无调用，成员不可达 |
| 17 | `dismissExportPlan` | `app/src/main/java/com/m1racle/reader/ui/pdf/PdfViewerViewModel.kt:1819` | 1 行（定义及本地注释），旧文档 1 行 | 0 | 无调用，成员不可达 |
| 18 | `onOptionSelected` | `app/src/main/java/com/m1racle/reader/ui/pdf/ReaderMoreHost.kt:173` | 1 行（定义及本地注释），旧文档 4 行 | 0 | 无调用，成员不可达 |
| 19 | `mustCollapseInlineForChild` | `app/src/main/java/com/m1racle/reader/ui/pdf/ReaderMoreTransitions.kt:108` | 1 行（定义及本地注释），旧文档 1 行 | 0 | 无调用，成员不可达 |
| 20 | `currentStage` | `app/src/main/java/com/m1racle/reader/ui/reader/ReaderHighlightController.kt:74` | 1 行（定义及本地注释），旧文档 1 行 | 0 | 无调用，成员不可达 |

StatusBarProtectionExtraHeight 的两处命中都在待删组件文件内。按顶层声明与文件引用进行保守筛查，仅另外筛出 ReaderScrollBenchmark，它由测试运行器发现，必须保留。未发现第二个可证明整文件无入口的源码文件，也不删除任何源码目录。

## 第二批 删除前清单

| 候选 | 全仓证据 | 入口与测试判定 |
|---|---|---|
| Theme.dynamicColor / ignoreDynamic | `dynamicColor` 只在 Theme.kt 的默认 false 参数、解释注释和 ignoreDynamic 赋值出现；ignoreDynamic 只有定义 | 所有主题调用均未传此参数；没有测试传值；变量从未被读取。不影响实际固定调色板 |
| LocalImageLoader.decodeWithImageDecoder 中 SDK < 28 的 return | app/build.gradle.kts 明确 minSdk=31；该私有解码函数仅有定义和 loadFile 的调用 | 从 ImageBlockView → LocalImageLoader.load 可达，但已安装应用的 SDK 必须 >=31，条件恒假；未找到低于31的 Robolectric 配置或 SDK_INT 改写 |
| GlassCapabilityProbe.detect 中 SDK < 31 的分支 | app/build.gradle.kts 明确 minSdk=31；detect 由 Theme 调用 | detect 可达，但该条件在受支持系统恒假；保留 API31/32 的 BLUR_ONLY 与 API33+ 的 FULL_OPTICAL；SOLID_FALLBACK 在跨窗口和测试中有真实用途，保留枚举及其他路径 |

对应原始搜索保存在 `search-before-deletion/dynamicColor.jsonl`、`ignoreDynamic.jsonl`、`decodeWithImageDecoder.jsonl`、`GlassCapabilityProbe.jsonl`；SDK、Manifest 与测试配置证据另存于第二批证据文件。

## 保留与原因

### 按字符串、框架或测试使用而保留的具体项

| 项目 | 证据位置 | 保留原因 |
|---|---|---|
| window.ReaderMarkdown 的 load / setSourceMode / setReadOnly / setTheme / undo / redo / focus / getMarkdown / isComposing | reader-markdown-editor/src/main.mjs 与 ui/documents/MarkdownWebEditor.kt | 对外桥接对象，Kotlin 通过 evaluateJavascript 和 command 字符串调用；不凭单语言引用数删 API |
| Native.postMessage | ui/documents/MarkdownWebEditor.kt 的 addJavascriptInterface / @JavascriptInterface；main.mjs 的 window.Native?.postMessage | WebView 按字符串查找，R8 也有明确 keepclassmembers |
| QaGlass、QaMaterialComparisonScreen、QaGlassVariantSettings | app/build.gradle.kts 的 QA true 覆盖；src/qa 与 src/production 同名实现；HubSettingsScreen 的 QA gate | 所有构建变体合并核查后确实可达，production 替身参与编译 |
| ReaderScrollBenchmark.coldStartup / openDocumentAndFlingScroll | benchmark/src/main/java/com/m1racle/reader/benchmark/ReaderScrollBenchmark.kt | JUnit 注解是入口，不需要普通调用者 |
| PdfLatePageCountHarnessActivity.armLatePageCountDuringNextMotion / currentPageCount / capturedDocumentState | PdfLatePageCountInstrumentedTest.kt | 原生测试直接使用，测试使用算真实使用 |
| PdfMotionAspectHarnessActivity.prepareRestoredShortPage / isRestoredShortPageApplied / publishPageAspect | PdfScrollJumpInstrumentedTest.kt | 原生测试直接使用 |
| PdfTextSelection 内的 PDFTextStripper override、PdfBodyEditor 的 showGlyph / operatorException | 相应类的 override 声明及 PDFBox 继承关系 | 上游框架回调，不要求仓内存在普通函数调用 |
| R7ViewportTestActivity 与 QA Manifest 中的 activity / provider | src/debug/AndroidManifest.xml、src/qa/AndroidManifest.xml | Android Manifest 及仪器测试按名称实例化 |
| 独立 CLI 脚本与生成工具 | scripts/、reader-markdown-editor/package.json、docs/code-wiki/tools/ | 可由人直接执行，不能由没有 import 推断为废弃 |

上述按名称分派、框架生成、同名重载及潜在外部 API 中没有充分删除证据的项全部保留。更完整的测试专用候选见 `output/unused-code-20261106/test-only-retained-candidates.json`。

保守文件级图把 import、标识符及字符串引用都算作潜在边，并将所有构建变体、Manifest、生成入口、测试、benchmark 和脚本作为根。第一批后 595 个 Kotlin 文件均有潜在路径；这仅用于找断开文件群，不冒充语义调用图，也不是证明每个成员都在运行。原始图见 `output/unused-code-20261106/remaining-file-reachability.json`。

- Markdown 的 window.ReaderMarkdown API、Native.postMessage、@JavascriptInterface，以及按 command 拼接的方法名：Kotlin / JavaScript 按字符串互调，不能按静态零调用删除。
- QA_GLASS_VARIANTS_ENABLED、READER_PERFORMANCE_EXPERIMENTS_ENABLED 和 production 的 QaGlass / QaExperimentScreens：Release 关闭，QA 明确启用；同名变体替身还必须参与主源码编译。
- DETAIL_TILE_DIAGNOSTICS_ENABLED 等 Gradle 属性：默认关闭但可覆盖，不是硬编码永久关闭。
- ReaderScrollBenchmark / coldStartup / openDocumentAndFlingScroll：由 JUnit / benchmark runner 按注解发现，不能按无直接调用删除。
- Room DAO、Entity、数据库迁移和 schema：生成代码、历史用户数据及迁移测试依赖；不按 getter 零引用删除。
- Android 生命周期 override、ContentProvider、PDFBox 回调、Compose prefetch / popup 回调：框架调用。
- testdata、branding、构建 / 安装 / 备份 / 性能脚本：人工验收、资产来源或独立命令入口；“无应用 import”不代表废弃。
- 仅被测试使用的几何规则、策略接口及导出辅助：测试入口算真实使用，不删测试以消除引用。
- 未发现可信的整段被注释掉的可执行代码。KDoc、ProGuard 配置示例、解析器中的注释标记字符串保留。
- 同名重载、数据类生成访问器、共享模块的剩余导出未因稀少引用被推断为死代码；此类需语义调用证据，保留。

## 验证

清理前：app QA 1420 / Release 1256 项，均 0 failure、0 error、2 项既有 skip；core:database Release 48、core:pdf Release 458 项，全部通过；core:model 无测试。编辑器 Node 9/9。四模块 lint 无错误。正式签名压缩 Release 94 构建成功，Outbox 与构建摘要一致，并复制至当前模拟器 Download。后续每批均在冻结源码后跑同样检查；失败整批恢复到批前实际字节，不回退旧 HEAD，不改测试。

当前物理手机未连接；历史装机 / 手感结论不适用于本次清理。

## 失败回滚记录

第一批首次检查在执行任何 Gradle 任务前发生 JVM 本机内存分配失败。已立即从 batch1-before.zip 恢复全部 19 个文件（含整文件恢复），705 项输入 SHA-256 与 initial 基线全部相同。恢复后的原源码在较小 JVM 下仍发生相同内存失败，证明当时是主机内存条件导致。

暂停原有无窗口模拟器（不卸载、不清理应用数据）后，使用单工作线程、2 GiB Gradle heap、2 个处理器和 192 MiB code cache，原源码完整复验成功；随后重新应用同一份字节匹配的删除计划。未修改仓库默认构建配置、任何测试、断言或容差。初次失败和恢复证据均保留。

额外检查：Python AST、JavaScript 函数/类/导出和 PowerShell 函数的全仓词引用筛查没有产生新增可证明无用候选；同名、字符串分派和手动入口仍按保留处理。

## 逐项搜索原文

### 1. pageBitmapPresent

位置：`app/src/qa/java/com/m1racle/reader/ui/pdf/PdfLatePageCountHarnessActivity.kt:136`。

所属文件通过应用入口或 QA / core 模块可达，但此成员无调用、无函数引用、无测试调用，也无框架注解或生命周期 override。

全仓精确词搜索（删除前）：

```text
rg --json --hidden -w --glob !.git/** --glob !output/** --glob !**/build/** --glob !**/node_modules/** --glob !**/dist/** --glob !.gradle/** --glob !.kotlin/** --glob !.external_outbox/** --glob !.local-backups/** --glob !docs/UNUSED_CODE_AUDIT_20261106.md pageBitmapPresent .
app/src/qa/java/com/m1racle/reader/ui/pdf/PdfLatePageCountHarnessActivity.kt:136:     internal fun pageBitmapPresent(pageIndex: Int): Boolean = state.pageBitmaps.containsKey(pageIndex)
```

测试引用：0。完整原始结果：[JSONL](../output/unused-code-20261106/search-before-deletion/pageBitmapPresent.jsonl)。

### 2. clearInstanceForTests

位置：`core/database/src/main/java/com/m1racle/reader/data/local/AppDatabase.kt:573`。

所属文件通过应用入口或 QA / core 模块可达，但此成员无调用、无函数引用、无测试调用，也无框架注解或生命周期 override。

全仓精确词搜索（删除前）：

```text
rg --json --hidden -w --glob !.git/** --glob !output/** --glob !**/build/** --glob !**/node_modules/** --glob !**/dist/** --glob !.gradle/** --glob !.kotlin/** --glob !.external_outbox/** --glob !.local-backups/** --glob !docs/UNUSED_CODE_AUDIT_20261106.md clearInstanceForTests .
docs/code-wiki/reference/source-index.md:34: | [AppDatabase.kt](../../../app/src/main/java/com/m1racle/reader/data/local/AppDatabase.kt) | 577 | [AppDatabase](../../../app/src/main/java/com/m1racle/reader/data/local/AppDatabase.kt#L25) | [documentDao()](../../../app/src/main/java/com/m1racle/reader/data/local/AppDatabase.kt#L27) · [pdfPrintPageAnchorDao()](../../../app/src/main/java/com/m1racle/reader/data/local/AppDatabase.kt#L28) · [textbookOutlineNodeDao()](
core/database/src/main/java/com/m1racle/reader/data/local/AppDatabase.kt:573:         fun clearInstanceForTests() {
```

测试引用：0。完整原始结果：[JSONL](../output/unused-code-20261106/search-before-deletion/clearInstanceForTests.jsonl)。

### 3. pageRole

位置：`core/model/src/main/java/com/m1racle/reader/core/model/PdfRenderQuality.kt:185`。

所属文件通过应用入口或 QA / core 模块可达，但此成员无调用、无函数引用、无测试调用，也无框架注解或生命周期 override。

全仓精确词搜索（删除前）：

```text
rg --json --hidden -w --glob !.git/** --glob !output/** --glob !**/build/** --glob !**/node_modules/** --glob !**/dist/** --glob !.gradle/** --glob !.kotlin/** --glob !.external_outbox/** --glob !.local-backups/** --glob !docs/UNUSED_CODE_AUDIT_20261106.md pageRole .
core/model/src/main/java/com/m1racle/reader/core/model/PdfRenderQuality.kt:185:     fun pageRole(pageIndex: Int, primaryIndex: Int): PageRenderRole = when (pageIndex) {
docs/code-wiki/reference/source-index.md:166: | [PdfRenderQuality.kt](../../../app/src/main/java/com/m1racle/reader/pdf/PdfRenderQuality.kt) | 210 | [QualityTier](../../../app/src/main/java/com/m1racle/reader/pdf/PdfRenderQuality.kt#L17) · [PageRenderRole](../../../app/src/main/java/com/m1racle/reader/pdf/PdfRenderQuality.kt#L25) · [ContinuousQualityHysteresis](../../../app/src/main/java/com/m1racle/reader/pdf/PdfRenderQuality.kt#L35) · [PdfRenderQuality](../../
```

测试引用：0。完整原始结果：[JSONL](../output/unused-code-20261106/search-before-deletion/pageRole.jsonl)。

### 4. forMode

位置：`core/model/src/main/java/com/m1racle/reader/core/model/PdfViewportSessionState.kt:42`。

所属文件通过应用入口或 QA / core 模块可达，但此成员无调用、无函数引用、无测试调用，也无框架注解或生命周期 override。

全仓精确词搜索（删除前）：

```text
rg --json --hidden -w --glob !.git/** --glob !output/** --glob !**/build/** --glob !**/node_modules/** --glob !**/dist/** --glob !.gradle/** --glob !.kotlin/** --glob !.external_outbox/** --glob !.local-backups/** --glob !docs/UNUSED_CODE_AUDIT_20261106.md forMode .
core/model/src/main/java/com/m1racle/reader/core/model/PdfViewportSessionState.kt:42:     fun forMode(mode: PdfReadingMode, nextGeneration: Long = generation): PdfViewportSessionState = copy(
docs/code-wiki/reference/source-index.md:172: | [PdfViewportSessionState.kt](../../../app/src/main/java/com/m1racle/reader/pdf/PdfViewportSessionState.kt) | 115 | [PdfViewportSessionState](../../../app/src/main/java/com/m1racle/reader/pdf/PdfViewportSessionState.kt#L3) · [CanonicalPdfAnchor](../../../app/src/main/java/com/m1racle/reader/pdf/PdfViewportSessionState.kt#L55) · [PdfViewportSessionTracker](../../../app/src/main/java/com/m1racle/reader/pdf/PdfViewport
```

测试引用：0。完整原始结果：[JSONL](../output/unused-code-20261106/search-before-deletion/forMode.jsonl)。

### 5. clearCache

位置：`core/pdf/src/main/java/com/m1racle/reader/pdf/PdfThumbnailLoader.kt:115`。

所属文件通过应用入口或 QA / core 模块可达，但此成员无调用、无函数引用、无测试调用，也无框架注解或生命周期 override。

全仓精确词搜索（删除前）：

```text
rg --json --hidden -w --glob !.git/** --glob !output/** --glob !**/build/** --glob !**/node_modules/** --glob !**/dist/** --glob !.gradle/** --glob !.kotlin/** --glob !.external_outbox/** --glob !.local-backups/** --glob !docs/UNUSED_CODE_AUDIT_20261106.md clearCache .
core/pdf/src/main/java/com/m1racle/reader/pdf/PdfThumbnailLoader.kt:115:     suspend fun clearCache() {
docs/code-wiki/reference/source-index.md:169: | [PdfThumbnailLoader.kt](../../../app/src/main/java/com/m1racle/reader/pdf/PdfThumbnailLoader.kt) | 161 | [PdfThumbnailLoader](../../../app/src/main/java/com/m1racle/reader/pdf/PdfThumbnailLoader.kt#L20) · [ThumbResult](../../../app/src/main/java/com/m1racle/reader/pdf/PdfThumbnailLoader.kt#L41) · [Ready](../../../app/src/main/java/com/m1racle/reader/pdf/PdfThumbnailLoader.kt#L42) · [Loading](../../../app/src/main/j
```

测试引用：0。完整原始结果：[JSONL](../output/unused-code-20261106/search-before-deletion/clearCache.jsonl)。

### 6. intersects

位置：`core/pdf/src/main/java/com/m1racle/reader/pdf/PdfTileRendering.kt:16`。

所属文件通过应用入口或 QA / core 模块可达，但此成员无调用、无函数引用、无测试调用，也无框架注解或生命周期 override。

全仓精确词搜索（删除前）：

```text
rg --json --hidden -w --glob !.git/** --glob !output/** --glob !**/build/** --glob !**/node_modules/** --glob !**/dist/** --glob !.gradle/** --glob !.kotlin/** --glob !.external_outbox/** --glob !.local-backups/** --glob !docs/UNUSED_CODE_AUDIT_20261106.md intersects .
docs/code-wiki/reference/source-index.md:171: | [PdfTileRendering.kt](../../../app/src/main/java/com/m1racle/reader/pdf/PdfTileRendering.kt) | 586 | [PageLayoutRect](../../../app/src/main/java/com/m1racle/reader/pdf/PdfTileRendering.kt#L13) · [DocumentViewportRect](../../../app/src/main/java/com/m1racle/reader/pdf/PdfTileRendering.kt#L29) · [PdfPageRect](../../../app/src/main/java/com/m1racle/reader/pdf/PdfTileRendering.kt#L35) · [TilePixelRect](../../../app/src
core/pdf/src/main/java/com/m1racle/reader/pdf/PdfTileRendering.kt:16:     fun intersects(other: PageLayoutRect): Boolean =
app/src/main/java/com/m1racle/reader/ui/pdf/ContinuousDocumentViewport.kt:773:         //   1. The target page intersects the viewport (any part visible).
docs/R7_P0C1_PREDICTIVE_TILE_RENDERING_HANDOFF.md:27: `PdfTilePlanner` inverse-transforms the viewport, intersects only actually visible pages, and
```

测试引用：0。完整原始结果：[JSONL](../output/unused-code-20261106/search-before-deletion/intersects.jsonl)。

### 7. shouldPublishSettled

位置：`core/pdf/src/main/java/com/m1racle/reader/pdf/ZoomPageOverlayMath.kt:40`。

所属文件通过应用入口或 QA / core 模块可达，但此成员无调用、无函数引用、无测试调用，也无框架注解或生命周期 override。

全仓精确词搜索（删除前）：

```text
rg --json --hidden -w --glob !.git/** --glob !output/** --glob !**/build/** --glob !**/node_modules/** --glob !**/dist/** --glob !.gradle/** --glob !.kotlin/** --glob !.external_outbox/** --glob !.local-backups/** --glob !docs/UNUSED_CODE_AUDIT_20261106.md shouldPublishSettled .
core/pdf/src/main/java/com/m1racle/reader/pdf/ZoomPageOverlayMath.kt:40:     fun shouldPublishSettled(settledPage: Int, viewModelPage: Int): Boolean =
docs/code-wiki/reference/source-index.md:179: | [ZoomPageOverlayMath.kt](../../../app/src/main/java/com/m1racle/reader/pdf/ZoomPageOverlayMath.kt) | 42 | [OverlayRect](../../../app/src/main/java/com/m1racle/reader/pdf/ZoomPageOverlayMath.kt#L13) · [PageTap](../../../app/src/main/java/com/m1racle/reader/pdf/ZoomPageOverlayMath.kt#L21) · [ZoomPageOverlayMath](../../../app/src/main/java/com/m1racle/reader/pdf/ZoomPageOverlayMath.kt#L23) · [PagerRequestPolicy](../..
```

测试引用：0。完整原始结果：[JSONL](../output/unused-code-20261106/search-before-deletion/shouldPublishSettled.jsonl)。

### 8. touchLastOpened

位置：`app/src/main/java/com/m1racle/reader/data/repository/DocumentRepository.kt:321`。

所属文件通过应用入口或 QA / core 模块可达，但此成员无调用、无函数引用、无测试调用，也无框架注解或生命周期 override。

全仓精确词搜索（删除前）：

```text
rg --json --hidden -w --glob !.git/** --glob !output/** --glob !**/build/** --glob !**/node_modules/** --glob !**/dist/** --glob !.gradle/** --glob !.kotlin/** --glob !.external_outbox/** --glob !.local-backups/** --glob !docs/UNUSED_CODE_AUDIT_20261106.md touchLastOpened .
app/src/main/java/com/m1racle/reader/data/repository/DocumentRepository.kt:321:     suspend fun touchLastOpened(id: String, time: Long = System.currentTimeMillis()) {
docs/code-wiki/reference/source-index.md:55: | [DocumentRepository.kt](../../../app/src/main/java/com/m1racle/reader/data/repository/DocumentRepository.kt) | 1161 | [DocumentRepository](../../../app/src/main/java/com/m1racle/reader/data/repository/DocumentRepository.kt#L64) · [ResolvedImportRequest](../../../app/src/main/java/com/m1racle/reader/data/repository/DocumentRepository.kt#L1108) · [ResolvedImportCommit](../../../app/src/main/java/com/m1racle/reader/da
```

测试引用：0。完整原始结果：[JSONL](../output/unused-code-20261106/search-before-deletion/touchLastOpened.jsonl)。

### 9. clearAllOutlineNodes

位置：`app/src/main/java/com/m1racle/reader/data/repository/DocumentRepository.kt:771`。

所属文件通过应用入口或 QA / core 模块可达，但此成员无调用、无函数引用、无测试调用，也无框架注解或生命周期 override。

全仓精确词搜索（删除前）：

```text
rg --json --hidden -w --glob !.git/** --glob !output/** --glob !**/build/** --glob !**/node_modules/** --glob !**/dist/** --glob !.gradle/** --glob !.kotlin/** --glob !.external_outbox/** --glob !.local-backups/** --glob !docs/UNUSED_CODE_AUDIT_20261106.md clearAllOutlineNodes .
app/src/main/java/com/m1racle/reader/data/repository/DocumentRepository.kt:771:     suspend fun clearAllOutlineNodes(documentId: String) {
docs/code-wiki/reference/source-index.md:55: | [DocumentRepository.kt](../../../app/src/main/java/com/m1racle/reader/data/repository/DocumentRepository.kt) | 1161 | [DocumentRepository](../../../app/src/main/java/com/m1racle/reader/data/repository/DocumentRepository.kt#L64) · [ResolvedImportRequest](../../../app/src/main/java/com/m1racle/reader/data/repository/DocumentRepository.kt#L1108) · [ResolvedImportCommit](../../../app/src/main/java/com/m1racle/reader/da
```

测试引用：0。完整原始结果：[JSONL](../output/unused-code-20261106/search-before-deletion/clearAllOutlineNodes.jsonl)。

### 10. fromDocument

位置：`app/src/main/java/com/m1racle/reader/markdown/MarkdownAssetResolver.kt:109`。

所属文件通过应用入口或 QA / core 模块可达，但此成员无调用、无函数引用、无测试调用，也无框架注解或生命周期 override。

全仓精确词搜索（删除前）：

```text
rg --json --hidden -w --glob !.git/** --glob !output/** --glob !**/build/** --glob !**/node_modules/** --glob !**/dist/** --glob !.gradle/** --glob !.kotlin/** --glob !.external_outbox/** --glob !.local-backups/** --glob !docs/UNUSED_CODE_AUDIT_20261106.md fromDocument .
docs/code-wiki/reference/source-index.md:121: | [MarkdownAssetResolver.kt](../../../app/src/main/java/com/m1racle/reader/markdown/MarkdownAssetResolver.kt) | 474 | [MarkdownDocumentBase](../../../app/src/main/java/com/m1racle/reader/markdown/MarkdownAssetResolver.kt#L19) · [AppOwnedDirectory](../../../app/src/main/java/com/m1racle/reader/markdown/MarkdownAssetResolver.kt#L21) · [FileDirectory](../../../app/src/main/java/com/m1racle/reader/markdown/MarkdownAssetR
app/src/main/java/com/m1racle/reader/markdown/MarkdownAssetResolver.kt:81:  * [fromDocument] needs [Context] only to classify app-owned paths.
app/src/main/java/com/m1racle/reader/markdown/MarkdownAssetResolver.kt:109:     fun fromDocument(context: Context, document: DocumentEntity): MarkdownDocumentBase {
docs/reviews/20261031-reader-v93-review/STATUS.md:21: - MarkdownAssetResolver.kt:101-106 `fromDocument()` 已经会对 MARKDOWN 优先用 sourceUri，但全仓没有调用者。
docs/reviews/20261031-reader-v93-review/STATUS.md:23: - 限制：sourceUri 为 content:// 时，即使改用 fromDocument 也只得到 SingleContentDocument，仍无法解析相对路径，修复需另定方案。
docs/reviews/20261031-md-repro/STATUS.md:7: - F2：MarkdownAssetResolver.imageBaseUriFor(document)：MARKDOWN 且 sourceUri 为 file:// 时用原文件，否则用书库副本。fromDocument 与 ViewerScreen 都改用它。
```

测试引用：0。完整原始结果：[JSONL](../output/unused-code-20261106/search-before-deletion/fromDocument.jsonl)。

### 11. isInPageAnchor

位置：`app/src/main/java/com/m1racle/reader/markdown/MarkdownLinkRouter.kt:17`。

所属文件通过应用入口或 QA / core 模块可达，但此成员无调用、无函数引用、无测试调用，也无框架注解或生命周期 override。

全仓精确词搜索（删除前）：

```text
rg --json --hidden -w --glob !.git/** --glob !output/** --glob !**/build/** --glob !**/node_modules/** --glob !**/dist/** --glob !.gradle/** --glob !.kotlin/** --glob !.external_outbox/** --glob !.local-backups/** --glob !docs/UNUSED_CODE_AUDIT_20261106.md isInPageAnchor .
docs/code-wiki/reference/source-index.md:123: | [MarkdownLinkRouter.kt](../../../app/src/main/java/com/m1racle/reader/markdown/MarkdownLinkRouter.kt) | 43 | [MarkdownLinkRouter](../../../app/src/main/java/com/m1racle/reader/markdown/MarkdownLinkRouter.kt#L10) · [Action](../../../app/src/main/java/com/m1racle/reader/markdown/MarkdownLinkRouter.kt#L12) · [InPageAnchor](../../../app/src/main/java/com/m1racle/reader/markdown/MarkdownLinkRouter.kt#L13) · [External](.
app/src/main/java/com/m1racle/reader/markdown/MarkdownLinkRouter.kt:17:     fun isInPageAnchor(destination: String): Boolean =
```

测试引用：0。完整原始结果：[JSONL](../output/unused-code-20261106/search-before-deletion/isInPageAnchor.jsonl)。

### 12. hintZh

位置：`app/src/main/java/com/m1racle/reader/pdf/export/ExportRangeResolver.kt:258`。

所属文件通过应用入口或 QA / core 模块可达，但此成员无调用、无函数引用、无测试调用，也无框架注解或生命周期 override。

全仓精确词搜索（删除前）：

```text
rg --json --hidden -w --glob !.git/** --glob !output/** --glob !**/build/** --glob !**/node_modules/** --glob !**/dist/** --glob !.gradle/** --glob !.kotlin/** --glob !.external_outbox/** --glob !.local-backups/** --glob !docs/UNUSED_CODE_AUDIT_20261106.md hintZh .
app/src/main/java/com/m1racle/reader/pdf/export/ExportRangeResolver.kt:258:     fun hintZh(): String = when (this) {
docs/code-wiki/reference/source-index.md:197: | [ExportRangeResolver.kt](../../../app/src/main/java/com/m1racle/reader/pdf/export/ExportRangeResolver.kt) | 242 | [EndPageSource](../../../app/src/main/java/com/m1racle/reader/pdf/export/ExportRangeResolver.kt#L8) · [ExportRangePlan](../../../app/src/main/java/com/m1racle/reader/pdf/export/ExportRangeResolver.kt#L33) · [ExportRangeResolver](../../../app/src/main/java/com/m1racle/reader/pdf/export/ExportRangeResolve
docs/R25_ADVANCED_FOREGROUND_CARD_UNIFICATION_REPORT.md:67: | 导出章节 | CONTENT | 章节摘要一行「… · PDF 22–23」+ 更换章节 toggle（radio 列表 heightIn 200）；两个格式 radio（labelZh/hintZh 短说明）；页码范围两个 compact 字段同行；取消/选择保存位置（系统 SAF 流程接管） |
```

测试引用：0。完整原始结果：[JSONL](../output/unused-code-20261106/search-before-deletion/hintZh.jsonl)。

### 13. dismissError

位置：`app/src/main/java/com/m1racle/reader/share/ExternalOpenCoordinator.kt:352`。

所属文件通过应用入口或 QA / core 模块可达，但此成员无调用、无函数引用、无测试调用，也无框架注解或生命周期 override。

全仓精确词搜索（删除前）：

```text
rg --json --hidden -w --glob !.git/** --glob !output/** --glob !**/build/** --glob !**/node_modules/** --glob !**/dist/** --glob !.gradle/** --glob !.kotlin/** --glob !.external_outbox/** --glob !.local-backups/** --glob !docs/UNUSED_CODE_AUDIT_20261106.md dismissError .
docs/code-wiki/reference/source-index.md:216: | [ExternalOpenCoordinator.kt](../../../app/src/main/java/com/m1racle/reader/share/ExternalOpenCoordinator.kt) | 355 | [ExternalOpenCoordinator](../../../app/src/main/java/com/m1racle/reader/share/ExternalOpenCoordinator.kt#L25) | [ackNavigation()](../../../app/src/main/java/com/m1racle/reader/share/ExternalOpenCoordinator.kt#L86) · [onViewerOpened()](../../../app/src/main/java/com/m1racle/reader/share/ExternalOpenCo
app/src/main/java/com/m1racle/reader/share/ExternalOpenCoordinator.kt:352:     fun dismissError() {
```

测试引用：0。完整原始结果：[JSONL](../output/unused-code-20261106/search-before-deletion/dismissError.jsonl)。

### 14. StatusBarProtection

位置：`app/src/main/java/com/m1racle/reader/ui/common/StatusBarProtection.kt:35`。

整文件仅声明此组件及其私有使用的高度常量；没有 import、Compose 调用、Manifest、资源、测试或脚本入口。

全仓精确词搜索（删除前）：

```text
rg --json --hidden -w --glob !.git/** --glob !output/** --glob !**/build/** --glob !**/node_modules/** --glob !**/dist/** --glob !.gradle/** --glob !.kotlin/** --glob !.external_outbox/** --glob !.local-backups/** --glob !docs/UNUSED_CODE_AUDIT_20261106.md StatusBarProtection .
docs/code-wiki/reference/source-index.md:241: | [StatusBarProtection.kt](../../../app/src/main/java/com/m1racle/reader/ui/common/StatusBarProtection.kt) | 63 | — | [StatusBarProtection()](../../../app/src/main/java/com/m1racle/reader/ui/common/StatusBarProtection.kt#L34) |
docs/HOME_CONTENT_FIRST_LAYOUT_REPORT.md:63: - 无 StatusBarProtection 厚洗、无折叠大玻璃板
app/src/main/java/com/m1racle/reader/ui/common/StatusBarProtection.kt:35: fun StatusBarProtection(
```

测试引用：0。完整原始结果：[JSONL](../output/unused-code-20261106/search-before-deletion/StatusBarProtection.jsonl)。

### 15. formatMeta

位置：`app/src/main/java/com/m1racle/reader/ui/home/HomeScreen.kt:296`。

所属文件通过应用入口或 QA / core 模块可达，但此成员无调用、无函数引用、无测试调用，也无框架注解或生命周期 override。

全仓精确词搜索（删除前）：

```text
rg --json --hidden -w --glob !.git/** --glob !output/** --glob !**/build/** --glob !**/node_modules/** --glob !**/dist/** --glob !.gradle/** --glob !.kotlin/** --glob !.external_outbox/** --glob !.local-backups/** --glob !docs/UNUSED_CODE_AUDIT_20261106.md formatMeta .
docs/APP_WIDE_SCROLL_AND_MAIN_THREAD_STALL_ISOLATION_REPORT.md:33: | Scroll-path residual | `formatMeta` / relative time every recompose (light); not multi-second |
docs/APP_WIDE_SCROLL_AND_MAIN_THREAD_STALL_ISOLATION_REPORT.md:188: 2. **Home warm:** profile `DocumentCard` recompose (`formatMeta` remember, contentType, reduce theme reads).
app/src/main/java/com/m1racle/reader/ui/home/HomeScreen.kt:296: internal fun formatMeta(document: DocumentEntity): String = formatListMetadata(document)
docs/code-wiki/reference/source-index.md:286: | [HomeScreen.kt](../../../app/src/main/java/com/m1racle/reader/ui/home/HomeScreen.kt) | 287 | [DocumentCardTitles](../../../app/src/main/java/com/m1racle/reader/ui/home/HomeScreen.kt#L201) | [HomeScreen()](../../../app/src/main/java/com/m1racle/reader/ui/home/HomeScreen.kt#L63) · [HomeContent()](../../../app/src/main/java/com/m1racle/reader/ui/home/HomeScreen.kt#L95) · [resolveDocumentCardTitles()](../../../app/src/
docs/HOME_PLAIN_CONTROLS_RESTORE_REPORT.md:60: - **HomeViewModel / formatMeta / 标题解析** 等逻辑 —— 零改动。
docs/R10_HOME_LIST_CARD_PERFORMANCE_REPORT.md:53: Candidate: separate LazyColumn header/card contentType, remember formatMeta(document), and reuse the already-read meta color.
```

测试引用：0。完整原始结果：[JSONL](../output/unused-code-20261106/search-before-deletion/formatMeta.jsonl)。

### 16. endHover

位置：`app/src/main/java/com/m1racle/reader/ui/material/GlassInteraction.kt:107`。

所属文件通过应用入口或 QA / core 模块可达，但此成员无调用、无函数引用、无测试调用，也无框架注解或生命周期 override。

全仓精确词搜索（删除前）：

```text
rg --json --hidden -w --glob !.git/** --glob !output/** --glob !**/build/** --glob !**/node_modules/** --glob !**/dist/** --glob !.gradle/** --glob !.kotlin/** --glob !.external_outbox/** --glob !.local-backups/** --glob !docs/UNUSED_CODE_AUDIT_20261106.md endHover .
docs/code-wiki/reference/source-index.md:313: | [GlassInteraction.kt](../../../app/src/main/java/com/m1racle/reader/ui/material/GlassInteraction.kt) | 189 | [GlassInteractionState](../../../app/src/main/java/com/m1racle/reader/ui/material/GlassInteraction.kt#L24) · [GlassInteractionTransitions](../../../app/src/main/java/com/m1racle/reader/ui/material/GlassInteraction.kt#L33) · [GlassInteractionContext](../../../app/src/main/java/com/m1racle/reader/ui/material/G
app/src/main/java/com/m1racle/reader/ui/material/GlassInteraction.kt:107:     fun endHover(): Boolean = transitionTo(GlassInteractionState.IDLE)
```

测试引用：0。完整原始结果：[JSONL](../output/unused-code-20261106/search-before-deletion/endHover.jsonl)。

### 17. dismissExportPlan

位置：`app/src/main/java/com/m1racle/reader/ui/pdf/PdfViewerViewModel.kt:1819`。

所属文件通过应用入口或 QA / core 模块可达，但此成员无调用、无函数引用、无测试调用，也无框架注解或生命周期 override。

全仓精确词搜索（删除前）：

```text
rg --json --hidden -w --glob !.git/** --glob !output/** --glob !**/build/** --glob !**/node_modules/** --glob !**/dist/** --glob !.gradle/** --glob !.kotlin/** --glob !.external_outbox/** --glob !.local-backups/** --glob !docs/UNUSED_CODE_AUDIT_20261106.md dismissExportPlan .
docs/code-wiki/reference/source-index.md:344: | [PdfViewerViewModel.kt](../../../app/src/main/java/com/m1racle/reader/ui/pdf/PdfViewerViewModel.kt) | 2661 | [PdfViewerUiState](../../../app/src/main/java/com/m1racle/reader/ui/pdf/PdfViewerViewModel.kt#L93) · [Loading](../../../app/src/main/java/com/m1racle/reader/ui/pdf/PdfViewerViewModel.kt#L94) · [Ready](../../../app/src/main/java/com/m1racle/reader/ui/pdf/PdfViewerViewModel.kt#L96) · [Error](../../../app/src/m
app/src/main/java/com/m1racle/reader/ui/pdf/PdfViewerViewModel.kt:1819:     fun dismissExportPlan() {
```

测试引用：0。完整原始结果：[JSONL](../output/unused-code-20261106/search-before-deletion/dismissExportPlan.jsonl)。

### 18. onOptionSelected

位置：`app/src/main/java/com/m1racle/reader/ui/pdf/ReaderMoreHost.kt:173`。

所属文件通过应用入口或 QA / core 模块可达，但此成员无调用、无函数引用、无测试调用，也无框架注解或生命周期 override。

全仓精确词搜索（删除前）：

```text
rg --json --hidden -w --glob !.git/** --glob !output/** --glob !**/build/** --glob !**/node_modules/** --glob !**/dist/** --glob !.gradle/** --glob !.kotlin/** --glob !.external_outbox/** --glob !.local-backups/** --glob !docs/UNUSED_CODE_AUDIT_20261106.md onOptionSelected .
app/src/main/java/com/m1racle/reader/ui/pdf/ReaderMoreHost.kt:173:     fun onOptionSelected(current: ReaderMoreState): ReaderMoreState = ReaderMoreState.MAIN
docs/code-wiki/reference/source-index.md:347: | [ReaderMoreHost.kt](../../../app/src/main/java/com/m1racle/reader/ui/pdf/ReaderMoreHost.kt) | 1031 | [ReaderMoreState](../../../app/src/main/java/com/m1racle/reader/ui/pdf/ReaderMoreHost.kt#L93) · [ReaderMoreStateMachine](../../../app/src/main/java/com/m1racle/reader/ui/pdf/ReaderMoreHost.kt#L155) · [PdfChromeMenuItem](../../../app/src/main/java/com/m1racle/reader/ui/pdf/ReaderMoreHost.kt#L243) · [PdfChromeMenuOpti
docs/R22_READER_CHROME_CONTAINER_MORPH_REPORT.md:53: - `onOptionSelected`：→Main（选中二级即收起）
docs/R36_INTERACTION_ARCHITECTURE_AUDIT.md:13: | S4 | Advanced child（页面映射/目录/缩略图/导出） | SettingsCard（popover） | S2 → PREPARING_*（先收 Basic、等 Main 几何）→ CHILD_* morph（MoreMorphSpring：宽/高 animateTo，content 0.94→1.0 scale） | RootAndMenu | 菜单主体 | MAIN（onOptionSelected 回 MAIN；collapse 路径） | foreground transient | 容器 morph；document 不动 |
docs/R36_INTERACTION_ARCHITECTURE_AUDIT.md:27: | MAIN→PREPARING_*→CHILD_* | 是（两阶段：先收 Basic 复原 Main 几何再 morph） | 否（单向；回 MAIN 经 onOptionSelected/collapse） | 否（MoreMorphSpring 宽/高 + 0.94 scale，damping .8 / stiffness 450） | 否 | 否 | S3/S4 互斥（r26.1 单一 exclusive state） |
```

测试引用：0。完整原始结果：[JSONL](../output/unused-code-20261106/search-before-deletion/onOptionSelected.jsonl)。

### 19. mustCollapseInlineForChild

位置：`app/src/main/java/com/m1racle/reader/ui/pdf/ReaderMoreTransitions.kt:108`。

所属文件通过应用入口或 QA / core 模块可达，但此成员无调用、无函数引用、无测试调用，也无框架注解或生命周期 override。

全仓精确词搜索（删除前）：

```text
rg --json --hidden -w --glob !.git/** --glob !output/** --glob !**/build/** --glob !**/node_modules/** --glob !**/dist/** --glob !.gradle/** --glob !.kotlin/** --glob !.external_outbox/** --glob !.local-backups/** --glob !docs/UNUSED_CODE_AUDIT_20261106.md mustCollapseInlineForChild .
docs/code-wiki/reference/source-index.md:348: | [ReaderMoreTransitions.kt](../../../app/src/main/java/com/m1racle/reader/ui/pdf/ReaderMoreTransitions.kt) | 110 | [ReaderMoreBackAction](../../../app/src/main/java/com/m1racle/reader/ui/pdf/ReaderMoreTransitions.kt#L10) · [SectionToggleDecision](../../../app/src/main/java/com/m1racle/reader/ui/pdf/ReaderMoreTransitions.kt#L21) · [OpenChildDecision](../../../app/src/main/java/com/m1racle/reader/ui/pdf/ReaderMoreTran
app/src/main/java/com/m1racle/reader/ui/pdf/ReaderMoreTransitions.kt:108:     fun mustCollapseInlineForChild(current: ReaderMoreState): Boolean =
```

测试引用：0。完整原始结果：[JSONL](../output/unused-code-20261106/search-before-deletion/mustCollapseInlineForChild.jsonl)。

### 20. currentStage

位置：`app/src/main/java/com/m1racle/reader/ui/reader/ReaderHighlightController.kt:74`。

所属文件通过应用入口或 QA / core 模块可达，但此成员无调用、无函数引用、无测试调用，也无框架注解或生命周期 override。

全仓精确词搜索（删除前）：

```text
rg --json --hidden -w --glob !.git/** --glob !output/** --glob !**/build/** --glob !**/node_modules/** --glob !**/dist/** --glob !.gradle/** --glob !.kotlin/** --glob !.external_outbox/** --glob !.local-backups/** --glob !docs/UNUSED_CODE_AUDIT_20261106.md currentStage .
docs/code-wiki/reference/source-index.md:362: | [ReaderHighlightController.kt](../../../app/src/main/java/com/m1racle/reader/ui/reader/ReaderHighlightController.kt) | 73 | [ReaderHighlightController](../../../app/src/main/java/com/m1racle/reader/ui/reader/ReaderHighlightController.kt#L16) · [CommitResult](../../../app/src/main/java/com/m1racle/reader/ui/reader/ReaderHighlightController.kt#L21) · [Success](../../../app/src/main/java/com/m1racle/reader/ui/reader/R
app/src/main/java/com/m1racle/reader/ui/reader/ReaderHighlightController.kt:74:     fun currentStage(): AnnotationCreationFlow.Stage = flow.current()
```

测试引用：0。完整原始结果：[JSONL](../output/unused-code-20261106/search-before-deletion/currentStage.jsonl)。
