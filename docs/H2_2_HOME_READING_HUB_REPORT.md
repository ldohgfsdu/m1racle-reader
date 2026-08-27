# H2.2 Home Reading Hub 报告

**日期：** 2026-08-26
**性质：** 首页升级任务。PDF/Markdown 阅读器核心零改动；GlassMaterial 参数零改动
（引擎文件 SHA 与 H2.1 收口时一致）；无网络/账号/同步/AI/笔记编辑/EPUB。
**验证：** `:app:testQaUnitTest`（JDK17）**1264 tests / 0 failures / 0 errors**
（基线 1245 + 新增 19：HomeViewModelTest 10 / BookmarkScreenTest 4 /
NavigationStateTest 5）。既有 `HomeLayoutContractTest`(9)、`HomeListUpdateTest`(6)、
`HomeImportEventTest`(2) 全部原样通过——上一轮的源码钉结构契约未被破坏。

---

## Phase 1 — Continue Reading

- **数据来源**：唯一事实源 DocumentEntity 阅读字段族（无新投影表）。
  选择器 `HomeHubSelectors.continueReading(list)`：输入为 DAO 的
  lastOpenedAt DESC 序，取第一条 `lastOpenedAt > 0` 的文档；
  **无阅读记录 → null → hero 卡整卡不渲染**（优雅降级，列表照常展示）。
- **展示**：继续阅读标签 + 文件名（复用 resolveDocumentCardTitles 标题链）+
  类型 + 当前位置行（PDF=「第 x / y 页」复用 formatPdfProgress；MD=进度语义复用
  formatReadingProgress）+ 进度百分比 + 进度条 + 前进箭头。
- **入口**：点击走既有 `onOpenDocument` 流程（NavGraph 单一活跃阅读器语义不变）。
- 状态推导提取为纯函数 `homeUiState(source, pendingDeleteIds)`（与乐观删除
  pending 协同：被删文档不可能成为 hero 目标），宿主直测。

## Phase 2 — Floating Glass Dock

- 形态：底部悬浮胶囊（`GlassDock`），非 BottomNavigation 样式；Control 级玻璃，
  圆角 26dp，navigationBarsPadding + 14dp 底距悬浮。
- 入口：首页 / 文库 / 书签 / 设置（`HubDestination` 四枚举 ↔ Routes 一一对应，
  由 NavigationStateTest 冻结）。
- 行为自然：静态选中态（primary 高亮）、无滚动隐藏动画（不引入重绘路径）；
  内容侧以底部 inset 避让（LazyColumn bottom inset += Dock 高度+间隙；FAB 上移
  到 Dock 之上）——**永不遮挡内容**。
- 显示范围：仅四个 Hub 顶层路由渲染 Dock（各屏自持实例）；viewer/pdf 路由天然
  无 Dock，阅读页沉浸行为不受影响。
- 导航：`navigateToHub` 以 HOME 为锚点 popUpTo+saveState/restoreState+
  launchSingleTop（标准顶层 Tab 模式）；阅读器返回路径（popBackStack(HOME)）
  不变。

## Phase 3 — Library

- `LibraryScreen` + 薄壳 `LibraryViewModel`（状态推导在纯函数 `libraryUiState`）：
  数据 = `observeRecentDocuments()` 冷流；过滤器 ALL/PDF/MARKDOWN
  （FilterChip），过滤逻辑 `LibraryFilters.apply` 保持最近序、Markdown 涵盖教材包。
- **EPUB 扩展位**：仅以枚举扩展点形式存在（PRD 明确 EPUB 不做，不放死按钮）。
- 文档卡：主行=文件名(1行)+类型徽标；次行=位置/进度 + 弱化的大小编号；
  LinearProgressIndicator 细进度条。实色 surfaceContainerLow（与首页卡片一致，
  见设计要求节）。

## Phase 4 — Bookmarks

- 数据：`BookmarkDao.observeAll()` JOIN documents.displayName →
  `BookmarkWithDocument` 投影（查询投影非第二事实源），`ReadingRepository.
  observeAllBookmarks()` 响应式冷流；`BookmarksViewModel` 直通暴露。
- 行展示：标题（书签 title 优先回退文档名）+ 创建时间（同日 HH:mm / 历史
  yyyy-MM-dd，now 注入可测）+ 文档名 + **locationKind 分流位置语义**：
  PAGE_INDEX→「页码 N」（显示 1-based 人读页）；SCROLL_POSITION→「滚动位置 N」；
  未知 kind 中性回退「位置 N」——不假设都是 PDF 页码。备注 note ≤2 行展示。
- 点击行 → 按 documentId 取实体后走统一 openDocument 流程。
- 空态文案引导到阅读器内建书签（本轮只做 Hub 展示端）。

## 设计要求落实

| 要求 | 落实 |
|---|---|
| Glass System 但强度低于阅读页 | 仅复用现有 preset：hero=Menu(α0.60/blur16)、Dock=Control(α0.50/blur12)；阅读页堆叠采样(RootAndMenu)未用于首页 |
| 继续 > 文档卡 > Dock | hero=Menu 玻璃 > 文档卡=实色 tonal(surfaceContainerLow) > Dock=Control 薄玻璃 |
| 轻量防玻璃堆叠 | 文档卡保持实色（沿用上轮 pin），全页仅 hero+dock 两处液态表面；Library/Bookmarks/Settings 经共享 `HubGlassShell` 提供 backdrop host |

兼容性说明：上一轮「cards stay solid」源码钉与本轮层级要求共同成立——强度链中
普通文档卡以实色+边框居中层，避免首页玻璃堆叠（公约 §1/§2 精神）。

## 测试（新增 19）

| 文件 | 数量 | 覆盖 |
|---|---|---|
| `ui/home/HomeViewModelTest.kt` | 10 | hero 选择（最新打开/未打开排除/0% 已算记录/降级 null/空态/乐观删除不可成为目标）、位置行 PDF 页码与 MD 进度语义、百分比钳制、文库三过滤器保序分流 |
| `ui/hub/BookmarkScreenTest.kt` | 4 | 书签查询联表直通与新在前排序、locationKind 三分支语义（含未知 kind 回退）、headline 回退、时间格式化（注入 now） |
| `ui/navigation/NavigationStateTest.kt` | 5 | 四入口固定顺序、route 往返、viewer/pdf/未知→null（Dock 不显示）、顶层可见性、Routes↔HubDestination 一致性 |

Fake DAO 策略沿用仓库既定约束（linux-aarch64 无 Robolectric SQLite 后端）：
DAO 内存 Fake 拦截，真实 SQL 已由 v9 迁移链测试覆盖表结构。

## 变更面

```
M app/src/main/java/com/markleaf/app/ui/home/HomeScreen.kt          (hero item + dock + insets + 2 个格式化函数升 internal)
M app/src/main/java/com/markleaf/app/ui/home/HomeViewModel.kt       (UiState.continueReading + homeUiState 纯函数)
M app/src/main/java/com/markleaf/app/ui/navigation/NavGraph.kt      (+3 路由/hub 导航/openDocument 共用)
A app/src/main/java/com/markleaf/app/ui/home/HomeHubModel.kt        (选择器/格式化)
A app/src/main/java/com/markleaf/app/ui/home/ContinueReadingCard.kt
A app/src/main/java/com/markleaf/app/ui/hub/{HubDestination,LibraryModel,BookmarkRowFormatter,GlassDock,LibraryScreen,BookmarksScreen,HubSettingsScreen}.kt
M app/src/main/java/com/markleaf/app/data/local/{ReadingLibraryEntities,ReadingLibraryDaos}.kt (+observeAll JOIN)
M app/src/main/java/com/markleaf/app/data/repository/ReadingRepository.kt (+observeAllBookmarks)
M app/src/main/res/values/strings.xml (+14)
± 测试 ×3 新增、×2 适配（Fake 补 observeAll）
```

阅读器核心（PdfViewerScreen/ViewerScreen/两 ViewModel/GlassMaterial.kt）零改动。

## 未做项（明确声明）

- **device_pending**：Dock 手感、hero 玻璃观感、暗色主题实拍、IME 场景——按公约由人工装包验收（本轮不进入设备验收）。
- 书签/收藏的**创建入口仍在阅读器侧**（后续任务）：Hub 只做 Library 展示端。
- Settings 为最小静态页（版本信息+阅读外观指引），无新设置项。
