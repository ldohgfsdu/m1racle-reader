# R27 Note Foundation Integration 报告

**日期：** 2026-08-26
**状态：** `implemented / automated_verified / device_not_run_by_scope`
**范围：** 阅读内轻量 Note 闭环；不扩展为独立笔记系统。
**约束：** Anchor 协议、`GlassMaterial.kt`、Home 均未修改；无 schema 变更、无新依赖。

## 1. 闭环结果

```text
已存 Highlight（页面覆盖层 ✎ / AnnotationManagementSheet 高亮行）
  → 恢复 Highlight 的 DocumentAnchor
  → NoteEditDialog（创建；同 anchor 已有 Note 时进入编辑）
  → ReaderNoteController
  → ReadingRepository
  → NoteDao / Room notes
  → AnnotationManagementSheet Note 分区重新读取
  → 关闭并重新打开文档后从同一持久化表恢复
```

- PDF 路由按 `documentId` 注入 `ReaderNoteController`；UI 不接触 DAO，也不直接持有
  `ReadingRepository`。
- Note 创建、编辑、删除全部经 `ReaderNoteController → ReadingRepository`。
- 编辑 SQL 只更新 `content/updatedAt`，不写 `createdAt`。
- 更新、删除 SQL 同时匹配 `id + documentId`，文档 A 的控制器不能修改或删除文档 B
  的 Note。
- 内容在控制器边界 trim，并拒绝空 Note；未引入富文本、标签、搜索、独立导航或 Note
  首页。

## 2. Highlight / Note 关联语义

未新增关联列，也未改 Anchor 协议。关联键保持：

```text
(documentId, AnchorCodec.encode(DocumentAnchor))
```

- 从 Highlight 创建 Note 时，使用 Highlight 恢复出的完整 anchor（包括 PDF 归一化矩形），
  因而 Note 与 Highlight 的 anchor 编码一致。
- 同一文档、同一 anchor 已有关联 Note 时，再次从该 Highlight 进入会打开编辑态，避免 UI
  重复创建。
- 删除 Note 只删除 notes 行，不删除同 anchor Highlight；两者不是数据库级联关系。
- 文档删除仍沿用既有 `DocumentEntity → notes/highlights ON DELETE CASCADE` 生命周期。

## 3. UI 集成

### 页面内入口

- `PdfHighlightLayer` 选中高亮后显示左侧批注角标与右侧删除角标。
- 点击批注角标后按 Highlight id 获取当前文档行、恢复 anchor，并打开创建/编辑 Dialog。
- 连续阅读与横向翻页都使用同一 `pageAnnotation` 槽位；覆盖层位于 PDF 图像/瓦片之上，
  仍使用页面局部坐标。

### AnnotationManagementSheet

- 面板分为「高亮」和「批注」两个区段。
- 高亮行保留跳转/删除，并新增“添加批注”入口。
- Note 行直接展示内容、页码和更新时间，支持点击查看/编辑及独立删除。
- 创建、更新、删除后分别推进 Highlight/Note 版本并重新从控制器读取；UI 不维护第二份
  持久化事实源。

### NoteEditDialog

- 同一 Dialog 承担创建与编辑；编辑态提供删除。
- 只接受非空纯文本，保持轻量 Note 边界。
- 从管理面板进入时，Dialog 结束后回到管理面板；从页面高亮进入时返回阅读页。

## 4. 自动化验证

新增 R27 测试共 **7** 个：

| 测试 | 数量 | 覆盖 |
|---|---:|---|
| `ReaderNoteControllerTest` | 5 | 从 Highlight 创建并共享 anchor；更新不覆盖 `createdAt`；删除 Note 不影响 Highlight；跨文档读写隔离；新 Repository + Controller 实例从同一持久化 DAO 重新读取恢复 |
| `NoteFoundationAuthorityTest` | 2 | PDF UI 禁止 DAO/Repository 直写且必须经过 Note Controller；管理面板同时具备 Highlight/Note 分区和 Note 操作 |

执行结果：

```text
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ./gradlew :app:testQaUnitTest
BUILD SUCCESSFUL
1316 tests / 0 failures / 0 errors / 0 skipped
```

环境没有名为 `java17` 的相对目录；使用系统实际安装的 JDK 17 路径执行，语义等价于
任务要求的 `JAVA_HOME=java17`。

## 5. 基线收敛说明

接手时工作树中的 R23–R26 报告与 `PdfViewerScreen` 实际状态不一致：NavGraph 已传入
Bookmark/Highlight controller，但屏幕函数尚未接收，R26 页面 overlay/管理入口也未挂载；
同时屏幕仍引用已删除的旧 Glass 消费 API，R22 capsule 测试要求的生产模型缺失。

为使 R27 建立在可编译且测试全绿的既有链路上，本轮在 `PdfViewerScreen` 恢复了这些报告
已声明完成的消费端接线，并对齐工作树中现有 Glass API；**未修改 `GlassMaterial.kt`**。
R22 capsule 恢复按既有报告/测试冻结的单 spring、结构化内容模型完成，不改变 R27 产品范围。

## 6. 验收状态与未做项

- `implemented`：Repository 边界、关联语义、创建/查看/编辑/删除、双分区管理、两种 PDF
  阅读模式接线均完成。
- `automated_verified`：定向 R27 测试与全量 `:app:testQaUnitTest` 通过。
- `device_verified`：**未执行**；任务明确要求不进入设备验收，本报告不声明实机 PASS。
- 未执行 `assemble*`，因此没有生成或交付新 APK。
- 未做：独立 Note 页面、搜索/筛选、标签、富文本、导出、Markdown Note UI、云同步。

## 7. 变更面

```text
M data/local/AnnotationDaos.kt                    (Note 写入按 documentId 隔离)
M data/repository/ReadingRepository.kt            (Note scoped update/delete)
A/M ui/reader/ReaderNoteController.kt             (创建/编辑/删除/恢复与关联查询)
M ui/reader/ReaderHighlightController.kt          (复用既有 anchor 恢复入口)
A/M ui/annotation/{NoteEditDialog,AnnotationsManagementSheet,
                    PdfHighlightGeometry,PdfHighlightLayer}.kt
M ui/navigation/NavGraph.kt                       (Note controller 注入)
M ui/pdf/PdfViewerScreen.kt                       (阅读内完整 Note/R26 接线)
M res/values/strings.xml                          (Note 文案)
A test/ui/reader/{ReaderNoteControllerTest,
                  NoteFoundationAuthorityTest}.kt
```
