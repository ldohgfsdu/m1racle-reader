# R9 Markdown Scroll Chrome Recovery Report

**Object:** `R9_COMPOSE_LIST_VS_PDF_SCROLL_ISOLATION`  
**Date:** 2026-07-16  
**Scope:** Markdown reader top-bar scroll chrome only  
**Final conclusion:** **DEVICE_ACCEPTED**

ADB device retest update: functional slow/fast drag, reverse jitter, search, landscape,
document reset and settled-top checks passed on 25098PN5AC / Android 16. FrameTimeline
SQL: 279 frames, p50 5.449 ms, p95 10.133 ms, no frame over 16.67 ms, two App
Deadline Missed. See `docs/R9_MARKDOWN_SCROLL_CHROME_ADB_RETEST.md`. Human feel passed on 2026-07-19.

---

### 1. 隔离证据

来源：`docs/R9_COMPOSE_LIST_VS_PDF_SCROLL_ISOLATION_REPORT.md`（设备 `198.18.0.1:35627` / 25098PN5AC，120Hz warm）。

| Path | ADM (r2+r3 mean) | >8.33ms | p95 | late_present | Notes |
|------|------------------:|--------:|----:|-------------:|-------|
| A Markdown chrome ON (原生产) | 7.5 | 272 | 9.83 | 215 | nestedScroll + hide + Animatable |
| B Markdown fixed bar（诊断） | **3.0** | **114.5** | **8.51** | **53.5** | 无 scroll-hide 联动 |
| PDF continuous control | — | 11 (r2) | 7.17 | — | VALID；生产 PDF **未改** |
| Home | — | 610 (r2) | 11.7 | — | **独立**卡片 workload（本轮不处理） |

固定顶栏相对原生产：ADM **−60%**，>8.33 **−58%**，late_present **−75%**。

Deep slice（同报告 §2.4）：

| Path | Main doFrame>8.33 | measure>5ms | anim sum_ms |
|------|------------------:|------------:|------------:|
| md_warm r2 (A) | 96 | 58 | 3724 |
| md_fixedON r2 (B) | **32** | **27** | 3290 |

---

### 2. 原实现调用链

| 步骤 | 文件 / 符号 | 行为 |
|------|-------------|------|
| 1 | `ViewerScreen.kt` → `ViewerSuccessScreen` | `remember` `ReaderTopBarScrollState`；`asNestedScrollConnection()` |
| 2 | `ViewerScaffold` | `Modifier.nestedScroll(connection)`（非搜索时） |
| 3 | `ReaderTopBarScrollState.onPostScroll` | `UserInput` 时 `onUserDragDelta`；**return Offset.Zero**（不消费正文） |
| 4 | `visible: MutableState` | 达 hide/show 阈值后翻转 |
| 5 | `ReaderTopBarOverlay` | `snapshotFlow { visible }` → `Animatable.animateTo(..., tween(190))` |
| 6 | `graphicsLayer { translationY }` | 每动画帧更新顶栏绘制 |
| 7 | （原）`LaunchedEffect` | `snapshotFlow { firstVisibleItemIndex==0 && offset<=0 }` **滚动中每帧观测** → `forceShow()` |
| 8 | `ReaderDocumentBody` | `listTopInset = ReaderTopBarHeight + 8.dp`（固定；不读 visible） |

Scaffold `topBar` 在非搜索模式下为空；正文与顶栏为 **Box overlay**（HEAD 已是 overlay 结构，并非 Scaffold 动态 height 挤压）。

---

### 3. 根因

**Markdown-only：** 滚动顶栏联动路径的额外开销，不是 LazyColumn 本身「写死无解」。

固定顶栏改善的已证实原因：

1. **动画帧消失** — 原 `Animatable.animateTo(190ms)` 在 show/hide 翻转后持续多帧与 fling 争用；fixed 无翻转 → 无该时钟。  
2. **状态写入 / 观测减少** — 无 `visible` 翻转；无（原）每帧 `firstVisibleItemScrollOffset` at-top `snapshotFlow`。  
3. **measure/layout 次要下降** — deep slice measure>5ms 58→27；正文 padding 本已固定，剩余 measure 来自 Markdown 块 + 偶发 chrome 失效级联，fixed 去掉 chrome 级联。  
4. **viewport 高度本身** — 原生产已是 fixed `listTopInset`，**不是**「顶栏 height 挤压 LazyColumn」型根因；诊断 fixed 的收益主要来自 **去掉联动副作用**，不是改 padding 公式。

**非根因（本轮）：** PDF tile/gesture、Home 卡片、P1 高亮、R8 图片、External Open session。

---

### 4. 生产修复（候选 C）

**不得**把 always-visible 作为唯一生产方案。本轮落地：

**overlay + `graphicsLayer.translationY` + 阈值自动隐藏（snap，无 Animatable 帧循环）**

| 要求 | 实现 |
|------|------|
| 正文 viewport 稳定 | `listTopInset = ReaderTopBarHeight + 8.dp` 固定；不随显隐改 padding/height |
| 顶栏位移 | `graphicsLayer.translationY`；visible→0 / hidden→`-heightPx` |
| 无 per-delta 动画 | `mutableFloatStateOf` **snap** 一次/翻转；删除 `Animatable.animateTo(190)` |
| UI 层状态 | 仅 `ReaderTopBarScrollState`；不写 VM/DB/SavedState |
| 阈值 + 迟滞 | hide 32dp / show 88dp / reversal jitter 12dp / cooldown 250ms；同向慢拖逐帧累计 |
| 顶部强制显示 | **settled** 后检查 at-top（不滚动中每帧读 offset） |
| nestedScroll 不消费 | `onPostScroll` 恒 `Offset.Zero`；仅 `UserInput` |
| 文档切换 | `remember(document.id, density)` 新建 state → 默认 visible |
| 搜索优先 | `searchActive` → `forceShow` + `nestedScrollConnection = null` + SearchTopBar |

**改动文件（chrome 范围）：**

- `app/src/main/java/com/markleaf/app/ui/viewer/ReaderTopBarScroll.kt` — `reset()`、同向慢拖累计、反向 jitter、observe-only 连接  
- `app/src/main/java/com/markleaf/app/ui/viewer/ViewerScreen.kt` — 恢复 nestedScroll；snap overlay；document key；settled at-top  
- `app/src/test/java/com/markleaf/app/ui/viewer/ReaderTopBarScrollStateTest.kt` — 阈值/迟滞/cooldown/nestedScroll 不消费等  

**未改：** PDF、Home、搜索语义、P1 高亮逻辑、R8、External Open、DB、H1.5A。

**R9 closure commit on acceptance; 未 push。**

---

### 5. 原版 / 固定顶栏 / 优化自动隐藏 A/B

| 版本 | 定义 | 状态 |
|------|------|------|
| **A** | HEAD 原生产：nestedScroll + threshold + `Animatable.animateTo(190)` + 滚动中 at-top flow | 隔离报告已测 |
| **B** | 诊断：`nestedScrollConnection = null`，栏常显 | 隔离报告已测 |
| **C** | 本轮：nestedScroll + threshold + **snap translation** + settled at-top + document key | **代码已落地；本环境无 adb 设备，FT 待人工** |

**C 相对 A 的预期机制（设计层，非本轮新 FT 数字）：**

- 去掉 190ms 动画帧队列 → 接近 B 的 anim 侧收益  
- 去掉滚动中 at-top 每帧 snapshot → 减少无效 invalidation  
- 保留自动隐藏 UX 与固定 list padding → 无正文跳动  

若人工 FT 中 C 明显差于 B，优先查：nestedScroll 回调频率、`visible` 翻转次数、overlay 子树 recompose——**不得**以「自动隐藏天然有开销」结案。

---

### 6. 功能回归

| 项 | 结果 |
|----|------|
| 阈值/迟滞/慢速同向 delta/反向 jitter/forceShow/reset/nestedScroll 不消费 | **单元测试通过**（`ReaderTopBarScrollStateTest`） |
| R9 搜索 / P1 高亮 / markdown / R8 image / package / share 单测 | **`testDebugUnitTest` 定向 + 全量 debug unit 通过** |
| `lintDebug` | 通过 |
| `assembleDebug` / `assembleQa` / `assembleQaAndroidTest` | 通过 |
| 真机手势：hide/show/fling/搜索/横竖屏/External Open | **PASS**（自动 QA + 2026-07-19 human feel） |

逻辑上保证（代码审查）：

- 向下累积 ≥ hide → 隐藏一次；同向不再重复启动动画（snap 单次写）  
- 向上累积 ≥ show → 显示一次  
- 小抖动 < jitter 忽略  
- 文档 key 变化新建 state → 默认显示  
- 搜索模式断 nestedScroll + forceShow  

---

### 7. 性能结果

| 版本 | 本轮设备 FT | 引用数据 |
|------|-------------|----------|
| A | 未重采（无设备） | 隔离 warm r2+r3 mean：ADM 7.5 / >8.33 272 / late 215 |
| B | 未重采 | ADM 3.0 / >8.33 114.5 / late 53.5 |
| C | **已采**（25098PN5AC / Android 16） | 279 frames；p50 5.449ms；p95 10.133ms；max 14.001ms；>16.67ms=0；App Deadline Missed=2 |

FrameTimeline recorded 2 App Deadline Missed events, but no application frame exceeded 16.67 ms; p95 was 10.133 ms and maximum application frame duration was 14.001 ms. No sustained or interaction-correlated jank was observed. This is not described as zero dropped frames. Together with >100 gesture cycles and five valid `gfxinfo` runs, the sample is sufficient for the R9 automated performance gate; repeat capture only if human review finds a specific issue.

---

### 8. 已知限制

1. **C 的自动化功能、FrameTimeline 与人工交互体感均已通过。**  
2. Snap 无 190ms 过渡 — 自动隐藏保留，动效更「硬切」；若产品要柔和过渡，须用**单次**短动画且禁止 per-delta 队列后再测。  
3. at-top forceShow 在 **settle** 后触发 — 快速掠过顶部时可能短暂延迟显示（避免 120Hz offset 观测）。  
4. 工作区仍含 **P1 高亮等未提交改动**（本轮未碰其逻辑，也未 commit）。  
5. Home 卡顿未动。

---

### 9. Home 独立问题

记录为后续候选对象（**本轮未改代码**）：

**`R10_HOME_LIST_CARD_PERFORMANCE`**

- 证据：ladder E 卡片 Surface 复杂度跳变；Home 无 scroll-hide chrome  
- 与 Markdown chrome 根因分离  

---

### 10. 人工复验 APK

| Artifact | Path | sha256 |
|----------|------|--------|
| Debug | `dist/r9-markdown-scroll-chrome/markleaf-debug-r9-md-chrome-c-slow-drag.apk` | `190cd15a88826130332c5ae0e434999874743c6943cbda0251f9809078d62d18` |
| QA | `dist/r9-markdown-scroll-chrome/markleaf-qa-r9-md-chrome-c-slow-drag.apk` | `89e5d81c590cdf609e3b7ff3f8f5ac09a3f572380a4d34d82f38f126d1406a2b` |
| Checksums | `dist/r9-markdown-scroll-chrome/apk.sha256` | — |

**人工体感协议（QA 包至少两轮）：**

1. 正常阅读手势，不刻意找问题。  
2. 刻意慢拖、微抖、明确反向、fling 后立刻反向。  
3. 只判断自动化无法可靠衡量的主观项：慢拖是否跟手、阈值是否自然、微抖是否稳定、明确反向是否及时、fling 后是否残留、snap 是否闪跳/回弹/停顿、旋转后首次手势是否自然、连续数分钟后手感是否漂移。  
4. 无粘滞、非预期突跳、抖动、方向切换迟钝或随时间漂移即可通过。  

---

## 结论

**DEVICE_ACCEPTED**

- 根因与隔离证据对齐；生产路径为 **C（overlay + snap translation 自动隐藏）**，非 always-visible 定案。  
- 自动测试、ADB 功能门禁、gfxinfo、Perfetto FrameTimeline 与人工体感均已完成。  
- 2026-07-19 在 25098PN5AC / Android 16 上使用冻结 QA 候选冷启动进入 Large Search Document；正常阅读、慢拖、微抖、明确反向、fling 后立即反向及持续使用均由人工判定通过。  
- 安装包回拉哈希与候选一致：7e3df156cba089de515e0b3b1a45ae8b2ca4e1674aee68413a3eca7ff61f19cf。  
- Chrome Perfetto 冻结哈希：38451ae9a93d331713b6850f633f9c1e1e52739dd5dee94220cae8aa660de467。  
- 验收证据：dist/r9-markdown-scroll-chrome/human-feel-pass/20260719-device-accepted/。后置 gfxinfo 因进程不可 dump 而无效，未用作正向证据；crash buffer 为空，logcat 无 MarkLeaf FATAL/ANR。  
- R9 关闭；未 push，未进入 Home / P1 checkpoint。
