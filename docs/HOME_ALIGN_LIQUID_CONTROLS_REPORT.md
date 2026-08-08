# 首页对齐收口 + 浮动控件统一为 PDF 阅读页同款 Liquid Glass

## 范围

首页信息架构不动（白底 + 浅灰实体卡 + 裸三点 + 右下 FAB）。本轮：
① 统一整页 24dp 横向内容轨道；② FAB 恢复圆角正方形并改用 **PDF 阅读页同款
Liquid Glass**（共享实现 + Control token，零新材质）；③ 三点弹出菜单同源
liquid glass；④ 文件卡保持实体不液态化。未改 Reader。

## 1) 统一内容轨道（对齐修复）

- **PageHorizontal 16 → 24dp**：品牌区三行文字（墨阅 MarkLeaf / 副标题 /
  最近文件）、section title、列表卡片 surface 全部落在同一 vertical rail——
  LazyColumn `contentPadding(start/end = PageHorizontal)` 同时作用于 header item
  与卡片，左边起线 = 右边收口 = 24dp。
- **空态修复**：空态是普通 Column（无 LazyColumn padding），之前品牌文字贴边
  （x=0）；现在空态 header 也加 `padding(horizontal = PageHorizontal)`，两种状态
  同轨。
- 卡片内部结构未动：标题/类型/meta 起线（40dp = 24 轨 + 16 卡内边距）全卡一致；
  ··· 按钮右缘 = 卡右缘 -16dp，全卡一致；弹出菜单右缘 = ··· 右缘（不越出卡片
  收口）。上下间距仅保留上一轮数值（HeaderToFirstCardGap=2、CardGap=8 等）。

## 2) FAB → 圆角正方形 + PDF 阅读页同款 Liquid Glass

- **56dp rounded square、圆角 20dp、+ 图标 26dp**（`onSurface` 深色，浅色主题下
  即黑色加号）；位置不变：右 20dp、底部 safe inset +20dp；列表 bottom padding
  nav+96dp，末卡完整滚到 FAB 上方。
- 材质 = **共享 `markLeafLiquidSurface` + `LiquidSurfaceLevel.Control`**（正是
  PDF 阅读页 chrome 的 level：`readerChromeSurface(Chrome) → Control`）：
  surface alpha 0.50、blur 14dp、lens 14/28、soft shadow 8dp@α0.12、
  edge highlight 0.5dp/α0.55、按压态 = 共享 `pressed` 增强（coverage +0.06 /
  highlight +0.18）+ 0.96 微缩。**未新造任何首页专用材质**；FAB 在 Scaffold 槽位
  （内容 `layerBackdrop` 之外）真实采样首页内容。

## 3) 三点弹出菜单 → 同源 Liquid Glass（布局保持小矩形）

- 仍为 140×48dp 单行小矩形（min 140 / max 160、行高 48、行 padding 16、
  icon 22、icon-text gap 12），锚定 ···、右缘对齐向左展开（offset x = -92dp）。
- 材质 = 共享 `markLeafLiquidSurface(Control, sampleBackdrop = false)`（Popup
  跨窗口无法采样，走同一 token 的软板路径：白 α0.50 + Control shadow 8dp），
  并补画 **Control 同值边光**（0.5dp / α0.55 白 border）= PDF chrome 的 soft
  border。圆角 = PDF chrome 菜单行角 **10dp**（ReaderMoreHost 行高亮
  `RoundedCornerShape(10.dp)`），保持矩形不 pill。
- 内容朴素：单行「删除记录」+ 22dp 删除 icon，按压轻微变淡。

## 4) 文件卡不液态化（防止首页变乱）

- 卡片保持 `surfaceContainerLow` 实体 + 24→20dp 圆角 + 细边框 + 3dp 进度条，
  无 blur/refraction/backdrop。Liquid 只出现在 FAB 与 ··· 菜单两个浮动交互层。

## 5) 保留

单一 LazyColumn（header item 随内容滚出）、`key = { doc -> doc.id }`、乐观删除
回流、无 `animateItem`、无固定 header/nestedScroll/裁切。**Reader 零改动**：
`MarkLeafLiquidSurface.kt`、`ReaderChromeMaterial.kt`、PdfViewerScreen、
backdrop:2.0.0 依赖均未触碰（首页只是重新调用共享入口）。改动文件：
`HomeScreen.kt`、`HomeDesignTokens.kt`、两个测试。

## 验证

- **automated_verified**：`HomeLayoutContractTest` 9 项（24dp 轨道断言含空态、
  liquid 仅浮动控件负向断言卡片不液态、菜单右对齐/10dp 角/0.5dp-α0.55 边光
  断言、既有密度门禁）、`HomeListUpdateTest` 6 项、`ReaderChromeMaterialTest`
  7 项（home 断言更新为「复用共享 liquid surface」）全 PASS；全量 107 suite 全绿。
- **build**：assembleRelease PASS → `.external_outbox/markleaf-release-latest.apk`
  （sha256 c1607650…68cd13）→ 已复制到 `/storage/emulated/0/Download/`。
- **device_verified**：PENDING — adb 两设备 offline。安装后按以下清单做整页
  视觉校准：1) 顶部三行文字与第一张卡左右绝对齐（24dp 轨）；2) 所有卡片标题
  起线一致；3) 所有三点右缘一致；4) FAB 为圆角正方形且材质与 PDF 阅读页一致；
  5) 首页整体未因 liquid 回潮变乱（卡片仍实体、玻璃只在 FAB/菜单）。
