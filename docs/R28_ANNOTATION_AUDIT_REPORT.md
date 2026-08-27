# R28 Annotation System Audit & Polish 报告

**日期：** 2026-08-26
**状态：** `implemented / automated_verified / device_not_run_by_scope`
**范围：** Annotation System v1（Bookmark / Highlight / Note / Anchor / Overlay / Management）收口审查与必要的小型修整；不新增大型功能。
**约束：** Anchor 协议、`GlassMaterial.kt`、Home 均未修改；未引入同步、AI、导出实现、schema 变更或新依赖。

## 1. 结论

R28 发现并修复了四个会随标注数量放大的问题：

1. 页面 overlay 原先每页读取当前文档的全部 Highlight 和全部 Note，再在内存过滤；现改为
   `(documentId, locationKind, location)` 精确查询。
2. Highlight 绘制层原先消费 Highlight + Note 的合并结果，可能把带矩形的 Note 当作黄色高亮重复绘制；
   现只消费 `AnnotationType.HIGHLIGHT`。
3. PDF 阅读器原先在屏幕级 `SnapshotStateMap` 累积所有访问页的 overlay；现改为页面组合项本地
   `remember(documentId, page)` 状态，离开组合后随页面释放。
4. 从页内高亮打开 Note 时原先先全量查询 Highlight、再全量查询 Note；现由命中的 overlay item
   直接携带 anchor，Note 只查询该 anchor 所在位置。

Annotation v1 的持久化边界、文档隔离、恢复和管理流程现已一致；没有发现需要修改 Anchor 协议、
Glass 系统或 Home 的理由。

## 2. Annotation 性能审计

### 2.1 Overlay 加载与页面过滤

生产查询现为：

```sql
SELECT * FROM highlights
WHERE documentId = ? AND locationKind = ? AND location = ?
ORDER BY createdAt ASC, id ASC
```

SQLite `EXPLAIN QUERY PLAN` 在 1000 条 Highlight 和 1000 条 Note 数据上确认分别使用：

- `index_highlights_documentId_locationKind_location`
- `index_notes_documentId_locationKind_location`

规模契约测试将 10 / 100 / 1000 条 Highlight 均匀放在 10 页，并为同页准备同量 Note。查询第 8 页的
确定性结果如下；这是查询数量与处理行数审计，不是设备 wall-clock benchmark。

| 文档 Highlight 总数 | 目标页 Highlight | R27 每页读取 | R28 高亮层读取 | R28 DAO 查询数 |
|---:|---:|---:|---:|---:|
| 10 | 1 | 全部 10 Highlight + 全部 Note | 1 Highlight | 1 |
| 100 | 10 | 全部 100 Highlight + 全部 Note | 10 Highlight | 1 |
| 1000 | 100 | 全部 1000 Highlight + 全部 Note | 100 Highlight | 1 |

最坏情况下若 1000 个矩形全部位于同一页，查询仍是 1 次且索引精确命中，但解码、命中测试和绘制
必然仍为 `O(1000)`。本轮没有引入空间索引、聚合或渲染分片，因为没有设备帧数据证明这些复杂度
必要；该极端同页绘制吞吐仅能标为 `pending_device_evidence`。

### 2.2 查询频率

- 页面首次进入 Compose 组合：每个被实际组合/预取的页执行 1 次 Highlight 精确查询。
- 普通 recomposition：`LaunchedEffect(page, controller, highlightVersion)` key 不变，不会重复查询。
- 新增或删除 Highlight：`highlightVersion` 变化，当前仍在组合中的页各刷新 1 次；不会扫描 Note。
- 新增、编辑或删除 Note：不会触发黄色 Highlight overlay 重载。
- 从高亮进入 Note 编辑：执行 1 次目标位置 Note 查询，再以规范化 anchor 匹配；不再读取全量列表。
- 打开管理面板：按设计各执行 1 次当前文档 Highlight/Note 全量列表查询。管理面板需要展示完整清单，
  这是合理的全量边界；关闭时不查询，打开期间发生对应类型写入后重新读取。

### 2.3 Recomposition 与列表风险

- 删除屏幕级 `pageHighlightItems` 状态图，避免访问页越多、常驻状态越大，也避免任一页写入共享
  `SnapshotStateMap` 时扩大失效范围。
- overlay 快照现在属于单个页面组合项；内存规模由“历史访问页数”收敛为“当前被 Compose 保留的页数”。
- `AnnotationManagementSheet` 从两个各自限高的嵌套 `LazyColumn` 改为一个虚拟化滚动容器；
  Highlight/Note item key 增加类型前缀，避免两张表相同本地 id 在同一列表内发生 key 冲突。
- 管理面板仍会在打开时持有当前文档的完整实体列表，空间复杂度为 `O(H + N)`；1000 行下 UI 子项
  由 LazyColumn 按需组合，但实体列表本身不会分页。v1 本地轻量标注范围内暂不增加分页协议。

## 3. Annotation UX 收口

### 页内流程

```text
长按拖选 → 创建 Highlight → Repository → 当前组合页刷新
点击 Highlight → 选中态 → 批注 / 删除
批注 → 同一 NoteEditDialog（无 Note 时创建，有 Note 时查看/编辑/删除）
删除 Highlight → Repository → 当前组合页刷新
```

### 管理流程

```text
AnnotationManagementSheet（单一滚动面）
├─ Highlight：查看摘录 / 跳页 / 添加或打开关联 Note / 删除
└─ Note：查看内容 / 编辑 / 删除
```

- 页内入口和管理入口共享 `ReaderHighlightController`、`ReaderNoteController` 与同一个 Note 编辑器；
  写入成功后统一刷新相应版本并显示 snackbar。
- UI 不直接访问 DAO 或 Repository；持久化写入仍为
  `UI → document-scoped Controller → ReadingRepository → DAO`。
- Highlight 模型没有可编辑的用户字段；“修改选区”在 v1 中保持删除后重选，不虚构 Highlight 编辑器。
- 删除 Highlight 和删除 Note 都只删除自身实体；二者通过 `documentId + anchor` 关联，而不是互相级联。

## 4. 数据完整性

| 不变量 | 实现证据 | 自动化证据 |
|---|---|---|
| 文档删除清理标注 | Highlight/Note 外键均为 `DocumentEntity` 的 `ON DELETE CASCADE` | 宿主 SQLite 删除文档后两表计数均为 0；孤儿插入被拒绝 |
| Highlight 文档隔离 | 单条读取、删除 SQL 均匹配 `documentId + id` | doc-A controller 无法删除 doc-B Highlight |
| Note 文档隔离 | 更新、删除、单条读取均匹配 `documentId + id` | doc-A controller 无法修改/删除 doc-B Note |
| Highlight/Note 相互隔离 | 两者无实体间 FK；关联只使用同文档等价 anchor | 删除 Note 保留 Highlight；删除 Highlight保留 Note |
| 跨文档恢复 | 所有列表、位置查询都以 documentId 为首要条件 | 新 Repository/Controller 实例分别恢复 doc-A/doc-B，内容不串文档 |
| 重启/重开恢复 | anchor 和内容保存在 Room，UI 无第二持久化事实源 | 新 Repository/Controller 实例恢复 Highlight overlay 与 Note |
| Note 时间完整性 | update SQL 只写 `content/updatedAt` | 更新后 `createdAt` 保持首次创建值 |

本轮将 Highlight 的单条读取/删除也收紧为 document-scoped，与 R27 Note 的写入边界保持一致。
这是完整性修复，不改变数据库 schema 或 Anchor 编码。

## 5. Export readiness（仅审查，不实现）

当前模型已具备未来定义导出 DTO 所需的最小语义：

| 语义 | 当前字段/规则 | 就绪判断 |
|---|---|---|
| 文档归属 | `documentId` | 可按文档收集；导出格式仍需定义可移植的文档身份 |
| 精确位置 | 规范化 `anchor` + `locationKind/location` 投影 | PDF 矩形和 Markdown 字符区间可恢复；无需改 Anchor 协议 |
| Highlight 内容 | `excerpt`、`createdAt` | 支持原文快照与创建时间；excerpt 允许为空 |
| Note 内容 | `content`、`createdAt`、`updatedAt` | 支持正文与创建/更新时间 |
| Highlight/Note 关联 | 同 `documentId + AnchorCodec.encode(anchor)` | 导出层可分组，但应保留两者为独立记录 |
| 稳定输出顺序 | `location, createdAt, id` / 单页 `createdAt, id` | 同一数据库内可确定性排序 |

未来实现导出前仍必须单独决定：格式与版本号、文档可移植身份、时区/时间表示、空 excerpt 语义、
冲突与导入策略。Room 自增 `id` 只是本机行标识，不应直接声明为跨设备稳定 ID。R28 未创建导出 DTO、
序列化器、分享入口或任何同步路径。

## 6. 自动化验证

R28 新增 **5** 个测试：

| 测试 | 新增数 | 覆盖 |
|---|---:|---|
| `AnnotationPerformanceContractTest` | 3 | 10/100/1000 精确页查询与恒定查询数；合并 source 每类型一次精确查询；页面本地状态、无全局 map、管理面板单 LazyColumn |
| `MigrationV9ToV10Test` | 1 | 1000 行下 Highlight/Note 精确位置 SQL 实际采用复合索引 |
| `ReaderNoteControllerTest` | 1 | Highlight 跨文档删除隔离，删除 Highlight 不删除关联 Note |

并强化既有 Note 跨文档重开恢复与 Highlight 重开 overlay 恢复断言。既有 cascade、Note 创建、
更新时间、删除隔离和管理闭环测试继续参与全量门禁。

执行结果：

```text
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ./gradlew :app:testQaUnitTest
BUILD SUCCESSFUL
1321 tests / 0 failures / 0 errors / 0 skipped
```

仓库中不存在名为 `java17` 的相对目录；使用系统实际安装的 JDK 17 绝对路径执行，语义等价于任务
要求的 `JAVA_HOME=java17`。

## 7. 验收边界

- `implemented`：查询收敛、Highlight-only overlay、页面状态作用域、Note 定位查询、管理 UX、
  document-scoped 完整性均已落地。
- `automated_verified`：定向测试与全量 `:app:testQaUnitTest` 均通过。
- `device_verified`：**未执行**；任务明确不进入设备验收，不声明滚动帧率、触摸体验或极端同页
  1000 矩形绘制性能 PASS。
- 未执行 `assemble*`，因此没有生成或交付新 APK。
- 未修改 Anchor 协议、`GlassMaterial.kt` 或 Home；未实现导出、同步、AI、独立笔记系统或分页管理协议。

## 8. R28 变更面

```text
M data/local/AnnotationDaos.kt
M data/repository/ReadingRepository.kt
M ui/annotation/{AnnotationOverlay,PdfHighlightLayer,AnnotationsManagementSheet}.kt
M ui/reader/{ReaderHighlightController,ReaderNoteController}.kt
M ui/pdf/PdfViewerScreen.kt
M test/data/local/MigrationV9ToV10Test.kt
M test/data/repository/AnnotationAnchorRepositoryTest.kt
M test/ui/{annotation,reader,hub} 中受 DAO 契约影响的测试替身
A test/ui/annotation/AnnotationPerformanceContractTest.kt
A docs/R28_ANNOTATION_AUDIT_REPORT.md
```
