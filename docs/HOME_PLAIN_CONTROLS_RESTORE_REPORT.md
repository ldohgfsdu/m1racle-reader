# 首页恢复普通 Material：移除首页 Liquid Glass（定向回退，不碰 Reader）

## 目标

首页视觉回到「普通干净的文档列表 + 裸三点操作 + 黑色高对比 FAB」。
Liquid Glass 只保留在 Reader（PDF 浮动 chrome）。**定向恢复首页 UI 组件，
不使用 git reset/checkout**——数据层、列表稳定性修复、滚动结构全部保留。

## 1) 本轮改动文件（仅首页 UI 表现 + 测试）

| 文件 | 动作 |
|------|------|
| `ui/home/HomeScreen.kt` | 重写：移除全部 Liquid 调用，恢复普通 Material 控件 |
| `ui/home/HomeDesignTokens.kt` | 重写：普通 Material token（FAB/menu/overflow/紧凑卡片） |
| `ui/home/HomeLiquidControls.kt` | **删除**（LiquidFab / LiquidIconButton / LiquidPopover / LiquidMenuItem / HomeLiquidControlTokens 全部随文件移除） |
| `test/.../HomeLayoutContractTest.kt` | 重写为新契约（无 Liquid + 普通控件 + 稳定性不变） |
| `test/.../ReaderChromeMaterialTest.kt` | 仅更新其中 home 断言（home 不再消费共享 liquid surface）；Reader 断言零改动 |

**未修改（本轮零触碰）**：`ui/common/MarkLeafLiquidSurface.kt`、
`ui/pdf/ReaderChromeMaterial.kt`、`ui/pdf/PdfViewerScreen.kt`、全部 Reader
chrome 文件、`build.gradle.kts`（`io.github.kyant0:backdrop:2.0.0` 依赖原样）。

## 2) 移除的首页 Liquid 调用（逐项）

- `LiquidFab(...)`（PrimaryControl，真实 backdrop 采样）→ 普通 M3
  `FloatingActionButton`。
- `LiquidIconButton(...)`（QuietControl 圆形玻璃 core）→ 裸 `Icon(MoreVert)`
  + clickable，无任何 surface/shadow/highlight。
- `LiquidPopover(...)`（Popover 级玻璃菜单）→ M3 `DropdownMenu` 轻量菜单。
- `rememberLayerBackdrop() / layerBackdrop(rootBackdrop) /
  CompositionLocalProvider(LocalLiquidBackdropHost)` —— 首页整个 backdrop
  host 基础设施删除（首页不再有任何玻璃采样需求）。
- `HomeLiquidControls.kt` 文件整体删除。

共享渲染层 `markLeafLiquidSurface` / `LiquidSurfaceLevel`（含
QuietControl/PrimaryControl/Popover 枚举值，属共享 token 家族定义）**保留原样**
——Reader 的 `readerChromeSurface` 映射（Control/Parent/Foreground）与参数
（alpha/blur/lens/shadow/highlight 冻结值）完全未改。

## 3) 恢复的普通 Material 视觉

| 元素 | 实现 |
|------|------|
| 文件卡 | `surfaceContainerLow` + `outlineVariant`(α0.45) 细边框，24dp 圆角，tonalElevation 1dp，无 blur/refraction |
| 卡排版 | 紧凑 4 行：标题 20sp SemiBold ≤1 行 → 类型 12sp（6dp）→ `时间 · 大小 · 进度` 12sp（4dp）→ 3dp 细进度条（8dp）；内边距 16h/8v，页边距 16dp，卡距 12dp |
| 三点按钮 | 裸 `MoreVert` 图标：40dp 触控热区，视觉仅 24dp 字形，`onSurfaceVariant` 着色，与标题行顶对齐，无圆形 surface/高光/阴影 |
| 删除菜单 | M3 `DropdownMenu`：12dp 圆角、`surfaceContainer` 浅色、shadowElevation 2dp、tonalElevation 0、单行 44dp+ 项高、minWidth 148dp，锚定在 ··· 图标旁，紧凑不遮标题 |
| FAB | M3 `FloatingActionButton`：62dp、22dp 圆角、`containerColor = scheme.primary`（浅色主题 = 近黑 #171717）+ 白色 + 图标 28dp，elevation 2dp；右边距 20dp、底部 = 手势安全区 +20dp；列表 bottom padding nav+96dp，末卡可完整滚到 FAB 上方 |
| 顶部 | 墨阅 MarkLeaf / 副标题 / 最近文件 = 普通页面文本（无 plate、无 Liquid），作为 LazyColumn 第一项随内容滚动 |

## 4) 保留的修复（未回滚）

- **数据/删除回流**：乐观删除 `pendingDeleteIds` + `HomeListUpdate.visibleDocuments`
  纯函数过滤 + Room Flow 确认后清理 pending —— 不变。
- **列表稳定性**：`key = { doc -> doc.id }` 稳定身份、无 `animateItem`/exit
  placeholder —— 不变，删除后无顶部空槽、无半张卡残留。
- **滚动结构**：单一 LazyColumn，品牌头 `item(key = "home_brand_header")`
  与卡片同一 flow 自然滚动，无固定 header、无 nestedScroll、无顶部裁切 —— 不变。
- **系统栏**：首页强制 `wic.show(all)`，不继承 PDF immersive —— 不变。
- **HomeViewModel / formatMeta / 标题解析** 等逻辑 —— 零改动。

## 5) 验证

- **implemented**：如上文件级改动。
- **automated_verified**：`HomeLayoutContractTest` 7 项（含「home 无任何 Liquid
  调用」负向断言、token 带断言、单 LazyColumn/稳定 key/乐观删除断言）、
  `HomeListUpdateTest` 6 项、`ReaderChromeMaterialTest` 7 项（Reader 侧断言不变）
  全 PASS；全量 testDebugUnitTest 107 suite 全绿。
- **build**：assembleRelease PASS →
  `.external_outbox/markleaf-release-latest.apk`
  （sha256 78df3216…1d361d9c0eb）→ 已复制到
  `/storage/emulated/0/Download/markleaf-release-latest.apk`。
- **device_verified**：PENDING — `adb devices` 两设备均 offline。安装后请补 3 张
  实机截图：① 首页默认态 ② 打开删除菜单 ③ 滚到底部显示 FAB（末卡不被遮挡）。
