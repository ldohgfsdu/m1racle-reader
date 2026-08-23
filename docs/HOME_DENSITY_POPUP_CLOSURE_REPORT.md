# 首页密度 + popup 最终收口（不重构首页、不改 Reader）

## 范围

首页已锁定「普通文件卡 + 裸三点 + 黑色 FAB」方向（无任何 Liquid 首页实验）。
本轮只做两件事：① RecentFileCard 整体高度压缩约 15–20%，恢复一屏快速扫读；
② 删除记录 popup 收敛为右对齐的 utility popup。未重构首页结构、未改 Reader。

## 1) 卡片密度（目标：一屏明显更多文件，接近旧版高密度）

| 项 | 前 | 后 | 说明 |
|----|----|----|------|
| 卡片高度（单行标题） | ~109dp | **~93dp**（-14.7%） | 主要来自去掉 ··· 40dp 触控对首行的撑高 |
| card gap | 12dp | **8dp** | 一屏 5 张卡额外省 16dp（带 8–10 ✓） |
| radius | 24dp | **20dp** | 带 18–20 ✓ |
| 内 padding | 16h / 8v | **16h / top 12 / bottom 10** | 带：top 12–14 ✓ bottom 10–12 ✓ |
| 标题 | 20sp/24sp lh、1 行 | **18sp/22sp lh、maxLines=2** | 带：~18sp/~22sp ✓ |
| 标题→类型 / 类型→meta / meta→进度 | 6/4/8 | 6/4/8 | ✓ |
| 类型 / metadata | 12sp（lh16） | 12sp（**lh14**） | 收紧行高各 -2dp |
| progress | 3dp | 3dp | 带 3–4 ✓ |

**关键结构变化**：卡片从 `Row{title;icon}`（icon 触控 40dp 把首行撑到 40dp 高）
改为 **Box 叠加布局**——文字块（标题/类型/meta/进度）`end padding = 触控宽 48dp`，
裸三点以 `align(TopEnd)` 浮在右上。48dp 触控区完全不参与行高计算，
卡片高度只由文字行决定。

每屏信息密度：单卡 -14.7%、卡距 -33%，5 张卡/屏 ≈ **+16% 文件数**，接近旧版。

## 2) 三点 overflow（保持裸三点，禁止圆形/玻璃/阴影/border）

- 触控 target **48dp**（带 44–48 ✓，全 invisible，无任何绘制）。
- 可见字形 **22dp**（48 − 2×13dp pad；带 20–22 ✓），`onSurfaceVariant`(α0.75)
  二次色，纯 `Icon(MoreVert)` + clickable，无 surface/shadow/border。
- 位置：卡片右上、与标题行对齐；因叠加布局，大触控区不撑高卡片。

## 3) FAB（保持黑色实体高对比，不恢复 Liquid）

- 尺寸 **58dp**（带 56–60 ✓，收敛自 62）、radius **20dp**（带 18–20 ✓）、
  白色 plus **26dp** 居中。
- `containerColor = scheme.primary`（浅色 = 近黑 #171717）+ 白色图标，
  elevation 2dp（普通 Material）。
- right **20dp**（带 16–20 ✓）、bottom = nav/gesture safe inset + **20dp**（带 16–20 ✓）。
- 列表 bottom padding = nav + 96dp（> 58+20+余量），末卡可完整滚到 FAB 上方。

## 4) 删除记录 popup（收敛为 utility popup）

| 项 | 值 | 带 |
|----|----|----|
| 宽度 | min 140 / max 160dp（实际 ~140） | 140–160 ✓ |
| 单行高度 | 48dp（M3 item minHeight） | 48–52 ✓ |
| 行水平 padding | M3 12dp + 4dp = **16dp** | 16 ✓ |
| icon | 22dp | 20–22 ✓ |
| icon-text gap | 12dp（M3 内部固定） | 12–14 ✓ |
| radius | 12dp | 12–16 ✓ |
| surface | `surfaceContainer` 浅色 + shadow 2dp / tonal 0 | 极轻 elevation ✓ |

**定位**：以 ··· 48dp 触控 Box 为 anchor，`offset = DpOffset(x = -92dp)`——
菜单右缘 = 三点右缘（`MenuAnchorOverhang = MenuMinWidth(140) − OverflowTouch(48)`），
向左展开；宽仅 140dp、高 48dp 单行，只遮挡卡片右上小片区域，不遮大片标题。

## 5) 保持不变（未回滚）

- 单一 LazyColumn：品牌头/副标题/「最近文件」= `item(key="home_brand_header")`
  与卡片同 flow 自然滚出屏幕；无固定 header、无 nestedScroll、无顶部裁切。
- `key = { doc -> doc.id }` 稳定身份 + 乐观删除（`pendingDeleteIds` +
  `HomeListUpdate.visibleDocuments`）+ 无 `animateItem` —— 无空槽/半卡残留。
- 卡片 tonal `surfaceContainerLow`、无 backdrop；首页无任何 Liquid 调用。
- **Reader 零改动**：`MarkLeafLiquidSurface.kt`、`ReaderChromeMaterial.kt`、
  PdfViewerScreen、backdrop:2.0.0 依赖全部未触碰。
- 未使用 git checkout/reset——仅改 `HomeScreen.kt`、`HomeDesignTokens.kt`、
  `HomeLayoutContractTest.kt`。

## 验证

- **implemented**：如上。
- **automated_verified**：`HomeLayoutContractTest` 8 项（新密度带：radius 18–20、
  gap 8–10、padding top 12–14/bottom 10–12、title 17–19/21–23、touch 44–48、
  可见字形 20–22、FAB 56–60/18–20、menu 140–160/12–16/icon 20–22、右对齐 offset
  数学断言；叠加布局不撑高断言；menu utility 断言）、`HomeListUpdateTest` 6 项、
  `ReaderChromeMaterialTest` 7 项全 PASS；全量 107 suite 全绿。
- **build**：assembleRelease PASS → `.external_outbox/markleaf-release-latest.apk`
  （sha256 e2400987…71ba0d）→ 已复制到设备 `/storage/emulated/0/Download/`。
- **device_verified**：PENDING — `adb devices` 两设备 offline。安装后请补：
  ① 默认首页 ② 打开删除菜单 ③ 快速滚动中 ④ 滚到底部 FAB；
  并录制一次连续快速上下滚动。验收重点：卡片明显更紧凑、裸三点不抢视觉、
  popup 足够小、FAB 单一强焦点、无滚动裁切/空槽/跳位。
