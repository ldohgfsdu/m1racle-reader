# R14 PDF Reader 右上 More 控件：单一 TopEnd surface 几何模型 + 统一玻璃材质

**Date:** 2026-08-04
**Scope:** ①消除「先下移再展开、收起后再上移」——collapsed 圆与 expanded 菜单改为**同一个 layout node、
同一个 TopEnd anchor**；②统一左右圆 / 中间胶囊 / 右上展开菜单的玻璃材质 token。
**Status:** 实现完成（compile / unit / lint / assemble 全 PASS）；**设备验收 PENDING**（ADB 离线）。未 commit。

---

## 1. 根因与几何模型修正

实机录屏显示：点击三点后控件先向下迁移到另一 anchor 再扩张，收起时先缩小再向上迁回 ——
根因是 collapsed 圆按钮与 expanded menu 使用了两套布局坐标（按钮 = safe-inset TopEnd anchor；
菜单 = 独立 Popup 窗口 + TopEnd + statusInset + 按钮高度偏移的独立定位），且中间经历
「App 窗口按钮消失 → Popup 窗口出现」的坐标系切换。

**本轮结构变更（彻底消除双坐标系）：**

- 删除 `PdfMorphingMenuHost` 的整层 Popup 机制（popup window、position provider、catcher window）。
- 新增 **`ReaderMoreSurface`**：一个常驻 App 窗口的 layout node，同时承载 collapsed（44dp 三点圆）
  与 expanded（232dp 菜单卡）两种状态，**永远由同一 TopEnd 锚点定位**：
  `align(TopEnd) + padding(end=12dp, top=statusBarsIgnoringVisibility+8dp)` —— 与左返回圆完全同构，
  不再存在第二个坐标系，也没有「按钮下方 44/48/56dp」的额外 y offset。
- 展开 = 该 node 自身 width 44→232dp（向左增长）、height 44→菜单实际高度（向下增长）、
  corner radius 恒为 22dp（44dp 圆的半径 == 卡片圆角，处于规格 20–24dp 区间，端点重合的连续插值）、
  内容 alpha/clip 进入、图标淡入卡片右上角。**无 translationY-then-expand，无圆/卡两节点切换。**
- 收起 = 完全反向：内容淡出、surface 向右上 TopEnd 收缩、圆角恢复为圆、落回原三点圆的同一像素位置；
  关闭状态在 morph 完成后才翻转（`pdfMenuClosing` 阶段 + 240ms 延迟）。
- 二级 inline 展开（阅读方式/阅读界面）只改变同一 surface 的 height（解析式目标高度 + Animatable），
  TopEnd 不动，卡片不产生上下/横向漂移。
- outside-tap / Back 改为 App 内处理：菜单打开时全屏 tap-catcher（仅消费 tap，拖拽仍透传 PDF），
  `BackHandler` 二级优先折叠、再按 morph 收起；不再依赖 popup 窗口的 dismiss 机制。

## 2. 统一玻璃材质（ReaderChromeGlassTokens）

新增 `ReaderChromeGlass.kt`：

| token | 值 |
|---|---|
| containerColor | `scheme.surface`（**圆与菜单同色**，不再用 surfaceContainerHighest） |
| containerAlpha | 0.82f（圆/胶囊）；菜单展开时经同一材质算法连续插值至 0.86f（面积补偿，唯一允许偏差） |
| border | 1dp `scheme.outlineVariant` @ 0.65 |
| shadow | elevation 6dp，spot 0.20 / ambient 0.16 |
| contentColor | `scheme.onSurface`，tonalElevation 0 |

- 左返回圆、右更多圆（`FloatingChromeButton`）共用同一 spec；中间胶囊（`PdfInfoCapsule`）复用同一基础玻璃。
- 右圆 collapsed 与 expanded menu 使用**同一容器色 + 同一描边 + 同一阴影配方**，glassAlpha 随 morph 连续插值，
  视觉上明确是「这颗圆展开成了这张卡片」；底层 PDF 轮廓可感知、正文不可直接阅读。
- 菜单表面 modifier 与按钮表面 modifier 为同一个 `Modifier.readerGlassSurface(spec, shape)`。

## 3. 锚点稳定性证据（内置）

`ReaderMoreSurface` 内置 `MORE_SURFACE` logcat 证据：

- `onGloballyPositioned` + `boundsInWindow()`：top/right 一旦变化（>0.5px）即打印；
- 每个状态（collapsed / expanded_level1 / expanded_level2 / closing）settle 后打印一次 `settled state=… top=… right=…`。

四个状态的 top/right 应当恒等（或仅抗锯齿级误差）——验收时可 `adb logcat -s MORE_SURFACE` 直接核对。

## 4. 修改文件

| 文件 | 变更 |
|---|---|
| `app/src/main/java/com/markleaf/app/ui/pdf/ReaderChromeGlass.kt` | **新增**：ReaderGlassSurfaceSpec + readerChromeGlassSpec + Modifier.readerGlassSurface |
| `app/src/main/java/com/markleaf/app/ui/pdf/PdfExpandableMenu.kt` | `PdfMorphingMenuHost`（Popup 版）→ `ReaderMoreSurface`（单一 in-app node）；删除 popup/catcher window；锚点证据日志 |
| `app/src/main/java/com/markleaf/app/ui/pdf/PdfViewerScreen.kt` | 左/右圆与胶囊改玻璃 spec；more 层 = 全屏 Box + in-app tap-catcher + ReaderMoreSurface（zIndex 3）；chrome 显隐 slide 钉死 22dp；`pdfMenuClosing` 阶段 + BackHandler + requestClosePdfMenu |
| `app/src/main/java/com/markleaf/app/ui/pdf/PdfChromePrefs.kt` / `strings.xml` 等 | 无变更（模式两档保持） |

## 5. 构建验证（本机）

- `:app:testDebugUnitTest` / `:app:lintDebug` / `:app:assembleDebug` / `:app:assembleQa` / `:app:assembleRelease` 全 PASS。
- QA：`.external_outbox/markleaf-qa-latest.apk` SHA-256 `8f08a09499326362927c1588b1a9726e6e8f86b1746de53e13d8f858f87b3415`
- Release：`.external_outbox/markleaf-release-latest.apk` SHA-256 `8ee62b1c591d8756d7802cc7302105f0706dbd1ad597b879fddd8e72c7990a05`
  → 已推送 `/storage/emulated/0/Download/markleaf-release-latest.apk`（校验 OK）。

## 6. 设备验收（PENDING — ADB 离线）

1. 慢速录屏：三点圆原地 morph 成菜单，无先下移再展开。
2. 收起反向 morph 回完全相同圆形位置，无最后阶段上移。
3. `adb logcat -s MORE_SURFACE`：collapsed / expanded_level1 / expanded_level2 / closing 四状态
   top/right 恒等（或 ≤0.5px）。
4. 阅读方式/阅读界面 inline 展开时卡片 top/right 不动。
5. 圆形状态与菜单状态同区域截图：背景色调、透明度、描边、阴影属同一玻璃体系
   （圆是白玻璃、菜单也是同一块白玻璃，不再是灰塑料）。
6. 左右翻页居中、模式两档等 R13 验收项保持不回退。
