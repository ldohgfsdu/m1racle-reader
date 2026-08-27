# R22 Reader Interaction Polish 报告

**日期：** 2026-08-25
**范围：** 阅读器交互打磨（R22.1 胶囊 / R22.2 More 菜单空间动画 / R22.3 PDF 视口 / R22.4 Chrome 状态机收口）。
**约束遵守：** Glass Material Engine 冻结未动（`GlassMaterial.kt` / `GlassAlertDialog.kt` 零改动）；无新增依赖、无新增 UI 单元、无设置项；布局层级与业务语义不变。

---

## R22.1 Capsule Interaction Polish — implemented / automated_verified（构建+全量单测）

### 结构化内容模型（单一事实源）

- 新增 `PdfCapsuleData(title, positionLine, progressPercent, printDetail)`，由 Ready 态统一推导：
  - 标题 = 当前最深大纲节点标题（`currentOutlineNode?.title`），无大纲回退文档名；
  - 位置 = `pageLabel`（"78 / 982"）；
  - 进度 = `(pageIndex+1)/pageCount` 四舍五入百分比；
  - 教材页绑定详情仅在展开态显示（默认态不堆信息）。
- Loading/Error 态传标题-only 数据。旧 `capsuleMain/capsuleSub` 拼接字符串路径删除。

### 布局重设计（默认看一眼知道在哪里；展开才提供操作）

- **收起态（200dp pill）：**
  - 第一行：章节名（溢出自动 marquee）
  - 第二行：`378 / 384 · 98%`
- **展开态（同一 surface 向下生长）：**
  - Header 交叉淡变为仅章节标题
  - hairline 分隔线
  - `页码 · 教材页 N` 行
  - `阅读进度 N%` 行
  - 页码输入 + 取消/跳转（原 JumpPageBody 移除重复标题/范围提示，纯操作区）

### 动画：单一 spring 时钟

- 删除双 tween 时钟（宽度 tween(200) + bodyProgress snapTo/animateTo），改为 **一个** `Animatable<Float>` morph：
  `spring(dampingRatio=0.8f, stiffness=450f)` 同时驱动 宽度、梯形轮廓、header 交叉淡变、body 区域高度、body 内容 alpha（×1.5 提前淡入）。
- 同一 layout node 全程不销毁重建；顶边位置连续性不变（`CapsulePopoverShape` topWidth=pill 宽度）；中断续播天然连续（animateTo 从当前值出发）。

### 长章节名 marquee

- `basicMarquee(iterations=MAX_VALUE, initialDelayMillis=1500, repeatDelayMillis=1200)`，慢速默认值；仅溢出时滚动（fitting 文本不动画）；无手动横向滚动。应用于收起态标题与展开态标题。

### 字符串

- 新增 `pdf_capsule_progress_percent`（"%1$d%%"）、`pdf_capsule_reading_progress`（"阅读进度 %1$d%%"）；删除失效的 `pdf_jump_title`、`pdf_jump_range`。

## R22.2 More Menu Spatial Animation — implemented / automated_verified

- 打开 morph 由 tween 改为 **spring**（`MoreMorphSpring`，同胶囊参数族）；关闭保持 170ms 确定性 tween（与宿主 `MENU_CLOSE_MORPH_MS=200` 交接窗口匹配）。
- **单一时钟**：圆角、内容 alpha、三点图标 alpha、内容 scale 全部从 spring 驱动的卡片宽度推导 `expandFraction`：
  - corner: Closed↔Open 插值；contentAlpha = f×1.6 封顶；iconAlpha = 1−2.5×f；contentScale = 0.94→1。
- 内容 `graphicsLayer` 以 **top-right TransformOrigin** 微缩放 —— 卡片从按钮锚点生长、收回按钮，全程 TopEnd 对齐锚点不动，三点按钮槽位不变。
- 删除两个独立 alpha 时钟（`animateFloatAsState` ×2）与 `IconFadeMs`、`MorphExpandMs` 死常量。

## R22.3 PDF Viewport Interaction — 部分 implemented；zoom-leak 修复 device_pending

### Pinch Zoom 焦点保持 — automated_verified（既有三层覆盖，本轮全量通过）

- `ZoomPanPhysicsTest`：scale 变化保持 legal focal anchor；centroid 移动时 anchor 不漂移。
- `R7H14UnifiedGestureControllerTest`：真实控制器 pinch 帧 `focalAnchorDriftPx ≤ 0.001`（finger midpoint ↔ document focal point）。
- `R7P0b1PinchAnchorTest`：多帧 zoom+pan、pointer rebase、settle commit 后焦点文档点 ≤1px 稳定。

### 横向快速翻页 scale 泄漏 — pending（根因证据不足，禁止盲修）

静态审查结论：commit 路径本身满足「old transform → commit → new page reset from viewport state」（`horizontalSession(page)` 捕获 → `restoreViewportSession(copy(pageIndex=target))`），渲染侧 stale session 已有 `pageIndex==activePage && generation!=applied` 双重门。未能在静态层面复现「继承错误 scale」的确切根因；按公约「修 bug 必须先确认根因」，不提交猜测性修复。

**待办（设备回归在线后）：** QA 包 + `MarkLeafH14/H15_PAGE_TRANSITION_COMMIT` 日志（scaleBefore/scaleAfter）+ 快速连翻复现；必要时扩展 `R7H15ThreePageStripRealUiTest` 自动化多页快翻断言。

### 一个已识别的可疑点（记录，不在本轮修改）

同一手势内 strip commit 后继续拖拽触发第二次 settle 时，`pageStripSettlingGeneration == pageTransition.gestureGeneration` 早退守卫可能跳过第二次 settle（`R7H15PlatformScrollAndPageStripTest` 未覆盖该序列）。需设备/ instrumentation 复现后再定性。

## R22.4 Chrome INTERACTION_LOCKED — implemented / automated_verified

- `PdfChromeScrollStateMachine` 新增正交锁定模式位（level 不变）：
  - `enum ChromeInteractionLock { PINCH, PAGE_JUMP, TEXT_SELECTION }`
  - `beginInteractionLock/endInteractionLock`（幂等；进入/退出即清手势残留）
  - 锁定期间 `onScrollSample` 返回空且不累计、`onFling` 返回 null；中央单击仍允许（显式操作）。
- 接线（PdfViewerScreen Ready 态）：pinch 经 `onPdfZoomGestureActive` 包装（含 `setZoomGestureActive`）；page jump 以 `LaunchedEffect(pageJump?.id)` 包裹跳转窗口。TEXT_SELECTION 预留枚举（PDF 无文本选择，暂无调用方）。
- 回归测试 ×4（PdfChromeScrollStateMachineTest）：锁定期样本忽略且 level 不变、解锁后干净 session 重累计、嵌套锁需全部释放、begin/end 幂等。

---

## 验证基线

| 项目 | 结果 | 证据 |
|---|---|---|
| `:app:testQaUnitTest`（JDK17） | **1173 tests / 0 failures / 0 errors**（+4 新增） | 本地构建输出 |
| `:app:compileQaKotlin` | PASS | 同上 |
| `:app:assembleRelease` | PASS → `.external_outbox/markleaf-release-latest.apk` 11,509,603 bytes | SHA-256 `3e55b800f626c825a0f4d6a024ce74e54681270347d64a17fa332171e4089936` |
| 设备交付 | **PENDING**：无线调试离线（192.168.0.92:36791 连接超时，多次重试失败）。未能复制到 `/storage/emulated/0/Download/markleaf-release-latest.apk` | adb 输出 |
| 真机验收（胶囊动画自然/marquee 自然/页码同步/Menu 出入锚点/PDF fling·pinch·横翻连续恢复/Performance Glass Engine PASS 保持） | **全部 device_pending** | 无 |

## 设备回归清单（下次 ADB 在线执行）

1. `adb install -r` release → 冷启动冒烟（crash 缓冲干净）。
2. 胶囊：点击展开/收起 ×10（观察 spring 手感、顶边不跳、内容交叉淡变）；长章节 PDF 看 marquee 1.5s 起播；翻页时页码/进度即时同步。
3. Menu：从三点按钮打开/关闭 ×10（从锚点生长/缩回，无跳跃）；展开 inline 区后再关。
4. Chrome 锁定：双指 pinch 拖动内容 → chrome 不自动收起；TOC 跳页动画期间 chrome 不收起；松手后正常滚动收纳/恢复。
5. 横向：pinch 至 2.5x 后快速连翻 ≥5 页，抓 `H14/H15` 日志核对每帧 `scaleBefore/scaleAfter` 一致性（R22.3 根因取证）。
6. Performance：gfxinfo idle 0 redraw、GPU cache 不增长（Glass Engine PASS 状态保持）。
