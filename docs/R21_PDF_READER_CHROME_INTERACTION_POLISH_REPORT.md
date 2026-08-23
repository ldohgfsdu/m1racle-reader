# R21 PDF Reader Chrome Interaction Polish（交互收口）

**Date:** 2026-08-05
**Date r21b:** 2026-08-05（修复 ② 菜单卡高度失控）
**Scope:** 在 R19/R20 已确认的两个核心方向（卡片内部展开保留兄弟项、胶囊向下展开）上做**交互与视觉收口**，
不做任何新能力、不引入第三套 popup 状态机：
①三点菜单真正避让顶部三个控件（从 row 下方展开，8–12dp 间隙）；②菜单卡改为轻量 anchored surface，
一级/二级建立缩进+字号+字重层级；③跳转页面保留顶部向下展开但瘦身为 popover（顶部宽度与胶囊连续、
下面适度拓宽、输入框更克制）；④设置教材页码从大 BottomSheet 降级为 compact editor；
⑤缩略图/导出/目录等大 Sheet 打开时隐藏顶部 Chrome；⑥清理 Export Sheet 重复标题、radio 层级、
紧凑页码范围字段；⑦统一 overlay 分层契约（row=z1 / catcher=z2 / popover=z3，row 永不收缩）。
**Status:** 实现完成（unit 1007 + lintDebug + assemble Qa/Release PASS）；**实机验收 PENDING**（ADB 离线）。
Release `af0c0b30…`（10.9MB）已复制 `/storage/emulated/0/Download/`（SHA 一致）。未 commit。

**核心方向不变声明**：「阅读方式/阅读界面」卡片内部展开并保留其余菜单项、「跳转到页面」从顶部胶囊向下
展开这两个范式保持，本轮的菜单下落、层级强化都是把这两个范式打磨成唯一范式，未再造第三套交互。

---

## 1. 三点菜单：row 下方独立 popover，禁止重叠（r21 一/二/八）

### 几何（R20 → R21）

| 项 | R20 | R21 |
|---|---|---|
| 菜单锚点 | TopEnd == 三点圆 top（与圆共享锚点，morph 展开） | **row bottom + 10dp gap**（`MoreMenuDropGap`），right 仍 == 圆 right |
| 三点圆 | 展开时被卡片取代（morph） | **row 元素，两态保持原位**（`MoreCircleButton` 独立存在） |
| 胶囊 | 菜单打开时整条 RETRACT（与菜单重叠） | **永不收缩**；菜单在 row 下方展开，视觉零重叠 |
| 展开动画 | width/height morph + origin pop | **fade + 轻微 slide（1/24 高）pop**，关闭同速反向 |
| 卡面材质 | glass 0.84 增密 | **统一 0.80 玻璃 + 轻微 tonal elevation**（`tonalElevation=2dp`，向 surfaceContainerHigh 混 1/3 步） |
| 卡面形态 | 216dp 宽、22dp 圆角 | 不变（有限宽度、明确圆角、轻阴影 = anchored surface） |

- 移除整套 morph 机件：`cardWidth/cardHeight Animatable`、`morphScale`、`glassAlpha` 动画、
  `reportNaturalHeight` intrinsic 上报、icon-first-frame 逻辑——卡片现在是普通 surface，自然高度即卡高。
- `ReaderMoreSurface` 拆分为 `MoreCircleButton`（row 元素）+ `ReaderMoreMenuCard`（popover surface）。
- 卡片高度上限 = 屏高 − 锚点偏移 − 12dp，内容 `verticalScroll`（横屏/大字号不裁底）。
- 菜单打开/关闭不再影响胶囊可见性（删除 `menuOpen` retraction）。

### 一级/二级层级（r21 三）

| 项 | 一级 | 二级 |
|---|---|---|
| 缩进 | label 起点 = 10+20+10 = 40dp | **68dp（再深 28dp，24–32 带）** |
| 字号/字重 | 14sp Medium | **13sp Normal（降一级）** |
| 图标 | 20dp icon + 10dp gap | 无 icon，仅文字 + 16dp ✓ |
| row 高 | 46dp | 42dp |

父子关系一眼可辨：「上下连续/左右翻页」与「设置教材页码/教材目录」不再等重。

## 2. 跳转页面：胶囊向下生长的 popover（r21 四）

- 保留：顶部向下展开、键盘出现后仍保持顶部位置（ADJUST_NOTHING + body 在 IME 可用高度内滚动）。
- 新增 `CapsulePopoverShape`：展开时表面宽度动画 200→240dp（受 capsule cap 钳制），
  **顶部边缘恒为 pill 宽度（200dp）且 top/centerX 不变**——顶部与胶囊形成连续关系；
  body 区域适度拓宽到 240dp（底边 16dp 圆角 vs 顶边 22dp），形状随动画宽度平滑变化
  （动画两端都是纯圆角矩形，只有 body 可见时外扩）。收起的 pill 形状与位置逐像素不变。
- 输入框 50dp → **44dp**（更克制）；「取消」维持轻量文字动作、「跳转」唯一强调按钮（R20 已确立）。
- 胶囊头部内容固定在 pill 宽度内居中，不随表面加宽而拉伸。

## 3. 设置教材页码：大 Sheet → compact editor（r21 五）

- 规则落地：**1 个字段 + 1 个动作 → 小浮层/compact editor；需要浏览大量内容 → Bottom Sheet；
  复杂多字段任务 → Large Bottom Sheet**。
- `PrintPageSheet.kt`（ModalBottomSheet）删除，替换为 `PrintPageEditor.kt`：
  288dp 宽、20dp 圆角、tonal elevation 2dp 的顶部 anchored 卡，与菜单卡同族；
  锚点同菜单（row bottom + 10dp、right == 圆 right）；44dp 数字输入框自动聚焦；
  IME 契约同跳页卡（ADJUST_NOTHING + 滚动）；菜单先 fade 完再出现（互斥时序不变）。
- 大 Sheet 打开时（缩略图/目录/导出）顶部 Chrome 强制 HIDDEN，关闭后恢复原 level——
  两个控制层不再同时抢注意力。

## 4. Export Sheet 清理（r21 七）

- 「导出章节」标题只保留一处（删除重复的章节区段标题 `pdf_export_pick_node`）。
- 章节选择改为**明确 selector row**（当前章节 path + 页码范围 + chevron），点击后 inline 展开
  章节列表（radio rows，depth 缩进）。
- 两个导出格式改为**清晰 radio-style rows**（RadioButton 指示器 + 主副文案），不再只靠右侧 ✓。
- 页码范围两个全宽 OutlinedTextField → **44dp 紧凑字段并排**（label 缩短为「起始页/结束页」）。
- 删除废弃的 `ChapterExportConfirmDialog`（无调用点，不留兼容层）。

## 5. Overlay 契约统一（r21 六/八）

单一规则，全屏通用：

```
content = z0 ｜ chrome row（返回/胶囊/三点）= z1 ｜ outside-tap catcher = z2 ｜ popover = z3
```

- row 两态都在原位（悬浮阅读控件本身允许覆盖正文）；popover（菜单卡/跳页卡/打印编辑器）
  一律在 row 下方展开，互斥激活，catcher 全屏拦截外部点击。
- 胶囊层 zIndex 随展开态 1→3；菜单卡、打印编辑器独立成层。

## 6. r21b：菜单卡高度失控修复（2026-08-05）

### 实机反馈（r21 验收）

- ① 避让顶部控件 PASS；③ 一级/二级层级 PASS；④ 跳转页 popover PASS；⑤ 教材页码 compact editor PASS；
  ⑥ 大 Sheet 隐藏 Chrome PASS；⑦ Export Sheet PASS。
- **② FAIL**：菜单卡高度完全失控——从 row 下方一直拉到接近屏幕底部，六个菜单项只需 ~350–450dp，
  外壳却留下近半屏空白；展开二级后仍是超长外壳，像右侧挂了一块全高侧栏。

### 根因（布局约束，非视觉参数）

`ReaderMoreMenuCard` 的 blank-area sink 使用了 `fillMaxSize()`：sink 按父约束测量成
`heightIn(max = popoverMaxHeightDp)` 的上限（≈ 屏高 − 锚点 − 12dp）；Box 尺寸 = max(全部子节点测量值)，
于是卡片被撑满到接近全高。菜单内容 Column（verticalScroll）本身测量正确（clamp(自然高, max)），
是 sink 把外壳拉长了。

### 修复

- sink：`fillMaxSize()` → `matchParentSize()`（跟随内容 Column 的测量尺寸，不参与 Box 尺寸计算）。
- 卡片高度契约：**content-driven**——Column 按实际 rows 自测（6 条一级 ≈ 3+46×6+divider ≈ 288dp；
  展开二级自然增长 ≈ +84dp），仅 `heightIn(max = 可视区域)` 作极端小屏（横屏/大字号）保护；
  一级态底边应落在「导出章节」下方 ~16–20dp。
- 按反馈未动：玻璃 0.80 alpha（高度修正后大概率自然干净）、二级纵向间距（暂不单独调）。

### 验证

- unit 1007 PASS、lintDebug PASS、assemble Qa/Release PASS；Release `af0c0b30…` 已交付 Download。
- **实机 PENDING**：需三张截图——默认菜单 / 展开阅读方式 / 展开阅读界面，高度正确后 R21 收口。

## 测试

- `ReaderChromeGeometryTest` 重写为新契约：菜单 top = row bottom + 10dp gap（8–12dp 断言）、
  right == 圆 right、二级缩进 24–32dp、6 条一级 row、菜单打开胶囊保持原位、胶囊展开 top/centerX 不变。
- 全量 unit **1007 PASS**（新增 2 断言组），lintDebug 无新增问题，assemble Qa/Release PASS。

## 验收清单（实机，PENDING）

1. 三点菜单在胶囊/圆下方展开，8–12dp 间隙，背景不压到任何顶部控件；三点与胶囊两态均原位。
2. 菜单卡轻量（不黑不厚），一级/二级缩进+字号+字重层级一眼可辨。
3. 跳转页面：顶部宽度与胶囊连续、下方适度拓宽、输入框克制；键盘弹出后仍贴顶。
4. 设置教材页码：compact 顶置编辑器（非大 Sheet），数字键盘自动聚焦，IME 不推走控件。
5. 缩略图/目录/导出打开时顶部 Chrome 隐藏，关闭后恢复。
6. Export Sheet：单一标题、selector row 展开章节、radio 格式行、紧凑页码输入。
7. 所有 popover 互斥、catcher 点击关闭、Back 键按 胶囊→编辑器→二级→菜单 顺序退出。
