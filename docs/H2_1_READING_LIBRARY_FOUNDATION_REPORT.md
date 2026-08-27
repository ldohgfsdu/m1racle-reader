# H2.1 Reading Library Foundation 报告

> **[2026-08-26 已部分被收口任务取代]** 本报告中 reading_states 表与
> ReadingRepository 阅读状态 API 已按 H2_1_READING_MODEL_AUTHORITY_REPORT.md
> 删除（v8→v9）：documents 阅读字段族为唯一阅读位置事实源。本文其余内容
> （bookmarks/favorites 模型、宿主测试策略）仍然有效；Bookmark 定位已中性化为
> locationKind+location（见权威报告 §4）。

**日期：** 2026-08-25
**性质：** 数据层基础设施。不实现任何首页/Hub UI；无网络、无账号、无同步字段；
Room schema 最小化（v7 → v8 纯增量建表）。

---

## 数据模型（`data/local/ReadingLibraryEntities.kt`）

| 表 | 字段 | 说明 |
|---|---|---|
| `reading_states` | documentId(PK) / lastPosition / progress / lastOpenedTime | Hub「最近阅读」聚合视图。lastPosition：Markdown=滚动索引，PDF=0-based 页索引；progress 与 DocumentEntity.readingProgress 同口径 |
| `bookmarks` | id(auto PK) / documentId / page / offset? / title? / note? / createdAt | 按文档列书签；createdAt 升序 = 阅读顺序 |
| `favorite_documents` | documentId(PK) / createdAt | 幂等收藏（IGNORE 冲突，保留首次时间） |

- 三表均以 `documentId` FK → `documents(id)` **CASCADE**（与 pdf_print_page_anchors
  同模式）：删除文档记录即清理全部派生数据，杜绝孤儿行。
- 索引最小化：reading_states(lastOpenedTime, documentId)、bookmarks(documentId)、
  favorites(createdAt)。无云同步预留字段。

## 迁移 v7 → v8（`AppDatabase.MIGRATION_7_8`）

纯增量 `CREATE TABLE IF NOT EXISTS` ×3 + 索引 ×4；SQL 常量公开（internal），
宿主 JVM 测试逐条直跑真实 SQLite 验证（机制沿用 MigrationV6ToV7Test）。
schema JSON 已导出：`app/schemas/com.markleaf.app.data.local.AppDatabase/8.json`
（version=8，六表）。不做迁移兼容层、不留旧路径。

## Repository（`data/repository/ReadingRepository.kt`）

统一门面（构造注入三个 DAO），UI 层禁止直访数据库：

- ReadingState：`saveReadingState` / `updateReadingProgress`（缺失即建、合并写回、
  lastOpenedTime 单调不回拨、进度钳制）/ `getReadingState` / `recentReadings(limit)` /
  `removeReadingState`
- Bookmark：`createBookmark` / `deleteBookmark` / `bookmarksForDocument`
- Favorite：`addFavorite`(幂等返回是否新收藏) / `removeFavorite` / `isFavorite` /
  `favorites()`

排序契约在仓库层完成并因此可被宿主测试完整验证：最近阅读按 lastOpenedTime 降序
（同刻 documentId 稳定序）、书签 createdAt 升序、收藏 createdAt 降序。

接线点：`MarkLeafApplication.readingRepository`（lazy，手动 DI，与既有仓库一致）。
**本轮无任何 UI 调用方。**

## 宿主测试策略说明（如实记录）

本机 linux-aarch64 无 Robolectric SQLite 后端（仓库既定约束），因此：
- Repository 契约测试用内存 Fake DAO 拦截 DAO 流量（机制 B，同
  ExternalOpenPendingNavigationTest 先例）；排序逻辑位于仓库层故可完整验证；
- 建表/FK/CASCADE/AUTOINCREMENT 用 sqlite-jdbc 直跑生产迁移 SQL 常量验证
  （机制 A，MigrationV7ToV8Test）；Room 运行时全链路校验仍归 androidTest（设备）。

## 测试（新增 14）

| 文件 | 数量 | 覆盖 |
|---|---|---|
| `data/repository/ReadingStateRepositoryTest.kt` | 4 | 保存位置往返、更新进度（创建/合并不回拨钳制）、最近排序+limit、删除 |
| `data/repository/BookmarkRepositoryTest.kt` | 3 | 创建（自增 id/可选字段默认 null）、删除只删目标、按文档过滤+升序 |
| `data/repository/FavoriteRepositoryTest.kt` | 4 | 收藏查询、取消与再收藏、重复收藏幂等保首时、降序列表 |
| `data/local/MigrationV7ToV8Test.kt` | 3 | user_version=8、三表列结构冻结、索引名、FK 拒孤儿+CASCADE、自增不复用 |

## 当前范围与后续决策点（未做，明确声明）

- **双写风险**：阅读器现有保存链路仍写 documents 内嵌字段；reading_states 尚无
  写入方。接入 Home Reading Hub 时需决策统一写入路径（建议：阅读器保存路径单点
  同步写 reading_states，或 Hub 读侧聚合），避免两处事实源分叉。
- 书签 page 口径（PDF 人读页 vs Markdown 块索引）待 UI 任务定稿后固化注释与校验。
- 孤儿防护依赖 FK CASCADE，仅覆盖「删文档记录」路径；外部源文件删除检测属既有
  cleanup 流程范畴，本轮不动。
