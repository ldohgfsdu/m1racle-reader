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

implemented / automated_verified 已具备；签名构建与交付待执行；device_verified 为 NOT_RUN（ADB 无设备）。风险中：导航过渡与动态材质；没有新依赖、数据库改动或阅读器行为改动。
