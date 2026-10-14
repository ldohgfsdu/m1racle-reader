# 07 EPUB 阅读引擎

[返回目录](README.md)

## 当前实现与范围

当前 EPUB 路径是项目的原生解析模型与 Compose 排版，支持 reflowable EPUB、目录、章节、图片、字体和位置恢复，阅读模式为 Slide、Continuous、Curl。翻卷效果使用 `pagecurl`；普通滑动由 Compose `HorizontalPager` 管理。

主应用构建未声明 Readium 依赖。`output/` 中第三方研究源码与历史 SDK locator 格式不代表当前后端。DRM/受保护包、固定版式、音视频、脚本、完整 CSS 排版、EPUB 搜索与批注不在完整支持范围。

## 打开链路

```text
EpubReaderViewModel.load
  → DocumentRepository.openEpubBook(id)
  → 找到私有 EPUB 原包
  → EpubPackageImporter.openCached(epubFile, files/epubs/id, hash)
  → 校验缓存完成标记 / 安全解包
  → META-INF/container.xml → OPF → manifest / spine / nav 或 NCX
  → EpubXhtmlParser → EpubBook / EpubChapter / EpubBlock
  → EpubReadingAnchors.restore
  → EpubReaderContent 排版与阅读
```

`openCached()` 仅复用内容哈希及 extractor version 匹配的完整提取目录；完成标记名为 `.m1racle-extraction-complete`。已完成缓存缺失必要文件时，Repository 对 Corrupt 路径重建一次。解包拒绝目录逃逸，并对 XML 大小、深度、节点和全书模型设置预算。

## 关键类与函数

| 符号 | 职责与返回值 |
|---|---|
| [EpubPackageImporter.readMetadata()](../../app/src/main/java/com/m1racle/reader/epub/EpubPackageImporter.kt) | 从原包解析标题等元信息 |
| `open()` / `openCached()` / `parseExtracted()` | 原包或已提取目录到 `EpubBook` |
| `resolvePackagedFile()` | 在 EPUB 根内解析章内图片、href 等资源路径 |
| [EpubXhtmlParser.parse(bytes)](../../app/src/main/java/com/m1racle/reader/epub/EpubXhtmlParser.kt) | 返回 blocks 与章节 HTML ID 到块索引的映射；`BLOCK_RULES_VERSION=1` |
| [EpubModel](../../app/src/main/java/com/m1racle/reader/epub/EpubModel.kt) | Metadata、SpineItem、TocNode、Chapter、Book；与 UI 排版行模型区分 |
| `EpubBlock` | Heading、Paragraph、ListBlock、Quote、Code、Image、Divider、Unsupported |
| [EpubTextLayout](../../app/src/main/java/com/m1racle/reader/ui/epub/EpubTextLayout.kt) | 从源块建立可测量文本行/行索引与布局结果 |
| [paginateEpub()](../../app/src/main/java/com/m1racle/reader/ui/epub/EpubPagination.kt) | 依据行高、页面高度、段间距拆出 `EpubPage`/slice |
| `pageForText()` / `pageForPosition()` | 将源文本位置或旧位置映射至分页目标 |
| `epubPageTurnLayer()` | 页层动画的几何/视觉策略；交互 settle 由 Pager 负责 |

## 排版和模式

[EpubReaderContent](../../app/src/main/java/com/m1racle/reader/ui/epub/EpubReaderContent.kt) 按可用尺寸、字体、行高和边距建立页面，图片通过 `epubFitImage()` 保持比例。页与源块/字符之间有映射，避免字号变化时仅保留失效的页码。

| 模式 | 承载组件 | 位置更新原则 |
|---|---|---|
| Slide | `HorizontalPager` | 翻页停止且 offset 归零后采集稳定读点 |
| Continuous | 连续列表 | 停止滚动时采集行/像素位置并映射为源文本锚 |
| Curl | `PageCurl` | 卷页进度归零后采集当前页读点 |

`EpubReaderViewModel.setReadingMode()` 写入设置；`EpubReadingSettings`/`EpubReaderFonts` 处理内置字族和导入字体。系统栏沉浸与纸页圆角分别在 EpubSystemBars/EpubDeviceCorners/EpubPaperEdge 中实现。

项目提取部分出版内容的结构与样式信息，但这不是浏览器级 CSS cascade 或完整出版社排版还原。具体支持以解析器和文本布局实现为准。

## 文本锚恢复

[EpubReadingAnchors.anchorAt()](../../app/src/main/java/com/m1racle/reader/epub/EpubReadingAnchors.kt) 把阅读位置转为 `DocumentAnchor.EpubText`：章节 href、章内块序、原文字符偏移、规则版本及约 32 字 quote。

`restore()`/`resolve()` 输出 `EpubRestoreResult`，依次采用适合的恢复 tier：Exact、Quote、QuotePrefix、ChapterStart、Progress、LegacyPosition、BookStart。完整 quote 失效可检查前 12 字；章节仍在时应退化到该章，而不是无条件回到全书开头。

[EpubRestoreSession](../../app/src/main/java/com/m1racle/reader/epub/EpubRestoreSession.kt) 控制降级提示与首次保存保护。在恢复不可靠时保留旧锚，用户实际导航后才允许新的候选覆盖，避免布局初始化把历史读点写成开头。

`saveReadingPosition(EpubPosition)` 同时保存文本锚和旧行/像素/进度字段；`ReadingPositionSaver` 串行保证 latest-wins，退出或清理时 `flushReadingPosition()` 补写。

`rollbackEpubPosition()` 仅在读取历史 `epub-locator:` 值时做一次性转换，保留必要的降级信息；无当前 SDK 双运行路径。

## 错误与验证

`EpubException` 区分 NotAnEpub、Protected、FixedLayout、Corrupt、UnsafeEntry、TooLarge；ViewModel 将其转为带显示名的 Error。路径安全、模型预算、缓存完整性和缺失章节属于解析/恢复验证；实际翻页手感、系统栏、重排和字体观感需要设备证据。

对应测试包括包解压/模型预算、XHTML、锚恢复/重排、分页/行索引、翻页释放、字体、沉浸和设备渲染。可从 [测试索引](reference/test-index.md) 定位 `epub` 与 `ui.epub` 包。
