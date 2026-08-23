# R12 PDF Reader Tools Popover · Inline Submenu · Immersive System Bars

**Date:** 2026-08-04
**Scope:** PDF reader top-right tools menu (anchored popover), secondary interaction model,
jump/print-page sheets, outline/thumbnails/export visual polish, system-bar immersion semantics.
**Constraint honored:** Reader Chrome FULL / COMPACT / IMMERSIVE architecture untouched;
PdfContent geometry/zoom/scroll anchors decoupled from all overlays.
**Status:** 实现完成（compile / unit / lint / assemble 全 PASS）；**设备验收 PENDING**（ADB 离线，
见 §9 离线安装协议）。未 commit。

---

## 1. 目标与结论

按参考录屏风格重做右上工具菜单：**轻量 anchored popover → 同一 surface 内原地展开短选项 → 复杂任务才进 Sheet/Page**。
彻底移除“popover → 第二张 popover / Dialog”的碎片化交互。全屏沉浸语义重定义：默认全屏沉浸只隐藏状态栏、
保留透明手势导航栏，不再诱导用户进入 SystemUI transient-bars 灰色遮罩状态；仅“深度沉浸”同时隐藏两条系统栏。

## 2. 一级菜单（紧凑 anchored popover）

| 项 | 旧 | 新 |
|---|---|---|
| 宽度 | 240dp | **232dp** |
| 行高 | 54dp | **52dp** |
| 图标 | 22dp | **22dp** |
| 字号 | bodyMedium 14sp | **13sp** |
| 圆角 | 22dp | 18dp |
| surface | surface 0.96 近实心 | **surfaceContainerHighest @ 0.86 半透明玻璃** + 1dp 细描边 + 轻阴影（spot 0.16 / ambient 0.10） |
| scrim | 无 | 无（全屏透明触摸捕获层，**不是 dim scrim**） |

- 底层页面轮廓可感知（页面白纸 vs 画布灰底的明度差透过玻璃可见），正文不可直接阅读。
- 菜单锚定右上更多按钮；top/right 几何在打开与内部展开过程中**零漂移**（全屏 Popup 窗口原点=屏幕原点，
  菜单 Box 固定 TopEnd + offset(-18dp, statusInset+56dp)，窗口不随内容高度变化而移动）。

## 3. 二级交互模型（inline expandable menu，核心变更）

- 单一浮动 surface：`PdfExpandableMenu`（新文件 `PdfExpandableMenu.kt`）。
- 点击“阅读方式 >”/“顶部表现 >”：该行原地转为展开区 header，右侧 chevron 用
  `animateFloatAsState(0→90°)`（tween 200ms）从 `>` 平滑旋转为 `⌄`。
- 二级选项**从该行下方在同一 surface 内向下长出**（`expandVertically(expandFrom = Top)` + fadeIn，
  200ms；收起 180ms）。无横向飞入、无弹簧位移、无整菜单跳位。
- 被点击行上方的一级项保持原位；下方 sibling（另一组 + 分隔线 + 普通项）在展开时经
  `AnimatedVisibility` shrink/fade 暂时退出，收起后恢复。
- 同一时间只允许展开一组：切换时先折叠（`MENU_SUBMENU_SWITCH_DELAY_MS = 190ms` 延迟）再展开，
  sequence token 防止快速连点竞态。
- 阅读方式展开：上下连续 ✓ / 左右翻页 ✓（当前模式 check 标识）。
- 顶部表现展开：普通 ✓ / 顶部渐变 ✓ / 全屏沉浸 ✓ / 深度沉浸 ✓（check 标识）。
- 点击选项**立即应用并自动折叠回一级菜单**（不再弹确认，也不关闭整个菜单）。
- Back：二级展开时第一次 Back 只折叠二级，第二次才关闭整个菜单（Popup `dismissOnBackPress=true`
  的 `onDismissRequest` 内做折叠优先判断）。
- 点击 popover 外部：关闭整个菜单（全屏透明捕获层，无 scrim）。
- 普通项（教材目录/页面导航/导出章节/设置教材页码）点击后关闭菜单并打开对应 Sheet。

**禁止项已全部移除：** 左右并排 cascading submenu（旧第二张 Popup）、`PdfReadingModeSheet`(ModalBottomSheet)、
顶部表现 AlertDialog、`PdfReaderMenuSheet`（死代码整文件删除）。

## 4. 系统栏沉浸语义（灰罩问题的正解）

实机灰罩只在“全屏沉浸 + 底部上滑临时唤出导航栏”后出现 —— 这是 SystemUI transient-bars 自身的半透明层，
不是 App 的顶部渐变，**不能用 App scrim 掩盖**（也不承诺 hack 掉 OEM 层）。

| 模式 | 状态栏 | 导航栏 | behavior |
|---|---|---|---|
| 普通 / 顶部渐变 | 显示 | 显示（透明 gesture bar） | BEHAVIOR_DEFAULT |
| **全屏沉浸**（默认语义修正） | **隐藏** | **保留透明 gesture bar** | BEHAVIOR_DEFAULT |
| 深度沉浸 | 隐藏 | 隐藏 | BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE |

- 全屏沉浸不再需要用户每次从底部边缘唤出导航栏 → 不再进入 transient-bars 灰罩状态。
- PdfContent 持续 edge-to-edge，底部 gesture handle overlay 内容，**无永久 bottom padding**。
- 深度沉浸下唤出的灰色 transient 层为 SystemUI 行为，App 不绘制任何额外 status/nav scrim。
- **未**把 `window.isNavigationBarContrastEnforced=false` 当作修复方案（grep 确认 app 内无此配置）。
- 证据日志：`PDF_SYSBARS` tag —— 应用模式时打印 mode/behavior；composition 级读取
  statusBars/navigationBars insets，任何变化（含 transient bars 动画）都会打印
  `visible insets statusTop=… navBottom=… mode=…`。
- Chrome 锚点继续使用 `statusBarsIgnoringVisibility`（stable inset）：状态栏隐藏/显示不引起
  返回圆/胶囊/更多圆上窜下移。
- 顶部渐变模式：保留系统状态栏，PdfContent edge-to-edge，仅 120dp 极轻渐变
  （0.065 → 0.02 → 0 alpha），无整块灰矩形、无硬边、无永久留白。

## 5. 跳转/页码输入 → 紧凑 BottomSheet（AlertDialog 已移除）

- `JumpToPageDialog` → **JumpToPageSheet**：标题 + 上下文说明 + 数字输入（焦点自动弹出）+ 主操作“跳转”实心按钮。
- `SetPrintPageDialog` → **SetPrintPageSheet**：标题 + 映射说明 + 当前推算提示 + 数字输入 + “清除本页锚点”/“取消”/“保存”。
- 两者均为 `ModalBottomSheet`，`scrimColor = Black 0.30`（轻量 scrim），不再有居中大白 Dialog。

## 6. 教材目录 / 页面导航 / 导出章节统一 polish

- **教材目录**：篇 20sp Bold→17sp SemiBold、章 18sp→16sp、节 17sp→15sp；行距/padding 整体收紧；
  缩进 DepthStep 20→18dp、chevron 槽 28→24dp；“当前”徽标 11sp→10sp、底色 alpha 0.12；
  selected 行仍为 `onSurface 0.06` 圆角 12dp（克制，无粗黑框/大灰卡）；页范围行 14sp→12sp；
  底部“在当前页新建目录项”与“扫描/手动新建”按钮 44dp→36dp，字号收紧。真实层级缩进+展开关系保留。
- **页面导航（缩略图）**：删除工程化文案“失败格可点『重试』”→ “点击缩略图跳转页面”；
  标题/说明层级保留，选中态为 2dp primary 细描边（非粗黑框）。
- **导出章节**：RadioButton 全部替换为紧凑 check 行（18dp Check 图标）；节点列表按真实深度
  `depth*16dp` 缩进 + 标题/页码两级文字；输入框与说明间距收紧；主操作改实心按钮。

## 7. 修改文件

| 文件 | 变更 |
|---|---|
| `app/src/main/java/com/markleaf/app/ui/pdf/PdfExpandableMenu.kt` | **新增**：菜单数据类 + 玻璃 surface + inline 展开逻辑 + chevron 旋转 |
| `app/src/main/java/com/markleaf/app/ui/pdf/PdfViewerScreen.kt` | popover 重写（全屏透明捕获层 + 单 surface 菜单）、sysbar 语义、Jump/Print Sheet、删除死代码、日志 |
| `app/src/main/java/com/markleaf/app/pdf/PdfChromePrefs.kt` | 新增 `DEEP_IMMERSIVE` |
| `app/src/main/java/com/markleaf/app/ui/pdf/TextbookOutlineSheet.kt` | 排版/缩进/选中态收紧 |
| `app/src/main/java/com/markleaf/app/ui/pdf/ChapterExportDialogs.kt` | radio→check 行、间距收紧 |
| `app/src/main/res/values/strings.xml` | 新增深度沉浸、缩略图文案、清理死字符串 |
| `app/src/main/java/com/markleaf/app/ui/pdf/PdfReaderMenuSheet.kt` | **删除**（死代码） |

## 8. 构建验证（本机）

- `:app:compileDebugKotlin` / `:app:assembleDebug` / `:app:assembleQa` / `:app:testDebugUnitTest` / `:app:lintDebug` — 全 PASS。
- QA APK：`.external_outbox/markleaf-qa-latest.apk`
  SHA-256 `17c06481a8c6ca2dad91699f63e88e8c38e37fc955f0f44b35ea224a36fad622`
- Debug APK：`.external_outbox/markleaf-debug-latest.apk`

## 9. 设备验收（PENDING — ADB 离线）

本次会话 `adb devices` 无设备（198.18.0.1:44441 等均连接失败）。按 `docs/DEVICE_INSTALL_AND_ADB_TEST.md`
与 `docs/APK_DELIVERY.md`：**未做任何真机 PASS 声明**。验收清单：

1. 一级菜单紧凑截图（232dp/52dp/13sp/玻璃质感），尺寸密度接近参考。
2. 点“阅读方式”同一张卡原地向下展开，无第二张独立子卡，选项直接可选。
3. 点“顶部表现”同样原 surface 内展开并显示当前模式 check。
4. 录屏“一级菜单 → 阅读方式展开 → 收起 → 顶部表现展开 → 收起”；出现旁侧第二卡/AlertDialog/BottomSheet 即失败。
5. 跳转页码紧凑 BottomSheet。
6. 设置教材页码紧凑 BottomSheet。
7. 目录/页面导航/导出章节视觉 polish。
8. 普通/顶部渐变无整块灰色 status-bar 遮罩。
9. 全屏沉浸 = 状态栏隐藏 + 透明手势导航栏保留（无需底部上滑唤出）。
10. 深度沉浸下底部上滑唤出 transient bars 录屏 + logcat `PDF_SYSBARS` 证据，区分 App 层与 SystemUI 层。
11. 所有菜单展开/收起/Sheet/系统栏变化过程中 PDF 几何、zoom、滚动锚点不变。

离线安装：复制 `.external_outbox/markleaf-qa-latest.apk` 到 `/storage/emulated/0/Download/` 由人工安装，
或 `bash scripts/install-release-outbox.sh`（仅 release 包）。
