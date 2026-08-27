# R30 Search Experience Integration 报告

**日期：** 2026-08-26
**状态：** `implemented / automated_verified / device_not_run_by_scope`
**范围：** Home/Library 搜索入口、本地搜索结果页、canonical anchor 跳转、增量索引更新。
**约束：** 未修改 Anchor 协议或 `GlassMaterial`；未引入 AI、向量数据库、云同步、网络权限或新依赖。

## 1. 结果

R30 已形成阅读内本地搜索闭环：

```text
Home / Library 搜索入口
  → SearchScreen
  → SearchViewModel
  → SearchRepository.search(query)
  → SearchResult(documentId + documentTitle + type + content + canonical anchor/location)
  → 读取当前 DocumentEntity
  → 既有 Reader 路由
  → Markdown scrollToBlockIndex / PDF PdfViewerViewModel.goToPage
```

搜索页支持 Document、Highlight、Note 三类结果，并显示所属文档标题、来源类型、内容摘要与
页/段定位信息。查询输入采用 180ms debounce；新输入会取消旧查询，取消不会被误报为失败。

## 2. Search UI

- Home 标题区与 Library 标题区均提供搜索入口。
- 搜索页包含输入、加载、初始提示、空结果、错误与结果列表状态。
- 结果稳定身份为 `documentId + contentType + sourceId`。
- 结果内容：
  - 标题：当前文档 `title`，空标题回退到 `displayName`；
  - 来源：文档 / 高亮 / 笔记；
  - 摘要：索引内容，最多三行；
  - 定位：PDF 显示 1-based 页码，Markdown 显示 1-based 段号。

`SearchIndexDao` 在查询时 JOIN 当前 documents 表取得标题，标题不是复制进派生索引的第二份事实源。

## 3. Result Jump

Viewer/PDF Viewer 路由新增可选 `anchor` query 参数。参数内容仍由既有 `AnchorCodec` 产生，
路由层仅进行标准 URL 编解码；Anchor 类型、字段、格式和 resolver 均未修改。

- 搜索结果点击先按 `documentId` 读取当前 `DocumentEntity`，再按其真实类型选择现有 Reader。
- Markdown Reader 在内容 ready 后，将 `MarkdownBlock` 交给既有 `scrollToBlockIndex`。
- PDF Reader 在 ready 后，将 `PdfPage.page` 交给既有 `PdfViewerViewModel.goToPage`。
- 没有从摘要推导位置，也没有新增第二套页码/滚动跳转算法。
- 不携带 anchor 的原有 Home、Library、Bookmark 与外部打开路由保持原行为。

## 4. 增量索引

索引写入继续只经过 `SearchRepository`，UI 不访问 `SearchIndexDao`。

### Document

- `DocumentRepository.upsert` 写 Document 后立即更新 Document 索引。
- 普通导入和 ZIP/Markdown package 导入统一经过私有 `upsertAndIndex`，不保留未索引的 DAO 直写路径。
- Markdown 解析标题更新后重新索引，使查询内容和结果标题同步。
- 文档删除事务内先清理派生索引，再删除 Document；Room FK cascade 仍提供数据库完整性兜底。

### Highlight / Note

- Highlight 创建取得 Room id 后立即建立索引；删除同步删除对应派生项。
- Note 创建取得 id 后建立索引；内容更新后读取权威 Note 行并原位重建索引。
- Note 更新不修改 `createdAt`；索引排序时间使用 `updatedAt`。
- Note 删除同步删除派生项；空白可搜索内容会清除旧索引行。

## 5. 历史数据回填设计（未执行）

R29 v12 migration 创建的是空派生表；R30 不在应用启动或 UI 查询中执行全库扫描，也没有引入
大规模后台任务。R30 后的新导入和标注写入会持续增量建索引。

未来若明确启动历史回填，应遵循以下设计：

1. 以 `documentId` 为批次边界，按稳定游标读取 Document 及其 Highlight/Note；
2. 每批只调用三个现有 `SearchRepository.index(...)` overload，保持来源实体为事实源；
3. 复合主键保证任务可重入、可中断、重复执行无重复项；
4. 记录索引版本和最后完成的 document 游标，失败后从批次边界恢复；
5. 删除或不存在的来源通过 document 批次清理后重建，避免保留孤儿；
6. 调度、批大小、空闲/电量条件必须由后续明确任务决定并单独验收。

本轮只记录该方案，没有实现 scheduler、WorkManager job 或首次搜索时的隐式全量扫描。

## 6. 自动化验证

R30 新增 **7** 个测试：

| 测试 | 数量 | 覆盖 |
|---|---:|---|
| `SearchRepositoryTest` 新用例 | 1 | 类型优先、同类型 `indexedAt DESC` 稳定排序 |
| `SearchExperienceIntegrationTest` | 6 | Document 创建/更新/删除；Highlight/Note 增量更新与删除；Note `createdAt` 保持；跨文档隔离与标题 JOIN；canonical anchor 路由往返及既有 Reader authority；写入边界；Home/Library 入口和结果字段 |

验证结果：

```text
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ./gradlew :app:testQaUnitTest
BUILD SUCCESSFUL
1334 tests / 0 failures / 0 errors / 0 skipped

JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ./gradlew :app:compileQaAndroidTestKotlin
BUILD SUCCESSFUL
```

仓库中不存在名为 `java17` 的相对目录；使用系统实际安装的 JDK 17 绝对路径，语义等价于任务
要求的 `JAVA_HOME=java17`。

## 7. 验收边界

- `implemented`：搜索入口、查询 UI、三类结果展示、canonical anchor Reader 跳转、增量索引同步。
- `automated_verified`：定向测试、全量 QA 单元测试和 androidTest 源集编译通过。
- `device_not_run_by_scope`：按任务要求未进入设备验收，不声明真机交互或性能 PASS。
- 未执行 `assemble*`，因此没有生成或交付新 APK。
- 未实现历史全量回填、FTS/分词、搜索建议、AI、向量检索、同步或云服务。

## 8. R30 主要变更面

```text
A  ui/search/SearchScreen.kt
M  ui/home/HomeScreen.kt
M  ui/hub/LibraryScreen.kt
M  ui/navigation/NavGraph.kt
M  ui/viewer/ViewerScreen.kt
M  ui/pdf/PdfViewerScreen.kt
M  data/local/SearchIndex{Entity,Dao}.kt
M  data/repository/{Search,Document,Reading}Repository.kt
M  MarkLeafApplication.kt
M  res/values/strings.xml
M  repository/controller tests（注入 SearchRepository）
A  test/data/repository/SearchExperienceIntegrationTest.kt
M  test/data/repository/SearchRepositoryTest.kt
A  docs/R30_SEARCH_EXPERIENCE_REPORT.md
```
