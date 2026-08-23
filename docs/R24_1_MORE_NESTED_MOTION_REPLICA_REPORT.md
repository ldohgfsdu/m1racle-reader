# R24.1 精确复刻 ChatGPT More/Nested Card Motion

**Date:** 2026-08-05
**Scope:** 按 r24.1（基于 17381–17388 vs 17389/17390 逐帧复核）纠正上一轮 motion 假设，
只动 Parent More 视觉质量、Nested Child 实体遮挡、source-row→target-bounds transform。
冻结：教材印刷页/目录/缩略图/跳页/导出、PdfChromeScrollStateMachine、CONTENT_MOVING_UP/DOWN、
两圆滑动显隐、page tap→pill、BottomSheet 业务。
**Status:** 实现完成（unit+UI 1033 PASS + lintDebug + assemble Qa/Release PASS）。
Release `0a2fb3b6…` 已复制 `/storage/emulated/0/Download/`（SHA 一致）。
**实机录屏 PENDING**（adb 离线；提交录屏与关键帧后等待视觉复核，不自行宣布 PASS）。

---

## 1. 参考事实一落实：三点圆 → Main transform（快、clip reveal）

| 项 | R24 | R24.1 |
|---|---|---|
| 主 transform | 260ms + 内容 delay 80ms | **190ms**（160–200ms 参考带）；height 210ms + 25ms delay（width 保持轻微领先） |
| easing | emphasized decelerate | 不变（CubicBezierEasing(0.2,0,0,1)，无 overshoot/spring） |
| content | delay 后 120ms fade | **预布局 + 首帧即 alpha=1**，由容器 animated clip 自然揭示——绝无空白卡 + 整组 ghost fade；仅关闭保留 100ms 先退 |
| ⋮ icon | 90ms | **60ms**（前 ~30% 淡出） |
| corner | 22→26 插值 | 不变（圆半径→卡圆角，柔和非药丸） |
| ripple | 无 Material ripple | 不变（indication=null 中性 bounded press，独立节点，morph 开始即结束——绝不被容器放大） |

## 2. 参考事实二落实：Nested Child = source row → target bounds transform

- **sourceBounds = 被点一级 row 在 More overlay 坐标系中的真实 bounds**（top =
  vertical pad + index × row height；宽 = row 宽），**targetBounds = 内容需要**：
  child 的 width/height 都是 Animatable 连续插值（source→target），**不是硬编码
  parent 宽**——当前 ReadingMode/ReadingInterface 内容与 parent 基本同宽（216），
  未来更宽内容将保持 source top/right continuity 同 left+down 扩张。
- **child 实体遮挡**：ChildSurfaceAlpha **0.97→0.99**（0.98–1.00 带）——覆盖区域内
  parent text/icon 不得肉眼可见；Parent More **0.95→0.97**（人物轮廓不穿 label）；
  Form 0.97 同族；顶部 back/pill chrome 0.80 保持更透明（分层不统一）。
- **children 预布局 + clip reveal**：children 按 target 坐标提前布局在 child 内
  （非 AnimatedVisibility 主导），child clipToBounds 随 bottom 下移自然揭示；
  opacity 仅 **0.85→1 轻辅助**（展开）/ 1→0 跟随 reverse clip（收起）——无 0→1 大 fade。
- **source row→child 圆角连续过渡**：child corner 10dp（row）→ 26dp（card）动画。
- **nested 时长**：expand 180ms（160–200 参考带）、collapse 160ms。
- **parent 全程不动**：nested 动画中 parent bounds 与所有 row 坐标不变、无 reflow；
  覆盖到的 parent row 只是被 child Surface 物理遮住（真实输入命中 child——UI 测试
  用 performClick 验证遮挡），绝不删除/移动/fade 伪造。
- **一次一个 child**：状态机先收回 + UI 串行 handover（260ms + seq token）。
- **icon 颜色统一**：icon/chevron onSurfaceVariant（过浅）→ **onSurface 75%**，label
  onSurface 95–100%——同一中性色体系，不再"浅灰 icon + 纯黑 label"割裂。

## 3. 删除的废弃逻辑

- 内容滞后 choreography（ContentDelayMs/ContentFadeMs、菜单 AnimatedVisibility
  enter fade delay）——删除；内容改为首帧预置 + clip reveal。
- child 固定宽写死（`size(width = MoreMenuWidth)`）→ Animatable bounds 插值。
- child content 0→1 fade → 0.85→1 轻辅助。
- 旧时长常量（260/200/60/40/220/180）→ 新基线（190/170/25/20/180/160）。

## 4. 测试结果

- unit+UI **1033 PASS**（遮挡语义、child header 双节点、串行切换、几何契约不变——
  corner 22→26、父卡高恒定、child source Y、密度带）。
- lintDebug PASS；assemble Qa/Release PASS；Release `0a2fb3b6…` 交付 Download（SHA 一致）。

## 5. 实机录屏（PENDING — adb 离线）

按 r24.1 硬门禁需录：A 三点圆→Main→圆 连续开合 5 次；B Main→ReadingMode child→Main；
C Main→ReadingInterface child→Main；D ReadingMode→ReadingInterface 切换；
E 快速 nested 开合 10 次；每条导出 0/20/40/60/80/100% 帧。硬门禁 1–11
（体感 160–200ms、clip reveal 主导、source top/right continuity、PDF 人物不穿 label、
child 覆盖区无 ghost、source 精确来自 clicked row、target 由内容决定、nested 中 parent
bounds/row 坐标不变、无 bounds jump/corner snap、collapse 严格 reverse、无双 child/
白闪/ghosting/被 clip/ripple 放大）逐项待设备复核。

## 6. 明确回答

- 当前 nested 是否仍是 inline accordion？—— **否**（CHILD OVERLAY CARD，parent 零 reflow）。
- child 覆盖区域内是否还能看到 parent 文字鬼影？—— **否**（child 0.99 实体，覆盖区不可见）。
- child target width 是否被硬编码成 parent width？—— **否**（width 是 Animatable
  source→target 插值；当前内容恰好与 parent 基本同宽）。
- More/Nested 是否仍靠低 alpha 假装玻璃？—— **否**（More 0.97 / child 0.99 近实体）。
- 主 reveal 是否仍主要依赖整组 fadeIn？—— **否**（内容首帧预置，animated clip 揭示主导）。
