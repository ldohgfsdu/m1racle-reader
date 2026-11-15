# S1c Markdown 依赖分诊（只读，未删除任何代码）

日期：2026-11-15
基线：`refactor/pdf-only` @ `68c3814`
方法：按**完全限定名**（`com.m1racle.reader.markdown.X` 的 import 语句）统计，不用裸名。
范围：`app/src/main/**` + `core/**`，测试源集单独标注。

---

## 0. 必须先说的一个口径问题

裸名 grep 会把两个同名类型混在一起：

| 类型 | 位置 | 性质 |
|---|---|---|
| `com.m1racle.reader.markdown.MarkdownBlock` | `markdown/MarkdownBlock.kt` | **解析器块模型**，随 Markdown 退役 |
| `DocumentAnchor.MarkdownBlock` | `core/model/DocumentAnchor.kt` | **锚点枚举分支**，历史书签与标注的坐标类型，必须保留 |

`ui/annotation/SelectionModel`、`AnnotationOverlay`、`data/repository/SearchRepository` 命中的都是后者。按裸名统计会得出「PDF 标注依赖 Markdown 解析器」的错误结论，进而删掉 PDF 还在用的类型。

本报告全部按 FQN 统计。同名误判只有一处残留：`search/SearchMatch.kt:18` 的 KDoc 注释里写了一次 `com.m1racle.reader.markdown.MarkdownBlock.ListBlock`，那是文档链接，不是代码依赖。

---

## 1. 总览

`markdown` 包：**12 文件 / 2,552 行**。

### 外部消费者只有 5 处

| 消费文件 | import 的符号 | 行 |
|---|---|---|
| `ui/home/HomeScreen.kt` | `DocumentListTitle` | 1 |
| `search/MarkdownSearchTextExtractor.kt` | `InlineMarkdownParser`, `MarkdownBlock` | 2 |
| `search/DocumentSearch.kt` | `MarkdownBlock` | 2 |
| `data/repository/DocumentRepository.kt` | `MarkdownParser`, `MarkdownAnnotationRemapper` | 2 |
| `image/LocalImageLoader.kt` | `MarkdownAssetResolver` | 1 |

### 包内依赖图

```
MarkdownParser        -> AnchorRegistry, InlineMarkdownParser, MarkdownAnchorSlug,
                         MarkdownBlock, MarkdownLinkRouter, MarkdownPerformance, TocItem
AnchorRegistry        -> MarkdownAnchorSlug
DocumentListTitle     -> InlineMarkdownParser
InlineMarkdownParser  -> MarkdownPerformance
MarkdownAnnotationRemapper -> MarkdownBlock
MarkdownLinkRouter    -> InlineMarkdownParser
```

`MarkdownSource` **包内包外都没有引用**，是纯孤儿。

### 两个「生产零调用」的消费者

这是本次分诊最重要的发现，靠 FQN + 调用点两轮统计得出：

| 文件 | 生产调用点 | 仅剩 |
|---|---|---|
| `search/DocumentSearch.kt`（189 行） | **0** | 5 个测试类 |
| `image/LocalImageLoader.kt` | **0** | 4 个测试类 |

`DocumentSearch` 是块级文档内搜索，调用方只有 `MarkdownSearchTextExtractor`（它自己也只被 `DocumentSearch` 用）。批 A 删掉 Markdown 阅读器后，这条搜索链路失去了唯一入口。

**PDF 阅读器没有任何文档内搜索实现**（`ui/pdf` 里 grep 不到 `searchText`/`findText`/`SearchQuery`）。所以产品范围里的「搜索」目前只有文库搜索（`SearchRepository`，索引 `document.title` / `displayName` / highlight 摘要），那条链路不依赖 markdown 包。

`LocalImageLoader` 是 Markdown 渲染器加载本地图片用的。PDF 走 `PdfRenderer`，不需要它。

---

## 2. A 类：可删除

按删除顺序排列，每批独立可编译、可回退。

### A1 `LocalImageLoader` 与其图片解析依赖

| 项 | 内容 |
|---|---|
| 文件 | `image/LocalImageLoader.kt`（342 行）、`markdown/MarkdownAssetResolver.kt`（478 行） |
| 引用位置 | `LocalImageLoader.kt:13-15` import 三个 markdown 符号；`packageimport/ZipPackageImporter` 也被它 import |
| 调用入口 | **无**。生产代码 0 调用 |
| 数据风险 | 无。不写数据库，不碰已存记录 |
| 验证 | 删除后编译通过；`LocalImageSampleTest`、`RelativeImageResolveTest`、`MarkdownAssetResolverTest`、`PackageImageIntegrationTest` 随之删除（它们只测这条死链路） |
| 备注 | `MarkdownAssetResolver` 是第二大文件（478/2,552 = 18.7%），最大的是 `InlineMarkdownParser`（717 行，28.1%） |

### A2 块级搜索链路

| 项 | 内容 |
|---|---|
| 文件 | `search/DocumentSearch.kt`（189）、`search/MarkdownSearchTextExtractor.kt`（149）、`search/SearchMatch.kt`（26）、`markdown/InlineMarkdownParser.kt`（717） |
| 引用位置 | `DocumentSearch.kt:3-4` import `MarkdownBlock`/`blockId`；`MarkdownSearchTextExtractor.kt:3-5` import `InlineMarkdownParser`/`MarkdownBlock` |
| 调用入口 | `DocumentSearch.search(...)` 生产 0 调用；`SearchMatch` 只被 `DocumentSearch` 产生 |
| 数据风险 | 无。纯计算，不落库 |
| 验证 | 删除 `DocumentSearchTest`、`DocumentSearchIsolationTest`、`DocumentSearchUnicodeOffsetTest`、`MarkdownSearchTextExtractorTest`、`SearchHighlightTest`、`WholeBlockHighlightRegressionTest`；文库搜索（`SearchRepository`）必须仍绿 |
| 注意 | `InlineMarkdownParser` 还被 `DocumentListTitle` 用（B1），所以它必须最后删 |

### A3 `DocumentRepository.rebaseMarkdownAssets`

| 项 | 内容 |
|---|---|
| 文件 | `DocumentRepository.kt:115-203`（88 行） |
| 引用位置 | 唯一调用点 `DocumentRepository.kt:241`：`if (before.type == DocumentType.MARKDOWN) rebaseMarkdownAssets(...)` |
| 调用入口 | `saveDocumentEdits`（PDF 编辑保存也走这里，但只在 MARKDOWN 类型时进入该函数） |
| 数据风险 | **低**。它只在 Markdown 正文被改写后迁移高亮/笔记/书签锚点。Markdown 编辑器已删，这条路径不可达 |
| 验证 | 删除后 PDF 编辑保存仍能提交（`saveDocumentEdits` 的 PDF 分支不动）；同时把 `:211` 的 `check(before.isPdf \|\| before.type == MARKDOWN)` 收紧为 `check(before.isPdf)` |
| 连带 | `MarkdownParser`、`MarkdownAnnotationRemapper` 的两个 import 一并消失 |

### A4 教材包（ZIP）导入与导出

| 项 | 内容 |
|---|---|
| 文件 | `packageimport/ZipPackageImporter.kt`（272）、`packageimport/MarkdownPackageExport.kt`（56） |
| 引用位置 | `DocumentRepository.kt:419, 635, 660, 663, 1208`；另被 `LocalImageLoader`、`MarkdownAssetResolver`（A1 已删） |
| 调用入口 | `exportDocument` 的 `DocumentType.MARKDOWN_PACKAGE` 分支（`:324-337`）；导入路径的 ZIP 分支 |
| 数据风险 | **中**。库里若存在 `MARKDOWN_PACKAGE` 类型的历史记录，删除后「导出文件」对它们会走 else 分支，把已解包的 `.md` 入口当普通文件导出，而不是重建 ZIP。**必须先在 UI 层拦掉这条路径并给提示**，不能让用户拿到一个语义错误的文件 |
| 验证 | 现有 6 个 `MarkdownPackageExportTest` 用例随功能退役一并删除（它们正是当前唯一的 6 个失败）；`exportDocument` 的 PDF 分支必须有独立测试覆盖 |
| 备注 | `DocumentRepository.MAX_MARKDOWN_BYTES`（`:1208`）指向 `ZipPackageImporter.MAX_MARKDOWN_BYTES`，删除时一并移除，PDF 用 `PDF_MAX_BYTES` |

### A5 `markdown` 包本体

| 项 | 内容 |
|---|---|
| 文件 | 剩余 12 文件（A1/A2 已删 3 个，剩 9 个） |
| 引用位置 | 删完 A1-A4 后应无外部 import |
| 调用入口 | 无 |
| 数据风险 | 无（见第 5 节：锚点层不依赖它） |
| 验证 | `grep -r "com.m1racle.reader.markdown" app/src/main` 应为空；全量测试绿 |

---

## 3. B 类：需要分离，不能整文件删

### B1 `DocumentListTitle`（首页列表标题）

| 项 | 内容 |
|---|---|
| 文件 | `markdown/DocumentListTitle.kt`（207 行） |
| 引用位置 | `ui/home/HomeScreen.kt:58` import；`:245, 259, 268` 三处调用 `normalizeListDisplayTitle` |
| 调用入口 | 首页/文库列表每行的标题渲染，**PDF 也走这里** |
| 依赖 | `normalizeListDisplayTitle` 内部调用 `InlineMarkdownParser.toPlainText(extractedTitle)`，用于把 `**粗体**` 这类 inline 标记去掉 |
| 数据风险 | 低。只影响显示文本 |
| 分离方案 | 保留 `normalizeWhitespace` / `stripLeadingEmojiClusters` / `fileNameStem` / `normalizeListDisplayTitle`，迁到 `ui/home` 或 `core/model`；用一个约 30 行的轻量「去 inline 标记」替代 `InlineMarkdownParser.toPlainText`。`extractDocumentTitle`（从 Markdown 正文提 H1）与 `findFirstValidH1Source` 删除 |
| 验证 | 现有列表标题测试保留；旧 Markdown 记录标题仍能显示（可能丢失 inline 标记转换，属可接受退化，因为该文档已不能阅读） |

### B2 `DocumentRepository.saveDocumentEdits` 的类型检查

| 项 | 内容 |
|---|---|
| 位置 | `DocumentRepository.kt:211` |
| 现状 | `check(before.isPdf \|\| before.type == DocumentType.MARKDOWN)` |
| 处理 | 收紧为 `check(before.isPdf)`，错误文案从「仅支持 PDF 和独立 Markdown 正文编辑」改为「仅支持 PDF 正文编辑」 |
| 风险 | 低。Markdown 编辑器已删，不存在合法调用 |
| 验证 | PDF 编辑保存仍通过；已有编辑保存测试保留 |

### B3 UI 层的 `isMarkdownPackage` 分支

| 项 | 内容 |
|---|---|
| 引用位置 | `ui/home/ContinueReadingCard.kt:56`、`ui/home/DocumentListRow.kt:53`、`ui/home/HomeScreen.kt:258`、`DocumentRepository.kt:507` |
| 现状 | 这些分支决定列表类型标签（「Markdown 包」）和内容身份复用策略 |
| 处理 | **保留 `DocumentEntity.isMarkdownPackage` 属性本身**（它是 `type == MARKDOWN_PACKAGE` 的便捷判断，历史记录还要靠它分类显示），只把「可点击进入」的路径断掉——那已经在 S1a 做完了 |
| 风险 | 无 |
| 验证 | 旧 Markdown 包记录在列表里仍显示、点击给提示、不崩溃（S1b 真机已验证同类路径） |

### B4 文库筛选条的已退出格式

| 项 | 内容 |
|---|---|
| 引用位置 | `ui/hub/LibraryModel.kt:6-11` 的 `LibraryFilter`（ALL/PDF/MARKDOWN/EPUB）；`strings.xml` 的 `hub_filter_markdown` / `hub_filter_epub` |
| 现状 | 真机截图确认筛选条仍是「全部 / PDF / MD / EPUB」四项 |
| 处理 | 从枚举和筛选条里移除 MARKDOWN / EPUB 两项。**注意**：库里仍有旧格式记录，删掉筛选项后它们只出现在「全部」里，这是正确行为 |
| 风险 | 低。若 `LibraryFilter` 被持久化（需确认），删除枚举值要有 fallback |
| 验证 | 筛选条只剩两项；「全部」仍显示旧格式记录；相关布局测试更新 |

---

## 4. C 类：必须保留（历史数据兼容）

### C1 锚点编解码

| 项 | 内容 |
|---|---|
| 文件 | `data/anchor/AnchorCodec.kt`（126 行） |
| Markdown 相关分支 | `:35` 编码 `DocumentAnchor.MarkdownBlock`、`:47` 编码 `EpubText`、`:69, :94, :100, :105, :117` 解码 |
| 调用入口 | `BookmarkTarget.kt:50, 52`、`SearchRepository.kt:179`、`NavGraph.kt:112`（`Routes.decodeAnchor`） |
| 为什么必须留 | 数据库里 `bookmarks.anchor`、`highlights.anchor`、`documents.readingAnchor` 存的是编码后的字符串。旧 Markdown/EPUB 记录仍在库里。删掉这些分支 → 历史记录解码返回 null → 书签打不开、标注位置丢失 |
| 数据风险 | **高**。这是本次分诊里风险最高的一处 |
| 验证 | 保留现有锚点编解码测试；真机上点一条旧格式书签，确认给提示而不是崩溃或静默丢失 |

### C2 标注锚点解析器

| 项 | 内容 |
|---|---|
| 文件 | `data/anchor/AnnotationAnchorResolver.kt`（131 行） |
| Markdown 相关 | `MarkdownBlockAnchorResolver`（`:59`）、`EpubTextAnchorResolver`（`:88`）、分派表 `:119-129` |
| 调用入口 | `ReadingRepository:87, 107, 135, 155`、`SearchRepository:128, 161`、`ui/annotation/AnnotationCreationFlow:110` |
| 为什么必须留 | `forLocationKind(row.locationKind)` 从数据库读 `locationKind` 字符串（`SCROLL_POSITION` 等）分派。历史 Markdown 书签/标注的 `locationKind` 就是这个值 |
| 数据风险 | **高**。删掉 → `forLocationKind` 返回 null → `ReadingRepository:107` 的 `?: return null` 静默丢弃记录 |
| 验证 | 同上；另外 `SearchRepository` 的高亮索引重建不能因此抛异常 |

### C3 `DocumentAnchor` 的 Markdown / EPUB 分支

| 项 | 内容 |
|---|---|
| 文件 | `core/model/DocumentAnchor.kt` |
| 分支 | `MarkdownBlock(blockIndex, charStart, charEnd)`、`EpubText(spineHref, blockIndex, ...)` |
| 为什么必须留 | 它们是 C1/C2 的坐标类型；删了编译就断，且历史数据无法表达 |
| 备注 | S1b 已把 KDoc 里指向已删解析器的悬空链接改掉 |

### C4 `DocumentType` 枚举与 `fromStorage`

| 项 | 内容 |
|---|---|
| 文件 | `core/database/.../DocumentType.kt` |
| 保留项 | `MARKDOWN`、`MARKDOWN_PACKAGE`、`EPUB` 三个枚举值；`fromStorage()` 的 `else -> MARKDOWN` 兜底 |
| 为什么必须留 | `DocumentEntity.documentType` 是 TEXT 列，`fromStorage` 负责解析。删枚举值 → 旧行读出来类型错误 |
| 备注 | 已知遗留问题：`fromStorage` 对未知值兜底成 `MARKDOWN`，这会把「未来新增的未知类型」误判成 Markdown。当前不构成缺陷（没有新类型），但应在正式版前改成显式 `UNKNOWN` 分支 |

### C5 数据库列

| 列 | 用途 |
|---|---|
| `documents.documentType` | 类型判定 |
| `documents.lastScrollIndex` / `lastScrollOffset` | Markdown 阅读位置（历史） |
| `documents.readingAnchor` | 锚点编码（v15 加的 EPUB 位置列也在内） |
| `highlights.anchor` / `bookmarks.anchor` / `locationKind` | 标注与书签坐标 |

删列需要 Room 迁移，等于丢用户数据。**本轮不动。**

### C6 `DocumentRepository.updateEpubReadingPosition` / `updateParseMetadata`

| 项 | 内容 |
|---|---|
| 位置 | `DocumentRepository.kt` |
| 为什么留 | 纯列写入，无解析器依赖；被 6–7 个测试 fake override；列里还有数据 |
| 验证 | 保留即可，无需改动 |

### C7 `SelectionModel.MarkdownSelection`（建议保留）

| 项 | 内容 |
|---|---|
| 位置 | `ui/annotation/SelectionModel.kt` |
| 现状 | `toAnchor()` 有 `MarkdownSelection` 分支，转成 `DocumentAnchor.MarkdownBlock` |
| 判断 | 它不依赖解析器，只依赖锚点枚举。删掉要改 `toAnchor` 的 when 分支，收益约 10 行，风险是碰 PDF 选区 |
| 建议 | **保留**。等 S1c 主体稳定后再决定 |

---

## 5. 三个关键结论

**一、锚点与标注层完全不依赖 Markdown 解析器。**

```
grep -r "import com.m1racle.reader.markdown" data/anchor/ ui/annotation/
→ 空
```

`data/anchor` 和 `ui/annotation` 只 import `DocumentAnchor`（core/model 的枚举），不 import `markdown` 包。所以**删掉 2,552 行解析器不会破坏历史锚点解码**。这是本次分诊最重要的结论，也是「不能靠文件名判断」的直接反例。

**二、Markdown 的实际活代码比行数看起来少得多。**

12 个文件里：
- 2 个消费者（`DocumentSearch`、`LocalImageLoader`）生产调用为 0
- 1 个孤儿（`MarkdownSource`）
- 1 个要分离（`DocumentListTitle`）
- 真正还活着的只有 `DocumentRepository` 的两条路径：`rebaseMarkdownAssets`（不可达）和教材包导入导出（A4）

**三、6 个已知失败全部属于 A4。**

`MarkdownPackageExportTest` 的 6 个用例测的是教材包导出（原始字节保留、损坏包拒绝、删除包时保留用户源文件等）。它们不是「测试坏了」，是「功能还在但已不在范围」。按 A4 退役时一并删除，不能作为正式版遗留。

---

## 6. 建议的删除顺序

每批独立提交、独立编译、独立测试。

| 批 | 内容 | 预期删除 | 门禁 |
|---|---|---|---|
| C-1 | A1 图片链路 + A2 搜索链路 | 1,901 行（820 + 1,081） | 编译绿；文库搜索测试绿 |
| C-2 | A3 `rebaseMarkdownAssets` + B2 类型检查 | 89 行 | PDF 编辑保存测试绿 |
| C-3 | A4 教材包导入导出 + 6 个失败测试 | 328 行 | PDF 导出测试绿；旧包记录点击给提示 |
| C-4 | B1 `DocumentListTitle` 迁移 | 207 行迁出，净减约 170 | 列表标题测试绿；旧记录标题正常 |
| C-5 | A5 `markdown` 包剩余 10 文件 | 1,150 行 | `grep -r com.m1racle.reader.markdown app/src/main` 为空 |
| C-6 | B4 文库筛选条 | 约 20 行 | 筛选条两项；布局测试更新 |

C 类（第 4 节）在全部批次中**一行不动**。

---

## 7. 正式发布前必须处理的两项（不在 S1c 范围）

1. **versionCode 单调递增**。当前候选包仍是 97（刻意保留回退路径）。正式发版必须规划新号，且要考虑从 97 直接升到新号的覆盖安装验证。
2. **6 个已知失败随 A4 关闭**。不能以「已知失败」的名义进入正式版。

---

## 8. 本次未做

- 未修改任何代码。
- 未构建 APK。
- 未触碰 S3 的两组依赖。
- 未合并 `master`。
- 未删除任何数据库列或迁移。
