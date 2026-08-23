# R13 PDF Reader 产品收敛：两档模式 · 三点圆 morph 菜单 · 左右翻页居中

**Date:** 2026-08-04
**Scope:** ①顶部表现四档收敛为两档（普通/全屏沉浸）；②右上三点菜单改为「圆自身 morph 成卡片」；
③左右翻页 single-page 垂直/水平居中 + FitPage 默认。Reader Chrome FULL/COMPACT/IMMERSIVE 三级、
左右固定圆锚点、中间胶囊、PdfContent edge-to-edge 几何全部保持不动。
**Status:** 实现完成（compile / unit 975 全 PASS / lint / assemble 全 PASS）；**设备验收 PENDING**（ADB 离线）。
未 commit。

---

## 1. 模式收敛：普通 / 全屏沉浸（删除顶部渐变、深度沉浸）

- `PdfTopBarMode` 只剩 `NORMAL` / `IMMERSIVE`；`PdfChromePrefs` 默认值从 GRADIENT 改为 NORMAL，
  旧存储值（GRADIENT/DEEP_IMMERSIVE）自动回落 NORMAL。
- **普通**：保留系统状态栏，继续当前 edge-to-edge Reader，App 不绘制任何 scrim/渐变。
- **全屏沉浸**：只隐藏 statusBars；透明 gesture navigation bar 保留（`wic.show(nav); wic.hide(status)`，
  `BEHAVIOR_DEFAULT`）——从产品层面消除「底部上滑唤出导航栏 → SystemUI transient-bars 灰罩」路径。
  不再存在 deep-immersive transient-bars 分支。
- 「顶部表现」菜单项更名为 **「阅读界面」**；二级只显示 普通 / 全屏沉浸，当前项 check 标识。
- 删除：GRADIENT 顶部渐变 Box（PdfChrome 中整块删除）、DEEP_IMMERSIVE 分支、相关字符串
  （`pdf_top_bar_mode_gradient` / `pdf_top_bar_mode_deep_immersive` / hint）与死字符串。

## 2. 三点菜单：圆形按钮自身 morph 成菜单卡片（PdfMorphingMenuHost）

**目标形态**（严格对照 ChatGPT 三点菜单）：44dp 圆形三点按钮就是菜单的视觉 origin；点击后**同一 surface**
从圆自身位置向左下方向 morph 成 232dp 宽菜单卡；收起反向 morph 回完全相同的圆。不再是「圆按钮 + 圆下方
另开一张卡」的割裂结构。

实现要点：

- **单 surface 连续体**：44dp 圆（radius 22dp）与卡片圆角 22dp 共用同一 `RoundedCornerShape(22.dp)`；
  圆就是 corner radius 22 的正方形，因此 morph 是纯 size 插值（无 radius 跳变、无形状突变）。
- **锚点零漂移**：卡片 Box 固定 TopEnd + offset(-12dp, statusInset+8dp)（与隐藏的 App 圆按钮位置完全一致），
  宽度向左扩张、高度向下扩张，right/top 在整段 morph 中不动。
- **morph 动画**：`Animatable` 驱动宽/高（打开 200ms tween；收起 180ms tween），无 spring 大弹跳；
  三点图标 alpha 在展开时融入卡片右上角（fade out），收起时反向 fade in。
- **打开序列**：frame 1 = 原位置 44dp 圆（图标可见）→ 下一帧起宽/高同时动画到 232×H ——
  视觉上明确是「圆自身长成卡片」，不是「圆消失后下方 fade in 一张卡」。
- **收起序列**：请求关闭 → 卡片 200ms 内缩回 44×44 圆（图标同时淡入）→ 之后才移除 popup、
  恢复 App 窗口内的三点按钮（同位置同尺寸，无缝交接）。
- **内容高度**：解析式计算（行高 50dp 固定 + divider 5dp + 上下 12dp padding），随「阅读方式/阅读界面」
  inline 展开状态实时切换目标高度；二级展开/收起即卡片高度 morph，配合选项行 fade（不再有独立
  expandVertically 双重动画）。
- **几何**：宽 232dp、行高 50dp、图标 22dp、主文字 14sp、圆角 22dp、背景 `surfaceContainerHighest@0.93`
  （比上轮更实，底层 PDF 仅轮廓可感知、正文不可读）、1dp 描边 + 轻阴影。
- **交互语义不变**：全屏透明窗口做 outside-tap catcher（无 dim scrim）；Back 二级展开时先折叠、
  再按才收起；「阅读方式」「阅读界面」同一时间只展开一组；选项点击立即应用并折叠回一级；
  普通项（目录/导航/导出/教材页码）点击后 morph 收起并打开对应 Sheet。

## 3. 左右翻页：single-page paging + 页面在 viewport 中居中（结构性修正）

实机缺陷：左右翻页后单页贴 viewport 顶部，内容少的页面上方拥挤、下方大片空白 —— 布局语义是
「连续模式的 top-aligned 布局横过来用」。本轮修正：

- **垂直居中（根因修复）**：`HorizontalZoomPanPhysics.fitPage` 的 `contentTopPx` 从固定 margin
  改为 `margin + max(0, (availableHeight - fittedHeight)/2)`：页面比视口矮时上下等距居中，
  页面铺满高度时保持阅读 margin（无负空间吸附）。所有消费 `contentTopPx` 的路径
  （tile 可见带、viewportSession、restoreViewportSession、transformOrigin）自动一致。
- **布局落点**：slot Box 的 `padding(top = pageContentGap)` → `padding(top = fittedPage.contentTopPx.toDp())`，
  页面 Surface 仍由 TopCenter 水平居中；graphicsLayer 缩放/平移数学不动。
- **FitPage/Contain 默认**：scale 1 = contain-fit（fitted 尺寸已按 aspect 适配宽高）。
  `PdfViewerViewModel.setReadingMode`：**首次进入左右翻页**（无已保存的 horizontal session）时
  modeSession.scale 强制 1f（不继承连续模式的用户 zoom）；**返回**已 zoom 过的左右翻页时恢复用户 zoom。
- **用户 zoom 尊重**：morph/翻页均不重算 scale；放大后只对小于 viewport 的轴居中，
  大于的轴走既有 pan bounds。
- **相邻页一致**：每页按自身 aspect 独立 fit+居中（同规则），不会一页贴顶一页居中；
  翻页（ThreePageStrip）的 entry transform 走同一 contentTop 数学。
- **系统栏解耦**：视口 = 全屏 edge-to-edge；普通/全屏沉浸切换只动 statusBars visibility，
  不触发 onSizeChanged，当前页 scale/center/页码不变。Reader Chrome 是 overlay，不参与 page measure。

## 4. 修改文件

| 文件 | 变更 |
|---|---|
| `app/src/main/java/com/markleaf/app/ui/pdf/PdfExpandableMenu.kt` | 重写：新增 `PdfMorphingMenuHost`（Popup + catcher + morph 卡片 + 图标层 + 收起时序）；`PdfExpandableMenu` 降为纯内容；50dp 行/14sp/22dp 图标/0.93 背景 |
| `app/src/main/java/com/markleaf/app/ui/pdf/PdfViewerScreen.kt` | 模式两档、删除渐变 Box、PdfChrome 去 topBarMode 参数、三点按钮与 morph host 切换、horizontal padding 改 contentTopPx |
| `app/src/main/java/com/markleaf/app/pdf/PdfChromePrefs.kt` | 枚举收敛两档、默认 NORMAL |
| `app/src/main/java/com/markleaf/app/pdf/HorizontalZoomPanPhysics.kt` | fitPage 垂直居中 |
| `app/src/main/java/com/markleaf/app/ui/pdf/PdfViewerViewModel.kt` | 首次进入 horizontal 默认 fit scale 1f |
| `app/src/main/res/values/strings.xml` | 「顶部表现」→「阅读界面」、删除渐变/深度沉浸/死字符串 |
| `app/src/test/.../R7H1HorizontalZoomPanPhysicsTest.kt` | 旧 top-aligned 断言 → 居中断言（含铺满高度保持 margin 用例） |
| `app/src/test/.../R7H14UnifiedGestureControllerTest.kt` | contentTop 193 / session anchor 0.5 断言更新 |

## 5. 构建验证（本机）

- `:app:compileDebugKotlin` / `:app:compileQaKotlin` / `:app:testDebugUnitTest`（975 tests 全 PASS）/
  `:app:lintDebug` / `:app:assembleDebug` / `:app:assembleQa` / `:app:assembleRelease` — 全 PASS。
- QA APK：`.external_outbox/markleaf-qa-latest.apk`
  SHA-256 `1d7e384214d526a020eece8a1f76aa0f2659fd4a115ab3c2ed4e994dabe4d609`
- Release APK（已推送 Download）：`.external_outbox/markleaf-release-latest.apk`
  SHA-256 `d1bbf2e433e6695d0f28e01620f54cfa63e4f5d41ec6224702c6a251977bd177`
- `/storage/emulated/0/Download/markleaf-release-latest.apk` 已替换（校验 OK）。

## 6. 设备验收（PENDING — ADB 离线）

按 `docs/DEVICE_INSTALL_AND_ADB_TEST.md`：未做任何真机 PASS 声明。验收清单：

1. 用户界面只存在 普通 / 全屏沉浸 两个模式（无顶部渐变、无深度沉浸）。
2. 慢速录屏：三点圆按钮直接 morph 成菜单（surface 从圆本身扩张，非圆外新卡）。
3. 收起反向 morph 回完全相同的圆形位置。
4. 阅读方式 inline 展开（同一 surface）。
5. 阅读界面 inline 展开且仅两项。
6. 同一 PDF 上下连续 / 左右翻页截图对比。
7. 左右翻页用「正文铺满页」与「内容较少页」各验证一次，整页在 viewport 中水平/垂直居中。
8. 连续翻 10 页以上，不同尺寸/内容高度不出现 top alignment 漂移。
9. 普通 ↔ 全屏沉浸切换后当前页 scale、center、滚动/页码状态稳定。
10. 全屏沉浸下不再出现底部上滑灰罩路径（gesture nav 常驻透明）。

离线安装：`/storage/emulated/0/Download/markleaf-release-latest.apk`（人工安装）；
或 ADB 恢复后 `adb install -r .external_outbox/markleaf-qa-latest.apk`。
