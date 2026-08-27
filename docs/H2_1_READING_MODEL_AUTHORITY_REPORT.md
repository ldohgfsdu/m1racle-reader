# H2.1 Reading Model Authority Report —— 阅读模型事实源收口

**日期：** 2026-08-26
**性质：** 数据模型收口任务。零 UI 改动、零首页接入、零功能扩展。
**验证：** `:app:testQaUnitTest`（JDK17）**1245 tests / 0 failures / 0 errors**
（上轮 1240 → 删 4 个 State 测试 + 新增 9 个收口测试，净 +5）。

---

## 1. documents 阅读字段审查（要求①）

`documents` 表当前承载的阅读状态字段族：

| 字段 | 语义 | 写入方 | 消费方 |
|---|---|---|---|
| `lastOpenedAt` | 最近打开时间 | DocumentRepository.touchLastOpenedAt | **Home 最近列表排序（现役 UI 直接消费）** |
| `lastScrollIndex` | MD=滚动索引 / PDF=页索引 | updateReadingPosition / updatePdfReadingPosition | ViewerViewModel / PdfViewerViewModel 恢复 |
| `lastScrollOffset` | 块内偏移编码 | 同上 | Markdown 精恢复 |
| `readingProgress` | 归一化进度 | 同上 | 列表进度显示 |
| `pdfViewport*` ×8 | 视口会话（scale/anchor/generation） | updatePdfViewportSession 原子快照 | PDF 视口恢复 |

结论：这是一族**高精度、单写者、有现役读写双方**的活数据。

## 2. 唯一阅读位置事实源（要求②）

> **documents 表阅读字段族是唯一阅读位置事实源（Reader Authority）。**

理由：
1. 它是今天唯一同时具备写入方（DocumentRepository 保存路径）与消费方（双
   ViewModel 恢复 + Home 排序）的存储；
2. 它持有 reading_states 无法表达的保真字段（块内 offset 编码、PDF 视口会话）；
3. Hub「最近阅读」所需的 (documentId, progress, lastOpenedAt) 它已全部具备，
   Home 现役查询就是 `ORDER BY lastOpenedAt DESC`。

权威身份已在 `DocumentEntity` KDoc 中标注（含禁止第二存储声明），由
`ReadingModelAuthorityTest` 源码钉锁定。

## 3. 重复字段处置（要求③）

R22.6 引入的 `reading_states(documentId, lastPosition, progress, lastOpenedTime)`
经审查为 documents 已有语义的**粗粒度复制**：零写入方、零消费方、无法表达精度
字段——其存在本身就是第二事实源风险。

**处置：整体删除，不留投影、不留双写、不保留兼容层。**

- 实体/DAO/Repository API/Application 接线全部移除（旧实现直接删净，符合公约）；
- DB v8 → v9：`DROP TABLE IF EXISTS reading_states`（迁移 SQL 常量公开，
  MigrationV8ToV9Test 用宿主真实 SQLite 逐条直跑验证）；
- v7→v8 历史 SQL 常量按升级链要求原样冻结保留（仅作为历史路径存在，被钉子
  枚举锁定，不允许新增出现点）；
- 未来若 Hub 需要独立聚合视图，必须以**查询/派生**实现，禁止重建第二存储表
  （源码钉强制）。

## 4. Bookmark 定位语义（要求④）

旧结构 `page: Int` 是 PDF 分页绑定命名。v9 重建为文档中性定位：

```kotlin
locationKind: String   // BookmarkLocationKind：PAGE_INDEX | SCROLL_POSITION
location: Int          // PAGE_INDEX=0-based 页索引；SCROLL_POSITION=块/滚动索引
offset: Long?          // 可选次级精定位（如字符偏移）
```

- 坐标系由 kind **显式声明且无默认值**——写入方必须选择，实体层不解释语义；
- 不绑定任何单一文档类型的分页概念；Markdown 即用 SCROLL_POSITION，
  未来新文档类型以**新增枚举值 + 加列**纯增量演进（本仓库 PRD 范围仍为
  Markdown+PDF，EPUB 明确不做——此处仅保证定位结构不排斥扩展）；
- 迁移采用可移植四步重建（create new → copy with 'PAGE_INDEX' → drop old →
  rename），旧行数据零丢失映射，AUTOINCREMENT 序列延续，FK CASCADE 与孤儿
  拒绝行为在重建后复验。

## 5. 测试（要求⑥）

| 文件 | 变化 | 覆盖 |
|---|---|---|
| `data/local/MigrationV8ToV9Test.kt` | 新增 4 | 表删除、新列结构与索引、旧行 PAGE_INDEX 零丢失映射、收藏幸存+CASCADE 复验、自增序列延续 |
| `data/local/ReadingModelAuthorityTest.kt` | 新增 4 | 权威标注在位、reading_states 仅存于冻结迁移常量（枚举计数钉）、version=9、书签中性化（page 列禁复活/kind 无默认）、Application 无 state DAO |
| `data/repository/BookmarkRepositoryTest.kt` | 重构 4 | 双 locationKind 创建与往返、中性断言、删除、过滤+升序 |
| `data/repository/FavoriteRepositoryTest.kt` | 适配 4 | 原 4 项不变（构造签名收敛） |
| `data/repository/ReadingStateRepositoryTest.kt` | **删除** | 路径随实体一并移除 |

## 6. 变更面

```
M app/src/main/java/com/markleaf/app/data/local/AppDatabase.kt        (v9 + MIGRATION_8_9)
M app/src/main/java/com/markleaf/app/data/local/DocumentEntity.kt     (权威 KDoc 标注)
M app/src/main/java/com/markleaf/app/MarkLeafApplication.kt           (去 state DAO)
M app/src/main/java/com/markleaf/app/data/local/ReadingLibraryEntities.kt (重写)
M app/src/main/java/com/markleaf/app/data/local/ReadingLibraryDaos.kt     (删 DAO)
M app/src/main/java/com/markleaf/app/data/repository/ReadingRepository.kt (删 state 区块)
A app/schemas/com.markleaf.app.data.local.AppDatabase/9.json
± 测试 5 个文件（上表）
A docs/H2_1_READING_MODEL_AUTHORITY_REPORT.md
```

UI 层（Home/Viewer/PdfViewerScreen 等）零改动；GlassMaterial.kt 未触碰。
