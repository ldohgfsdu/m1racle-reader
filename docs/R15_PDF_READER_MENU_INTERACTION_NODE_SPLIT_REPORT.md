# R15 PDF Reader 更多菜单：交互节点职责拆分（消灭巨型 ripple）

**Date:** 2026-08-04
**Scope:** 修复展开瞬间卡片中央出现的百 dp 级灰色 ripple 圆；按「动画/几何 host 与点击 host 分离」
重构交互节点；全部菜单交互改用无 indication 的克制 pressed feedback。
**Status:** 实现完成（compile / unit / lint / assemble 全 PASS）；**设备验收 PENDING**（ADB 离线）。未 commit。

---

## 1. 根因

实机静态截图确认：点击右上三点后 expanded menu 中央出现约百 dp 灰色圆形 indication。
代码审计定位：`ReaderMoreSurface` 的几何 host（尺寸 44dp → 232×H 的 morph surface）上挂了
`Modifier.clickable { ... }`（默认 Material ripple indication）。点击圆时 PressInteraction 开始，
tap 完成瞬间 `menuExpanded=true`、同一 node 已扩张为整张卡片 —— ripple 按**扩张后的节点 bounds**
绘制，于是出现卡片中央的巨大灰圆。ripple 随 morph surface 尺寸扩张 = 本轮的根因。

## 2. 修复：拆分「动画/几何 host」与「点击 host」

| 节点 | 职责 | clickable / indication |
|---|---|---|
| **ReaderMoreSurface 几何 host** | 固定 TopEnd 定位 + 尺寸/圆角/alpha morph + 玻璃绘制 + 锚点日志 | **无 clickable、无 indication、无 pointer** |
| **MoreCircleButton**（新增，仅 collapsed 时组合） | collapsed 三点圆唯一的点击宿主：固定 44dp、独立 `MutableInteractionSource`、`indication=null` | pressed feedback = 图标 alpha 1→0.55 + scale 1→0.95（tween 80ms），无 ripple；展开瞬间该节点离开组合 → PressInteraction 立即结束，绝不可能被大 surface 重绘 |
| **菜单内容 wrapper**（expanded） | 空白区（padding/divider）tap 吞掉 | `pointerInput + detectTapGestures(onTap = {})`，零反馈、无 interaction state |
| **PdfChromeMenuRow** | 每个菜单行各自独立的 `MutableInteractionSource` | `indication=null` + 行内克制高亮（onSurface@0.08，tween 80ms，圆角 10dp，clip 在行 bounds 内），无 ripple |
| **Outside-tap catcher**（PdfChrome） | 菜单外 tap 关闭（拖拽透传 PDF） | `pointerInput + detectTapGestures`，无 clickable、无 indication |
| 左返回圆 / 中间胶囊 | 固定尺寸 chrome | 默认 ripple 但 bounds 固定为自身 44dp/胶囊，不随任何动画扩张（不在本轮缺陷路径） |

- 展开动画保持 R14 的 fixed-TopEnd anchored reveal：collapsed 圆淡出（图标节点首帧保持可见再 fade）、
  expanded 卡 scale/size/clip 出现；两者短暂 crossfade，**不共享 InteractionSource**，
  也不要求同一 clickable node 物理拉伸。
- 二级 inline 展开（阅读方式/阅读界面）只改变几何 host 的 height；行 pressed feedback 始终限制在该行
  bounds 内，卡片高度变化不会放大任何反馈。
- 菜单卡片空白区域点击：无 ripple、无任何反馈（被 wrapper sink 吞掉，也不关闭菜单）。

## 3. Debug 审计（代码内置）

`ReaderMoreSurface` 顶部新增 INTERACTION AUDIT 注释块，逐节点列明点击职责；本机 grep 验证：

- `PdfExpandableMenu.kt` 中仅 2 处 `clickable(`，均为 `interactionSource=…, indication=null`（圆按钮 + 行）；
- 几何 host / 内容 wrapper / catcher / 父级 AnimatedVisibility / 父级 Box：无 clickable、无 indication；
- 无 combinedClickable；无默认 ripple 挂在任何会改变尺寸的节点上。

## 4. 修改文件

| 文件 | 变更 |
|---|---|
| `app/src/main/java/com/markleaf/app/ui/pdf/PdfExpandableMenu.kt` | 几何 host 移除 clickable/semantics；新增 MoreCircleButton（独立 interaction source + 克制 pressed）；内容 wrapper 增加 pointerInput 空白 sink；行改 indication=null + 行内高亮；图标节点首帧保持；INTERACTION AUDIT 注释 |
| `app/src/main/java/com/markleaf/app/ui/pdf/PdfViewerScreen.kt` | 无变更（catcher 已是 pointerInput/detectTapGestures） |

## 5. 构建验证（本机）

- `:app:testDebugUnitTest` / `:app:lintDebug` / `:app:assembleDebug` / `:app:assembleQa` / `:app:assembleRelease` 全 PASS。
- QA：`.external_outbox/markleaf-qa-latest.apk` SHA-256 `73d2bced9e4d01fa26d4ce2cf7051d391f6e5a0c26faaabd6af984c97f191443`
- Release：`.external_outbox/markleaf-release-latest.apk` SHA-256 `e068457eaa4c5a250f6f33ec15f0f63a170ca7fcf84932aace30eb05b5e233c4`
  → 已推送 `/storage/emulated/0/Download/markleaf-release-latest.apk`（sha256 校验 OK）。

## 6. 设备验收（PENDING — ADB 离线，需关闭系统 Show taps）

1. 点击三点展开：逐帧确认三点圆只有局部克制 pressed feedback，无随卡扩张的 ripple。
2. 展开过程中及最终状态：卡片中央/空白区无任何巨大灰圆。
3. 点击「阅读方式」等条目：仅该行产生局部高亮。
4. 点击菜单空白区域：无 ripple、菜单不关闭。
5. 点击菜单外部：只关闭菜单（morph 收起），无可见点击波纹。
6. 回归：R14 四状态 top/right 恒定（`adb logcat -s MORE_SURFACE`）；R13 两档模式/左右翻页居中不回退。
