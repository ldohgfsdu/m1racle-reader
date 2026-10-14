# 08 UI、导航、材质与文档工具

[返回目录](README.md)

## 导航入口

[MainActivity](../../app/src/main/java/com/m1racle/reader/MainActivity.kt) 设置边到边窗口、系统栏和同分辨率最高刷新率候选，将外部 Intent 交给应用级协调器，观察 pendingNavigation 后执行一次阅读器替换。

[M1racleReaderNavGraph](../../app/src/main/java/com/m1racle/reader/ui/navigation/NavGraph.kt) 组装 HomeViewModel、文档操作、仓库/控制器、各阅读器及页面。顶层 Tab 导航使用 `popUpTo(home)`、saveState/restoreState 与 singleTop；打开文档先回到 Home 锚点，维持一个活跃阅读器语义。

| 路由 | 页面/职责 | 参数 |
|---|---|---|
| `home` | 最近文档、继续阅读、导入 | 无 |
| `library` | 书库、收藏等列表 | 无 |
| `bookmarks` | 全局书签列表 | 无 |
| `settings` | 应用设置、字体与工具入口 | 无 |
| `search` | 本地跨文档搜索 | 无 |
| `document-tools` | 新建/合并/加密/文字转换与扫描 | 无 |
| `viewer/{documentId}?anchor={anchor}` | Markdown/Markdown 包 | documentId、可选编码锚点/书签快照 |
| `pdf/{documentId}?anchor={anchor}` | PDF | 同上 |
| `epub/{documentId}` | EPUB | documentId；当前未声明 anchor 参数 |
| `pdf-edit/{documentId}?page={page}` | PDF 正文编辑 | documentId、零基 page，默认 0 |

`Routes.viewer/pdfViewer()` 编码 canonical anchor；`Routes.bookmark()` 编码 BookmarkTarget。EPUB 页面当前没有消费对应 query anchor 的完整链路，因此不能仅依据路由生成工具宣称 EPUB 精确书签已支持。

## Hub 与阅读页面

| 包/对象 | 责任 |
|---|---|
| `ui/home`：HomeScreen/HomeViewModel/HomeHubModel | 最近文档列表、导入事件、继续阅读、列表更新与布局 token |
| `DocumentOperations` | 文档观察、更名、导出等共用操作；通过 CompositionLocal 提供给页面 |
| `ui/hub`：LibraryScreen/BookmarksScreen/HubSettingsScreen | 书库、书签和设置；部分 ViewModel 与 Screen 同文件声明 |
| `HubDestination` / `HubTabTransitions` | 顶层目标及转场策略 |
| `GlassDock` / `OpticalDockTabs` / `DockMotion` | 四个顶层入口、中央导入动作、滑动命中、透镜、拖动与弹性反馈 |
| `ui/reader` | 跨格式阅读进度与书签、高亮、笔记控制器 |
| `ui/annotation` | 临时选择、创建流程、覆盖层、笔记编辑和批注管理 |
| `ui/theme` / `ui/common` | 颜色、排版、运动、系统栏保护和明暗对比 |

Dock 只在当前和所有可见导航项均为顶层页面时出现，避免阅读器退出动画尚未结束时采样到阅读正文。Hub 的捕获区域包含页面不透明底色，Dock 位于捕获区域之外。

## 书签与标注控制器

`ReaderBookmarkController` 是阅读 UI 的书签中介，使用位置类型与 BookmarkTarget 创建/删除固定位置记录。`ReaderHighlightController` 和 `ReaderNoteController` 将选择转换后的 DocumentAnchor 交给 ReadingRepository，再将持久列表提供给覆盖层与管理 UI。

`TransientSelection` 和 `SelectionModel` 仅描述当前选择；`AnnotationCreationFlow` 管理创建步骤，`AnnotationOverlay`/`PdfHighlightLayer` 等绘制持久资产。颜色、命中搜索和菜单选中状态分别有自己的语义，不能以视觉上相似为由共用持久字段。

## 玻璃材质架构

当前 Dock 视觉验收以 [iOS App Store 日间 / 夜间新参考](../IOS_MATERIAL_STANDARD_20261013.md)为准，两种外观分别检查静止、按住、两端、拖动、释放与彩色背景透过；夜间保留深色透光底、近白色未选中图文、蓝色强调及细窄边缘色散。72 版完成色阶与轮廓阶段调整，但用户随后指出折射、色散和点击动画仍不足；最新实现与验收边界见 [光学与点击动画报告](../DOCK_OPTICS_20261013.md)。

日夜适配在 `LiquidGlassStyle` 的 Dock 角色定义色调和真实轨道高光，继续使用统一引擎及原有 4dp 模糊 / 折射。夜间轨道采用 22% 中灰叠色，黑底上仍呈炭灰；紧凑 DockLens 使用 12% 白色叠色，按住后该叠色收为透明，直接透出真实轨道。浅色轨道增强实际细轮廓，供透镜捕获，两端仍通过原有像素断言验证。PDF Control / Capsule、其他 SelectionLens 的配方独立保留，不随 Dock 调色。夜间蓝色由 `DockSelectedDark` 同时用于透镜内图文与导入动作。实现与验证证据见 [日夜适配报告](../DOCK_IOS_ADAPTATION_20261013.md)。

[DockRefraction.dockRefraction()](../../app/src/main/java/com/m1racle/reader/ui/material/DockRefraction.kt) 负责 DockLens 的单次折射采样：复用带版权声明的 Backdrop 2.0 圆角 SDF / 圆弧映射 / 七波段重建，移除横纵位置乘积衰减，保持上下中点及端帽色散连续。按住折射带 16dp、位移 8dp、色散强度 0.05；输入为真实根背景、等比投影的实际轨道、真实前景。PDF 与紧凑 SelectionLens 继续使用库原路径。透明中央不填色，色散不随静止时间变换。

`OpticalDockTabs` 记录点击时的实际透镜起点，随拾起进度连续移动到目标；短点击释放从当前渲染位置续接弹簧，导航仍在 UP 后提交一次。`HubTabTransitions.enter()/exit()` 对实际页面执行按入口顺序的 8% 短滑动与淡入淡出，时长 220/180ms；Dock 保持在 NavHost 外。新增原生测试检查透镜中间帧，以及实际 NavGraph 前进 / 保存状态后返回首页的页面位置。

2026-10-13 用户取消独立玻璃工作台，设置入口、路由与 QA 实验实现均已删除。iOS 材质参考直接用于实际 Dock / 阅读控件；当前统一引擎仍是 Android / Compose 实现。详情见 [范围调整与验证](../WORKBENCH_REMOVAL_20261013.md)。

材质相关代码主要在 [GlassMaterial](../../app/src/main/java/com/m1racle/reader/ui/material/GlassMaterial.kt)、[GlassAuthority](../../app/src/main/java/com/m1racle/reader/ui/material/GlassAuthority.kt)、[LiquidGlassStyle](../../app/src/main/java/com/m1racle/reader/ui/material/LiquidGlassStyle.kt)、[GlassInteraction](../../app/src/main/java/com/m1racle/reader/ui/material/GlassInteraction.kt)。

- `GlassMaterialRole` 表达控件角色；`GlassEnvironment` 区分应用/阅读器与深浅色；`GlassMaterialEngine.resolve()` 输出材质 spec。
- `GlassCapabilityProbe.detect()`：API 31–32 为 BLUR_ONLY，API 33+ 可为 FULL_OPTICAL；无适合的 backdrop/shape 时由 `forSurface()` 降级为 BLUR_ONLY 或 SOLID_FALLBACK。
- `Modifier.glassMaterial()` 将背景采样、模糊/透镜、颜色、阴影等连接到 Compose；文字 alpha 独立，避免调透明背景时连文字一起变淡。
- 当前版本 Dock 按压透镜有专用采样/几何处理，不能把所有光学参数简化为只有一种 surfaceColor。
- `GlassBackdropHost` 管理采样层；[GlassBackdropSource](../../app/src/main/java/com/m1racle/reader/ui/material/GlassBackdropTopology.kt) 在捕获子树内把 LocalGlassBackdropHost 置空，控件作为捕获节点的兄弟 overlay 取样，防止递归 RenderNode 依赖。
- 弹窗跨窗口时可能采用无法取样的实色/soft plate 路径；它是设备/窗口能力约束，不是第二套内容身份逻辑。

Markdown 阅读顶栏由 [ReaderGradient.readerGradient()](../../app/src/main/java/com/m1racle/reader/ui/viewer/ReaderGradient.kt) 与 [ProgressiveChromeBlur.apply()](../../app/src/main/java/com/m1racle/reader/ui/viewer/ProgressiveChromeBlur.kt) 处理固定的模糊深度。77完整光学路径以平台原生高斯生成同一正文的五个尺度，以连续且和为1的权重衔接到原始正文，废弃自算变半径双向核。视口外使用实际页面色，滤波尾部留足整数像素边距；不以透明遮罩叠回清晰底图。按钮显隐只改变独立前景，下缘32dp由 `ReaderChromeFadeHeight` 同时供给容器和渲染。`ProgressiveChrome` 配方24dp映射到峰值平台高斯半径12dp，浅色/深色叠色为24%/52%并随深度退去；API31–32保留既有平台降级。本轮全量和52项原生、正式日夜模拟器滚动/搜索通过，用户否定76，77手机及最终观感仍待验。见 [77连续过渡报告](../MARKDOWN_TOPBAR_NATIVE_TRANSITION_20261014.md)。

`QaGlass`、`QaExperimentScreens` 在 `production` 与 `qa` 源集分别提供同名实现。Release 的 QA 开关关闭；实际调参页面与 provider 留在隔离 QA 源集。主应用的 DockLens 使用上述带来源声明的派生 shader，其余玻璃依旧由 Backdrop 库处理；QA 开关不决定正式 Dock 的光学路径。

## PDF chrome 与菜单状态

`PdfChromeScrollStateMachine`/`PdfChromeVisibility` 处理滚动显隐，`ReaderMoreHost`、`ReaderMoreTransitions` 与 `AnchoredNestedPopover` 管理主菜单、内联区与子面板；`AdvancedChildLayoutSpec` 和 capsule 形状模型控制展开布局。

返回事件需要先关闭当前子面板/菜单，再离开阅读器。进度轮 `ReaderProgressWheel` 将拖动读点与实际页/进度转换；局部预览不能直接成为数据库稳定快照。

## 文档操作与工具

[ReaderDocumentDialog](../../app/src/main/java/com/m1racle/reader/ui/documents/ReaderDocumentDialog.kt) 显示书库名、读取副本与原文件位置，提供重命名和完整导出。重命名改书库显示名，不改源文件名；导出通过 CreateDocument 指定目标。

[DocumentTools](../../app/src/main/java/com/m1racle/reader/documents/DocumentTools.kt) 只产生新的缓存文件，调用者选择目的地并决定是否导入：

| 函数 | 行为与限制 |
|---|---|
| `createPdf(text, blankPages)` | 平台 PdfDocument 输出基本文字页或 1–1000 张空白页，非完整富文本排版 |
| `merge(inputs)` | 至少两份未加密 PDF，PDFBox 合并后验证页数 |
| `encrypt(input, password)` | 未加密 PDF 生成 AES 256 位密码副本，重新用密码打开验证 |
| `toMarkdown(input)` | PDFTextStripper 按页提取为 Markdown；检查提取权限，不保留复杂版式、不提供 OCR |

`DocumentToolsViewModel` 管理任务、取消、输出临时文件、扫描及导入。`DocumentDiscovery.scan(root)` 使用 canonical containment、visited 集合、隐藏目录/Android 目录排除与取消检查，返回支持扩展名的文件列表。UI 通过系统设置申请所有文件访问权限后才扫描共享存储；扫描发现不等于全部导入。

## Markdown 编辑代码的状态

[MarkdownEditorScreen](../../app/src/main/java/com/m1racle/reader/ui/documents/MarkdownEditorScreen.kt) 和 `MarkdownEditorViewModel` 已有文本修改、撤销/重做、草稿、保存/导出相关实现；`MarkdownDraftStore` 用 AtomicFile 保存 baseHash/text。但当前 NavGraph 没有对应编辑路由或页面调用，用户授权文档也将编辑工作交给 M1racle Note。应归类为**已有未接入源码**，不能作为用户可运行功能宣传。
