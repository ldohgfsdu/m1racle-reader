# 真实Dock捕获 — 2026-10-11

用户指出62版仍在透镜中伪造另一条较矮底栏，实际上Dock没有那样放大；新增录屏也显示折回的蓝色文字和彩边。62版的自动测试只能证明当时的局部断言，不能证明达到酷安视觉要求。63版是调参中间产物，同样保留错误来源，已被64版取代。

## implemented

- 删除隐藏采样Row、LocalDockSampling、LocalDockSampleScale、DockLensContent及独立1.2倍前景。实际材质、图标、标签、加号只绘制一次，被同一个可见捕获层记录。
- 可见Dock先在页面host下解析材质，只读页面root；捕获层与透镜为兄弟节点。透镜组合页面root和真实Dock捕获，禁止自采样。
- 捕获上下左右扩展16dp，包含真实栏外缘小幅伸展及阴影；实际Dock和命中布局不变。底栏按压总宽增量仍16dp，最多平移4dp。
- 真实前景强调色跟随按压与回程位置，导航松手才提交。修复取消后透镜尚未返回、原图标就提前变蓝的问题。
- 按压镜片折射范围/位移4dp/6dp、内阴影3dp，防止文字折回底缘；底栏折射过渡24dp/24dp，减少窄底缘重复模糊条。保留61版6dp模糊与浅24%/深黑40%覆盖，整栏不因按压加白。

## 参考核对

重新读取用户链接的[最新RiseDiary底栏源码](https://github.com/sky-shunfengjun/RiseDiary/blob/04722776899252e30c46cd0ae7f062093307d5f3/app/src/main/java/com/risediary/app/ui/components/LiquidGlassBottomBar.kt)，提交04722776899252e30c46cd0ae7f062093307d5f3。其整栏也是16dp宽度增量、4dp跟随、78/56镜片拾起比例；这些处理已在当前组件中沿用。该源码另有alpha(0)隐藏采样Row、较矮selectionHeight和1.2倍前景，不能以整段复制它来满足用户本轮真实Dock要求，因此不恢复这些路径。来源副本保存于`../output/dock-real-capture-20261011/`，仅作研究证据。

## automated_verified

定向JVM63项通过，包括实际内容生命周期仅一份、单Row捕获结构、不捕获镜片及手势状态。真机原生14项通过（30.258s），覆盖整栏远端颜色、透光、真实图标按压预览、取消回程不重复点亮、命中几何、72帧往返拖动及底缘蓝色文字折回。

失败证据保留于`../output/dock-review-20261011/`：初次类名路径错误的测试启动不计通过；真实捕获首轮发现取消后原图标过早变蓝，修复后完整复跑14项通过，未削弱断言。旧62实现新增文字底缘断言失败，参数修正后通过。QA日夜按住截图已检查，最终正式包录屏另存最终证据目录。

## device_verified / 正式交付

正式签名64已通过Release lint（0 errors / 198 warnings / 4 hints），生产证书连续、16KiB对齐、min31/target35/无INTERNET。APK 27,131,998 bytes，SHA256 `779b2aed068398090128b4cf9c3f7e0d4de69a40e69b048495578f7b0cc8a5a2`，构建、Outbox和手机Download一致，快照`m1racle-reader-release-v1.0.0-20261011-231855.apk`。

物理手机db04f865/API36保留数据覆盖安装成功。正式包日夜各执行长按及文库→首页→设置→首页往返拖动，末态首页selected、导入取消通过；按压截图已查看，录屏保存在最终证据目录`light-motion.mp4`与`dark-motion.mp4`。系统夜间恢复auto，QA 10021恢复ignore。用户后续明确以酷安效果为目标、RiseDiary为实现参考，此要求沿用。

## 视觉验收边界

本报告的代码与自动测试证明捕获路径已改为真实Dock。不同页面内容与背景下的外观需以实际录屏核对，不能据此宣称与酷安完全一致。Room17、阅读逻辑和用户数据不变；不清除主包数据，不commit/push。未全量重验积压功能，未重验历史模拟器启动ANR。
