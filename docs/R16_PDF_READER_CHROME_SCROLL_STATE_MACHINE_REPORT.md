# R16 PDF Reader Chrome 滚动驱动状态机重规范（内容方向语义 + 非对称阈值）

**Date:** 2026-08-04
**Scope:** 重写 Reader Chrome 滚动驱动显隐：方向语义改为「PDF 内容实际运动方向」；
两圆保留优先级硬不变式；取消胶囊独立 timer；层级非对称阈值；fling/单击/菜单冻结规则；
可验证状态机日志。FULL / BUTTONS_ONLY / HIDDEN 三级 overlay 架构不变。
**Status:** 实现完成（状态机单测 + 全量 unit + lint + assemble 全 PASS）；**设备验收 PENDING**（ADB 离线）。未 commit。

---

## 1. 方向语义修正（根因）

旧实现用「delta 正负 + 上下滚」语义：内容向上（阅读前进）持续滚动时会把 BUTTONS_ONLY 自动收成
HIDDEN（两圆被收掉）——实机缺陷。

新架构：**滚动适配层把原始 delta 映射成 `CONTENT_MOVING_UP / CONTENT_MOVING_DOWN`
（PDF 内容在屏幕上的实际运动方向）**，再交给状态机；状态机不接触 raw delta 正负。
本仓库语义：`scrollDeltaY > 0` = 内容向屏幕上方移动（FORWARD，阅读前进）。

## 2. 状态机规则（PdfChromeScrollStateMachine，纯逻辑可单测）

| 状态 | 方向 | 阶段阈值（dp） | 目标 |
|---|---|---|---|
| FULL | UP / DOWN | 36（收信息层） | BUTTONS_ONLY（胶囊先收） |
| BUTTONS_ONLY | DOWN | +56（追加） | HIDDEN（两圆后收，唯一允许收两圆的方向） |
| BUTTONS_ONLY | UP | 停留（cameFrom==FULL） | —（**硬不变式：两圆永远保留**） |
| BUTTONS_ONLY | UP | +28（cameFrom==HIDDEN 恢复序列） | FULL（后胶囊） |
| HIDDEN | UP | 32 | BUTTONS_ONLY（先两圆） |
| HIDDEN | DOWN | 停留 | — |

- **硬不变式**：CONTENT_MOVING_UP 无论累计多少位移，绝不允许 BUTTONS_ONLY → HIDDEN；
  恢复永远「先两圆、后胶囊」，收纳永远「先胶囊、后两圆」（视觉 stagger 由胶囊 enter 延迟 100ms /
  两圆 exit 延迟 100ms 的动画编码，fling 一次迁移也自动保持顺序）。
- **追加阈值**：进入下一级后重新累计（FULL→BUTTONS_ONLY 用 36；继续 DOWN 再 56 才 HIDDEN；
  HIDDEN→BUTTONS_ONLY 用 32；继续 UP 再 28 才 FULL）。
- **dead zone 8dp / 方向确认 12dp（cooldown 内加倍 24dp）**，基于内容方向；状态迁移后 350ms
  cooldown 只加严反向确认，**同方向达到下一级阈值立即继续迁移，不因 cooldown 滞留**。
- **fling 服从同一方向约束**：UP 快速 fling 不能收两圆（BUTTONS_ONLY 下直接拒绝）；
  DOWN fling 可 FULL→HIDDEN / BUTTONS_ONLY→HIDDEN；UP fling HIDDEN→FULL（先两圆后胶囊）。
- **中央单击**（显式控制，不走逐级）：FULL→HIDDEN；HIDDEN/BUTTONS_ONLY→FULL，单击后清全部残留。
- **菜单冻结**：pdfMenuExpanded/Closing 期间滚动样本不喂状态机；关闭时
  `resetForNewSession()` 清零累计/方向确认/fling residue，从当前 chromeLevel 开始新 session
  （历史 40–50dp 位移不会在菜单关闭后引发误收纳）。

## 3. 胶囊与三级显隐

- 删除胶囊独立 2s/2.5s timer：**胶囊存在只由 `chromeLevel == FULL` 决定**；打开文档默认 FULL +
  初始化锁定（2.5s），初始化/位置恢复/layout settling/程序化滚动（phase IDLE 不喂状态机）均不改变。
- 胶囊 = 阅读位置仪表：主信息「106 / 982 · 教材页 75」（pageLabel · printLabel），
  副信息仅当前章节路径（去掉文档名）。
- 两圆 exit 延迟 100ms、胶囊 enter 延迟 100ms：收纳「先胶囊后两圆」、恢复「先两圆后胶囊」的
  视觉顺序由动画编码，fling 整级迁移也不会三者同时消失/出现。

## 4. 日志与验收证据

`CHROME_STATE` tag 每次迁移打印：
`userGestureId / dir(UP|DOWN) / rawDelta / effectiveDistanceDp / levelBefore / levelAfter / reason`
（reason ∈ CAPSULE_HIDE/BUTTONS_HIDE/BUTTONS_SHOW/CAPSULE_SHOW 阈值迁移、
FLING_HIDE_CAPSULE/FLING_HIDE_BUTTONS/FLING_HIDE_ALL/FLING_RESTORE_BUTTONS/FLING_RESTORE_FULL、
TAP_FULL/TAP_HIDDEN）。实机 `adb logcat -s CHROME_STATE` 可核对方向未接反。

## 5. 修改文件

| 文件 | 变更 |
|---|---|
| `app/src/main/java/com/markleaf/app/pdf/PdfChromeScrollStateMachine.kt` | **新增**：方向枚举、三级枚举、PdfChromeScrollParams（阈值集中）、PdfChromeScrollStateMachine（纯逻辑） |
| `app/src/test/java/com/markleaf/app/pdf/PdfChromeScrollStateMachineTest.kt` | **新增**：18 用例覆盖验收①–⑧规则矩阵（硬不变式/追加阈值/恢复序列/cooldown/fling/单击/reset） |
| `app/src/main/java/com/markleaf/app/ui/pdf/PdfViewerScreen.kt` | 适配层（raw delta→CONTENT 方向）、状态机接线、删除旧逐级状态机/胶囊 timer、菜单冻结+reset、单击 toggle、胶囊副行仅章节路径、stagger 动画 |
| `app/src/test/java/com/markleaf/app/pdf/R6PdfInteractionTest.kt` | 旧「hide/showChromeOneLevel」源码断言更新为新状态机接线 |

## 6. 构建验证（本机）

- `:app:testDebugUnitTest`（含 18 个新状态机用例）全 PASS；`:app:lintDebug` PASS；
  `:app:assembleDebug/Qa/Release` PASS。
- QA：`.external_outbox/markleaf-qa-latest.apk` SHA-256 `323064f5eb8c55e1a4a5b00ece448d06de895f18811c1fe07fc319570cf9bcfb`
- Release：`.external_outbox/markleaf-release-latest.apk` SHA-256 `081ef55e6c675d242c2141b37a675937382d8a243c494058bd4758ad3daf8b42`
  → 已推送 `/storage/emulated/0/Download/markleaf-release-latest.apk`（校验 OK）。

## 7. 设备验收（PENDING — ADB 离线）

① 打开默认 FULL，静止 5s 仍 FULL；② 内容向上移动第一级只隐藏胶囊（两圆仍在）；
③ 继续大量向上移动两圆仍在、绝不进 HIDDEN；④ 内容向下移动先 BUTTONS_ONLY 再第二级两圆才收进 HIDDEN；
⑤ HIDDEN 下内容向上：先两圆、再胶囊；⑥ 小幅抖动不跳变；⑦ 快速 fling 同方向约束与先后顺序；
⑧ 菜单开/关后无历史累计误触发。全程 `adb logcat -s CHROME_STATE` 核对 dir 与 level 序列。
