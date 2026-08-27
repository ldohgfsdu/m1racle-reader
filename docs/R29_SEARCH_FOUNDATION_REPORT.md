# R29 Local Search Foundation 报告

**日期：** 2026-08-26
**状态：** `implemented / automated_verified / ui_not_in_scope / device_not_run`
**范围：** 本地搜索数据模型、Room 持久化、统一 Repository 与可跳转结果契约。
**约束：** R29 未修改 `GlassMaterial.kt`、Home、Anchor 协议或 Annotation 数据模型；未实现 AI、云同步、向量数据库或搜索 UI。

## 1. 结果

R29 建立了可独立消费的本地搜索数据链：

```text
DocumentEntity / HighlightEntity / NoteEntity
  → SearchRepository.index(source)
  → SearchIndexEntity
  → SearchIndexDao / Room search_index

query + optional documentId
  → SearchRepository.search(...)
  → SearchResult(documentId, contentType, sourceId,
                 anchor, locationKind, location, content)
```

- `SearchRepository` 是索引写入、删除与查询的唯一公开数据门面。
- `MarkLeafApplication.searchRepository` 提供进程级实例；没有接入 Home、阅读器或导航 UI。
- SearchIndex 是可重建的派生数据，Document/Highlight/Note 继续是内容事实源。

## 2. SearchIndex 模型

新增 Room v12 表 `search_index`：

| 字段 | 语义 |
|---|---|
| `documentId` | 所属文档；FK 到 `documents.id`，文档删除时 cascade |
| `contentType` | `DOCUMENT` / `HIGHLIGHT` / `NOTE` |
| `sourceId` | 来源实体身份；Document 使用自身字符串 id，Annotation 使用 Long id 字符串 |
| `content` | 实际参与本地子串查询的文本 |
| `locationKind/location` | 与既有阅读定位坐标系一致的查询投影 |
| `anchor` | 非空 `AnchorCodec` 规范编码，作为跳转的强语义定位 |
| `indexedAt` | 当前索引快照更新时间，用于确定性排序 |

主键为：

```text
(documentId, contentType, sourceId)
```

该键同时解决三件事：

- 同一来源再次索引为原位更新，不产生重复行；
- Highlight id 与 Note id 即使相同也属于不同索引项；
- 相同 sourceId 出现在不同文档时，更新或删除不会跨文档生效。

辅助索引为 `documentId` 与 `contentType`。本轮使用普通 Room/SQLite `LIKE` 子串查询，不引入
FTS、外部搜索服务或向量存储；当前目标是稳定数据契约，而不是大规模全文检索引擎。

## 3. 三类内容的索引规则

### Document

- 搜索文本为去空、去重后的 `title + displayName`。
- PDF 结果定位到 `DocumentAnchor.PdfPage(0)`；Markdown/Markdown package 定位到
  `DocumentAnchor.MarkdownBlock(0)`。
- 不把 `lastScrollIndex` 或阅读进度复制进 SearchIndex，避免建立第二份阅读位置事实源。

### Highlight

- 搜索文本为 `excerpt`。
- 位置由既有 `AnnotationAnchorResolvers` 恢复；v11 前 `anchor = null` 的历史行可从
  `locationKind/location/anchorStart/anchorEnd` 合成有效 anchor。
- 空 excerpt 不建立无意义搜索项；再次索引为空会删除该来源的旧派生行。

### Note

- 搜索文本为 `content`。
- anchor/location 恢复规则与 Highlight 相同。
- 空内容同样会清理旧派生行。

上述过程只调用既有 Anchor API，没有增加枚举值、编码字段或修改协议实现。

## 4. SearchRepository 查询契约

统一入口：

```kotlin
suspend fun search(
    query: String,
    documentId: String? = null,
    limit: Int = 50,
): List<SearchResult>
```

- `documentId = null`：跨文档本地查询。
- 提供 `documentId`：只返回该文档结果，用于文档内搜索基础能力。
- 空白 query 直接返回空结果，不访问 DAO。
- `%`、`_` 和 `\` 被转义为字面字符，避免用户输入意外改变 `LIKE` 语义。
- limit 强制在 `1..200`，防止无界结果集。
- 顺序固定为 Document → Highlight → Note；同类型按 `indexedAt DESC`，再按文档与来源 id
  稳定排序。

每个有效 `SearchResult` 都携带：

- 非空 `documentId`；
- 强类型 `DocumentAnchor`；
- `locationKind + location` 投影；
- `contentType + sourceId` 来源身份。

因此未来 UI 可以按文档打开后直接跳转原始页/块，而不需要从搜索文案重新推导位置。

## 5. 创建、更新与删除

- 创建：三个 `index(...)` overload 将稳定来源实体投影成 SearchIndex 行。
- 更新：相同复合主键执行 `REPLACE`，内容、定位与 `indexedAt` 一次更新，不产生双路径。
- 单项删除：必须提供 `documentId + contentType + sourceId`。
- 文档清理：`clearDocument(documentId)` 显式删除该文档全部索引；删除 DocumentEntity 时数据库
  FK 也会 cascade 清理。
- v11 → v12 migration 只创建新的派生表与索引，不改 documents/highlights/notes，也不伪造历史
  索引内容。

R29 没有增加后台爬取器、自动回填调度或 UI 写入接线。现有数据需要由未来明确的索引编排任务通过
`SearchRepository.index(...)` 建立；这是 foundation 的有意边界，报告不声明已有搜索入口可用。

## 6. 自动化验证

R29 新增 **6** 个测试：

| 测试 | 数量 | 覆盖 |
|---|---:|---|
| `SearchRepositoryTest` | 5 | 三类索引创建；同来源原位更新；单项/文档删除；跨文档更新、删除、查询隔离；Document/历史 Highlight/Note 定位信息完整 |
| `MigrationV11ToV12Test` | 1 | 表字段、复合主键、索引、孤儿拒绝、相同 sourceId 跨文档共存、Document cascade |

数据库版本从 11 升至 12，因此既有 `ReadingModelAuthorityTest` 的 schema 版本钉同步到 12；
禁止 `reading_states` 第二事实源的所有原断言保持不变并继续通过。

执行结果：

```text
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ./gradlew :app:testQaUnitTest
BUILD SUCCESSFUL
1327 tests / 0 failures / 0 errors / 0 skipped
```

仓库中不存在名为 `java17` 的相对目录；使用系统实际安装的 JDK 17 绝对路径执行，语义等价于任务
要求的 `JAVA_HOME=java17`。

## 7. 边界与未做项

- `implemented`：SearchIndex schema、迁移、DAO、Repository、三类索引投影和结果定位契约完成。
- `automated_verified`：R29 定向测试与全量 JVM 单元测试通过。
- 未做搜索 UI、导航接线、后台索引回填、搜索建议、排序打分、分词、FTS、AI、同步或向量数据库。
- 未执行设备验收；本轮没有 UI 或设备性能声明。
- 未执行 `assemble*`，因此没有生成或交付新 APK。
- R29 未修改 `GlassMaterial.kt`、Home、Anchor 协议或 Annotation 数据模型。

## 8. 变更面

```text
M  app/MarkLeafApplication.kt
M  data/local/AppDatabase.kt                         (v12 + MIGRATION_11_12)
A  data/local/{SearchIndexEntity,SearchIndexDao}.kt
A  data/repository/SearchRepository.kt
A  schemas/.../12.json
A  test/data/local/MigrationV11ToV12Test.kt
A  test/data/repository/SearchRepositoryTest.kt
M  test/data/local/ReadingModelAuthorityTest.kt       (latest schema pin 11 → 12)
A  docs/R29_SEARCH_FOUNDATION_REPORT.md
```
