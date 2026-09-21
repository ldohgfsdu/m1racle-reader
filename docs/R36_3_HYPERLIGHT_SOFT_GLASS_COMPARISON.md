# R36.3 HyperLight Soft Glass Comparison

状态：家族对照仍是 `SOFT_GLASS_COMPARISON_READY`。角色映射已落到预览，但本轮没有真机画面，所以**不标** `PRODUCTION_MAPPING_CANDIDATE_READY`。不是 production approved。

`HUMAN_VISUAL_NOT_RUN`

当前配方和旧折射参考已明确淘汰。H0 是家族基线。Control 映射偏 H1，Panel 映射偏 H0。H2 只留作厚玻璃参考。生产调用点没有替换。

## 为什么当前 .86 路线失败

这是本轮任务书对既有截图的判定，不是本轮真机观察。

当前正式 Control / Capsule / Menu 是 **18dp / tint .86**。任务书指出：

- tint 在主导材质，blur 的视觉贡献太小
- 背景颜色几乎被盖掉
- 看起来是白色 frosted card / 磨砂塑料，大 Panel 接近不透明
- 这组数字不再作为新候选的基准

旧「折射参考」是 **4.32dp / tint .42 / lens 16dp·32dp**。任务书指出 backdrop 已经开始参与、文字细节被破坏，但仍然过奶、tint 偏高、白边明显、折射和 blur 的比例不自然，而且没有彩色压力背景。禁止把这组直接升进 production。

## 为什么改成强 blur + 低 tint

玻璃感应来自 backdrop 处理，而不是一块高 alpha 白板。

目标顺序是：先毁掉高频细节，再保留低频颜色，最后只加很薄的 tint 和很轻的边缘。tint 上限 **0.20**。如果文字仍然可读，优先加 blur / 降 contrast / 调 brightness，禁止把 tint 拉回 0.5–0.9。

HyperLight / HyperOS 控制中心只作为视觉参考。本轮没有使用 HyperOS 私有 blur、MiBackgroundBlur、Xposed、截图循环、自采样 backdrop，也没有用 `Modifier.blur()` 去模糊玻璃自己。采样仍走现有的 Kyant backdrop。

## 页面

设置页对照分成三层，不再把原始候选和生产映射放在同一排：

- **生产映射**：默认页。两个小胶囊用 `ControlCandidate`，下面的大面板用 `PanelCandidate`。同一块 300dp 舞台。
- **家族参照**：H0、H1、H2。H2 标成厚玻璃参考，不进入映射。
- **已淘汰**：当前配方、旧折射参考。渐变只是旧的 Markdown 遮罩，放在这一层以免回到它。

浅色 / 暗色、正文 / 图案仍只改预览背景，不改配方。播放仍只滚动背景。舞台里的胶囊高 52dp，面板高 112dp。

`GlassPreviewPreset` 里的旧 tint 阶梯和 Dark D1/D2/D3 还在引擎里，因为 R36.2 契约测试还钉着它们。对照页不再把它们当作实验。

## Role Mapping Decision

裁决来自对照页，不是又一轮三选一。

- **当前配方淘汰。** 18dp / tint .86 由 tint 主导，背景颜色进不去，看起来是白塑料。不再作为基线，预览里标成「已淘汰」。
- **旧折射参考淘汰。** 4.32dp / tint .42 / lens 16dp·32dp 有特效感，白边重，blur 和折射不成比例。不回退，也不升入生产。
- **H0 胜出，作为家族基线。** 它比 H1 稳，比 H2 轻，tint .13 已经离开白板。后面的角色映射都从这套光学家族往下改，不另起一套风格。
- **Control 借 H1。** 小胶囊要更轻、更通透。H1 的方向（更低 tint、略少 blur、更高饱和）适合漂浮小件，但不能直接把 H1 的整套数字套上，否则和 Panel 仍然太像。
- **Panel 借 H0，并压染色。** 大面板要能放字，所以留在 H0 的稳这一侧。H0 在彩色底上偏蓝、偏像亚克力，所以 Panel 的饱和度降到 1.12（低于 Control 的 1.20，也低于 H0 的 1.20），中性 tint 升到 .15，contrast 压到 0.94。不用高 alpha 白板去盖颜色。
- **H2 不做主方案。** 40dp / tint .17 是厚玻璃参考。Control 如果走到这一档会失去轻盈。它留在家族参照里，不进生产映射。

染色的处理顺序是：先降 Panel saturation，再略升中性 tint，再压 contrast。边界继续用方向高光，不用 uniform stroke。映射候选的描边宽从家族的 0.75dp 收到 0.5dp，右下 alpha 收到 .03 / .02。Control 保留 0.5px 的边缘折射，Panel 折射为 0，避免大面板上的字被拉扯。

暗色不新增候选。沿 H0 Dark：blur +3dp，tint 换成 `#101418`，brightness 0.91。Panel 暗色 tint .18、饱和 1.10；Control 暗色 tint .14、饱和 1.14。角色差还在。

映射数字见下表。它们只存在于 `SoftGlassComparison.resolveMapping`，生产 `GlassMaterialEngine` 不读它们。

| | ControlCandidate | PanelCandidate |
|---|---|---|
| logical blur | 28dp | 35dp |
| 暗色 blur | 31dp | 38dp |
| tint | .10 白 | .15 白 |
| 暗色 tint | .14 `#101418` | .18 `#101418` |
| saturation | 1.20 | 1.12 |
| 暗色 saturation | 1.14 | 1.10 |
| brightness | 1.04 | 1.04 |
| 暗色 brightness | 0.91 | 0.91 |
| contrast | 0.96 | 0.94 |
| soft-light | 0.10 | 0.12 |
| highlight 左上 / 右下 | .22 / .03 | .18 / .02 |
| inner | .05 | .06 |
| refraction | 0.5px，带宽 8px，中心 0 | 0 |
| shadow alpha | .04 | .05 |
| rim | 0.5dp | 0.5dp |
| foreground | 1 | 1 |

「视觉检查」五项默认不勾，只能手点，不会自动通过。

## H0 / H1 / H2

Panel 使用基线。Control 只比 Panel 少 **3dp** blur、少 **0.02** tint，其余光学参数相同。暗色只展开 H0。H1 / H2 在暗色下仍解析浅色配方，并在摘要里写明「暗色未展开」。

| | H0 Panel | H1 Panel | H2 Panel | H0 Dark Panel |
|---|---|---|---|---|
| logical blur | 32dp | 26dp | 40dp | 35dp |
| Control blur | 29dp | 23dp | 37dp | 32dp |
| tint | .13 白 | .09 白 | .17 白 | .18 `#101418` |
| Control tint | .11 | .07 | .15 | .16 |
| saturation | 1.20 | 1.25 | 1.14 | 1.14 |
| brightness（设计值，1 = 不变） | 1.05 | 1.04 | 1.06 | 0.91 |
| contrast | 0.95 | 0.96 | 0.93 | 0.96 |
| soft-light | 0.13 | 0.10 | 0.15 | 0.065 |
| refraction amount | 1.0px | 0.8px | 1.2px | 1.0px |
| highlight 左上 / 中 / 右下 | .28 / .11 / .05 | .25 / .10 / .04 | .30 / .12 / .06 | 同 H0 |
| inner highlight | .08 | .06 | .09 | .08 |
| shadow alpha / radius | .06 / 2dp | .05 / 2dp | .07 / 2dp | .06 / 2dp |
| foreground alpha | 1 | 1 | 1 | 1 |

按压只写在解析器里，预览没有按压手势：scale **0.988**，左上高光 **+0.02**，tint 不变。

## 渲染映射

Kyant blur 半径就是像素，和 dp 线性对应。没有额外增益。UI 同时显示 logicalBlur 和 rendererBlur。

`rendererBlurPx = logicalDp × density`

例如 density 3：H0 Panel 32dp → 96px，Control 29dp → 87px。旧折射参考看起来远强于 4.32dp，是因为 lens 为 16dp / 32dp，不是因为 blur 被秘密放大。

| 设计量 | 渲染量 |
|---|---|
| saturation | 原样送入 Kyant `colorControls`（1 = 不变） |
| contrast | 原样送入同一矩阵（1 = 不变） |
| brightness | Kyant 是加法偏移，0 = 不变。渲染值 = 设计值 − 1。H0 的 1.05 → **+0.05** |
| soft-light | 不是 Kyant 的单位。渲染亮度 += 设计强度 × **0.35**。H0 的 0.13 → **+0.0455**。避免 0.13 被当成一块白 |
| refraction | amount 按设计 px 原样送入 Kyant lens。band 固定 **8px**。center = **0** |
| tint | 设计 alpha 画在 backdrop 之后。Compose sRGB 把 alpha 收成 8 bit，绘制值和设计值最多差 0.5/255。摘要里的 painted 是实际 Color.alpha |

预览链路的调用顺序就是 Kyant 的作用顺序：

blur → colorControls（saturation + brightness + contrast，一次矩阵）→ soft-light（第二次 brightness）→ edge lens → surface tint → 方向高光。

阴影在玻璃后面，radius 2dp。前景文字不乘 alpha。

方向高光不是 `Highlight.Plain`，也没有整圈固定 1dp 白描边。它是左上到右下的渐变描边（宽 0.75dp）加上左上内高光。Kyant 的 Default highlight 用 `abs(dot)`，左右两侧都会亮，所以没有用。

折射没有关。Kyant lens 在距边缘大于 `refractionHeight` 的像素上直接返回原样，中心位移是 0。1px 量级不是 16/32dp 那组变形。如果真机上仍把字拉变形，下一轮应把 amount 设为 0，而不是把 16/32dp 拿来用。

高光画在 lens 之后的表面上，所以表面反射不会跟着 backdrop 位移。这和任务书里「高光然后折射」的书写顺序不同，物理上高光不该被折射带走。

## 两套压力背景

切换正文 / 图案不进入 `resolve`。解析函数的参数只有 candidate、role、dark、pressed。

- **正文**：沿用米白 `#F4EEE3` 加原来的 14 行黑字。暗色页仍是 `#182027` 加白字。前景 alpha 仍是 1。
- **图案**：Compose 绘制的低频色块。深蓝、品红、黄橙、青、深灰，加一块亮色。没有网图，没有新增资源，没有细线。暗色开关不改这张彩色底，否则无法检查颜色能否进玻璃。

## Production 未变

`git diff` 对 `GlassMaterial.kt` 以及 Reader / PDF / Markdown / Home / Dock / Dialog 的生产调用文件为空。

`SoftGlassComparisonTest.production recipes stay on the frozen authority` 再钉了：

- 浅色 Control / Capsule / Menu alpha .86，ExpandedMenu / SettingsCard .90，Dialog .97，ProgressiveChrome .62
- 上述阅读角色 blur 仍是 18dp
- 暗色 Control .80，SettingsCard .86
- 解析一次 H0 Dark 之后，再解析生产 Control，结果与之前相同
- `softGlassPreview(` 只出现在 `BlurComparisonPreview.kt` 和 `SoftGlassPreview.kt`

## 构建

JDK 17。本机默认 `java` 是 25.0.3，Gradle 8.11.1 无法用它编译 Kotlin DSL，所以构建指定了 `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64`。

| 任务 | 结果 |
|---|---|
| `:app:testQaUnitTest` | **PASS**。1550 tests，0 failures，0 errors，0 skipped |
| `:app:compileQaAndroidTestKotlin` | **PASS**。仅有既有的 Compose 测试 API deprecation warning |
| `:app:compileReleaseKotlin` | **PASS** |
| `:app:minifyReleaseWithR8` | 已执行，未报错 |
| `:app:assembleRelease` | **PASS**。签名取自既有的 `/storage/emulated/0/M1racle Reader/keystore/m1racle-reader-release.p12`，别名 `m1racle-reader-release`。口令没有写入本文 |

本轮产物：

- `.external_outbox/m1racle-reader-release-v1.0.0-20260922-075240.apk`
- `.external_outbox/m1racle-reader-release-latest.apk`
- 已覆盖 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`
- 大小 7,101,114 bytes（6934 KB）
- SHA-256 `a2b086d096b0ea6f52b94bc7819ddf7b928023ce093bcbd840d0ea5cb90b2750`
- `versionName=1.0.0`，`versionCode=23`
- 与 2026-09-21 的旧包（7,068,346 bytes）不是同一个文件

没有改用 Debug。

## 截图索引

`adb devices` 无设备。家族对照 7 张和生产映射 4 张都没有生成。`lintVitalAnalyzeRelease` 仍会打出既有的 ASM `NegativeArraySizeException`，本轮构建没有因此失败。

| # | 计划路径 | 状态 |
|---|---|---|
| 1 | `.external_outbox/r36-3-soft-glass/01-light-text-current.png` | NOT_CAPTURED |
| 2 | `.external_outbox/r36-3-soft-glass/02-light-text-h0.png` | NOT_CAPTURED |
| 3 | `.external_outbox/r36-3-soft-glass/03-light-color-h0.png` | NOT_CAPTURED |
| 4 | `.external_outbox/r36-3-soft-glass/04-light-color-h1.png` | NOT_CAPTURED |
| 5 | `.external_outbox/r36-3-soft-glass/05-light-color-h2.png` | NOT_CAPTURED |
| 6 | `.external_outbox/r36-3-soft-glass/06-dark-text-h0.png` | NOT_CAPTURED |
| 7 | `.external_outbox/r36-3-soft-glass/07-dark-color-h0.png` | NOT_CAPTURED |
| 8 | `.external_outbox/r36-3-soft-glass/08-mapping-light-text.png` | NOT_CAPTURED |
| 9 | `.external_outbox/r36-3-soft-glass/09-mapping-light-color.png` | NOT_CAPTURED |
| 10 | `.external_outbox/r36-3-soft-glass/10-mapping-dark-text.png` | NOT_CAPTURED |
| 11 | `.external_outbox/r36-3-soft-glass/11-mapping-dark-color.png` | NOT_CAPTURED |

## 已知限制

- 没有真机像素。不能判断文字是否已经不可读、颜色是否进了玻璃、边缘是否仍像描边。
- 渐变描边仍是一条连续轮廓，只是 alpha 从左上落到右下。它可能还是会被看成边。
- 按压没有接到手势上。
- H1 / H2 没有暗色配方。
- saturation、brightness、contrast 在 Kyant 里是一个矩阵，不是三次独立 pass。
- 旧 tint 阶梯和 Dark D1/D2/D3 仍留在 `GlassPreviewPreset`，只是对照页不再列出。

## 最终状态

家族对照：`SOFT_GLASS_COMPARISON_READY`。角色映射：预览已接线，视觉未跑，因此不是 `PRODUCTION_MAPPING_CANDIDATE_READY`。`HUMAN_VISUAL_NOT_RUN`。不是 production approved。

设置页默认打开生产映射。等真机看过 TEXT/COLOR 和 LIGHT/DARK 之后再决定能不能收口。不要根据本文宣布 Panel 染色已经被中和。
