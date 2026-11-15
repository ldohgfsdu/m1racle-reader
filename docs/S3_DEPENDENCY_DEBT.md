# S3 架构债务清单（2026-11-15）

状态：只登记，不改代码。S1 的格式退出与 S3 的解耦分开进行，本清单是 S3 的输入。

统计口径：`import com.m1racle.reader.*` 的跨边界计数，边界按包前缀划分
（`core/model` 归 Model，`core/database` 归 Library，`core/pdf` 与 `pdf/` 归 PDF Engine，
`ui/annotation` 暂归 Reading Assets）。上一轮只报了出现次数最多的三个符号，合计 7；
下面是全部 13 处。

## 0. 一个必须先说的归类问题

13 处全部位于 `ui/annotation/` 的**界面文件**（对话框、浮层、颜色映射），
依赖的是主题、通用组件和设计色值。`data/anchor/` 数据层对 App Shell 的 import 为 **0**。

所以“资产数据层依赖 UI”这个结论不成立。真实情况是“标注界面依赖主题与通用组件”。
在六边界模型里，这些文件应归 Reader UI，而不是 Reading Assets。

9 处里有 8 处位于 `pdf/export/`，那是导出工具，按定义属于 PDF Tools 而不是渲染引擎；
真正落在引擎目录的只有 `pdf/PrintPageAnchors.kt` 一处。

因此 S3 的第一步不是搬代码，而是重划归属：界面归 Reader UI，导出归 PDF Tools。
重划之后，大部分 import 不再跨边界，不需要为了消掉 import 去加抽象层。

## 1. Reading Assets → App Shell（13 处）

| 位置 | 符号 | 定义于 | 用途 | 回归风险 | 建议归属 |
|---|---|---|---|---|---|
| ui/annotation/AnnotationsManagementSheet.kt:3 | M1racleReaderTheme | ui/theme | 标注管理页套主题 | 低，仅主题包裹 | 归 Reader UI |
| ui/annotation/AnnotationsManagementSheet.kt:31 | R | app | 字符串资源 | 低 | 归 Reader UI |
| ui/annotation/AnnotationsManagementSheet.kt:32 | sheetContainer | ui/theme/DesignSystem.kt:80 | 底部弹层底色 | 低，颜色 | 归 Reader UI |
| ui/annotation/AnnotationsManagementSheet.kt:35 | BookmarkRowFormatter | ui/hub | 复用书签行的日期格式 | 中，格式改动会联动 | 提取到共享格式化 |
| ui/annotation/NoteEditDialog.kt:3 | ReaderOutlinedTextField | ui/components | 批注输入框 | 低 | 归 Reader UI |
| ui/annotation/NoteEditDialog.kt:19 | R | app | 字符串资源 | 低 | 归 Reader UI |
| ui/annotation/NoteEditDialog.kt:20 | GlassAlertDialog | ui/material | 对话框容器 | 中，材质改动联动 | 归 Reader UI |
| ui/annotation/NoteEditDialog.kt:21 | m1racleReaderOutlinedFieldColors | ui/theme/DesignSystem.kt | 输入框配色 | 低 | 归 Reader UI |
| ui/annotation/PdfHighlightLayer.kt:51 | RadixColors | design | 高亮色板 | 中，颜色是渲染契约 | 色值下沉到 Model |
| ui/annotation/PdfSelectionColors.kt:4 | RadixColors | design | 选区色 | 同上 | 色值下沉到 Model |
| ui/annotation/ReaderHighlightColors.kt:5 | RadixColors | design | 标注色 | 同上 | 色值下沉到 Model |
| ui/annotation/SelectionActionsDialog.kt:5 | readerHairlineBorder | ui/components | 菜单描边 | 低 | 归 Reader UI |
| ui/annotation/SelectionActionsDialog.kt:6 | M1racleReaderTheme | ui/theme | 主题包裹 | 低 | 归 Reader UI |

违规原因（对归类为资产层而言）：资产层不应知道主题、资源和对话框。
但其中 10 处的文件本身就是界面，问题在归类而不在依赖方向。
真正值得调整的只有 3 处 RadixColors：Model 保存高亮类型和颜色标识这类语义数据，
具体的 Compose `Color` 和 Radix 色值仍由 UI 层负责映射，不把色值下沉到 Model。

验证：重划后 `data/anchor` 与 `ui/annotation` 的数据类不再 import `ui.*`；
现有标注测试（AnnotationCreationFlow、AnnotationOverlay、PdfHighlightGeometry）保持通过。

## 2. PDF Engine → Library（9 处）

| 位置 | 符号 | 定义于 | 用途 |
|---|---|---|---|
| pdf/PrintPageAnchors.kt:4 | PdfPrintPageAnchorEntity | data/local | 印刷页锚点实体 |
| pdf/export/ChapterPackageExporter.kt:6 | PdfPrintPageAnchorEntity | data/local | 导出时读取页锚点 |
| pdf/export/ChapterPackageExporter.kt:7 | TextbookOutlineNodeEntity | data/local | 导出时读取目录节点 |
| pdf/export/ChapterPackageModels.kt:3 | PdfPrintPageAnchorEntity | data/local | 导出模型直接持有实体 |
| pdf/export/ChapterPackageModels.kt:4 | TextbookOutlineNodeEntity | data/local | 同上 |
| pdf/export/ChapterPackageModels.kt:7 | TextbookOutlineResolver | data/local | 目录解析 |
| pdf/export/ExportRangeResolver.kt:3 | PdfPrintPageAnchorEntity | data/local | 计算导出页范围 |
| pdf/export/ExportRangeResolver.kt:4 | TextbookOutlineNodeEntity | data/local | 同上 |
| pdf/export/ExportRangeResolver.kt:7 | TextbookOutlineResolver | data/local | 同上 |

判断：8 处在 `pdf/export/`，按产品边界属于 PDF Tools，不属于渲染引擎。
归到 PDF Tools 后，它们对 Library 的依赖是“工具读取文库数据”，方向可以接受，
但 `ChapterPackageModels` 直接持有 DAO 实体，导致导出模型与表结构绑定。

建议（不加新抽象）：给导出模型定义自己的纯数据字段，由 Library 层在调用前
把实体映射成这些字段再传入。`TextbookOutlineResolver` 若只做纯计算，移入
PDF Tools；若它查询数据库，则把查询留在 Library，只把结果传入。

回归风险：章节导出与页范围计算。验证用现有的章节导出测试与
`ExportRangeResolver` 相关用例，改前后导出字节一致。

真正属于引擎目录的只有 `pdf/PrintPageAnchors.kt:4` 一处，单独处理。

## 3. 40 个失败的基线对照

在 S1a 之前的 `fc4788e` 上单独跑了这 7 个类（工作树
`.worktrees/baseline-fc4788e`，2026-11-15）：

| 类 | fc4788e | 当前分支 |
|---|---|---|
| ImportAcceptanceParityGoldenTest | 13 通过 | 10 失败 |
| ExternalOpenPendingNavigationTest | 14 通过 | 9 失败 |
| MarkdownPackageExportTest | 7 通过 | 6 失败 |
| ImportSizeLimitBoundaryTest | 11 通过 | 5 失败 |
| ZipExternalOpenContractTest | 6 通过 | 5 失败 |
| ContentIdentityImportWiringTest | 22 通过 | 4 失败 |
| HomeOperationsTest | 2 通过 | 1 失败 |
| 合计 | 75 通过 / 0 失败 | 40 失败 |

结论：这 40 个失败全部由 S1a 的准入收紧引入，没有一个是原先就存在的。
同时这 7 个类里仍有 35 个用例通过，所以不能整类删除，只能改或删失败的那部分。

分类：

1. 准入与外部打开类（ImportAcceptance、ExternalOpen、ImportSizeLimit、
   ZipExternalOpen、ContentIdentity、HomeOperations 的导入用例）：
   改为断言“非 PDF 被拒绝”。这是新策略的回归测试。
2. MarkdownPackageExportTest：随 Markdown 包导出功能退役，但要等 S1b/S1c
   确认该功能的调用方都已移除后再删。
3. 同时覆盖 PDF 的用例：上面 35 个仍通过的保留，不整类删除。
4. 无法解释的失败：本批没有。

