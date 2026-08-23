# R19 PDF Reader 顶部 Chrome 几何与密度收口（More 原位展开 + 胶囊原位跳页 + 密度压缩）

**Date:** 2026-08-04（r19a 修复：空菜单 bug + 胶囊收纳）
**Scope:** ①More 菜单与三点圆共用完全相同的 TopEnd anchor（禁止二级 y offset）；
②More 菜单密度压缩（row 48/option 44/icon 22/pad 12/测量式高度）；③左右翻页 FitPage/Center 逻辑
零改动 + 设备验证协议；④位置胶囊 → 自身 expanded state（同一 surface 向下增长 + IME 自适应）；
⑤不引入新视觉模式/动画/容器色。
**Status:** 实现完成（unit 1004 + lint + assemble Qa/Release PASS）；**实机验收 PENDING**（ADB 离线）。
未 commit（与 R18 一致，待实机）。

---

## 1. More 菜单：与三点圆共用同一 TopEnd anchor（r19 一）

- **删除全部二级 y offset**：`MenuTopGapBelowRow(52dp)`、`cardTopOffset` Animatable（0→52dp 下滑）、
  `anchorTopInsetDp` 参数全部移除。`ReaderMoreSurface` 不再有任何 `offset(y=...)`。
- **单一布局节点**：collapsed 44dp 三点圆与 expanded 卡片是同一个 TopEnd 对齐节点；外层
  AnimatedVisibility 的 `padding(top = inset+8, end = 12)` 是唯一的定位来源 ⇒
  **expanded top == collapsed top == inset+8（与返回圆、胶囊同一基线）；right 恒 == 屏宽-12**。
  （顺带修正了此前 More 圆内部 `offset(y=inset+8)` 造成的双 inset 错位：更多圆 top 曾比返回圆低
  inset+8；现在三者 top 完全一致。）
- **展开动画 = 纯 size reveal**：`cardWidth` 44→224（向左增长）、`cardHeight` 44→measured（向下增长），
  TopEnd 固定 → right/top 全程恒定，无任何先下移再展开/收起后归位。glass alpha 0.82→0.86 同材质增密。
- **证据**：`MORE_SURFACE` logcat 流（onGloballyPositioned + boundsInWindow），collapsed /
  expanded_level1 / expanded_level2 / closing 各态打印 `top / right / w / h`，settled 再打一条；
  收口后各态 top/right 必须逐字节相等。

## 2. More 菜单密度压缩（r19 二）

| 项 | R18 | R19 |
|---|---|---|
| MenuWidth | 232dp | **224dp** |
| 一级 row 高 | 50dp | **48dp**（46–50 建议带） |
| 二级 option 高 | 50dp | **44dp**（42–46 建议带） |
| icon | 22dp | 22dp（20–22 带） |
| 文字 | 14sp | 14sp（14–15 带） |
| row 左右 padding | 14dp | **12dp** |
| 卡片垂直 pad | 6dp | **4dp** |
| divider 上下 pad | 2dp | **1dp** |
| check 图标 | 18dp | **16dp**（小 check，无 selected 背景） |

- **高度改为真实测量**：菜单内容 Column 用 `height(IntrinsicSize.Min)` 以未受卡片钳制的自然高度
  `onSizeChanged` 上报，卡片 Animatable 精确动画到测量值 ⇒ 二级展开只增加实际选项高度
  （阅读方式/阅读界面 = 44×2dp；设置教材页码 = 输入区真实内容高度，无 156dp 固定余量），
  不再留下上下空隙。
- 阅读方式「上下连续 / 左右翻页」、阅读界面「普通 / 全屏沉浸」保持同 baseline、当前项 16dp primary
  check、row-local 0.08 alpha 按压高亮（无 ripple、无大面积 selected background）。
- 设置教材页码 body 同步压缩（spacer 6→4、按钮 padding 16→14、按钮行距 6→4）。

## 3. 左右翻页 FitPage/Center 保持不动（r19 三）

- `HorizontalZoomPanPhysics.fitPage`（contain 拟合 + 短页垂直居中 `top = margin + (availableH-fittedH)/2`）
  **零改动**；`ThreePageStrip` / `PdfGestureTransformController` / mode 切换 scale·center 保持链路未动。
- 普通/全屏沉浸仅切换系统栏（WindowInsetsControllerCompat），PDF 边到边 + statusBarsIgnoringVisibility
  ⇒ viewport 尺寸不变，scale/center 不变。
- **设备验证协议（PENDING）**：左右翻页模式连续翻 ≥20 页，覆盖正文很少页 / 图片页 / 横向页面 /
  不同 CropBox/MediaBox 尺寸页；所有 renderedPage 小于 viewport 的轴必须居中（不允许贴顶）；
  普通 ↔ 全屏沉浸切换后当前页 scale 与 center 不变。录屏 + `logcat -s PDF_SYSBARS MORE_SURFACE`。

## 4. 位置胶囊 → 自身 expanded state（r19 四）

- **单 surface**：`PdfInfoCapsule` 同时承担 collapsed 位置胶囊与 expanded 跳页输入；
  删除独立 `JumpCardBody`（原“胶囊下方附加卡片”结构）。同一 Column：
  - 顶部恒为「28 / 218 · 当前章节」header（44dp，两态一致）；
  - expanded 时 body 以 `expandVertically(expandFrom=Top)` 从 header 下缘在**同一 surface 内**向下增长
    （跳转到页面 / 请输入 1–218 / 数字输入 / 取消 / 跳转）；
  - 同一 `readerChromeGlassSpec(menuArea=expanded)`、同一 22dp corner、同一 border/shadow（glass 仅随面积增密 0.82→0.86）。
- **几何不变式**：surface 宽度固定 = `min(200dp, capsuleMax)`（TopCenter ⇒ top/centerX 与展开无关）；
  展开/收起只改变高度，top、left、right、centerX 全部不变；无全屏 dim（保留 outside-tap catcher）。
- **IME 契约**：PdfViewerScreen 驻留期间 window 切 `SOFT_INPUT_ADJUST_NOTHING`（退出恢复 manifest 默认
  adjustResize）⇒ 键盘出现时 Reader Chrome 不被推走、PDF viewport 尺寸不变；expanded body 以
  `WindowInsets.ime` 剩余空间 `heightIn(max)` + `verticalScroll` 自适应（横屏键盘场景防御）。
- **证据**：`CAPSULE_SURFACE` logcat 流（top / centerX / w，变化才打）——collapsed 与 expanded 必须同 top/centerX/w。

## 5. 未引入新视觉模式

- 全部改动限于几何 anchor、密度常量、同一 surface 的增长行为；无新容器色、无新动画类型
  （size/fade/clip reveal 均为此前已批准的形态）、无新 Material 组件。

## 6. r19a 修复（实机反馈）

- **空菜单 bug 根因**：`height(IntrinsicSize.Min)` 在 Compose 1.7.6 实现中
  （`IntrinsicSizeModifier.measure`）对内容执行 `constrain(incoming, contentConstraints)` ——
  intrinsic 结果被**钳制在父级传入约束内**。菜单卡片初始 44dp 高 ⇒ 内容测得 44dp ⇒ 卡片高度
  永不增长 ⇒ 展开后只有 224×44 空卡，全部菜单行被裁剪。
- **修复**：新增 `reportNaturalHeight` layout modifier —— 在 measure 内以**无界 intrinsic 查询**
  （`measurable.minIntrinsicHeight(maxWidth)`）取得真实自然高度并上报驱动卡片动画，随后仍按
  钳制约束正常布局（裁剪行为与 R18 视觉一致）。反馈环被打破：上报值不随卡片当前高度变化。
- **确定性保障**：菜单全部行高固定（48/44）；设置教材页码 body 改为固定 134dp
  （16 hint + 16 状态/错误 + 4 + 56 输入框 + 2 + 40 按钮，单行槽位），不再查询
  TextField intrinsic；`supportingText` 移除，错误信息改占状态行（红色），提示串缩短为单行
  「当前 PDF 第 N 页」。
- **胶囊收纳**：More 菜单打开（含 morph 收起期间）时胶囊层整体隐藏/收纳
  （`capsuleVisible && !menuOpen`，overlay 行为、不动 chromeLevel），菜单关闭后淡回；
  删除水平避让收窄逻辑（120dp 下限机制随之移除）。
- 几何测试更新：删除避让下限用例，新增「菜单打开 ⇒ 胶囊整体收纳、关闭 ⇒ 恢复固定宽」用例。

## 7. 自动化回归

- `ReaderChromeGeometryTest` 重写为 R19 不变式（4 用例）：
  ①expanded top==circle top、right==circle right、只向左/下增长；②level-2 锚点不动、只增 44×2；
  ③任何状态 top 恒定（横屏）；④胶囊宽度固定 min(200,cap)、TopCenter 不移动、避让 120dp 硬下限。
- 全量 `:app:testDebugUnitTest`（1004 tests PASS）+ `:app:lintDebug` PASS。
- `PdfChromeScrollStateMachineTest`（28 用例）等既有套件全绿——状态机/滚动规则零改动。

## 8. 构建与交付

- QA：`.external_outbox/markleaf-qa-latest.apk` SHA-256 `c0537c36673433435c61a1c7e921243e11f774a56e2808dcd8fba0dc60a50b3b`
- Release：`.external_outbox/markleaf-release-latest.apk`（10.7MB minify）SHA-256
  `7115fd461b2a7b8a1154b25247fbde3c76c6c16764b6c8ba9e204856fa3b1f02`
  → 已复制 `/storage/emulated/0/Download/markleaf-release-latest.apk`（SHA 校验一致）。
- ADB 离线：未做任何 device smoke（按 AGENTS.md 协议不标记 PASS）。

## 9. 实机验收（PENDING — 需设备）

①三点 collapsed ↔ expanded 叠图（右上角=原三点位置）+ `logcat -s MORE_SURFACE` top/right 恒定证明；
②阅读方式一级/展开态截图（显著更紧凑）；③阅读界面展开态截图；
④胶囊 collapsed → 跳转输入 expanded → collapsed 录屏（同一 TopCenter surface 原地向下展开，
top/centerX 不变，无 dim，键盘不推走 Chrome/不动 viewport）+ `logcat -s CAPSULE_SURFACE`；
⑤左右翻页连续多页居中录屏（≥20 页，含稀疏页/图片页/横向页/异 CropBox 页），普通↔沉浸切换 scale/center 不变。
核心不变式：More 菜单从三点圆原位展开（位置不跳）；Location capsule 从自身原位展开（位置不跳）；
所有展开都是 surface 在固定 anchor 上增长。
