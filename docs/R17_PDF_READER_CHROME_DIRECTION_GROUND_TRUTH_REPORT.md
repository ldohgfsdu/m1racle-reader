# R17 PDF Reader Chrome 方向映射修正：锚点实测 ground truth + 单方向规则矩阵

**Date:** 2026-08-04
**Scope:** R16 状态机方向映射经实机验证「整体反向」后，按「先证明内容往哪儿动，再命名方向，再驱动状态机」
原则修正。本轮**不改阈值**（36/56/32/28dp 原样），只修方向权威与规则矩阵。
**Status:** 实现完成 + 评审回归闭环（1048 unit + lint + assemble 全 PASS）；**实机方向验收 PENDING**（ADB 离线）。

**评审修复（r17.md 评审意见逐条落地）：** 见文末 §8。

---

## 1. Ground truth：锚点屏幕位移实测（不再猜符号）

从 ContinuousDocumentViewport 真实渲染变换推导并实测：

```
placeWithLayer(0, pageTop) + translationY = pageTop*(scale-1) - scrollY
⇒ 页顶 screenY = pageTop*scale - scrollY
⇒ 同一页面跨帧：anchorDeltaY = Δ(pageTop*scale) - ΔscrollY
```

- **同一主页面**跨帧的 `contentAnchorScreenY` 差分 = PDF 内容在屏幕上的真实像素位移；
  主页面切换帧重新基线（delta=0）。
- `ContinuousViewportSnapshot` 新增 `contentAnchorScreenY` / `contentAnchorDeltaY`
  （ContinuousDocumentViewport 内 `currentGeometry.pageTop(primaryPage)*scale - scrollY` 实测）。
- **唯一方向权威**：`mapScrollToContentMotion(anchorDeltaY)`：
  `anchorDeltaY < 0 → CONTENT_MOVING_UP`（同一锚点下一帧 screenY 更小，内容向屏幕顶部移动）；
  `anchorDeltaY > 0 → CONTENT_MOVING_DOWN`；`== 0 → null`（静止/基线帧，不驱动状态机）。
- UI 层 chrome 路径**不再读取 raw scrollDeltaY 正负**（grep 验证为空）；位移输入也改用
  `|anchorDeltaY|/density`（顺带修正 zoom 下 document px ≠ 屏幕 px 的问题）。
- 若实机诊断证明映射反向：**只反转 mapScrollToContentMotion 一处的 <0/>0 分支**，状态机与
  规则矩阵不得重写（防二次反转）。

## 2. 临时方向诊断（CHROME_DIR）

真实拖动中（节流 80ms）打印：
```
CHROME_DIR gestureId=3 rawDelta=2.4 consumedDelta=2.4 oldAnchorY=1234.0 newAnchorY=1231.6 anchorDeltaY=-2.4 mapped=CONTENT_MOVING_UP
```
实机慢拖两次（内容明显向上 / 明显向下），日志必须与肉眼一致：内容向上 ⇒ anchorDeltaY<0；
内容向下 ⇒ anchorDeltaY>0。不一致才需要反转 adapter。

## 3. 状态机规则矩阵（单方向规则，删除双方向共用）

`transitionFor(level, dir)` 显式表格，每条规则只属于一个方向：

| 规则 | 方向（内容运动） | 阈值 | 目标 |
|---|---|---|---|
| FULL × UP | 内容向上（保圆） | 36 | BUTTONS_ONLY（只收胶囊） |
| HIDDEN × UP | 内容向上（恢复） | 32 | BUTTONS_ONLY（先两圆） |
| BUTTONS_ONLY × UP（cameFrom=HIDDEN） | 内容向上（恢复续） | +28 | FULL（后胶囊） |
| FULL × DOWN | 内容向下（收纳第一阶段） | 36 | BUTTONS_ONLY |
| BUTTONS_ONLY × DOWN | 内容向下（收纳第二阶段） | +56 | HIDDEN（两圆后收） |
| 其余组合 | — | — | 停留 |

- 硬不变式不变：内容向上时 BUTTONS_ONLY 永不自动 → HIDDEN；恢复永远先两圆后胶囊。
- 阈值 36/56/32/28、dead zone 8、方向确认 12（cooldown 内 24）、cooldown 350ms 全部未动。

## 4. CHROME_STATE 日志升级

每次迁移打印 `anchorDeltaY`（实测）与 `dir`（映射结果）并列，杜绝「dir 命名反了日志自证正确」：
`userGestureId / dir / rawDelta / anchorDeltaY / effectiveDistanceDp / levelBefore / levelAfter / reason`。

## 5. 测试

- `PdfChromeScrollStateMachineTest` 现 26 用例：A 内容向上（FULL→BUTTONS_ONLY、永不→HIDDEN）、
  B 反方向两阶段进 HIDDEN、C HIDDEN 恢复严格 BUTTONS_ONLY→FULL、D 12dp 方向确认（cooldown 内 24）、
  E fling 不可绕过矩阵、F menu reset、G endGesture/reset 保留 level（programmatic settling 由
  UI 层 phase==IDLE 不喂状态机保证，源码断言覆盖）。
- 新增 adapter 测试：`mapScrollToContentMotion` 的符号期望**根据真实 placement 变换推导**
  （`screenY = pageTop*scale - scrollY ⇒ ΔscrollY>0 ⇒ anchorDeltaY<0 ⇒ UP`），测试不假设符号。
- `R6/R7` 源码接线断言更新（mapScrollToContentMotion / contentAnchorDeltaY / anchorDeltaY）。
- 全量 `:app:testDebugUnitTest`（1004 tests）+ `:app:lintDebug` PASS。

## 6. 构建与交付

- QA：`.external_outbox/markleaf-qa-latest.apk` SHA-256 `204d0d23193ffb87d0e9713edc09f0ffac24da9e87bcc2ab6d0f9222644bc5a8`
- Release：`.external_outbox/markleaf-release-latest.apk` SHA-256 `c22a75790bfcdfddd59464ed7c8109f396e1fa4df7f759bc5349f82daba06914`
  → 已推送 `/storage/emulated/0/Download/markleaf-release-latest.apk`（校验 OK）。

## 7. 实机验收（PENDING — ADB 离线）

1. 慢拖让 PDF 明显向上移动：录屏 + `adb logcat -s CHROME_DIR` 对齐，日志必须显示 `anchorDeltaY<0`、
   `mapped=CONTENT_MOVING_UP`；Chrome 行为 = 只收胶囊（两圆保留，绝无 HIDDEN）。
2. 慢拖让 PDF 明显向下移动：日志必须显示 `anchorDeltaY>0`、`mapped=CONTENT_MOVING_DOWN`；
   Chrome 行为 = 两阶段收纳（36dp 收胶囊 → +56dp 收两圆）。
3. 若 1/2 的 anchorDeltaY 与肉眼相反 → 反转 `mapScrollToContentMotion` 的 <0/>0 两个分支后重建，
   状态机不改。
4. 从 HIDDEN 反向手势恢复：先两圆（32dp）、再胶囊（+28dp），`CHROME_STATE` reason 序列为
   BUTTONS_SHOW_THRESHOLD → CAPSULE_SHOW_THRESHOLD。
5. 快速 fling 不绕过方向矩阵；菜单开/关后无历史累计误触发。

---

## 8. 评审修复（r17.md 意见逐条闭环）

| 评审项 | 修复 |
|---|---|
| P0 状态空间矛盾（BUTTONS_ONLY × UP 歧义） | 显式 `cameFrom` + 新增 **restoreLatch**：本手势内从 HIDDEN 恢复到 FULL 后置位，同一连续手势 FULL×UP 不再收胶囊（杜绝 HIDDEN→…→FULL→BUTTONS_ONLY 循环）；endGesture / 反向确认 / session reset 清除。回归测试：FULL 持续 UP 200dp 只到 BUTTONS_ONLY；HIDDEN 持续 UP 200dp 恢复后稳定。 |
| P0 zoom 污染 anchorDeltaY | 锚点 **epoch reset**：scale 变化（>0.001）、viewport 尺寸变化、主页面切换 → 该帧仅重建基线、anchorDeltaY=0（SKIPPED），zoom/layout/programmatic 位移永不进入状态机；zoom 手势期间 snapshot 本就被抑制（gestureActive && scale>1.001）。 |
| P1 跨页基线 ≠ 输入 0 | UI 层对 dir==null 不调用状态机（无副作用），累计/确认器/阈值原样保留；测试：30dp DOWN → 基线帧跳过 → 10dp DOWN 即达第一级。 |
| P1 单帧 overshoot 丢失 | 迁移循环改为 **remainder 保留**（`phaseAccum -= threshold` 而非清零），单帧 100dp 可连续消费 FULL→BUTTONS_ONLY→HIDDEN；测试：单帧 100dp DOWN→HIDDEN、70dp UP 恢复→FULL、90dp 后 remainder 54 续累计。 |
| P1 subpixel 噪声 / 帧率依赖 | deadZoneDp 8f→**0.5f**（方向噪声 epsilon：NO_MOTION_SAMPLE，不建方向/不参与确认/不清累计）；真实小位移（120Hz 慢拖 1dp/帧）全部累计，60/90/120Hz 同轨迹 transition path 一致（测试：60dp/100dp 轨迹 × 粗/中/细帧粒度终态相同）。 |
| P1 确认不依赖帧数 | 确认机制本就是 dp 距离累计（12/24dp），非帧数；同轨迹多粒度测试覆盖。 |
| P2 adapter 同源 oracle | 新增硬编码独立 oracle：oldAnchorY=500→480 ⇒ −20 ⇒ UP；500→525 ⇒ +25 ⇒ DOWN。 |
| P2 CHROME_DIR 进 Release | CHROME_DIR 诊断 gating 到 **debug builds**（FLAG_DEBUGGABLE），Release 不输出；CHROME_STATE（正式验收日志）保留。 |
| 6 组回归 | 全部落地为单测（见 §5 测试清单 + 本节各行）。 |

状态机测试现 34 用例；全量 unit 1048 通过。
