# HyperChanger 模糊实现核查

后续进展（2026-09-08）：用户已选择全局厚雾，现已接入底栏及 PDF 悬浮控件；Markdown 使用固定渐变顶栏。见 [当前实现与验证](MARKDOWN_FIXED_GRADIENT_AND_THICK_FROST.md)。下文保留参考研究及最初预览阶段记录。

2026-09-08，用户提供 https://github.com/ColdP/HyperChanger ，要求查看其模糊并作为目标参考。随后用户要求“搞到设置页看看”，已实现设置内四种对比预览，未改变全局 B 材质。

核查提交：`c428b96224d911d7be8621dc1e778f2a5aa49669`。只读参考下载在 `/tmp/markleaf-hyperchanger-reference`，未构建或运行外部项目。

- 依赖：Kyant Backdrop 2.0.0，以及 Miuix UI/blur 0.9.3。我们已有 Kyant Backdrop，无需为了这些应用内效果换库。
- 应用默认 `navigationStyle=hyper_os`；MainActivity.BottomBar 传 blurRadius=18。标准底栏用 vibrancy + 18dp blur，白底 alpha=.62 / 黑底 alpha=.58，无 highlight、无 lens。
- 顶部 ProgressiveBlurLayer 用固定 16dp blur，再用 runtimeShader 的 smoothstep alpha mask 让下半部逐渐透明；这属于模糊层的透明度渐变，不是每像素半径变化。绘制高度扩展至原高约 1.184625 倍，透明度随 collapsedFraction 变化。
- 顶部圆按钮另有配方：2dp blur，surfaceAlpha=.80；lens 16dp/32dp，depthEffect=true，chromaticAberration=true，按压时折射增强。这一路会出现用户此前不喜欢的色散。
- HyperOS 悬浮底栏另一路：输入 18 × .24 = 4.32dp blur；lens16/32dp、depthEffect=true、chromaticAberration=false；底色浅 .42/深 .30。不能把它与默认标准底栏混为同一组参数。
- 本地当前 B：普通控件8dp、alpha .40、lens4/6dp；展开面板16dp、alpha .72/.78。默认 HyperChanger 底栏相较普通 B 模糊更强、底色更厚，且不产生透镜扭曲；实际视觉还取决于背景与形状。

设置预览：厚雾、渐变、胶囊、当前 B；共享文字/斜线/色块采样背景，左右拖动与播放/暂停。当前 B 直接调用现有 glassMaterial，没有复制配方。局部采样源和效果层互为兄弟，避免递归采样。Android 12 可看模糊；需要 RuntimeShader 的渐变/折射在 Android 13+ 呈现，并在预览中说明。编译、Debug/签名 Release 构建和全量 QA 1457 项通过；已交付 Download。ADB 无设备，视觉与交互待人工验收，详见 [本轮交付](PDF_TEXT_HIGHLIGHT.md)。

这些是应用内 Compose 效果。仓库也有 LSPosed 修改 HyperOS 系统模糊的实现，但阅读器借鉴应用内效果不需要那些系统 hook。

来源：
- https://github.com/ColdP/HyperChanger/blob/c428b96224d911d7be8621dc1e778f2a5aa49669/app/build.gradle.kts#L63
- https://github.com/ColdP/HyperChanger/blob/c428b96224d911d7be8621dc1e778f2a5aa49669/app/src/main/java/btm/m/os4/systemuihook/MainActivity.kt#L800
- https://github.com/ColdP/HyperChanger/blob/c428b96224d911d7be8621dc1e778f2a5aa49669/app/src/main/java/btm/m/liquidglass/hook/GlassNavigation.kt#L756
- https://github.com/ColdP/HyperChanger/blob/c428b96224d911d7be8621dc1e778f2a5aa49669/app/src/main/java/btm/m/os4/systemuihook/MainActivity.kt#L5786
