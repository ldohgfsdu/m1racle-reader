# R23 Reading Assets Foundation 报告

**日期：** 2026-08-26
**性质：** 阅读资产闭环建设。GlassMaterial/阅读引擎渲染逻辑零改动；Home 架构零改动；
无 AI/云同步。
**验证：** `:app:testQaUnitTest`（JDK17）**1277 tests / 0 failures / 0 errors**
（基线 1264 + 新增 13）。schema `10.json` 导出。

---

## 1. Reader Bookmark Creation（书签写入闭环）

### 唯一写入路径（架构红线）

```
UI（PdfViewerScreen / ViewerScreen）
   → ReaderBookmarkController.toggle(location)      ← UI 侧唯一入口
      → ReadingRepository.findBookmarkAt / createBookmark / removeBookmarkAt
         → BookmarkDao                                ← UI 永不直访
```

由 `ReadingAssetsAuthorityTest` 源码钉锁定：两阅读器源码不得出现
BookmarkDao/ReadingRepository 引用；必须接收 Controller 并经 `.toggle(` 写入；
NavGraph 按路由注入对应坐标系实例。

### 坐标系绑定

| 阅读器 | locationKind | location 语义 | 注入点 |
|---|---|---|---|
| PDF | `PAGE_INDEX` | 0-based 当前页索引（Ready.pageIndex） | NavGraph PDF 路由 remember(documentId) |
| Markdown | `SCROLL_POSITION` | LazyColumn 绝对 item index（与 lastScrollIndex 同坐标系） | NavGraph VIEWER 路由 |

### 入口形态（两侧不对称 chrome 的最小侵入）

- **PDF**：More 菜单**追加末行**「添加书签/移除书签」。约束依据：
  `MainMenuContent` 将 plainMenuItems 行索引 2–5 硬编码映射到四个 Child 锚定，
  插入会破坏 morph 契约——追加至索引 6 无 Child 绑定，安全（行数动态计入
  totalRowCount）。
- **Markdown**：顶栏新增第 4 个图标钮（Search 前）：Outline=未收藏 /
  Filled(primary)=已收藏，点击切换。
- 菜单/图标态来自 `bookmarkedLocations()` 一次性加载 + 本地集合维护，
  **无逐帧查询、无新增重绘路径**。

## 2. Bookmark State Feedback

轻量 snackbar 反馈（「已添加书签」「已移除书签」），复用两屏既有
SnackbarHost——零布局改动、零新表面。PDF 经现有 snackbarHostState；MD 复用
复制反馈同通道。

## 3. Annotation Foundation（schema v10，未接 UI）

- `highlights(id, documentId FK-CASCADE, locationKind, location, anchorStart?,
  anchorEnd?, excerpt?, createdAt)` —— 定位语义与书签一致，文档中性；
- `notes(+ content NOT NULL, updatedAt)`；
- 索引：documentId + (documentId,locationKind,location) 双索引；
- `MIGRATION_9_10` 纯增量建表（SQL 常量公开，宿主 JDBC 直跑验证）；
- Repository 区块：add/remove/listForDocument（highlight）；add/updateContent
  (createdAt 不被覆盖)/remove/listForDocument（note）。
- 本轮零 UI 调用方。

## 4. 测试（新增 13）

| 文件 | 数量 | 覆盖 |
|---|---|---|
| `ui/reader/ReaderBookmarkControllerTest.kt` | 5 | 创建字段正确性（PDF PAGE_INDEX / MD SCROLL_POSITION）、toggle 加→删→加循环、删除按三元组精确隔离（跨位置/跨 kind 不误伤）、bookmarkedLocations 按 kind 过滤供状态图标 |
| `data/repository/AnnotationRepositoryTest.kt` | 3 | 高亮往返（可选锚点/摘录）、最简创建路径、note 创建/更新(updatedAt)/删除 |
| `data/local/MigrationV9ToV10Test.kt` | 2 | 列结构与索引名冻结、FK 拒孤儿 + CASCADE |
| `ui/reader/ReadingAssetsAuthorityTest.kt` | 3 | 两屏禁 DAO/仓库直引、必经 controller.toggle；NavGraph 双坐标系注入钉；Repository 单一门面 |

既有测试全部保持通过（含 H2.1 权威钉 version 断言随迁移链有意识修订 9→10）。

## 变更面

```
M data/local/AppDatabase.kt                (v10 + MIGRATION_9_10 + 2 dao)
A data/local/{AnnotationEntities,AnnotationDaos}.kt
M data/local/ReadingLibraryDaos.kt         (getAt/deleteAt/listForDocumentAllKinds)
M data/repository/ReadingRepository.kt     (定位读写 + 标注区块; 4 参构造)
A ui/reader/ReaderBookmarkController.kt
M ui/pdf/PdfViewerScreen.kt                (菜单末行 + 状态 + snackbar)
M ui/viewer/ViewerScreen.kt                (顶栏书签钮五段链路)
M ui/navigation/NavGraph.kt                (双控制器注入)
M res/values/strings.xml                   (+4)
A schemas/.../10.json
± 测试 ×4 新增、×3 Fake 适配
```

## 未做项（明确声明）

- **device_pending**：菜单第 5 行的 morph 视觉、MD 顶栏 4 图标拥挤度实拍、snackbar 手感。
- Highlight/Note 无 UI 入口（本轮边界）；选区→anchor 映射属后续标注 UI 任务。
- 收藏（Favorite）仍无阅读器入口（H2.2 时已声明，保持范围纪律）。
