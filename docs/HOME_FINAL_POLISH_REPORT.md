# 首页最终 polish：section 连续性 + 卡片 -5~8% + 控件统一（设计冻结）

## 范围

首页视觉方向已确认（白页 + 浅灰实体卡 + 裸三点 + 黑色 FAB），**设计冻结**。
本轮仅细调：①「最近文件」→ 首卡 gap 收紧；② 卡片整体再压缩 5–8%；
③ 三点统一性确认；④ FAB 收敛 56dp。未重新设计、未引入 Liquid、未改 Reader。

## 1) 「最近文件」→ 第一张卡 gap 收紧

- 之前：LazyColumn 全局 `Arrangement.spacedBy(CardGap = 8dp)`，标题→首卡 = 8dp。
- 现在：改为**逐项显式间距**——header item 自带 `padding(bottom = HeaderToFirstCardGap = 2dp)`，
  卡片之间仍为 `CardGap = 8dp`。
- 收紧量 = 8 − 2 = **6dp**（要求 6–10 ✓），section 与内容连续；
  「墨阅 MarkLeaf / 本地 Markdown / PDF 阅读器」品牌排版零改动。

## 2) 卡片整体再压缩 -5.4%（93 → 88dp，单行标题）

| 项 | 前 | 后 |
|----|----|----|
| vertical padding | top 12 / bottom 10 | **top 11 / bottom 9**（共 -2dp，要求「约 2dp」✓） |
| 标题 lineHeight | 22sp | **21sp**（字号保持 18sp，未降标题字号 ✓） |
| 标题→类型 | 6dp | **5dp** |
| metadata→progress | 8dp | **7dp** |
| 类型→meta / radius / gap / progress | 4 / 20 / 8 / 3dp | 不变（radius 18–20 ✓、gap 8–10 ✓） |

- 压缩幅度 = (93−88)/93 = **5.4%**（要求 5–8% ✓），`FileTitleSize` 保持 18sp。
- 标题 `maxLines=2` + ellipsis 不变，一行/两行标题自然决定条目高度，**不强制等高**。

## 3) 三点 overflow（统一性确认）

- 单一声明式 `OverflowMenuButton`：48dp invisible touch（44–48 ✓）、
  22dp 字形（20–22 ✓）、`onSurfaceVariant`(α0.75)，无圆形/Liquid/shadow/border。
- 所有条目共用同一 composable + token → icon size、trailing inset（卡内 16dp +
  触控居中偏移）、视觉轴完全一致，结构上不可能出现条间差异。

## 4) FAB 收敛

- 尺寸 58 → **56dp**（要求「约 56」✓）、radius 20dp（18–20 ✓）、白色 plus 26dp。
- right / bottom inset 统一：均为 **20dp**（Scaffold 16dp + `FabEdgePaddingExtra` 4dp；
  bottom = navigation/gesture safe inset + 20dp）。
- 列表 bottom content padding = nav + 96dp > 56 + 20 + 余量，
  最后一个文件可完整滚到 FAB 上方 ✓。

## 5) 保留（未回滚，未改）

单一 LazyColumn（header item 随内容滚出）、`key = { doc -> doc.id }`、
乐观删除回流、无 `animateItem`、无固定 header/nestedScroll/顶部裁切、
首页无 Liquid、`wic.show(all)`。Reader 与共享 `MarkLeafLiquidSurface.kt`
零改动。改动文件仅：`HomeScreen.kt`、`HomeDesignTokens.kt`、
`HomeLayoutContractTest.kt`。

## 验证

- **implemented / automated_verified**：`HomeLayoutContractTest` 9 项（新增：
  gap 收紧数学断言 8−HeaderToFirstCardGap ∈ 6..10；**密度门禁测试**——由 token
  计算单行卡高 = 88dp，压缩率 ∈ 5–8%，且标题字号断言 = 18sp；无 spacedBy 断言）、
  `HomeListUpdateTest` 6 项、`ReaderChromeMaterialTest` 7 项全 PASS；
  全量 107 suite 全绿。
- **build**：assembleRelease PASS → `.external_outbox/markleaf-release-latest.apk`
  （sha256 f24898b7…c01e2567）→ 已复制到 `/storage/emulated/0/Download/`。
- **device_verified**：PENDING — adb 两设备 offline。安装后仅做实机视觉验证 +
  快速滚动验证（默认首页 / 删除菜单 / 滚动中 / 滚到底部 FAB + 连续上下滚动录像），
  验收：卡片明显更紧凑、三点统一不抢视觉、popup 小、FAB 单一强焦点、
  无滚动裁切/空槽/跳位。**此后不再开启新的首页设计迭代。**
