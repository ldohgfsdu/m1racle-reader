# 首页视觉收口：Quiet/Primary/Popover Liquid 层级 + 单 LazyColumn 滚动修复

## 范围

最后一轮首页视觉收口。**不**重构信息架构、**不**把文件卡改回 Liquid Glass：
内容卡 = 低噪声 tonal/solid surface；浮动操作 = Liquid。本轮收敛三点按钮噪声、
优化 FAB、收紧卡片排版、修复滚动时首卡被「最近文件」区域硬裁切的 bug。

## 1) 文件卡（content surface，无 backdrop）

| 项 | 值 | 要求带 |
|----|----|--------|
| 圆角 | 24dp | 22–24 ✓ |
| 页面左右 margin | 16dp | 16 ✓ |
| 卡间距 | 12dp | 12 ✓ |
| 内 padding | 水平 16dp / 垂直 14dp | 16 / 14–16 ✓ |
| 标题 | 20sp Semibold，≤2 行 | 19–20 ✓ |
| 类型 | 14sp Medium，secondary（α0.75） | 14–15 ✓ |
| metadata | 14sp secondary（α0.70） | 14 ✓ |
| 标题→类型 | 8dp | 8 ✓ |
| 类型→metadata | 6dp | 6 ✓ |
| metadata→进度 | 12dp | 10–12 ✓ |

卡片仍为 `surfaceContainerLow` + `outlineVariant`(α0.45) 细边框 + 1dp tonal
elevation，无 blur/refraction/backdrop。旧 token（CardGapTight/Mid/Loose、
SectionGap、HeaderBottomPad）已删除，节奏改为显式语义 token
（TitleToTypeGap / TypeToMetaGap / MetaToProgressGap）。

## 2) Quiet Liquid 三点按钮（不再是一排白色玻璃珠）

- 触控 target **40dp**，实际可见玻璃 core **36dp** 圆形（core 是 target 内
  独立绘制层，静止时玻璃覆盖面积大幅缩小）。
- 完全收进 card bounds：top inset 14dp、trailing 16dp（即 card 内边距），
  无挂边视觉。
- 默认态极静：surface coverage 0.36（原 0.50），blur 10dp、lens 8/16
  （原 14/28），shadow **3dp**（2–4 带内），highlight 0.25dp/α0.22
  （原 0.5dp/α0.55），icon 18dp。
- pressed / selected（popover 展开）时经共享 `markLeafLiquidSurface(pressed=)`
  增强：surface α +0.06、highlight α +0.18，并微缩 0.94。
- 远看第一眼是标题，不是玻璃圆。仍 `sampleBackdrop=false`（按钮在 home
  `layerBackdrop` 内容树内，采样会 circular render 崩溃，保持不变）。

## 3) Primary Liquid FAB

- 60dp、圆角 24dp、+ icon 28dp。
- 右缘 margin **20dp**（Scaffold FAB 槽自带 16dp + 4dp extra）；
  底部 = navigation safe area + **20dp**。
- 材质 PrimaryControl：coverage 0.44（低于原 Control 0.50 → 纯白覆盖率下降；
  高于 Quiet 0.36 → 比三点高一级），blur 14dp、lens 12/24、shadow 6dp、
  highlight 0.5dp/α0.45，`depthEffect=true`（明显但克制的折射/高光）。
- FAB 仍在 Scaffold 槽位（内容 `layerBackdrop` 之外），保留真实玻璃采样。
- 列表 bottom content padding = nav + 96dp ≥ 60dp FAB + 20dp gap + 余量，
  最后一张卡可完整滚到 FAB 上方。

## 4) Popover（Liquid Glass，比 Quiet 高一级，非纯白实体卡）

- Popover 级：coverage **0.56**（高于 Quiet 0.36 / Primary 0.44），
  blur 18dp、lens 16/32、shadow 10dp、highlight 0.5dp/α0.50。
- 仍走 `sampleBackdrop=false` 软板路径（Popup 独立窗口无法跨窗口采样），
  同一 alpha/shadow/圆角语言；宽 152–200dp、项高 48dp。

## 5) 顶部滚动裁切 bug — 根因与修复

**根因**：首页是「固定 header + 独立 LazyColumn」的双层结构 —
`Column { HomeHeader(品牌+副标题+最近文件，固定) ; LazyColumn(weight 1f) }`。
LazyColumn 的上边界正好落在「最近文件」标题下方；滚动时第一张文件卡顶部被
LazyColumn 矩形裁剪边界硬切，视觉上表现为卡片钻到标题区下面被矩形裁掉。
（旧的 `HomeHeaderScrollState` 折叠 header 方案已完全未使用，属废弃代码，
已连测试一起删除。）

**修复**：收敛为**一个 LazyColumn**——品牌标题「墨阅 MarkLeaf」、副标题、
「最近文件」作为 `item(key = "home_brand_header")` 排在列表第一项，文件卡为
后续 items。滚动时整个 header 与卡片一起自然向上离开屏幕，不存在任何固定层
或裁剪边界。无 `stickyHeader`、无 nestedScroll、无双滚动路径。

## 6) 删除 / 列表更新

保持不变并复核：`key = { doc -> doc.id }` 稳定身份 + 乐观删除
（`pendingDeleteIds` → `HomeListUpdate.visibleDocuments`）+ 无
`animateItem`/exit placeholder。任何时刻不出现顶部空槽、半张卡残留。

## 7) Liquid control levels — 统一 token 家族

三个首页控件不再各自硬编码材质，统一映射到 `LiquidSurfaceLevel` +
`liquidLevelTokens()` 单一定义：

| Token | QuietControl（三点） | PrimaryControl（FAB） | Popover（菜单） | Control（Reader，冻结） |
|-------|----------------------|----------------------|-----------------|-------------------------|
| surfaceAlpha | 0.36 | 0.44 | 0.56 | 0.50 |
| blur | 10dp | 14dp | 18dp | 14dp |
| lens height/amount | 8/16dp | 12/24dp | 16/32dp | 14/28dp |
| shadow | 3dp | 6dp | 10dp | 8dp |
| highlight width/α | 0.25dp / 0.22 | 0.5dp / 0.45 | 0.5dp / 0.50 | 0.5dp / 0.55 |
| depthEffect | false | true | true | false |
| 采样 | soft plate（树内） | 真实 backdrop | soft plate（跨窗口） | — |

共享 pressed 增强：surface α +0.06、highlight α +0.18。
Reader 的 Control/Parent/Foreground token 值**未改动**（r26.1 冻结），
`readerChromeSurface` 代码零改动。

## 验证

- **implemented**：如上 4 个文件 + 删除 HomeHeaderScrollState(.kt/test)。
- **automated_verified**：`HomeLayoutContractTest`（6 项，含 token 带、
  单 LazyColumn 无 sticky/nested 断言、命名 level 断言）、
  `HomeListUpdateTest`（6 项）、`ReaderChromeMaterialTest`（7 项）PASS；
  全量 `:app:testDebugUnitTest` 107 个 suite 全绿。
- **build**：assembleRelease PASS → `.external_outbox/markleaf-release-latest.apk`
  （sha256 bd7954c8…ce79ee0e）→ 已复制到设备 `/storage/emulated/0/Download/`。
- **device_verified**：PENDING — `adb devices` 两设备均 offline，无法截图。
  安装 APK 后请补 4 张实机截图：
  1. 首页默认态
  2. 滚动中品牌区正在离场（无裁切）
  3. 三点菜单展开态
  4. 滚到底部 FAB 不遮最后一项
