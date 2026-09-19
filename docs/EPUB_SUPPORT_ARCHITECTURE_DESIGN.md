# EPUB 支持架构调查与 MVP 设计

> **范围状态（2026-09-19，用户明确授权）**：EPUB 有限阅读已立项（active object `EPUB_REFLOWABLE_MVP`），
> 本文档从只读调查转为实施依据，路线 B（XHTML → 中间模型 → Compose）为主路径。
> 有限范围：reflowable 文字章节连续阅读；DRM/fixed-layout/音视频/复杂排版明确不做。

| 字段 | 值 |
|------|-----|
| 文档类型 | 只读架构调查 + 设计评估（**非实现**；已 out-of-scope，见顶部标注） |
| 日期 | 2026-07-20 |
| 作者约束 | **不修改**生产代码、数据库、构建配置、依赖；**不修改** active object |
| 当前 active object | `R10_HOME_LIST_CARD_PERFORMANCE`（仍为唯一 active object；本报告不改变其状态） |
| 本报告状态 | 后续独立功能线候选；**禁止**在 R10 未关闭且未显式立项前开工 |

---

## 0. 结论摘要

1. **可以做**：EPUB（ZIP + OPF + XHTML）与现有「本地优先、无 INTERNET、ZIP 教材包」能力同构，技术上可接入。
2. **不能当小改动**：当前管线以 `DocumentType` + 二元路由（Markdown 阅读器 / PDF 阅读器）为轴；EPUB 需要**新类型、新导入/解包、新阅读会话、新进度语义**，并扩展外部打开与 Home 卡片。
3. **禁止**把 XHTML「降级转成 Markdown」当主路径：语义损失大、CSS/链接/图片基址难对齐，长期会污染 Markdown 解析器。
4. **推荐实现路线**：**B — XHTML → 中间文档模型 → Compose** 作为 MVP 主路径；用 **A（隔离 WebView）** 仅作可选 fallback 或后续增强，**不**默认引入重量级第三方阅读引擎（C）。
5. **安全必须一等公民**：在现有 `ZipPackageImporter` Zip Slip 防护之上，补齐 ZIP bomb、条目数/解压体积上限、XML 实体、脚本与网络禁用、资源沙箱。
6. **本轮交付物仅为本设计文档**；不立项、不改 R10、不写生产代码。

---

## 1. 现状链路审查（Markdown / PDF / MARKDOWN_PACKAGE）

### 1.1 总览图

```text
                    ┌─────────────────────────────────────────────┐
                    │              入口                            │
                    │  Home FAB (OpenDocument)                     │
                    │  External VIEW/SEND → ExternalOpenCoordinator│
                    └───────────────┬─────────────────────────────┘
                                    │
          ┌─────────────────────────┼─────────────────────────┐
          ▼                         ▼                         ▼
   DocumentRepository         ExternalDocumentImporter   ExternalFileIntake
   .importDocument()          (prepare → commit)         (旧/辅助 copy+import)
          │                         │
          │  ZIP? ──yes──► importZipPackage → packages/{id}/
          │  else resolve type
          ▼
   Room documents (documentType TEXT)
          │
          ├─ PDF ──────────────► Routes.pdfViewer  → PdfViewerViewModel
          ├─ MARKDOWN ─────────► Routes.viewer     → ViewerViewModel
          └─ MARKDOWN_PACKAGE ─► Routes.viewer     → 同上（uri=入口 .md）
```

### 1.2 文件导入

| 路径 | 入口 | 行为 |
|------|------|------|
| **主页 FAB** | `HomeScreen` → `ActivityResultContracts.OpenDocument()` | MIME 过滤器：`text/*`、`application/pdf`、`application/zip`、`application/x-zip-compressed`、`application/octet-stream`；业务层再校验 |
| **仓储导入** | `DocumentRepository.importDocument(uri)` | `takePersistableUriPermission`（失败可继续）；`ExternalUriMetadata.resolve` 显示名/大小；`DocumentType.isAllowedImport`；ZIP 走 `importZipPackage`，否则 PDF/Markdown 记 Room 行 |
| **教材包 ZIP** | `importZipPackage` + `ZipPackageImporter.extract` | 解压到 `filesDir/packages/{stableUriId}/`；解析入口 Markdown；`DocumentEntity.uri` 指向 **入口 .md 的 file://**；`documentType=MARKDOWN_PACKAGE` |
| **外部打开（现行主路径）** | `ExternalOpenCoordinator` → `ExternalDocumentImporter.prepare/commit` | 流复制到 `filesDir/library/{sha256}/…`，SHA-256 内容去重；`DocumentIdentityIndex` 绑定 externalKey；Room upsert |
| **外部打开（辅助）** | `ExternalFileIntake.importSharedUri` | 复制到 `filesDir/inbox/{id}/` 后调用 `importDocument`（ZIP 会走解包） |

**要点：**

- FAB 的 ZIP 与 ExternalFileIntake 会**解包教材包**；`ExternalDocumentImporter` 对非 PDF 仅按 `DocumentType.resolve` 写入类型（ZIP **不会**变成 `MARKDOWN_PACKAGE`，也不会解包）——外部 ZIP 与 FAB ZIP **行为不一致**。EPUB 设计必须**统一**「复制原包 + 受控解包」策略，避免再分叉。
- 导入大小软上限：`ExternalFileIntake.MAX_BYTES` / `ExternalDocumentImporter.MAX_BYTES` = **200 MiB**。

### 1.3 MIME / 扩展名识别

权威类型：`app/.../data/local/DocumentType.kt`。

| 能力 | 规则 |
|------|------|
| `resolve(mime, name, path)` | 仅 **PDF vs 默认 MARKDOWN**；MIME `application/pdf` / `application/x-pdf` 或扩展名 `.pdf`（在 mime 空/octet-stream/application/* 时）→ PDF |
| `isAllowedImport` | `.md`/`.markdown`/`.txt` **或** ZIP mime/扩展名 **或** PDF |
| ZIP | `isZipFileName` / `isZipMime`（`application/zip` 等） |
| **无 EPUB** | 无 `.epub`、无 `application/epub+zip` |

`DocumentType.fromStorage`：未知字符串 **回落为 MARKDOWN**（迁移安全，但误写类型会静默当 Markdown 打开）。

相关测试：`DocumentTypeTest`、`DocumentTypeZipTest`、`PdfProgressTest`（部分 resolve 断言）。

### 1.4 DocumentType 持久化

- Room 列：`documents.documentType TEXT NOT NULL`，默认 `'MARKDOWN'`（migration 1→2）。
- 枚举：`MARKDOWN` | `PDF` | `MARKDOWN_PACKAGE`。
- `DocumentEntity.type` → `DocumentType.fromStorage(documentType)`。
- 辅助：`isPdf`、`isMarkdownPackage`（无 isEpub）。

**无独立 EPUB 列**；扩展类型时优先 **新增枚举值 + 字符串存储**（与现有一致），避免为类型单独加列。

### 1.5 数据库与迁移

- DB：`markleaf.db`，**version = 6**，schema 导出：`app/schemas/.../6.json`。
- 实体：`documents`、`pdf_print_page_anchors`、`textbook_outline_nodes`（PDF 教材大纲，与 Markdown TOC 无关）。
- 迁移链：1→2 加 `documentType`/`pageCount`；2→3 印张锚点；3→4 教材大纲；4→5 大纲 provenance；5→6 PDF viewport session 多列。

**进度字段复用约定**（`DocumentEntity` 注释）：

| 字段 | Markdown / Package | PDF |
|------|--------------------|-----|
| `lastScrollIndex` | LazyColumn 绝对 item 索引 | 0-based 页码 |
| `lastScrollOffset` | LazyColumn offset px | 页内 fraction 编码 |
| `readingProgress` | 0–1 滚动比例 | ≈ page/(count-1) |
| `pageCount` | 0 | 总页数 |
| `pdfViewport*` | 未用 | 连续/分页会话 |

EPUB **不应**再 overload 为「假 PDF 页」或「假 Lazy 索引」而不文档化；见 §7 推荐模型。

### 1.6 最近文件卡片（Home）

- 数据：`HomeViewModel` ← Room 最近列表（按 `lastOpenedAt`）。
- 卡片标题逻辑 `resolveDocumentCardTitles`：
  - **PDF**：文件名 + supporting `"PDF"`；
  - **MARKDOWN_PACKAGE**：H1/`title` + 文件名或 `"Markdown 教材包"`；
  - **Markdown**：H1/`title` + 文件名或父目录标签。
- 进度文案：`isPdf` →「第 n 页」；否则百分比重进度。
- **性能约束（R10）**：卡片只读 Room 字段，**禁止**列表 recompose 时做文件 IO/重解析。EPUB 封面/书名必须在导入或首次打开时写入 Room，不能在 Home 滑动时读 EPUB。

### 1.7 阅读页路由

`NavGraph` / `MainActivity.navigateReplaceViewer`：

```text
DocumentType.PDF                 → pdf/{documentId}
MARKDOWN | MARKDOWN_PACKAGE      → viewer/{documentId}
```

外部打开 effect：`ExternalOpenUiEffect.NavigateToDocument(documentId, isPdf, …)` —— **仅布尔 `isPdf`**，非 PDF 一律进 Markdown viewer。

会话：`ExternalOpenCoordinator` 登记 `onViewerOpened/Closed`；PDF 有 `progressFlushHook`，Markdown 主要为顺序屏障。

### 1.8 进度保存与恢复

| 类型 | 保存 | 恢复 |
|------|------|------|
| Markdown | `ViewerScreen` 滚动/离开 → `ViewerViewModel.saveReadingPosition` → `DocumentRepository.updateReadingPosition` | 打开时 `document.lastScrollIndex/Offset` → LazyList |
| PDF | `FinalPdfProgressFlusher` + `updatePdfProgressSnapshotAtomic`（页 + viewport 事务） | 页索引 + viewport session |
| Package | 与 Markdown 相同（入口文档一条进度） | 同左 |

阅读显示设置：`ReaderSettings`（字号/行高/边距）经 **DataStore 全局**，Markdown 阅读器已接；PDF 另有阅读模式 prefs。

### 1.9 外部 Open With

- Manifest `VIEW`：`content` + pdf/zip/markdown/plain（**无 epub**）。
- Manifest `SEND`：pdf/zip/markdown/plain 分 filter。
- 流程：`onIncomingIntent` → 确认对话框（若有当前文档）→ prepare（旧会话仍活）→ flush progress → commit → `NavigateToDocument`。
- 身份：URI 级 externalKey + 内容 SHA-256；`DocumentIdentityIndex` 可重建，Room 为权威。

**缺口：** 导航与 UI 卡片多用 `isPdf`，缺少通用 `DocumentType`/`routeKind`，EPUB 必须改模型而非再塞一个 bool。

### 1.10 搜索、目录、缓存

| 能力 | Markdown | PDF | Package |
|------|----------|-----|---------|
| 文档内搜索 | `DocumentSearch` + `MarkdownSearchTextExtractor`（可见文本、块内匹配、上限 2000） | 无同路径 P0 搜索 | 同 Markdown |
| TOC | `MarkdownParser` → `TocItem`（H1–H3）+ `AnchorRegistry` | 嵌入大纲 / 教材 `textbook_outline_nodes` | 同 Markdown |
| 图片/资源 | `MarkdownAssetResolver` + package 沙箱 `ZipPackageImporter.resolvePackageImage` | PDF 位图缓存/tile | package root 沙箱 |
| 文本读取上限 | `readDocumentText` `MAX_PREVIEW_BYTES`（防巨型文件占内存） | N/A（分页渲染） | 读入口 md |
| 解析缓存 | 打开时解析；`title`/`headingCount` 写回 Room | 页渲染缓存 | 同 Markdown |

**EPUB 不能直接复用** `DocumentSearch(blocks: List<MarkdownBlock>)`，除非中间模型提供「可见文本段」适配层。

### 1.11 现有 ZIP 安全基线（可复用 / 不足）

`ZipPackageImporter` **已有**：

- 拒绝 `..`、canonical 路径必须在 `destDir` 下（Zip Slip）；
- 跳过 `__MACOSX` / `.DS_Store`；
- 图片相对路径拒绝 scheme / 越界。

**未见**（EPUB 必须补）：

- 条目数量上限、单文件/总解压体积上限、压缩比（ZIP bomb）；
- 解压过程字节计数与超时；
- XML 解析器 XXE/实体扩展关闭；
- 加密 ZIP/EPUB DRM 的明确拒绝策略。

---

## 2. EPUB 接入影响地图（只列不改）

> 下列为**将来实现时**的可能触点；本轮**零修改**。

### 2.1 核心模块 / 文件

| 区域 | 可能修改文件 | 变更性质 |
|------|----------------|----------|
| 类型 | `DocumentType.kt` | 增 `EPUB`；`resolve`/`isAllowedImport`；`MIME_EPUB`；`.epub` |
| 实体 | `DocumentEntity.kt` | 可选：EPUB 专用进度列 **或** 复用 + 编码约定；`isEpub` |
| DB | `AppDatabase.kt` + schema `7.json` + `MIGRATION_6_7` | 仅当进度/元数据需要新列；**仅加枚举值可不升版本**（TEXT 已存任意 type 名） |
| DAO | `DocumentDao.kt` | EPUB 进度更新 SQL（若新列/新语义） |
| 仓储 | `DocumentRepository.kt` | `importEpub` / 解包；读章节；进度 API |
| 包导入 | **新建** `epub/` 包：`EpubPackageImporter`、`EpubOpfParser`、`EpubTocParser`、`EpubSecurityLimits` 等 | 勿与 Markdown ZIP 入口逻辑混写 |
| 外部 | `ExternalFileIntake.kt`、`ExternalDocumentImporter.kt`、`ExternalUriMetadata.kt`、`ExternalOpenModels.kt`、`ExternalOpenCoordinator.kt` | MIME/扩展名；`isPdf` → `routeKind`/`documentType`；prepare 解包或延迟解包 |
| Manifest | `AndroidManifest.xml` | `application/epub+zip` VIEW/SEND；可选 `pathPattern` `.epub` |
| Home | `HomeScreen.kt`、`HomeViewModel.kt` | SAF MIME；卡片标题/副标题「EPUB」；进度文案（章/百分比） |
| 导航 | `NavGraph.kt`、`MainActivity.kt` | `Routes.EPUB_VIEWER`；三元路由；session id 前缀 `epub-` |
| 阅读 UI | **新建** `ui/epub/*`（Screen/ViewModel/组件） | 独立会话，避免污染 PDF/Markdown 性能路径 |
| 设置 | `ReaderSettings*` | 复用 DataStore；EPUB 注入字号/行高/边距/主题 CSS 或 Compose 样式 |
| 搜索 | `search/*` 或 epub 适配层 | MVP 可延后（见阶段计划） |
| 应用 | `MarkLeafApplication.kt`（若需单例缓存） | 可选章节缓存目录 |
| 字符串 | `res/values/strings.xml` | 错误文案：损坏/加密/过大/无 spine 等 |
| Proguard | `proguard-rules.pro` | 仅当引入第三方库时（路线 C） |
| Gradle | `app/build.gradle.kts` | **仅路线 C 或显式 XML 库时**；路线 B 优先 stdlib + 平台 XML |

### 2.2 数据结构（逻辑层，非本轮落地）

- `EpubBook` / metadata  
- `EpubManifestItem`  
- `EpubSpineItem`  
- `EpubTocNode`  
- `EpubReadingPosition`  
- `EpubCacheIdentity`  
- 可选 Room 表 `epub_package_cache`（documentId → extract path、opf path、content hash）——也可纯文件系统 + 内存

### 2.3 测试影响面（将来）

| 层级 | 建议用例主题 |
|------|----------------|
| 单元 | `DocumentType` epub resolve/allow；Zip Slip / bomb limits；OPF/NAV/NCX fixture；spine 顺序；内部链接解析；位置编解码 |
| 仓储 | import epub → Room type/uri；重复导入保留进度；损坏包 Unsupported |
| 外部打开 | VIEW epub；与 PDF/MD 替换确认；identity 去重 |
| UI 仪器 | 打开样例 → 目录跳转 → 杀进程恢复位置（可后置） |
| 回归 | **禁止**回归：R10 Home 卡片路径、Markdown 滚动、PDF 手势（EPUB 代码隔离） |
| 安全 | 恶意 zip/xml fixture 拒绝且不崩溃 |

### 2.4 明确不碰（本轮 + 推荐实现纪律）

- R10 Home 性能候选与已接受行为（除将来「卡片展示 EPUB 文案」的最小分支）  
- PDF tile/手势/viewport 生产路径  
- Markdown 解析器「为 EPUB 兼容」的 hack  
- 依赖清单（本轮零新增）

---

## 3. EPUB MVP 产品与技术范围

### 3.1 目标用户价值

在本地、无账号、无网络前提下，打开常见 **EPUB 2/3 reflowable** 电子书：能读、能跳目录、能记住进度、阅读版式可调。

### 3.2 功能范围内（MVP）

| 模块 | 要求 |
|------|------|
| 格式 | EPUB 2 与 EPUB 3 **reflowable** |
| 容器 | `META-INF/container.xml` → rootfile OPF |
| 包文档 | OPF：`metadata`（title/creator/language 等）、`manifest`、`spine`（含 `linear` 处理策略） |
| 目录 | EPUB 3 `nav`（`properties=nav`）优先；否则 EPUB 2 NCX |
| 正文 | spine 顺序加载 XHTML/HTML；基础块：标题、段落、列表、引用、强调、代码、图片、分隔 |
| 样式 | **基础 CSS 子集**（见 §5 路线 B）；用户字号/行高/边距/深浅色覆盖作者样式中的字体尺寸与页边 |
| 导航 | TOC 抽屉跳转；章节内 `#fragment`；跨章节相对链接（manifest 内 href） |
| 进度 | 按 spine 索引 + 章内归一化偏移（或段落锚点）；Home 显示百分比或「第 n 章」 |
| 导入 | SAF + Open With；完全离线 |
| 错误 | 损坏/缺 OPF/空 spine/加密 → 明确中文错误，不白屏 |

### 3.3 非目标（明确不做）

| 非目标 | 说明 |
|--------|------|
| **DRM** | Adobe/FairPlay 等加密 EPUB：检测后拒绝并提示「不支持受保护的电子书」 |
| **fixed-layout** | `rendition:layout=pre-paginated` 等：拒绝或只提示不支持 |
| **JavaScript** | 不执行 script；剥离或忽略 |
| **音视频** | 不播放；可占位「不支持的媒体」 |
| **在线资源** | 禁止 `http(s)` 图片/CSS/字体；无 INTERNET 权限保持不变 |
| **编辑 / 云同步 / 账号** | 与产品总原则一致 |
| **复杂 CSS 全兼容** | 不追求浏览器级布局引擎；浮动、复杂 grid、分页媒体等非目标 |
| **XHTML→Markdown 主路径** | 禁止作为架构基础 |
| **全文跨书搜索 / 书城** | 非 MVP |
| **迁移 PDFium 或改 PDF 栈** | 禁止 |

### 3.4 解析管线（逻辑）

```text
.epub (zip)
  → 安全解压到 app-private epubs/{cacheId}/
  → 读 META-INF/container.xml → full-path OPF
  → 解析 OPF metadata + manifest map(id→href,media-type,properties)
  → spine idref 序列 → List<SpineItem>
  → TOC: nav.xhtml 或 .ncx → 树
  → 打开章节：读 XHTML → 消毒/子集化 → 中间模型 / 或 WebView 输入
  → 资源：仅 manifest 内、位于解压根下的相对路径
```

### 3.5 阅读 UX 对齐

- 外壳：顶栏、返回、目录抽屉、设置 sheet（字号/行高/边距）与 Markdown 体验同级，**实现可独立 Composable**。  
- 主题：跟随系统深浅色 + Dynamic Color 背景；正文字色来自 Material，而非强制作者 CSS 全彩。  
- 不引入账号墙、广告、网络检测。

---

## 4. 实现路线比较（禁止 XHTML→Markdown 主路径）

### 4.1 路线 A — 隔离的本地 WebView 章节渲染

**做法：** 每章（或连续多章）在 `WebView` 中加载净化后的 XHTML；用 `WebViewAssetLoader` 或 `file` 映射到解压目录；注入 CSS 覆盖字号/主题；JS Bridge **只读**进度与跳转（或禁用 JS，用原生目录 + `loadUrl` fragment）。

| 维度 | 评估 |
|------|------|
| 实现成本 | **中低**：CSS/HTML 兼容开箱好；需自己做包解析、安全策略、进度（`scrollY`/Intersection 或注入） |
| APK 增量 | **~0**：系统 WebView |
| CSS/XHTML 兼容性 | **高**（相对 B）：常见 reflowable 书可读性最好 |
| 性能与内存 | **中**：大章 DOM、多 WebView 切换成本高；滚动手感与 Compose 列表不一致；需严格单 WebView 复用 |
| 安全性 | **风险最高面**：必须关 JS（或严格 CSP）、禁网络（`shouldInterceptRequest` 全拦非本地）、禁 file 穿越、禁 `content://` 任意访问；历史上 WebView 漏洞面大于纯 Compose |
| 与 Compose 外壳复用 | **壳可复用**（TopBar/TOC/设置）；**正文滚动/搜索/高亮难复用** R9 Markdown 路径 |
| 长期维护 | 依赖 WebView 版本碎片；OEM 差异；与「Compose 阅读性能」目标分叉 |

**适用：** 兼容性优先的快速 MVP 验证；或 B 的「复杂章节 fallback」。

### 4.2 路线 B — XHTML → 中间文档模型 → Compose（**推荐主路径**）

**做法：** 自研轻量 XHTML 子集解析 → 中立 `EpubBlock`（非 `MarkdownBlock`）→ Compose 懒列表渲染；图片走本地解码 + 尺寸上限；样式映射为 Compose `TextStyle`/padding，仅吸收基础 CSS（font-size/weight/align/margin 简化）。

| 维度 | 评估 |
|------|------|
| 实现成本 | **中高**：解析器 + 样式子集 + 链接解析工作量大；但边界清晰、可单测 |
| APK 增量 | **极低**：优先 `javax.xml`/`org.xmlpull` 等平台 API；无第三方引擎 |
| CSS/XHTML 兼容性 | **中**：覆盖教科书式 reflowable 足够；复杂杂志式会降级为近似排版 |
| 性能与内存 | **可控**：按章加载、LazyColumn、位图上限；可对齐 R9「块级」性能治理经验 |
| 安全性 | **好**：无脚本引擎；资源路径白名单；XML 安全配置集中 |
| 与 Compose 外壳复用 | **最高**：TOC/设置/进度/主题/沉浸顶栏模式可共享设计语言；搜索可复用「可见文本段」抽象 |
| 长期维护 | 完全自控；不绑 Readium 版本；需持续扩标签/CSS 子集 |

**明确：** 中间模型是 **EPUB 文档模型**，不是 Markdown AST；**不得** `toMarkdown()` 喂现有 `MarkdownParser`。

### 4.3 路线 C — 第三方 EPUB 阅读引擎

**候选方向（评估级，不引入）：** Readium Kotlin / 旧 epublib / 商业 SDK 等。

| 维度 | 评估 |
|------|------|
| 实现成本 | **集成中、定制高**：导航/主题/进度要适配引擎 API；与现有 Room/外部打开对齐成本不低 |
| APK 增量 | **中–高**（常见数 MB～十余 MB 级，视模块裁剪）— 与当前 release minify ~10–11MB 目标冲突风险大 |
| CSS/XHTML 兼容性 | **通常最高** |
| 性能与内存 | 取决于引擎；可能含自带 WebView/导航栈 |
| 安全性 | 依赖上游；仍需自管解压与 DRM 拒绝 |
| 与 Compose 外壳复用 | **低**：易变成「引擎 UI 嵌套」或双套导航 |
| 长期维护 | 许可证、传递依赖、AGP/Kotlin 版本、无网策略是否假设联网需审计 |

**结论：** MVP **不采用 C**；若未来兼容性债务压倒包体积，再单独立项评估裁剪版 Readium。

### 4.4 推荐决策

| 优先级 | 选择 |
|--------|------|
| **MVP 主路径** | **B** |
| **可选增强** | 单章 CSS 极复杂时 **A fallback**（同一解压缓存、同一进度模型） |
| **拒绝** | C 作为默认；XHTML→Markdown；在 Markdown/PDF 热路径里塞 EPUB 解析 |

---

## 5. 安全设计（必须）

### 5.1 威胁与控制

| 威胁 | 控制措施 |
|------|----------|
| **Zip Slip** | 规范化 entry 名；禁止 `..` 与绝对路径；`canonicalFile` 必须位于 `epubs/{id}/` 根下（对齐 `ZipPackageImporter.isUnder`） |
| **ZIP bomb** | 硬上限建议（可配置常量）：压缩包 ≤ 200 MiB（已有导入上限）；**条目数** ≤ e.g. 20_000；**解压总字节** ≤ e.g. 400 MiB；**单 entry** ≤ e.g. 32 MiB；**压缩比**异常（写入/压缩远超阈值）中止；禁止存储时无上限 `copyTo` |
| **加密/DRM** | 检测加密 entry 或已知 DRM 文件（如 `META-INF/encryption.xml` 含受支持外的加密）→ `UnsupportedProtected` |
| **XML/XHTML 恶意** | 关闭 DTD/外部实体/XInclude；限制解析深度与文档大小；不合规标签剥离 |
| **脚本** | 删除/忽略 `<script>`、事件属性 `onclick` 等、`javascript:` URL |
| **网络请求** | 应用保持 **无 INTERNET**；资源解析拒绝 `http`/`https`；WebView 路线下 `shouldInterceptRequest` 默认拒绝非沙箱 |
| **任意 file://** | 仅允许解压根内 URI；禁止 `file:///data` 等越界；不把用户任意 path 交给 WebView |
| **图片炸弹** | 解码前检查维度/像素预算（e.g. 最长边与总像素上限）；超限占位 |
| **路径混淆** | href 做 RFC3986 相对解析后仍做 sandbox 检查；`%2e%2e` 等解码后再验 |
| **异常降级** | 缺 nav 可用 spine 生成扁平 TOC；单章解析失败显示错误块并允许跳邻章；整包失败不写半残 Room 或回滚 extract 目录 |

### 5.2 存储布局建议

```text
filesDir/
  library/{sha256}/book.epub          # 可选：保留原包（外部打开去重）
  epubs/{documentId}/                 # 受控解压树
    META-INF/container.xml
    OEBPS/...
  epubs/{documentId}/.markleaf-epub-meta.json  # cache identity（可选）
```

删除最近阅读记录时：是否删除解压树/原包需产品决策；建议 **删 Room 时可清理 epubs 缓存**，与「不删用户原 SAF 文件」一致（只清 app-private）。

### 5.3 用户可见错误（文案方向）

- 不是有效的 EPUB / 缺少 container 或 OPF  
- 电子书已加密或受版权保护，墨阅无法打开  
- 文件过大或内容异常（安全限制）  
- 不支持固定版式（fixed-layout）  
- 文件可能已损坏  

---

## 6. 推荐数据模型

### 6.1 Room：`documents` 行（最小侵入）

| 字段 | EPUB 用法 |
|------|-----------|
| `id` | 稳定 id：`stableUriId` 或 `c:{sha256}`（与外部打开一致） |
| `displayName` | 文件名或 OPF title |
| `uri` | **原包** file/content URI，或解压根标记 URI（二选一，需全链路统一）；推荐 **原包 URI + 旁路 cache 目录** |
| `mimeType` | `application/epub+zip` |
| `documentType` | `"EPUB"` |
| `title` | OPF `dc:title`（导入/首次解析写入，供 Home） |
| `headingCount` | TOC 节点数（可选） |
| `lastScrollIndex` | **spineIndex**（0-based） |
| `lastScrollOffset` | **章内偏移编码**（见下） |
| `readingProgress` | 0–1 全书估计（spine 权重或等权） |
| `pageCount` | spine linear 项数量（语义变为 chapterCount，文档写清） |
| `pdfViewport*` | **保持 null**；勿复用 |

> 若担心与 Markdown Lazy 索引混淆，**可选 migration** 增加 `epubSpineIndex` / `epubCfiOrFraction` 列；MVP 可用编码约定 + 强类型 API 避免写错类型。

**章内偏移编码建议（无需新列）：**

- `lastScrollOffset`：0…1_000_000 表示章内滚动比例 × 1e6；或  
- 更高精度：高 8 位 reserved + 低位 fraction（需与 PDF 编码空间隔离，**仅当 documentType=EPUB 时解释**）。

### 6.2 EPUB metadata（内存 / 可选 JSON 旁路）

```text
EpubMetadata(
  title: String?,
  creators: List<String>,
  language: String?,
  identifier: String?,
  epubVersion: String?,   // "2.0" / "3.0" …
  isFixedLayout: Boolean,
  hasEncryptionXml: Boolean,
)
```

### 6.3 Manifest item

```text
EpubManifestItem(
  id: String,
  href: String,              // 包内相对路径（已归一化）
  mediaType: String,
  properties: Set<String>,   // nav, cover-image, …
)
```

### 6.4 Spine item

```text
EpubSpineItem(
  idref: String,
  href: String,
  linear: Boolean,           // non-linear 默认跳过阅读序，仍可被链接命中
  index: Int,                // linear 阅读序索引
)
```

### 6.5 TOC node

```text
EpubTocNode(
  id: String,
  title: String,
  href: String,              // 含可选 fragment
  spineIndex: Int?,          // 解析后绑定
  children: List<EpubTocNode>,
)
```

### 6.6 Reading position

```text
EpubReadingPosition(
  documentId: String,
  spineIndex: Int,
  fragmentId: String?,       // 优先于纯比例（若打开时仍存在）
  progressInChapter: Float,  // 0f..1f
  progressInBook: Float,     // 0f..1f
  contentRevision: String,   // cache identity，防解压变更错位
)
```

### 6.7 Cache identity

```text
EpubCacheIdentity(
  documentId: String,
  sourceSha256: String?,     // 有则强绑定
  sourceSizeBytes: Long?,
  opfPath: String,
  extractedAtEpochMs: Long,
  extractorVersion: Int,     // 安全策略/解析器版本，bump 则失效重解压
)
```

校验失败 → 删除 `epubs/{id}` 并重建。

### 6.8 中间渲染模型（路线 B）

```text
EpubBlock =
  Heading(level, spans, id?)
  | Paragraph(spans)
  | ListBlock(ordered, items)
  | Quote(blocks)
  | Image(resolvedFile, alt)
  | Divider
  | Code(text)
  | Unsupported(placeholder)
EpubChapterDocument(spineIndex, href, blocks, inlineIds)
```

与 `MarkdownBlock` **分类型**，共享的仅是「设置 + 外壳交互」概念。

---

## 7. 分阶段提交计划（未来独立 active object）

> 立项后方可写入 `.memory/active_object.yaml`。下列阶段彼此可 PR 隔离；**每阶段禁止改 PDF 生产热路径与 R10 已冻结行为以外的无关文件**。

### 阶段 E0 — 立项与夹具（文档 + testdata only）

- 锁定 2–3 本无 DRM reflowable 样例（EPUB2 + EPUB3）  
- 恶意夹具：Zip Slip、嵌套炸弹、XXE、encryption.xml  
- 验收：夹具清单与预期结果表进仓库 `testdata/epub_mvp/`（实现阶段再加）

### 阶段 E1 — 类型与导入骨架

- `DocumentType.EPUB` + allow/resolve  
- SAF MIME + Manifest intent  
- 安全解压 + container/OPF 最小解析 → Room 行 + title  
- **不**做阅读 UI  
- 验收：导入后 Home 可见类型副标题；错误包不崩溃

### 阶段 E2 — 阅读器 MVP（路线 B）

- `Routes.epub` + ViewModel  
- spine 章切换 + 基础块渲染 + 图片沙箱  
- ReaderSettings 接入  
- 验收：样例全书可顺序读；深浅色/字号生效

### 阶段 E3 — TOC + 内链 + 进度

- NAV/NCX → 抽屉  
- fragment / 跨章链接  
- 位置保存/恢复 + 进程被杀恢复  
- 外部打开完整路径（`NavigateToDocument` 类型化）  
- 验收：见 §8

### 阶段 E4 — 加固与搜索（可选）

- 安全上限调参、错误文案打磨  
- 文档内搜索（可见文本适配）  
- fixed-layout/DRM 检测完备  
- 回归矩阵全绿

### 建议 PR 粒度

1. `DocumentType` + 测试（无 UI）  
2. `EpubPackageImporter` 安全解压 + OPF  
3. Room/Repository 导入  
4. EpubViewer 最小  
5. TOC/进度/外开  
6. 文档与 ACCEPTANCE 条目  

---

## 8. 测试矩阵与验收标准

### 8.1 测试矩阵

| ID | 场景 | 层级 | 期望 |
|----|------|------|------|
| T1 | `.epub` + `application/epub+zip` 识别 | 单元 | `DocumentType.EPUB`，allow=true |
| T2 | 伪装 `.epub` 实为随机字节 | 单元/集成 | Unsupported/损坏提示 |
| T3 | Zip Slip entry | 单元 | SecurityException/失败，磁盘无越界文件 |
| T4 | ZIP bomb（高比/超条目） | 单元 | 中止，缓存目录清理 |
| T5 | 合法 EPUB2 + NCX | 集成 | TOC 非空，spine≥1 |
| T6 | 合法 EPUB3 + nav | 集成 | 优先 nav |
| T7 | encryption.xml / 加密 entry | 单元 | 明确拒绝 DRM |
| T8 | fixed-layout 标记 | 单元 | 拒绝或不可读提示 |
| T9 | 导入后杀进程再进 | 设备 | 同 documentId，进度恢复容差内 |
| T10 | 目录跳转再返回 | 设备 | 位置正确 |
| T11 | 章内 `#id` 与跨章相对链接 | 设备/集成 | 落点正确或安全忽略坏链 |
| T12 | 字号/行高/边距/深色 | 设备 | 可见变化，无网请求 |
| T13 | Open With VIEW epub | 设备 | 进 EPUB 路由非 Markdown |
| T14 | 与当前 PDF 阅读切换确认 | 设备 | 单会话、进度 flush |
| T15 | Home 列表滑动 | 设备 | 无 EPUB 解析 IO（R10 纪律） |
| T16 | 回归 Markdown 大文档滚动 | 设备 | 不劣于基线（抽样） |
| T17 | 回归 PDF 打开 | 设备 | 无崩溃 |
| T18 | 超大图 | 单元 | 占位，不 OOM |
| T19 | `javascript:` / 外链图 | 单元 | 剥离/占位 |
| T20 | 删除历史记录 | 集成 | Room 删除；app 缓存可清；用户原文件保留 |

### 8.2 MVP 验收标准（建议写入未来 ACCEPTANCE）

**必须全部满足方可称 EPUB MVP PASS：**

1. 无 INTERNET 权限；安装包不因 EPUB 引入不可审计的网络 SDK。  
2. 至少 1 本 EPUB2 + 1 本 EPUB3 reflowable 样例：导入 → 最近列表 → 打开 → 通读两章。  
3. TOC 可跳转；杀进程后进度恢复（章正确，章内偏移误差可接受，如视口高度 1 屏内）。  
4. 字号/行高/边距/主题至少各一档可调。  
5. DRM / 损坏 / 过大：有明确错误，不崩溃、不解压残留污染可写目录外。  
6. 外部打开 `.epub` 进入 EPUB 阅读器，**不会**当 Markdown 乱码打开。  
7. 自动化：单元测试覆盖类型识别、Zip Slip、OPF/TOC 夹具；关键路径无 PDF/Markdown 无关 diff。  
8. **不**将 XHTML 转为 Markdown 进入 `MarkdownParser`。

**明确不作为 MVP 门禁：** 完美 CSS、fixed-layout、全文搜索、字体子集嵌入全兼容、动画 SMIL。

---

## 9. 与三条现有链路的对齐建议

| 关注点 | 建议 |
|--------|------|
| 导入 | 对齐 **教材包**：受控解压到 app-private；同时可保留原包 hash 以便外部去重（对齐 `library/{sha256}`） |
| 类型路由 | 将 `isPdf: Boolean` 升级为 `DocumentType` 或 `ViewerRoute { Markdown, Pdf, Epub }`，一次改外部打开 + Nav + Home |
| 进度 | API 分离：`updateEpubReadingPosition`，避免 PDF flusher 误用 |
| Home | 仅展示缓存 title + 「EPUB」副标题 + 百分比；**零 IO** |
| 安全 | 以 `ZipPackageImporter` 为起点抽 **共享** `SafeZipExtractor`（实现阶段），Markdown ZIP 与 EPUB 共用上限 |
| 性能隔离 | EPUB 代码放独立 package；禁止在 Home item 中解析 OPF |

---

## 10. 风险与开放问题

| 风险 | 缓解 |
|------|------|
| 中间模型兼容性不足 | 样例驱动扩展标签；预留 WebView fallback 接口但不默认开 |
| 进度在重排版后漂移 | 优先 fragment id；其次比例；记录 `extractorVersion` |
| 外部打开与 FAB 行为再次分叉 | E1 起统一 importer API |
| 包体积 | 坚持路线 B/A，拒绝默认 C |
| 范围蔓延 | 严格非目标列表；独立 active object |

**开放问题（立项时由产品拍板）：**

1. 删除最近记录是否删除 `epubs/` 缓存与 `library/` 原包副本？  
2. non-linear spine 项是否对用户隐藏？  
3. MVP 是否包含文档内搜索？  
4. 多本书并行缓存的磁盘上限与 LRU？

---

## 11. 本轮合规声明

| 项 | 状态 |
|----|------|
| 生产 Kotlin/资源/Manifest | **未修改** |
| Gradle / 依赖 | **未修改** |
| Room / schema | **未修改** |
| `.memory/active_object.yaml` | **未修改**（仍为 R10） |
| 本文件 | **仅新增设计文档** `docs/EPUB_SUPPORT_ARCHITECTURE_DESIGN.md` |

---

## 12. 推荐后续动作（需人工确认后再做）

1. 将本文件链入 `docs/` 索引或 handoff（可选）。  
2. 准备无 DRM 样例与恶意夹具（仍可不改生产代码）。  
3. **单独**创建 active object（例如 `EPUB_REFLOWABLE_MVP`），并明确 **R10 已关闭且不在同一提交混改**。  
4. 按 E1→E3 实施；默认路线 **B**。

---

*End of design-only report.*
