# R20 PDF Reader Chrome 视觉密度收口（popover 化 + 轻玻璃 + 复杂任务出卡）

**Date:** 2026-08-04
**Scope:** 在 R19 已确认正确的几何与交互结构上做**减法式视觉密度 polish**：
①More 菜单进一步收紧为精致 popover + 从三点圆 origin 展开；②统一 ReaderChromeGlassTokens
降重量（阴影更轻、描边更细、实色压迫感降低）；③胶囊跳页展开态改为紧凑扩展区；④「设置教材页码」
从 More 卡内 accordion 移出，改为紧凑 BottomSheet；⑤所有展开态 1.0x/1.15x/键盘场景无裁切。
**Status:** 实现完成（unit 1005 + lint + assemble Qa/Release PASS）；**实机验收 PENDING**（ADB 离线）。
未 commit（与 R18/R19 一致，待实机）。

**结构零改动声明**：Reader Chrome 状态机、固定 TopEnd 锚点、胶囊原地展开（单 surface 向下增长）、
More 菜单 TopEnd 锚点、inline 二级展开模型（accordion 插入父 row 下方）、ADJUST_NOTHING IME 契约
全部保持 R19 不变。

---

## 1. More 菜单：popover 化 + 从三点圆 origin 展开（r20 一）

### 密度（R19 → R20）

| 项 | R19 | R20 |
|---|---|---|
| MenuWidth | 224dp | **216dp**（210–225 带） |
| 一级 row 高 | 48dp | **46dp**（44–48 带） |
| 二级 option 高 | 44dp | **42dp**（40–44 带） |
| icon | 22dp | **20dp** |
| 主文字 | 14sp | 14sp |
| row 左右 padding | 12dp | **10dp** |
| 卡片垂直 pad | 4dp | **3dp** |
| divider 上下 pad | 1dp | 1dp |
| 图标间隔 | 12dp | **10dp**（与 OptionIndent=10+20+10 对齐，option 文字与一级图标严格对齐） |
| check | 16dp | 16dp |

### 展开 origin（视觉连续性）

- 保留固定 TopEnd anchor（top == inset+8、right == 屏宽-12，两态恒定，MORE_SURFACE log 流不变）。
- 新增 **transform origin pop**：卡片 `graphicsLayer { transformOrigin = TransformOrigin(1f, 0f) }`，
  展开时 `scale 0.88 → 1.0`（tween 200ms，与 size morph 同速）+ 既有 content alpha 0→1 +
  clip reveal（shape 裁剪天然提供）⇒ 菜单视觉上**从三点圆右上角自身长出**，右上角像素全程钉在原圆位置，
  禁止整块无方向闪现。仅 menu active 时应用 scale（collapsed 圆恒为 1.0）；关闭后 snap 回 0.88
  供下次展开复用（收起仍是 R19 的纯 size 折叠）。
- 玻璃 alpha 动画目标同步 0.86/0.82 → **0.84/0.80**（与 §2 新 tokens 一致，无第二种 recipe）。

### 防裁切

- 菜单内容 Column 增加 `verticalScroll`（防御性）：横屏/1.15x+ 字号下自然高度超出可用空间时，
  卡片被父约束钳制、内容可滚动触达底部 row（导出章节等），不再出现底部按钮被裁切。
  锚点与展开模型零改动；竖屏永不触发滚动。

## 2. ReaderGlass 统一材质降重量（r20 二）

`ReaderChromeGlassTokens`（同一 recipe，左右圆/胶囊/菜单全部不变）R19 → R20：

| 项 | R19 | R20 |
|---|---|---|
| containerAlpha（base / menuArea） | 0.82 / 0.86 | **0.80 / 0.84** |
| borderWidth | 1dp | **0.5dp**（hairline） |
| borderAlpha | 0.65 | **0.55** |
| elevation | 6dp | **4dp** |
| spotShadowAlpha | 0.20 | **0.12** |
| ambientShadowAlpha | 0.16 | **0.08** |

- 未切换任何其他 Material surface（仍是 `scheme.surface` 一族 + 面积补偿）。
- 效果：大面积菜单/展开胶囊不再有厚灰/纯黑压迫感；底层 PDF 仍只见轮廓、不可直接阅读。

## 3. 胶囊「跳转到页面」展开态：紧凑扩展区（r20 三）

结构不变：同一玻璃 surface、同一 top/left/right bounds、TopCenter 恒定、仅向下增长。
内部从「表单」改为**紧凑扩展区**：

- 顶部 header 保留当前位置信息（44dp，两态一致，titleSmall + labelSmall）。
- 展开区标题降为 `labelLarge SemiBold`（原 titleSmall），辅助说明单行 `labelSmall`（原 bodySmall 双行）。
- 数字输入框：**50dp 高、11dp 圆角**、填充 `onSurface 6%`（只比父玻璃稍深，无纯黑大矩形），
  placeholder「PDF 页码」、Number 键盘 + IME Go；BasicTextField 自绘（去掉大 label/大边框）。
- 错误：单行 labelSmall error（替换原 supportingText）。
- 「取消」= 轻量 TextButton，点击收键盘并**折叠胶囊回 collapsed**（新增 `JumpCardParams.onCancel`，
  默认空实现）；「跳转」= 紧凑主操作（`contentPadding(14, 0)`，Material 40dp 最小高）。
- IME 契约不变：window ADJUST_NOTHING ⇒ 键盘不推走 Chrome、不动 PDF viewport；body 按
  `imeAvailableHeightDp - 44` 高度上限 + `verticalScroll` 自适应（横屏键盘场景）。

## 4. inline 二级 vs 复杂任务边界重划（r20 四）

- 「阅读方式」「阅读界面」：2–3 个短选项，**继续在 More popover 内原地展开**（accordion 模型不变）。
- 「设置教材页码」：从 More 卡内 accordion **移出**（`PdfChromeSubmenu.PRINT_PAGE` 删除、
  `PrintPageAccordionBody` 删除、134dp 固定槽删除）。菜单里改为**普通一级 row**，点击后：
  1. `printSheetOpen = true` + 菜单 morph 收起；
  2. 门控 `printSheetOpen && !menuExpanded && !menuClosing` ⇒ **菜单完全关闭后 BottomSheet 才展开**
     （互不叠放）。
- 新组件 `PrintPageSheet`（PrintPageSheet.kt）：紧凑 ModalBottomSheet，同一设计语言
  （`sheetContainer`、contentColor onSurface）、**轻 scrim 0.24**、数字键盘友好（Number + Done、
  打开即自动聚焦唤起 IME）、**不用 AlertDialog**。内容：标题「教材印刷页」+ 说明「当前 PDF 第 N 页」
  + 当前状态（未设置/锚点/推算，推算时附 primary 提示行）+ 52dp/12dp 圆角输入框（同胶囊字段语言）
  + 清除本页锚点（仅锚点态）/取消（TextButton）+ 保存（紧凑 Button）。
- IME：ModalBottomSheet 的 dialog window 自带 `SOFT_INPUT_ADJUST_RESIZE` +
  `decorFitsSystemWindows(false)`（material3 1.3.1 源码确认）⇒ 键盘弹出时 sheet 自身抬升，
  活动窗口的 ADJUST_NOTHING 不受影响、PDF viewport 不动。
- 多步骤任务（导出章节等）维持既有 Sheet/Page 路径，未改动。

## 5. 1.0x / 1.15x / 键盘展开态检查（r20 五）

- **More 菜单**：行高 46/42dp 固定，14sp×1.15 ≈ 16.1sp（行高 23sp）远小于行高 ⇒ 无纵向裁切；
  一级 row 高度实测化 + 防御性滚动兜底横屏/大字号。新增单元测试断言
  `optionRow > 14×1.15`、`row > 14×1.15`。
- **胶囊**：header 44dp 容纳 1.15x 两行（titleSmall 23sp + labelSmall 18.4sp ≈ 41.4sp < 44dp）；
  展开 body 键盘出现时高度受限 + 滚动（`heightIn(max)` + `verticalScroll`），输入框/按钮不被键盘遮挡。
- **PrintPageSheet**：全内容 `verticalScroll`，字段 52dp 固定、按钮 40dp 最小高，键盘由 sheet
  窗口 ADJUST_RESIZE 处理 ⇒ 无遮挡无裁切。
- 无任何固定 156dp/134dp 槽位残留（R19 的 print 固定槽已随 accordion 删除）。

## 6. 自动化回归

- `ReaderChromeGeometryTest` 更新：216/46/42/3dp 常量；新增用例「print 是普通一级 row——
  popover 永不承载 print accordion；高度 = 6 行 + pad + divider；option/row 高度容纳 1.15x 文字」。
- 既有 4 个锚点/胶囊不变式用例保持全绿。
- 全量 `:app:testDebugUnitTest` **1005 tests PASS**（1004 + 1）；`:app:lintDebug` PASS。
- `PdfChromeScrollStateMachineTest`（28 用例）等既有套件全绿——状态机/滚动规则零改动。

## 7. 构建与交付

- QA：`.external_outbox/markleaf-qa-latest.apk` SHA-256 `7d70cd83de554dc4710aa32a0b69f82412d6e8ee2acb514b02083baabf823ab5`
- Release：`.external_outbox/markleaf-release-latest.apk`（10.7MB minify）SHA-256
  `4a9b12d5ac7963f56fa5ae002e1e9ab4abffd539eb7ee8eeb19f7f29edabd4bf`
  → 已复制 `/storage/emulated/0/Download/markleaf-release-latest.apk`（SHA 校验一致）。
- ADB 离线（`adb devices` 无 device）：未做任何 device smoke（按 AGENTS.md 协议不标记 PASS）。

## 8. 实机验收协议（PENDING — 需设备）

①一级 More 紧凑截图（216dp popover，竖屏 + 横屏）；②阅读方式展开截图（42dp option、对齐 indent）；
③阅读界面展开截图；④胶囊跳页 collapsed → expanded → IME 弹出 → 取消/collapsed 录屏
（同一 TopCenter surface、top/centerX 不变、键盘不推 Chrome/不动 viewport；`CAPSULE_SURFACE` log）；
⑤设置教材页码：More 菜单关闭后 BottomSheet 展开 + IME 弹出录屏（sheet 抬升、PDF 不动）；
⑥系统字体 1.15x 复查全部展开态无按钮裁切（含横屏菜单滚动触达「导出章节」）。
证据流：`logcat -s MORE_SURFACE CAPSULE_SURFACE` + 截图/录屏。
