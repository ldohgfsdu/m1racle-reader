# 底栏参考优化

## 参考与范围

参考 RiseDiary `efcd0d12ac38892525c2c82a872077353c5971cc` 的 [LiquidGlassBottomBar](https://github.com/sky-shunfengjun/RiseDiary/blob/efcd0d12ac38892525c2c82a872077353c5971cc/app/src/main/java/com/risediary/app/ui/components/LiquidGlassBottomBar.kt) 与 [LiquidGlassNavigation](https://github.com/sky-shunfengjun/RiseDiary/blob/efcd0d12ac38892525c2c82a872077353c5971cc/app/src/main/java/com/risediary/app/ui/components/LiquidGlassNavigation.kt)。参考可见交互模式，自行用现有 Compose/Backdrop 实现，不搬入其业务组件或新增依赖。

吸收：通透悬浮外壳、内层选中胶囊、弹性位移、按压与横向拖动、选中实心图标。保留 Home / Library / Bookmarks / Settings 四入口及 Home 的既有导入按钮；不迁移对方独立业务动作。

## 现有调用链与决定

NavGraph → 四个页面 → Home GlassDock / 其他 HubGlassShell → GlassDock。当前每页各自持有静态底栏，仅图标文字变色。若直接把动画状态放进单页 Dock，换页会重建动画；因此动画宿主放在 NavHost 上方，目的地只来自真实导航栈。拖动仅保存预览位置，松手请求导航，取消返回当前目的地；不建立乐观选中值或延迟导航队列。

背景采样继续使用现有 sibling ownership，避免循环 RenderNode。动画帧只在绘制/graphicsLayer 阶段读位移；尺寸与命中区域保持稳定。Hub 间切换不交叉淡化两套底栏，阅读器等非 Hub 跳转沿用默认过渡。

用户已反馈 PDF 修复首版太不透；本轮为 Dock 单独定义材质，不调整 PDF 配方。

## 检查点

参考实现与现有调用链已核对；开始实现。ADB 本轮探测为空，真机验证待执行；仓库截图被 README 标明为 AI 生成，不作为真实交互验收证据。

### 实现检查点

Dock / DockSelection 独立材质、64dp 外壳、最大宽 440dp、4dp 内边距、24dp 图标、11sp 标签、选中实心图标、弹性位移与按压、横向拖动已实现。NavHost 上方持有动画宿主；页面不再传递各自硬编码的 selected。仅 Hub→Hub 关闭旧/新页面交叉淡化，其他导航沿用既有过渡。

首次编译发现测试导入错误，已修正。全量 1404 项中，交互测试的拒绝导航/拖动取消/短拖/RTL 和现有产品回归均通过；剩余动画推进检查尚在排查，未把最终选中正确当作动画正确。新增两种材质后产品角色数量契约同步更新为 7，既有配方边界保留。

### 自动化验证完成

全量 `:app:testQaUnitTest --no-daemon`：1404 / 0 / 0 / 0，JDK17。新增 3 项拖动几何/取消纯逻辑测试和 5 项 Compose 交互测试：拒绝导航时保持选中、换页重建期间连续 retarget、拖动松手只导航一次、取消不导航、短拖不过跳与 RTL。

动画测试使用 Compose v2 时钟。暂停时钟后，测试的外部路由状态写入需显式 `Snapshot.sendApplyNotifications()`，再推进帧；原失败实际是测试界面仍停在 HOME，而非动画跳到终点。补上快照同步后，仍保留中间位置严格在 0–3 之间和最终落点 1±0.001 的断言。没有扩大容差或删除动画检查。

既有材质参数/按压、Backdrop sibling 拓扑、导航和阅读器回归均通过。测试用 Robolectric LEGACY graphics 验证语义、输入和动画状态，不把它当 GPU 玻璃视觉或实机性能证据。

implemented / automated_verified 已具备；device_verified 为 NOT_RUN（ADB 无设备）。没有新依赖、数据库改动或阅读器行为改动。

## Release 交付

- 源码：`4c3159243106623284813db047694a3f4cb11d24`；版本保持 `1.0.0 / 23`。
- `:app:assembleRelease --no-daemon` 成功；production signer 与 v2 签名核验通过，Manifest 无 INTERNET 权限。
- APK：6,686,026 bytes，SHA-256 `648037982281e195d488175428370d4d7f182d5d4cb2082d980edabc9afd7da1`。
- outbox 与 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk` 已逐字节摘要核验一致。
- 证据：`.external_outbox/risediary-dock-20260905/` 中测试摘要、构建日志、签名、Manifest、构建记录与固定命名 APK。
- 构建中既有 lintVital ASM 异常仍出现；不将构建成功表述为完整 lint 通过。
- pending：实机玻璃显示、拖动手感及性能验收；Robolectric 结果不代替设备验收。

参考仓库临时克隆及本轮临时日志已清理，必要日志归入上述证据目录。

## 长按反馈补齐（2026-09-05）

用户反馈上一版缺少参考底栏明显的长按放大。根因：只给图标 1.08 倍缩放，选中胶囊仅在位移时拉伸，外壳没有按压缩放。现在按住任意入口或拖动时，外壳按宽度增加 16dp 的比例放大，胶囊放大至 1.18 倍，当前按压内容（图标与标签）放大至 1.2 倍；松手/取消弹性恢复。背景与交互内容改为兄弟层，避免外壳裁剪放大的胶囊；命中区域不缩放。

新增长按期间不提前导航、取消不导航、松手仅请求一次的交互回归；9 项定向测试通过，Release 构建、production 签名与交付副本校验通过。本轮 ADB 无设备，实际放大观感待安装验收。

长按版源码 `addf17da51daa5fab10d4c727fedb64ac72339e5`；APK 6686026 bytes，SHA-256 `68675db7aea7b9db3dd44580217a3a77a8cbd5dc1ac0376594a825cc53b42489`。已覆盖 Download 同名包；证据 `.external_outbox/dock-hold-20260905/`。implemented / automated_verified；device_verified: NOT_RUN；pending: 实机观感。既有 lintVital ASM 异常仍出现，不声称完整 lint 通过。

## 官方 LiquidBottomTabs 按压透镜（2026-09-05）

对照 Kyant `AndroidLiquidGlass` 的 [LiquidBottomTabs](https://github.com/Kyant0/AndroidLiquidGlass/blob/kmp/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt)。上一版只有缩放，缺少按压透镜、速度形变和图标折射。

现在胶囊按住放大到 78/56，拖动按速度拉伸；选中胶囊按压时才出现 10/14 色差透镜和内阴影，空闲霜层降到 0.03 让透镜透出来。着色后的标签行经 `GlassBackdropSource` 捕获，胶囊采样页面与该层的合成背景。外壳补 vibrancy，透镜 24/24。导航仍由路由持有，拖动只预览。

全量 `:app:testQaUnitTest` 1408 / 0 / 0 / 0。新增胶囊速度形变纯逻辑、DockSelection 按压透镜配方、捕获拓扑契约。Release 构建、production signer、v2 签名与 Download 摘要核验通过。ADB 无设备，透镜与手感待安装确认。

源码 `185c242b171465f6da6c1e69f54aba1fd87b0631`；APK 6,702,410 bytes，SHA-256 `f249ab9e859a2a11e6a30b87cf385edd56ad85f701e5dda9683467142b618147`。已覆盖 Download 同名包；证据 `.external_outbox/dock-liquid-20260905/`。implemented / automated_verified；device_verified: NOT_RUN。lintVital ASM 异常仍出现，不声称完整 lint 通过。

## 可见图标被采样层盖住（2026-09-05）

用户反馈图标全部消失。根因：捕获层画在整排标签上面。RiseDiary 可见层始终画空心图标，实心图标只给胶囊采样，捕获层不能盖住可见标签。

现在先画透明采样层，再把空心图标放进外壳，胶囊最后只盖选中格。全量 1409 / 0 / 0 / 0；标签可见性与绘制顺序已回归。ADB 仍无设备。

源码 `8ac90e563fb550b92db34e44c3414e0016e3bb71`；APK 6,702,410 bytes，SHA-256 `de9af083a375ddb365e7639f57f8ac96bb064643a9b3f38bb2df059fdda989d9`。已覆盖 Download 同名包；证据 `.external_outbox/dock-icons-20260905/`。implemented / automated_verified；device_verified: NOT_RUN。

## 图标必须画在玻璃节点外面（2026-09-05）

上一版把可见标签放进外壳 `drawBackdrop` 的子节点。Kyant 的透镜会在布局里加内边距，64dp 底栏不够放下 28dp 图标，看起来像整排图标消失。RiseDiary 可见层是空心图标行，实心图标只给胶囊采样，两者都不是外壳的子节点。

现在：透明采样层 → 空玻璃外壳 → 空心图标行叠在外壳上 → 选中胶囊。图标 28dp、标签 12sp。全量 1409 / 0 / 0 / 0。ADB 仍无设备。

源码 `dba5edf47adbc14ddb0298ff0ed23cc7a506ee6b`；APK 6,702,410 bytes，SHA-256 `b97dbe9c851b6dc552cccbd3a732ee50d417e4bde01bdaa4a2665d8029a899e3`。已覆盖 Download 同名包；证据 `.external_outbox/dock-icons-sibling-20260905/`。implemented / automated_verified；device_verified: NOT_RUN。


## 图标与透镜再次反馈（2026-09-06）

用户仍反馈图标消失、无透镜效果，上一轮不能视为视觉验收完成。此次只改底栏绘制及共享材质入口中为底栏提供的变换参数；不新增依赖，不改变路由或阅读器配方。

### 重新核对的事实

- 已检查实际使用的 `backdrop-android:2.0.0` Maven sources。`DrawBackdropNode.measure` 原样测量约束，并不扣透镜 padding；padding 仅用于扩大录制的背景。上一节“内边距导致图标不足 28dp”的根因解释撤回。
- 新增使用真实 `HubGlassShell` 的图标尺寸回归，四入口均有 28dp × 28dp 可见边界；此前仅标签语义测试无法发现实际像素被擦除/盖住。当前 ARM64 主机的 Robolectric native 包只提供 Linux x86_64，不能据 LEGACY 模式宣称像素正确。
- `DockSelection` 对页面与图标组合层再做 8dp blur；官方 `LiquidBottomTabs` 的选中透镜不加这次 blur。新增清晰采样回归在修改前失败（expected 0dp, actual 8dp）。现改为 0dp，外壳仍保留 8dp 背景模糊。
- 原胶囊放大在 `drawBackdrop` 外层，而 Backdrop 只会对传入的 `layerBlock` 做逆变换。外壳和胶囊的缩放现在由材质入口传给 Backdrop，位移保留在外层，与官方层级一致；图标采样不再随玻璃一起重复缩放。
- 图标改用标准 `Icon(tint = color)`，采样行直接使用主题强调色，移除每图标及整行的额外 ColorFilter 离屏合成。尺寸测试未复现“全部图标消失”，因此此处属于简化实际绘制链路，设备端根因和修复效果仍待确认，不能以推测替代证据。

### 验证检查点

新增 `GlassDockPixelsInstrumentedTest`：在隔离 QA 包下捕获包含最终玻璃叠层的整屏像素，分别检查浅/深色四个图标区域的笔画与背景对比；不单独截 Icon 节点，也不以标签存在代替图标像素。测试编译通过；设备尚不可用，未执行。

ADB 本轮一次探测为空。中风险验证集：32 项定向回归通过，全量 `testQaUnitTest` 1411 / 0 / 0 / 0；`compileQaAndroidTestKotlin` 通过。首轮全量发现两项旧契约冲突：全角色 blur>0 与固定 layerBlock 文本。现只对 DockSelection 明确要求 blur=0，其他材质原约束保留，并验证调用者变换和按压缩放组合；不是把 blur>0 泛化成 >=0 或跳过失败测试。

`assembleRelease` 成功；production signer SHA-256 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`、v2 签名、包名/版本 1.0.0 (23)、无 INTERNET 通过核验。APK 6,702,410 bytes（与上一候选相同），SHA-256 `4e562b1419fdc12f0d3f8eb380de62d8f11620fdf1f8d937b3235a9eff85b753`。已覆盖 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk` 及其 sha256；outbox/Download 摘要相同。源码为 `5284f801fffa62904ce80101ca687cfd415ea8c6` 加本轮未提交工作区；文件摘要与构建证据在 `.external_outbox/dock-render-20260906/`。

implemented / automated_verified 如上；device_verified: NOT_RUN；pending：设备端图标消失根因及本轮显示效果确认、按压透镜/横拖折射与手感。现有 lintVital 的 NegativeArraySizeException/ASM 迁移异常仍出现，不声明完整 lint 通过。


## 首页透明窗口与按压整栏透明反馈（2026-09-06）

用户反馈：静止时首页选中区域为透明背景，从首页开始按住后整条栏变透明。核对实际采样图：选中层原来组合“原始页面 + 清晰图标”，却不含已模糊的外壳背景。上一轮删掉选中层二次 blur 是保留图标清晰所需，但漏了处理过的背景，造成选中位置覆盖外壳时重新带回原始细节。按压将表面 alpha 降为 0.03 并放大，进一步暴露该问题。

本轮改为：页面 → Dock 玻璃表面导出 → 与清晰图标合成 → 选中透镜。复用 Backdrop 已有 `exportedBackdrop`，不新增依赖/独立渲染器；以 `sourceBackdrop` 替换旧的 additive `extraBackdrop` 参数，明确替换采样源，不把原始页面再次混入透明表面下。图标捕获层继续屏蔽 host，不构造采样环。选中层亮度变换归零，避免对已处理的表面二次提亮。

同时在外壳与胶囊各自的 graphics layer 上固定 clip 与 DockShape：Backdrop 的 lens shader 负责折射，不能作为外部绘制边界；输出只允许在各自圆角轮廓内出现。缩放仍在同一 layerBlock 中，因此放大轮廓本身而不是用静止大小截断放大内容。整条栏透明的设备复现尚不可用，不能把代码检查当作像素验收。

新增设备像素回归 `selectedAndInactiveTabsKeepBlurredGlassDuringHomePress`：捕获密集黑白条纹背景上的最终底栏，检查静止、按住首页、松手三个阶段四入口图标旁的玻璃区域不能重新出现原始高频条纹。既有浅/深色四图标像素回归保留。ADB 本轮一次探测为空，设备像素测试只能编译，device_verified 为 NOT_RUN。

中风险：共享渲染入口新增表面导出和显式采样源，仅 Dock 使用；其余角色默认路径和配方保留。验证/产物证据目录 `.external_outbox/dock-background-20260906/`。全量 `testQaUnitTest` 1411 / 0 / 0 / 0，`compileQaAndroidTestKotlin` 通过。原材质契约只针对 DockSelection 要求两主题亮度变换均为 0，其他角色的亮暗关系约束保留。

Release 构建成功，原 production signer、v2 签名、包名/版本 1.0.0 (23)、无 INTERNET 和交付摘要核验通过。APK 6,702,410 bytes，SHA-256 `6c38c070de7b69eace020730811a5494808ddcf4869de2a239404809e04c18e8`；已覆盖 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk` 和 sha256。源码仍为 `5284f801fffa62904ce80101ca687cfd415ea8c6` 加本轮工作区修改，未提交；源码摘要在本轮证据目录。

implemented / automated_verified 如上；device_verified: NOT_RUN；pending：图标和玻璃背景的真实像素、按压范围、透镜与拖动手感。既有 lintVital ASM/NegativeArraySizeException 仍出现，不声明完整 lint 通过。


## 按最新左右拼图调整底板磨砂感（2026-09-06）

用户要求底层背景不要像当前 Dock 那么透，以 DCIM/creative 最新图片里的同一界面两版对比为参考，选择稍微更透的一版；用户说明较不透的一版是当前效果。已查看最新 `IMG_20260906_102521.jpg`，它自身就是左右 PDF 顶栏对比拼图；不采用目录中较早的均衡器截图。

本次仍调整四入口 Dock 底板：浅色 surfaceAlpha 0.62、深色 0.74、blur 12dp（原 0.18/0.24、8dp）。目标是参考图略微透底的磨砂感，而不是原 Dock 的高透底。以现有 PDF 文档材质为尺度，浅色底板黑白背景调制理论约 0.304，略高于 PDF 当前配方约 0.26；这只是配方校准，不声称从图片反推了精确透明度或完成像素匹配。保留图标清晰度、选中透镜和按压/拖动行为。

低风险视觉调校；沿用已有材质回归，更新用户要求改变的 Dock alpha 期望值，其余角色与容差保持原样。定向 GlassMaterialRegressionTest / ReaderChromeTokensContractTest 21 / 0 / 0 / 0 通过；签名 Release 成功，production signer、v2 签名、包名/版本和无 INTERNET 验证通过。未因低风险配方调整重跑无关全量测试或设备测试编译。

APK 6,702,410 bytes，SHA-256 `38a8893c4a59b8e589a36a292f7dece40ce65b2a46df517089dd1cdf33d1f880`。已覆盖 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk` 及 sha256，副本摘要一致。源码仍为 `5284f801fffa62904ce80101ca687cfd415ea8c6` 加本轮工作区，未提交。证据目录 `.external_outbox/dock-frost-20260906/`。

implemented / automated_verified 如上；device_verified: NOT_RUN；pending：参考图所需的真实透明程度。ADB 本轮一次探测为空。既有 lint 工具异常状态未收口，不声明完整 lint 通过。

## 已确认底板铺开（2026-09-06）

用户确认上一版磨砂底板后，授权把同一底板铺到现有紧凑玻璃控件。Control / Capsule / 加号与 Dock 共用 regularFrost；Menu 略厚。加号位置未改，仍为底栏右上方独立导入按钮。全量 1411 项与签名 Release 通过。APK SHA-256 `005581c01b114486a1861cc356a404fc94acad28d2724fb01ac5e598aed6704c`。详见 [材质铺开记录](GLASS_BACKPLATE_ROLLOUT.md)。
