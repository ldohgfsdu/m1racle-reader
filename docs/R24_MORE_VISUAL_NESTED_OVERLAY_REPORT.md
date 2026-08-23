# R24 More Surface Visual + Nested Overlay Replica

**Date:** 2026-08-05
**Scope:** 按 r24 指令（基于 17379.mp4 vs 17276.mp4 逐帧对比）只改两个核心问题：
①三点圆→Main Card 的视觉 morph（时长/easing/圆角插值/width 领先/内容 reveal/实体度/阴影）；
②二级展开从「父卡变高 + siblings 位移」的 inline accordion 改为参考交互的
**CHILD OVERLAY CARD**（parent 保持原 bounds，child 从被点 row 向下生长、覆盖而非推挤）。
冻结：滚动状态机、两圆滑动显隐、tap→胶囊、BottomSheet 业务、目录/缩略图/导出业务、
JumpPageForm 业务逻辑。
**Status:** 实现完成（unit+UI 1033 PASS + lintDebug + assemble Qa/Release PASS）。
Release `9dbf6580…` 已复制 `/storage/emulated/0/Download/`（SHA 一致）。
**实机录屏 PENDING**（adb 离线）。未 commit。

---

## 1. 问题一：三点圆 → Main Card morph choreography

逐帧事实（17379）：morph 仅 ~150ms、圆迅速膨胀成空白圆角方块、文字整组 ghost fadeIn、
ripple 残留、0.82 过透。本轮重做 motion choreography：

| 项 | R23 | R24 |
|---|---|---|
| 主 morph 时长 | 260ms FastOutSlowIn | **260ms emphasized decelerate**（CubicBezierEasing(0.2,0,0,1)，240–270 带，无 overshoot/spring） |
| width / height | 同步 260ms | **width 260ms 立即；height 300ms delay 60ms**（横向先形成方向感，纵向随后持续向下——无空白圆角方块阶段） |
| 圆角 | 固定 22dp | **22（圆半径）→ 26（24–28 带）连续插值**，与 morph 同步（无药丸/肥皂块） |
| ⋮ icon | 90ms 淡出 | 90ms（前 25–35%）淡出，关闭最后回来 |
| 内容 reveal | 整组 delay 90ms ghost fade | **delay 80ms（~30% bounds）开始 + 120ms 快达**；主导是容器底边 clip reveal（第一行先进入，后续随纵向生长露出），非整组透明叠加 |
| ripple | 无 Material ripple | 保持无 ripple（indication=null 中性 bounded press 反馈，~80ms 结束于 morph 开始前） |
| 实体度 | More 0.82 | **More parent 0.95**（背景只能弱感知，人物轮廓不得压到菜单文字） |
| 分离感 | elevation 4/spot 0.12 | **elevation 6 / spot 0.16 / ambient 0.10**（极轻 separation，禁重阴影） |
| 视觉规格 | row 46 / pad 10 / icon 20 / gap 10 | **row 52（50–54 带）/ pad 16 / icon 22（22–24 带）/ gap 12（12–14 带）/ chevron 20**，一级 14sp Medium、二级 13sp Normal |

## 2. 问题二：inline accordion → CHILD OVERLAY CARD（17276 参考模型）

**删除**：`MainMenuContent` 中两个 accordion `AnimatedVisibility(expandVertically/shrinkVertically)`
及全部 inline 插入逻辑（siblings displacement 方案）。父卡高度在 Main 与 Expanded 完全一致
（6 条固定 row：3×2 pad + 52×6 + divider ≈ 321dp）。

**新模型**（同一 `ReaderMoreMorphSurface` 顶层 Box，两层层级）：

```
z0 — parent card（固定 measured bounds，永不 reflow）
z1 — NESTED CHILD overlay card（Expanded 时）：
     top 锁定在被点 row 的 top（MenuVerticalPad + index × 52dp，固定 row-height 数学）
     left/right 与 parent 完全对齐（同 216dp 宽、同 TopEnd 锚点）
     高度动画：row 高 52dp → header + children（52 + n×48dp）
     sibling 定位 ⇒ 不被 parent clip，允许超出 parent 底边
```

- **expand**：header row 先进入 active（chevron 0→90 旋转）；child bottom edge 220ms
  （180–230 带）从 source row bottom 连续下移，top 锁死；children 由 child 的底边
  clip reveal 逐渐露出 + 轻 opacity（120ms delay 40）——不是先放 children 再 resize。
- **collapse 完全 reverse**：children 被底边收回 → child 缩回 source row → child 移除；
  parent 下方 rows 坐标全程不动，从始至终无父布局 reflow。
- **覆盖语义**：child 经过的 parent rows 被物理遮住（真实输入命中 child 表面，sink
  吞点击——UI 测试用真实 performClick 验证了遮挡行为）；child 上方未覆盖的 parent
  rows 原位露出且可点（如 ReadingInterface child 打开时上方"阅读方式"可见可点）。
- **一次一个 child**：状态机 `Expanded(other) → toggle → Main`（先收回）；UI 层串行
  handover（`state=Main` → delay 260ms → `Expanded(next)`，seq token 防竞态）。
  Back/child header 点击 → Child→Main。
- **child 材质**：0.97（压住下层 parent 文本，两层文字不透叠）+ 极轻 shadow。
- ReadingMode child = header「阅读方式」+ 上下连续✓ + 左右翻页；ReadingInterface
  child = header「阅读界面」+ 普通 + 全屏沉浸✓。

## 3. Main → TextbookPageForm（第二优先级最小修复）

ghost overlap 修复（17379 6.7–6.9s 两套文字短暂同屏）：outgoing 菜单内容 exit 缩短为
**100ms**，form 内容 enter delay 增至 **160ms**（FormEnterDelayMs > MenuExitMs）——旧文字
先退干净，新文字才出现；不改 Form 状态机、不扩大 scope。

## 4. 删除的旧实现

- inline accordion 专用布局（`expandVertically/shrinkVertically`、accordion
  AnimatedVisibility、siblings displacement）——整段删除，不保留 legacy。
- 旧 `switchPdfMenuSubmenu` 直切逻辑 → 串行 handover（先收回再开）。
- 旧视觉常量（46/42/10/20/10、corner 22 固定、0.82/0.88）→ 新规格。

## 5. 测试结果

- unit **1033 PASS**：状态机测试更新（Expanded→other→Main 先收回、串行语义）；
  `ReaderChromeGeometryTest` 重写（corner 22→26 插值、父卡高度恒定 321dp、child
  source Y = pad + index×52、密度带 50–54/16/22–24/12–14）；`ReaderMoreMorphSurfaceUiTest`
  更新为真实交互序列（child header 收起、parent row 打开、遮挡语义、header 双节点断言）。
- lintDebug PASS；assemble Qa/Release PASS。
- Release `9dbf6580…` 已交付 Download，SHA 一致。

## 6. 实机录屏（PENDING — adb 离线）

需对照 17276.mp4 复录：圆→Main（0/25/50/75/100% 关键帧验证 width 领先、无空白方块、
无 circle fadeOut+card fadeIn、icon 前 35% 淡出）；Main→ReadingMode/ReadingInterface
child（parent bounds 不动、child 从被点 row 生长、siblings 无位移、collapse reverse、
被覆盖 rows 遮挡、一次一个 child）；Main→Form（无 ghost overlap）；Main→BottomSheet
handoff。逐帧门禁（source 残留/第二张 card/bounds jump/corner snap/白闪/content 抢跑/
reverse 精确回源/IME 跳卡/快速点击）待设备。

## 7. 明确回答

- A. 三点菜单是不是独立 Popover？—— **不是**（圆本身 morph 成卡，width 领先 + corner
  插值 + clip reveal，单一 container）。
- B. 阅读方式/阅读界面是不是另一张二级卡片？—— **不是**（是同一 More overlay 的
  CHILD OVERLAY CARD：parent 保持原 bounds，child 从被点 row 覆盖式生长；不是新窗口，
  不是 parent reflow）。
- C. 设置教材页码是不是关闭主菜单后凭空出现的新卡片？—— **不是**（同一容器 morph 成
  form，outgoing 内容先退干净）。
- D. 目录/缩略图/导出是不是继续使用 BottomSheet？—— **是**。
