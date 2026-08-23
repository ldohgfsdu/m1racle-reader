# R22 Reader More 菜单 Container Transform 重构（三点圆 ↔ 菜单卡 Morph）

**Date:** 2026-08-05
**Scope:** 按 r22 指令把 R21 的「圆 + 独立卡片 popover（gap 方案）」推翻，改为**参考交互 1:1 复刻式**
的 shared-container morph：三点圆本身向左/向下生长成菜单卡片；菜单卡内部继续做 nested accordion
展开；关闭时原路倒放收回三点圆。不新增任何能力、不引入第三套交互。
**Status:** 实现完成（unit 1019 + Robolectric Compose UI 6 + lintDebug + assemble Qa/Release PASS）。
Release `8b281998…` 已复制 `/storage/emulated/0/Download/`（SHA 一致）。
**实机录屏/关键帧截图 PENDING**（adb 离线，无设备；验收清单见 §8）。
未 commit（与 R18–R21 一致，待实机）。

---

## 1. 修改文件清单

| 文件 | 改动 |
|---|---|
| `app/src/main/java/com/markleaf/app/ui/pdf/PdfExpandableMenu.kt` | **重写**：新增 `ReaderMoreState`/`ReaderMoreStateMachine`；`ReaderMoreMorphSurface` 取代 `ReaderMoreMenuCard`；删除 popover 定位 |
| `app/src/main/java/com/markleaf/app/ui/pdf/PdfViewerScreen.kt` | 状态模型重构（sealed state）；菜单恢复单节点 morph 层；胶囊菜单期收纳；大 Sheet 与菜单 morph 的交接时序；MenuBook icon 改 AutoMirrored |
| `app/src/test/java/com/markleaf/app/ui/pdf/ReaderMoreStateMachineTest.kt` | **新增**：状态机纯逻辑测试（r22 §19） |
| `app/src/test/java/com/markleaf/app/ui/pdf/ReaderMoreMorphSurfaceUiTest.kt` | **新增**：Robolectric Compose UI 测试（r22 §19，JVM 无设备） |
| `app/src/test/java/com/markleaf/app/pdf/ReaderChromeGeometryTest.kt` | 重写为 morph 几何契约（top/right 恒定、corner 22 固定、高度=内容自然高） |
| `app/build.gradle.kts` | 新增 Robolectric 4.14.1 + compose ui-test-junit4（JVM UI 测试）+ `unitTests.isIncludeAndroidResources` |

## 2. 删除的旧 More Menu 路径

- `ReaderMoreMenuCard`（独立 popover 卡，row 下方 + 10dp gap 定位）——整段删除。
- `MoreMenuDropGap`（菜单 gap 常量）→ 改名 `MoreFormDropGap`，只服务于 PrintPageEditor form。
- R21 的 PdfChrome 菜单 popover 层（catcher + `AnimatedVisibility` fade/slide 卡片）——删除，恢复单节点层。
- `pdfMenuExpanded` / `pdfMenuClosing` / `pdfMenuSubmenu` 三个互相冲突的 Boolean —— 删除。
- `PdfChromeSubmenu.NONE` —— 删除（无展开由 sealed state 表达）。
- `MENU_SUBMENU_SWITCH_DELAY_MS` + `menuSwitchSeq`（先关 A 再开 B 的延迟切换）—— 删除（连续 accordion 直切）。
- R21 的 `MoreCircleButton` 独立 row 元素语义 —— 保留其绘制，但只作为 morph 的 collapsed 分支（同一节点两态）。

## 3. 最终状态模型

```kotlin
sealed interface ReaderMoreState {
    data object Closed : ReaderMoreState          // 渲染 44dp 三点圆
    data object Main : ReaderMoreState            // 圆已 morph 成主菜单卡
    data class Expanded(val section: PdfChromeSubmenu) : ReaderMoreState  // 卡片内二级展开
}
enum class PdfChromeSubmenu { READING_MODE, TOP_BAR }
```

转换路径固定：`Closed ↔ Main ↔ Expanded(section)`。`Expanded` 携带且只携带一个 section ——
「阅读方式 expanded AND 阅读界面 expanded」在模型上不可表达（构造级互斥）。关闭 morph 的
200ms 过渡由 UI 层 `moreClosing` 表达（sheet 交接时序），不属于状态。

`ReaderMoreStateMachine`（纯函数，无副作用）：
- `onToggleOpen`：Closed→Main；展开态 no-op（圆已不在）
- `onSectionToggle`：Main→Expanded(s)；Expanded(s)→Main；Expanded(s)→Expanded(s') 直切
- `onOptionSelected`：→Main（选中二级即收起）
- `onDismiss`：→Closed

## 4. Container morph 实现方式

自建单一 overlay 容器（不引入 SharedTransition API，理由：版本/API 稳定性 + 最小重构，r22 §15 允许）：

- `ReaderMoreMorphSurface`：一个组合节点、一个 TopEnd 锚点，两态。
  - **closed 分支**：44dp 玻璃圆 + ⋮（纯行元素）。
  - **morph 分支**：`Box(size(cardWidth, cardHeight))`，`Animatable` 驱动 width/height；
    `align(TopEnd)` ⇒ right/top 两态恒定，宽度只向左、高度只向下生长。
- **Shape 连续性（无瞬切）**：corner 固定 22dp —— 44×44@22 = 圆（corner==半径），
  216×h@22 = 卡片。同一 `RoundedCornerShape(22)` 的零帧与终帧，尺寸动画全程插值轮廓。
- **高度 content-driven**：内容 Column 通过 `reportNaturalHeight`（unbounded intrinsic 查询）
  上报自然高，`cardHeight.animateTo(自然高)`；二级展开时自然高增加 ⇒ 同一张卡变高。
  修复 r21b 的高度失控教训：morph 分支无任何 `fillMaxSize` 子节点，尺寸完全由 Animatable 决定。
- **内容布局**：内容固定 216dp 宽、右对齐（卡片窄时左缘被 clip，不重排）；内容 Column
  `verticalScroll` 兜底（横屏/大字号），竖屏永不滚动。

## 5. Nested expansion 实现方式

- 同一卡片内两个 `AnimatedVisibility` section：
  `enter = expandVertically(from Top, 220ms FastOutSlowIn) + fadeIn(delay 40ms)`，
  `exit = shrinkVertically(180ms) + fadeOut(120ms)`。
- 占位高度动画 ⇒ siblings 被连续推下（无跳位）；`cardHeight.animateTo(新自然高)` 同步 ⇒
  card bottom 一起下延，呈现「卡片内部撑开」。
- section 切换（阅读方式→阅读界面）：状态直切 ⇒ A exit + B enter 并行 + 高度连续过渡
  （r22 §7 连续 accordion，无先关后开）。
- 折叠：children 收起 + rows 上移 + card 缩短 + chevron 回转，全程动画。

## 6. Motion 参数（r22 §8/§9 节奏：容器先动、内容后跟）

| 阶段 | 参数 |
|---|---|
| 主 morph expand（宽/高） | 260ms，FastOutSlowIn（220–320 带） |
| 主 morph collapse（宽/高） | 200ms，FastOutSlowIn（略短，原路倒放） |
| 内容 alpha（开） | delay 90ms + 170ms（容器先动，内容后跟） |
| 内容 alpha（关） | 120ms 立即（内容先退） |
| ⋮ 图标（开） | 90ms 淡出（容器刚开始生长即退） |
| ⋮ 图标（关） | delay 110ms 后 90ms 淡入（最后回到圆） |
| nested expand | 220ms + children fade delay 40ms（180–260 带） |
| nested collapse | 180ms |
| chevron | 200ms FastOutSlowIn |
| 玻璃 | 0.80 alpha + tonalElevation 2dp（悬浮、非 modal、非不透明大白板） |

## 7. 测试结果

- **unit 1019 PASS**：新增 `ReaderMoreStateMachineTest`（12 个：Closed↔Main↔Expanded 全路径、
  互斥构造保证、快速点击路径稳定）；`ReaderChromeGeometryTest` 重写为 morph 契约
  （card top/right == circle top/right、corner 固定、二级 +84dp、胶囊收纳、print form 保持 gap 锚定）。
- **Robolectric Compose UI 6 PASS**（`ReaderMoreMorphSurfaceUiTest`，JVM 无设备，
  `@GraphicsMode(LEGACY)` + `@ConscryptMode(OFF)`，aarch64 主机兼容）：
  主菜单含全部 6 个一级项且无二级项；展开阅读方式后二级出现且 siblings 全在；
  折叠后二级消失；section 互斥直切；collapsed 恰好一个三点按钮；跨状态切换无重复节点。
- **lintDebug PASS**（无新增）；**assemble Qa/Release PASS**。

## 8. 实机视觉验收（PENDING — adb 离线）

按 r22 §20 需录制：A 圆→菜单、B 菜单→圆、C 阅读方式展开、D 收起、E 阅读方式→阅读界面、
F 收起、G 快速重复点击、H 外部 dismiss、I 复杂 PDF 背景、J 沉浸/普通 chrome。
关键帧截图：circle / morph 25% / 50% / fully open / nested expanding / nested open /
collapse 中间态 / circle restored。
**设备连接后执行；当前未 commit、device_verified = false。**

## 9. 明确结论

**主菜单是否仍是独立 popover？—— 否。** 三点圆本身 morph 成菜单卡（单一容器、
TopEnd 锚点恒定、尺寸连续动画、corner 固定插值）；卡内二级为同一卡片内部展开；
关闭原路倒放收回圆。不存在 circle + separate card 双 surface。
