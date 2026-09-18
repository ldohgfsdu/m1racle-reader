# R36.0 Glass Ground-Truth Audit

日期：2026-09-16。本文件只描述本地工作树事实。未改生产代码、测试、依赖、参数或 UI。未进入 R36.1。

## 0. Ground truth

| 项 | 值 |
|---|---|
| Git 根 | `/root/code/MarkLeaf` |
| 分支 | `feat/risediary-inspired-dock` |
| HEAD | `5284f801fffa62904ce80101ca687cfd415ea8c6`（`docs: record dock icons drawn above glass shell`） |
| 远端 | **不是**本审计事实源。工作树相对 HEAD 有大量未提交改动；下列结论以**当前磁盘源码**为准。 |
| 任务书 | `/storage/emulated/0/r36.txt`（只读引用，未复制进仓库） |

审计范围：`GlassMaterial.kt`、`GlassBackdropTopology.kt`、`GlassInteraction.kt`、`GlassAlertDialog.kt`、`BlurComparisonPreview.kt`、`PdfViewerScreen.kt`、`PdfChromeVisibility.kt`、`ReaderMoreHost.kt`、`AnchoredNestedPopover.kt`、`ViewerScreen.kt`、`ReaderGradient.kt`、`HomeScreen.kt`、`GlassDock.kt`、`LibraryScreen.kt` / `BookmarksScreen.kt` / `HubSettingsScreen.kt`、`DocumentListRow.kt`、`NavGraph.kt`、`Theme.kt`、`M1racleSurface.kt`、`app/build.gradle.kts`、相关契约测试（只读）。

---

## 1. 依赖与能力

| 依赖 | 证据 | 能力含义 |
|---|---|---|
| `io.github.kyant0:backdrop:2.0.0` | `app/build.gradle.kts` L336–342 | 唯一第三方玻璃库。排除了库自带 Kotlin stdlib。 |
| Kyant shapes | **未**出现在 `dependencies {}` | `supportsGlassLens` 用类名字符串 `"com.kyant.shapes.RoundedRectangularShape"` 判断；工程未声明 shapes 包。 |
| Miuix | **未**出现在 Gradle | 无 Miuix 依赖。 |
| Compose BOM | `androidx.compose:compose-bom:2024.12.01` | UI / Material3。 |
| minSdk | 31 | `RenderEffect` 模糊在 API 上可用。 |
| Lens / AGSL | `Build.VERSION.SDK_INT >= TIRAMISU`（33）且 `supportsGlassLens(shape)` 且 refraction > 0 | 见 `GlassMaterial.kt` L498–555。当前配方 `lensHeight`/`lensAmount` 均为 `0.dp`，折射分支进不去。 |
| 系统 `Modifier.blur` | 生产 `ui/` **无调用** | 仅 Kyant `effects { blur(...) }`。 |
| 应用层 `RenderEffect.create*` | 生产 **无调用** | `GlassMaterial.kt` 注释提到 Kyant 内部 RenderEffect。Kyant 内部实现 **UNKNOWN**（未展开 AAR 源码）。 |
| HyperOS / `setMiBackgroundBlur*` / LSPosed | 生产 **无调用** | 未发现 vendor blur。 |
| `rememberInfiniteTransition` | 生产 **无调用** | 设置页预览用 `LaunchedEffect` + `animateScrollTo` 循环，仅 `BlurComparisonPreview`。 |

Fallback：工程 **没有** `GlassCapability` / FullOptical / BlurOnly / SolidFallback。唯一降级是 `sampleBackdrop == false || host == null` → `softGlassPlate`（半透明色板 + `dropShadow`，不采样）。RuntimeShader 失败时 Kyant 行为 **UNKNOWN**。

---

## 2. Glass 事实源

### 2.1 单一引擎，但不是唯一光学路径

`Modifier.glassMaterial` 注释写明 “The only rendering entry for glass surfaces.”（`GlassMaterial.kt` L343–349）。角色配方只在 `GlassMaterialEngine.recipes`。

同时存在**不经** `glassMaterial` 的光学/表面路径：

| 路径 | 位置 | 与引擎关系 |
|---|---|---|
| `Modifier.readerGradient` | `ReaderGradient.kt` | `drawPlainBackdrop` + **硬编码** `blur(16.dp)` + `tint.copy(alpha = 0.62f)` + 白色 `DstIn` 渐变。不读 `GlassMaterialEngine`。 |
| Dock L3 实色条 | `GlassDock.kt` L154–162 | `M1racleReaderTheme.surface.overlay` + `elevation.overlay`（2.dp）。**不**调用 `glassMaterial`，**不**使用 `GlassMaterialRole.Dock`。 |
| 首页三点菜单 | `DocumentListRow.kt` L170–177 | `DropdownMenu` + `surface.overlay`，无 `glassMaterial`。 |
| Markdown 标题 Popup | `ViewerScreen.kt` L1272–1289 | `Surface` + `colorScheme.surface` + 1.dp border，无玻璃。 |
| 设置预览变体 1/2 | `BlurComparisonPreview.kt` L148–166 | 直接 `drawPlainBackdrop` / `drawBackdrop`，硬编码 blur/lens/alpha。变体 0 才走 `glassMaterial(Control, sourceBackdrop=…)`。 |

`M1racleGlassPolicy`（`M1racleSurface.kt`）只允许 `Toolbar | Dock | Menu`，且 **没有**接到 `glassMaterial()`。PDF 的 Control/Capsule 不在该 policy 集合里，但仍调用 `glassMaterial`。Policy 与 Engine 是两套名单。

`GlassMaterialRole.Dock` / `DockSelection` 在引擎里有配方，**生产 UI 零 `glassMaterial` 调用**（`GlassBackdropTopologyContractTest` 钉死 `GlassDock.kt` 不含这些 role）。

结论：**配方表是单一的；屏幕上的“玻璃/磨砂/浮层”不是单一路径。**

### 2.2 Role 解析表（idle，`pressed=false`）

数值来自 `GlassMaterialEngine.resolve` + `recipes` 当前字面量。Dialog 使用独立 `GlassRecipe()`，未复制 `regularFrost` 的 vibrancy/tint。

Light（`dark=false`）：

| Role | 生产调用 | blur | surface α | tint | lens H/A | vibrancy | highlight W/α | shadow r / α | scale | backdropLayer | export menu |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Dock | **无 glassMaterial** | 18 | 0.62 | `#F5F5F5` | 0/0 | true | 0 / 0 | 6 / 0.14 | 1（pressedScale=1） | Root | no |
| DockSelection | **无 glassMaterial** | 0 | 0.16 | `#8F99A3` | 0/0 | false（默认） | 0 / 0 | 3 / **0.10（data class 默认）** | 1 | Root | no |
| Control | PDF 返回圆、More 圆 | 18 | **0.86** | `#F5F5F5` | 0/0 | true | 0 / 0 | 6 / 0.14 | 1 | Root | no |
| Capsule | PDF 页码胶囊 | 18 | **0.86** | `#F5F5F5` | 0/0 | true | 0 / 0 | 6 / 0.14 | 1 | Root | no |
| Menu | PDF More **展开卡**（同一 role 也用于可见性） | 18 | **0.86** | `#F5F5F5` | 0/0 | true | 0 / 0 | 6 / 0.14 | 1 | Root | **yes** |
| ExpandedMenu | Markdown `Popup` 更多菜单 | 18 | **0.90** | `#F5F5F5` | 0/0 | true | 0 / 0 | 6 / 0.14 | 1 | Root | **yes** |
| SettingsCard | `AnchoredNestedPopover` | 18 | **0.90** | `#F5F5F5` | 0/0 | true | 0 / 0 | 6 / 0.14 | 1 | **RootAndMenu** | no |
| Dialog | `GlassAlertDialog` | 18 | 0.97 | 默认 `#FAFAFA` | 0/0 | **false（未设）** | 0.5 / 0.30 | 18 / **0.10（默认）** | 1 | Root | no |

Dark（`dark=true`）：Dock 0.58 / Control·Capsule·Menu 0.80 / ExpandedMenu·SettingsCard 0.86 / Dialog 0.97；shadow α Dock 族 0.28，DockSelection **0.46（默认 darkShadowAlpha）**，Dialog **0.46**。dark tint：Dialog 与 DockSelection 为 `#121212`，其余 `Color.Black`。Dialog highlight α 0.38。

Pressed：`surfaceAlpha += 0.04`（DockSelection 例外：`pressedSurfaceAlpha = 0.03`）。`scale = 0.965`（Dock/DockSelection = 1）。Highlight 仅 Dialog 在 pressed 时 +0.10。`innerShadow` 仅 DockSelection pressed 为 8.dp。

`GlassRenderState`：Menu / ExpandedMenu / SettingsCard / Dialog **恒为 INTERACTION**；其余 idle，除非 `pressed` 或 `interaction=true`（胶囊展开传 `interaction = expanded`）。

环境：只有 `LocalReaderDarkTheme`（跟随 `M1racleReaderTheme(darkTheme)`）。**没有** `ReaderLight` 覆盖。PDF 控件与系统深浅色绑定。`AnchoredNestedPopover` 分割线 α 使用 `isSystemInDarkTheme()`，与 `LocalReaderDarkTheme` 可能不一致（L240–243）。

前景 α：**引擎没有 foreground-alpha 字段。** 子级 Text/Icon 是容器的子节点，不乘 `surfaceColor.alpha`。次级文案用 `contentMutedAlpha`（light 0.58 / dark 0.75）。整层一起透明只发生在外层 `graphicsLayer { alpha }` / `AnimatedVisibility` fade（见 §6）。

---

## 3. Backdrop 拓扑（真实 DAG）

捕获边界：全仓库生产代码里，唯一 `.layerBackdrop(` 在 `GlassBackdropSource`（`GlassBackdropTopology.kt`）。该 composable 在挂 capture 前 `LocalGlassBackdropHost provides null`，使**被捕获子树不能再 sample 同一 host**。契约测试钉死这一点。

### 3.1 HOME

```
NavGraph Box
├── NavHost / HomeScreen
│   └── LocalGlassBackdropHost(homeRoot, homeMenuExport)   // 已提供
│       └── GlassBackdropSource(homeRoot)                  // host 在内部被置 null
│           └── HomeContent（实色顶栏、列表、英雄卡）
│       └── SnackbarHost（兄弟，未采样）
└── GlassDock（顶层兄弟，L3 实色 + 2.dp elevation，不 sample）
```

Home 捕获了 `homeRoot`，但 Home 树内 **没有** `glassMaterial`。该 backdrop 当前是**无消费者**的捕获。Dock 不读它。

### 3.2 Hub Library / Bookmarks / Settings

```
NavGraph Box
├── NavHost / *Screen
│   └── HubGlassShell
│       └── LocalGlassBackdropHost(hubRoot, hubMenuExport)
│           └── GlassBackdropSource(hubRoot)
│               └── 页内容
│                   └── Settings 另含 BlurComparisonPreview（见 3.5）
└── GlassDock（仍是 NavGraph 兄弟，不 sample hubRoot）
```

Hub 捕获同样 **无 glassMaterial 消费者**（除设置页预览的**另一套** backdrop）。

### 3.3 PDF Reader

```
PdfChrome
└── LocalGlassBackdropHost(rootBackdrop, parentExportBackdrop)
    └── Box(fillMaxSize, background)
        ├── GlassBackdropSource(rootBackdrop)     // 仅 content()
        │   └── PDF 页（含页内 graphicsLayer 平移/裁剪、标注层若在 content 内）
        └── Overlay siblings（不在 capture 内）
            ├── PdfChromeVisibility(Control) → FloatingChromeButton.glassMaterial(Control)
            ├── PdfChromeVisibility(Menu) → ReaderMoreHost
            │     ├── MoreCircleButton.glassMaterial(Control)
            │     ├── MAIN card.glassMaterial(Menu)  → drawBackdrop 采样 root，export → menuExport
            │     └── AnchoredNestedPopover.glassMaterial(SettingsCard) → Combined(root, menuExport)
            └── PdfChromeVisibility(Capsule) → PdfInfoCapsule.glassMaterial(Capsule)
```

`onDraw`：`drawRect(scheme.background); drawContent()`（`PdfViewerScreen.kt` L1472–1479）。采样含页面底色 + 正文绘制，**设计上不含** overlay chrome。

单向：

```
PDF document
    ↓ layerBackdrop(rootBackdrop)
Glass Control / Capsule / Menu
    ↓ Menu export → menuExport
SettingsCard ← Combined(root, menuExport)
```

**不是：** Glass → 写回 rootBackdrop。

### 3.4 Markdown Reader

```
Scaffold content Box
├── GlassBackdropSource(backdrop) { markdown body }
└── ReaderTopBarOverlay（兄弟）
    ├── Modifier.readerGradient(backdrop)   // drawPlainBackdrop + 16dp blur + 0.62 色罩
    └── 控件行 graphicsLayer { alpha = 1 - collapseFraction }
```

顶栏 **不**走 `glassMaterial`。更多菜单是 `Popup` + `glassMaterial(ExpandedMenu, sampleBackdrop=false)`（软板，不采这篇 backdrop）。

### 3.5 Settings 预览（局部）

`BlurComparisonPreview`：自己的 `rememberLayerBackdrop()` + `GlassBackdropSource`；采样层是兄弟，`LocalGlassBackdropHost(backdrop, backdrop)`（root 与 menuExport **同一实例**）。变体 0 用 `sourceBackdrop=backdrop` 的 Control。预览又嵌在 Hub `GlassBackdropSource` 里：外层 hub 捕获会把预览玻璃画进去，但 Dock 不采样 hub。**预览内部** source/sample 是兄弟，符合局部 DAG。`host(root=menuExport=同一对象)` 若将来有 Combined 消费者，是否自指 **UNKNOWN**（当前 Control 不 export）。

### 3.6 Dialog / 非 Reader 菜单

| 表面 | Window | 采样 |
|---|---|---|
| `GlassAlertDialog` | `BasicAlertDialog` → **独立 Dialog window** | `sampleBackdrop=false` → `softGlassPlate` |
| Markdown 更多 | `Popup` | `sampleBackdrop=false` |
| Markdown 标题展开 | `Popup` | 实色 Surface，无玻璃 |
| Home `DropdownMenu` | Popup window | 实色 overlay，无玻璃 |
| PDF More / 子面板 | **同一 Compose 树**，注释写明无 Popup（`ReaderMoreHost.kt` L562–563） | 采样 Reader `root` / Combined |

---

## 4. 必答项

### 4.1 是否存在 backdrop cycle / self-sampling？

**生产 Reader / Home / Dock：代码结构上未发现 cycle。**

证据：

- 捕获子树 `host=null`，无法 `drawBackdrop(host.root)`。
- PDF/Markdown/Home 的 `glassMaterial` 都在 `GlassBackdropSource` **之后的兄弟**（PDF 由 `GlassBackdropTopologyContractTest` 断言 `content()` 内无 `glassMaterial`）。
- Kyant `shadow = null`，外影走 Compose `dropShadow`，避免库内离屏 Clear 蒙版。

**不能在未读 Kyant AAR 的情况下证明** `exportedBackdrop` / `rememberCombinedBackdrop` 内部绝不会把 consumer 画进自己的 export。结构意图是 DAG。标 **UNKNOWN（库内部）**。

`BlurComparisonPreview` 把 `GlassBackdropHost(backdrop, backdrop)` 同一实例赋给 root 与 menuExport：当前无 Combined 消费者，**未构成已观测 cycle**。

### 4.2 Reader document backdrop 是否只采正文？

**PDF：只采 `GlassBackdropSource` 的 `content()`。** `content()` 是 PdfChrome 调用方传入的文档 UI（页面、视口 `graphicsLayer`、若放在 content 里的标注）。**不包含** Back / More / Capsule / ReaderMoreHost。

是否包含页缝 surround、注释高亮：取决于 `content()` 实现。`onDraw` 先画 `scheme.background` 再 `drawContent()`，页缝颜色会进采样。Chrome overlay 不进。

**Markdown：只采 body `content()`。** 顶栏是兄弟。

### 4.3 Overlay 是否可能重新进入采样源？

**按当前树：不会。** Overlay 是 source 的后续兄弟，且 source 内 host 为 null。

PDF More 展开后 `fillMaxSize` 仍在 overlay 侧。菜单玻璃采样 root（文档），并把自身 export 到 `menuExport` 给 SettingsCard，**不写回 root**。

### 4.4 Popup/Menu 是否跨 Window？

| 表面 | 跨 Window |
|---|---|
| PDF More / AnchoredNestedPopover | **否**（同树 overlay，坐标 `boundsInRoot`） |
| GlassAlertDialog | **是**（Dialog） |
| Markdown 更多 Popup | **是**；因此 `sampleBackdrop=false` |
| Home DropdownMenu | **是**；不用 backdrop |

跨 Window 的玻璃走软板，**不会**用错误 Window 坐标去采 Reader 的 LayerBackdrop。PDF 菜单不依赖 Popup，坐标错位类问题对 PDF More **不是**当前架构主路径。

### 4.5 GlassMaterial 是否仍是单一事实源？

**角色配方：是。调用入口：基本是。屏幕光学：否。**

- 所有 `GlassMaterialRole` 只在 `GlassMaterialEngine`。
- Markdown 顶栏、Dock、Home 菜单、预览变体 1/2 另有硬编码 blur/alpha/实色。
- `M1racleGlassPolicy` 与引擎 role 不一致，且不强制 `glassMaterial`。
- Dock/DockSelection 配方无 UI 消费者。

### 4.6 已关闭 / 弱化的效果（代码证据）

| 效果 | 状态 | 证据 |
|---|---|---|
| Lens / 折射 | **关**（量值为 0） | `regularFrost` `lensHeight=0.dp` `lensAmount=0.dp`；`drawBackdrop` 仅当 refraction>0 才 `lens()` |
| chromaticAberration | **关** | 默认 false；预览变体 2 显式 false |
| depthEffect | **关**（生产配方） | `regularFrost.depthEffect=false`；预览变体 2 为 true |
| Highlight / edge light | **关**（Dialog 除外） | `highlightWidth=0.dp`；Dialog 0.5.dp |
| Kyant 库外阴影 | **关** | `shadow = null` in `drawBackdrop`；改用 `Modifier.dropShadow` |
| Dock 玻璃采样 | **未使用** | Dock 实色 L3；role 配方闲置 |
| 文档专用 `overDocument` | **已删除** | 拓扑测试 `assertFalse(contains("overDocument"))` |
| 能力分级 fallback | **不存在** | 无 enum / 无 RenderEffect 探测 |
| `GlassInteractionContext` | **存在但 UI 未接** | 仅 `GlassInteraction.kt` + 单测；`glassMaterial` 只用 `pressed: Boolean` + scale spring |
| 预览循环滚动 | 仅设置页 | `BlurComparisonPreview` `while (isActive) animateScrollTo` |

### 4.7 正文穿透的主要原因（按代码，不按观感猜测）

截图事实（2026-09-15 DCIM）：白底教材上胶囊能读出底下表格字；外影重、发灰。

代码上同时成立：

1. **Surface recipe（主因，有直接参数证据）**  
   在 2026-09-16 工作树，Control/Capsule/Menu 浅色 `surfaceColor.alpha = 0.86`，并 `onDrawSurface { drawRect(surfaceColor) }` 盖在采样模糊之上。更早配方是 0.62（文档与回归测试曾冻结）。0.86 仍 < 1，正文对比度被压低而非空间低通到不可读。R36.16 禁止的“加白到 0.8–0.95”**已经发生在当前树**。

2. **Blur 固定 18.dp，且无“只糊正文、前景分离”的第二级低通**  
   模糊半径不随 PDF 字号/表格频率变化。18.dp 是否足以把教材细字打成色块：**设备 UNKNOWN**。代码没有按内容频率调整 blur。

3. **Foreground 在静止时并未乘 surface α**  
   胶囊标题是不透明 Text，叠在半透明板上。穿透的是**板后采样**，不是字跟着变透明。  
   **混层发生在动画：** `PdfChromeVisibility` 用 `AnimatedVisibility` fade 包住整个控件（玻璃+图标+字）。显隐过程整层 α<1。Markdown 顶栏 `graphicsLayer { alpha = 1-fraction }` 同样整层淡出。这是 R36.4 所说的混层，但是**滚动显隐**，不是胶囊静止可读穿透的主路径。

4. **采样错位 / 延迟**  
   结构上 overlay 采的是文档层。Kyant `LayerBackdrop` 是否每帧跟随 fling/pinch：**UNKNOWN**（未读库、无设备 trace）。`onDraw` 的 lambda `remember(backdropBackground)`，不随滚动失效，依赖层自身重绘。**没有证据**表明静止截图的穿透是坐标错了；错位更可能表现为拖影/跳到左上，截图未证明那一类。

5. **外影灰塑料**  
   `dropShadow` radius 6.dp、light α 0.14（曾 0.35）。边界靠投影+高 α 色板，不靠 edge light（width=0）。这与“灰卡片/白塑料”的代码路径一致，与 blur 是否足够是分开的问题。

**综合判断（代码）：静止正文穿透 = 中等模糊 + 仍透明的 surface tint 盖在未低通干净的 PDF 采样上，属于 recipe 组合问题；不是已证实的自采样或 Popup 坐标错误。** 加 α 是对穿透的补救，会加强塑料感。采样延迟/错位对静止穿透 **UNKNOWN / 证据不足**。

---

## 5. PDF 滚动 / 缩放 / 翻页与 backdrop

| 环节 | 证据 | 结论 |
|---|---|---|
| 采样源 | `rememberLayerBackdrop(onDraw = drawPdfBackdrop)`，`drawRect(bg)+drawContent()` | 源 = 文档层 GraphicsLayer 记录 |
| 内容变换 | 横向条 `graphicsLayer { translationX = … }` 在 `content()` 内（`PdfViewerScreen.kt` ~2886） | 变换在 capture 内，**应当**进入采样 |
| chrome | source 外兄弟 | 不进采样 |
| 刷新 | `drawPdfBackdrop` 只按 `backdropBackground` remember | 滚动不重建 callback；依赖 Kyant 层随 draw 更新 |
| 失效 | 无应用层 `invalidate` API 调用 | **UNKNOWN** 库是否每帧同步 |
| 截图像素循环 | 无 | 未使用 Bitmap screenshot 采样 |
| 坐标 | overlay 与 document 同 Window、同 root Box | 无跨 Window 采样 |

---

## 6. Foreground / 交互

- 静止：Text/Icon 为 `glassMaterial` 容器的子节点，无统一 `Modifier.alpha`。  
- 显隐：`PdfChromeVisibility` → `AnimatedVisibility(fade)` 包住整块 chrome（玻璃+前景）。  
- More 内容：`graphicsLayer { alpha = contentAlpha; scale… }`（`ReaderMoreHost.kt` ~602）。  
- 胶囊展开：标题行 `graphicsLayer { alpha = 1f-fraction }` 只作用于标题 morph，不是引擎前景通道。  
- 交互：`pressed: Boolean` → `animateFloatAsState` + 0.965 scale。`GlassInteractionContext`（Idle/Hover/Pressed/Dragging/Expanded）**没有接到任何 chrome**。触点位置变形、edge light 随 press **不存在**。  
- 无 infinite material clock。预览滚动动画仅设置页。

---

## 7. 性能风险（代码级，非实测）

- 每个 `drawBackdrop` 持有 GraphicsLayer + RenderEffect（注释 L502–505）。PDF 同时：Back、More 圆或 Menu 卡、Capsule，展开时再加 SettingsCard。  
- Menu/ExpandedMenu/SettingsCard/Dialog 的 `renderState` 恒为 INTERACTION，可能阻止 idle 优化：**UNKNOWN**（取决于 Kyant 是否用该字段）。  
- `pressProgress` 在 composable 中被读取，按压弹簧期间会重组。  
- Home/Hub 捕获无消费者：额外 layer 记录，成本 **UNKNOWN**。  
- 设置预览 `playing` 时长 5.5s 往返滚动 + 实时 blur。  
- 无设备，idle 是否稳定重绘 **UNKNOWN**。

---

## 8. UNKNOWN 清单

1. Kyant 2.0.0 `LayerBackdrop` / `exportedBackdrop` / `rememberCombinedBackdrop` 源码与 GPU 行为。  
2. 滚动/fling/pinch 时采样是否 ≤1 帧延迟、是否错位。  
3. RuntimeShader/RenderEffect 失败时的真实降级。  
4. 小米 RenderThread / `MiBackgroundBlurBlend`：应用代码未调用；库内是否触发 **UNKNOWN**。  
5. 0.86 表面 + 18.dp blur 在真机教材页上正文是否仍可读（需设备）。  
6. `GlassBackdropHost(backdrop, backdrop)` 若被 Combined 使用是否自指。  
7. Dock/DockSelection 配方是否计划使用，或仅为遗留。

---

## 9. 对 R36.1 的只读建议（不实施）

1. 先把 **文档 backdrop DAG** 收成唯一 DocumentBackdrop（PDF 已接近；Markdown 顶栏应纳入同一引擎或明确标为非 Glass）。  
2. 删除或接上闲置 Dock 配方，避免第二套 L3 实色与引擎分叉。  
3. 不要继续用加 `surface α` 解决穿透；R36.5 要求 blur/vibrancy/前景分离。当前 0.86/0.90 是审计必须记录的既成事实。  
4. 接 `GlassInteractionContext` 或删掉，禁止第三套 press 语义。  
5. ProgressiveGlass：Markdown `readerGradient` 的白矩形 `DstIn` 不符合 R36.11；若能力不够应标 `DEFERRED_PROGRESSIVE_GLASS`，不要当完成。  
6. 1–4（拓扑、跟踪、fallback、前景）未过前不要调 lens/edge。

---

## 10. 本阶段文件

仅新增：`docs/R36_GLASS_GROUND_TRUTH_AUDIT.md`。
