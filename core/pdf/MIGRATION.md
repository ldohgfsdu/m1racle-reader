# core:pdf migration

日期：2026-11-06

## 边界

- Android library 路径：`core/pdf`。
- Gradle 模块名：`:core:pdf`。
- Kotlin 包名保持 `com.m1racle.reader.pdf`。
- `core:pdf` 依赖 `:core:model`、Compose UI、coroutines、PDFBox 和 Android 平台。
- `core:pdf` 不依赖 app、`:core:database`、data、repository、Room、ViewModel 或 UI 页面。

## 已迁移

34 个核心实现已迁入 `core/pdf/src/main/java/com/m1racle/reader/pdf/`：

- PDFBox 文件直读与固定 scratch：`PdfBoxRuntime.kt`
- Android `PdfRenderer` 整页和分块渲染：`PdfPageRenderer.kt`、`PdfTileRendering.kt`
- 缩略图、位图缓存和分块队列：`PdfThumbnailLoader.kt`、`PdfPageBitmapCache.kt`、`PdfTilePipeline.kt`、`TileWorkQueue.kt`
- 内置目录解析：`PdfEmbeddedOutlineParser.kt`、`PdfEmbeddedOutlineModels.kt`、`OutlinePageIndexResolver.kt`
- 文字提取：`PdfTextSelection.kt`
- 连续、横向、缩放、页条、chrome 状态及几何逻辑
- 印刷页段与解析：`PrintPageResolver.kt`、`PrintPageSection.kt`

迁移保留现有 PDFBox 2.0.27 和 Android `PdfRenderer`，没有更换引擎，也没有增加全书页尺寸预渲染。

`PdfTextSelectionReader.extract(document, pageIndex, checkActive)` 是已打开文档上的核心提取入口。它保留给持有文档生命周期的调用方和跨模块回归测试；页面读取仍通过 `read(context, uri, page)` 在 IO 调度器执行。

## 保留在 app

- `PdfReadingPrefs.kt`：SharedPreferences。
- `PdfImmersiveSystemBars.kt`：系统栏界面行为。
- `FinalPdfProgressFlusher.kt`：repository 进度写入协调。
- `PrintPageAnchors.kt`：Room `PdfPrintPageAnchorEntity` 到中性 `PrintPageAnchor` 的转换适配器。
- `pdf/export/**`、`pdf/edit/**`：按授权边界未移动。

`PdfEmbeddedOutlineImporter` 不属于 `core:pdf`。旧 importer 及其持久化编排、测试清理由 database worker 负责；`core:pdf` 只保留目录解析、页面索引和 Draft 构造。

## 印刷页锚点

- 中性模型：`core/model/src/main/java/com/m1racle/reader/core/model/PrintPageAnchor.kt`。
- app Room 实体转换：`app/src/main/java/com/m1racle/reader/pdf/PrintPageAnchors.kt`。
- `PrintPageResolver` 和 `PrintPageSection` 只消费 `PrintPageAnchor`，不引用数据库实体。
- 三处 print 调用已由调用方先转换为中性模型：

```kotlin
PrintPageResolver.resolve(start, printAnchors.toPrintPageAnchors())
PrintPageResolver.resolve(end, printAnchors.toPrintPageAnchors())
PrintPageResolver.resolve(source0, printAnchors.toPrintPageAnchors())
PrintPageResolver.resolveDetailed(
    documentId = documentId,
    pdfPageIndex = pageIndex,
    anchors = printAnchors.toPrintPageAnchors(),
    sections = printSections,
)
```

没有保留同名模型或 app 兼容薄层。`OutlineImportMode` 的唯一权威定义位于 `core/model`。

## 大文件 IO 与失败边界

- 本地 `file://` 使用 `PDDocument.load(File, MemoryUsageSetting)` 随机读取，不先复制整本书。
- 非本地 URI 因 provider 输入流是顺序流，仍需由 PDFBox 缓冲，但所有临时文件固定写入 `cacheDir/pdfbox-scratch`。
- `ensureInit(context)` 会在初始化前创建并写删探测 scratch 目录。目录不可创建、不是目录或不可写时明确抛出 `IOException`。
- `memorySetting(context)` 固定使用 `setupTempFileOnly()` 和 app cache scratch，不存在 `setupMainMemoryOnly()` fallback。
- 无参 `memorySetting()` 只能在一次成功的 `ensureInit(context)` 后调用；未初始化时抛出 `IllegalStateException`，不会退回进程级临时目录。

## Gradle 接线

主代理负责根构建文件；当前接线要求如下。

`settings.gradle.kts`：

```kotlin
include(":core:pdf")
```

`app/build.gradle.kts`：

```kotlin
implementation(project(":core:pdf"))
```

`core/pdf/build.gradle.kts` 所需依赖：

```kotlin
api(project(":core:model"))
api(platform("androidx.compose:compose-bom:2026.05.00"))
api("androidx.compose.ui:ui")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
api("com.tom-roush:pdfbox-android:2.0.27.0")
```

`androidx.compose.ui:ui` 和 PDFBox 通过 `api` 暴露，是因为核心公开类型包含 Compose `ImageBitmap` / `Offset` 和 PDFBox `PDDocument`。app 的 export/edit 仍直接引用 PDFBox，因此 app 可保留现有直接依赖。不要给 `core:pdf` 增加 `:core:database`、Room、repository、ViewModel 或 app 依赖。

## 测试路由

- `core/pdf/src/test` 保留 39 份核心运行时和纯逻辑测试，包括 `PdfBoxRuntimeLargeFileTest`、目录解析、渲染几何及 `PrintPageResolverTest`。
- 直接读取 app UI 源码或使用 `ProductionSource` 的守卫测试保留在 `app/src/test`：`AdvancedGeometryTest`、`ContinuousViewportHotPathContractTest`、`R71FrameStabilityTest`、`R7ContinuousViewportTest`、`R7H11bHorizontalSharedPhysicsTest`、`R7H14UnifiedGestureArchitectureTest`、`R7H15PlatformScrollAndPageStripTest`、`R7H15ThreePageStripTest`。
- `ProductionSource` 只有 app 测试 helper 一份，继续解析 app 与 core 的权威源码路径；没有新增复制 helper，也没有让 core 测试反向依赖 app。
- 跨 app 与 core 的 `PdfTextSelectionTest` 保留在 app，覆盖锚点、UI 命中和公开的 `PdfTextSelectionReader.extract` 核心入口；原有断言及主代理对 R71/R7H15 的修正均保留。

## 验证状态

- 已完成静态核对：34 个核心实现、39 份 core 测试、无 core 到 app/data/database/UI/ViewModel 依赖、无重复 `OutlineImportMode`、无 core 测试引用 `ProductionSource`。
- 模块文件搬迁冻结时间：2026-11-06 03:22 +08:00；此后仅进行可见性、测试路由和文档收尾。
- 本 worker 未独自启动 Gradle、模拟器或设备。模块文件搬迁已冻结，等待主代理统一执行 `:core:pdf:testReleaseUnitTest`、`:app:testReleaseUnitTest`、lint 和正式 Release 构建。
