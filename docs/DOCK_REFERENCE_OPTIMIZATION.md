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
