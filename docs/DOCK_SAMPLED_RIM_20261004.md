# 按压玻璃内的完整底栏采样

用户提供本机 RiseDiary 源码参考，并澄清问题是：按压出现的玻璃内部，放大的内容应包含底栏本身及其边缘，不是让外框额外放大或增加外阴影。

## 根因与调整

此前选中透镜的来源为 `rememberCombinedBackdrop(barExport, iconBackdrop)`。`barExport` 是 Backdrop 内部 `exportedBackdrop`，只记录处理后的背景和表面，不包含外围高光 / 阴影修饰器；采样的底座高度还大于透镜的静止高度，使底座上下边落在采样区域之外。图标可以放大，但完整底栏轮廓没有进入放大的画面。

参考 RiseDiary `LiquidGlassBottomBar.kt` 的隐藏采样 Row：在页面采样源上绘制高度等于 `selectionHeight` 的完整底栏与蓝色图标，再捕获这一完整绘制结果。M1racle Reader 现在以单个 `selectionBackdrop` 捕获相同方式的完整短底栏，包含玻璃表面、边缘、圆弧与图标 / 标签。材质 Modifier 在外部页面 host 下解析，再传入受保护的捕获节点，因此来源始终为页面，捕获节点不会采样消费它的选中透镜。

删除不再使用的 `barExport` / `iconBackdrop` 合并路径及专用 `surfaceExport` 参数，设置菜单现有导出机制保留。底座尺度、透镜尺度、图标放大、弹簧、导航提交、取消、独立加号与其他材质配方均保持原值。

本轮一开始误解为外框放大和浮起阴影，用户澄清后这些试验代码、对应测试全部撤销；其试验结果不能作为本轮修复证据。

## 验证

- implemented：完整采样层及旧路径清理已实现。
- automated_verified：Hub / material 相关 JVM 回归 91 项全通过；API 35 QA 窗口像素回归 7 项全通过，均为 0 failures / errors / skipped。覆盖图标清晰度、真实页面颜色传递、模糊、折射、按压放大及取消后的蓝色归属；拓扑回归要求捕获层包含完整短底栏材质及其边缘。
- emulator_verified：浅 / 深棋盘背景的静止、按压、释放及双向拖动采集完成。按压文库截图与旧版对比中，玻璃内部已出现底栏上下边缘的折射带，见 `rim-before-after.png`（上：旧版；下：新版）。没有把新的透镜外阴影当作采样内容。
- automated_verified：Release Lint 第一次并行执行时在 MainActivity.kt 的 Kotlin UAST / OverdrawDetector 中工具内部崩溃；未屏蔽检查，以单工作线程重跑通过，0 errors / 159 warnings / 4 hints。
- pending：没有连接真实手机。

仪器测试编译被既有 `DeleteByIdTransactionTest.FailingDocumentDao` 缺少 EPUB 位置更新方法阻塞；仅补上原样委托，未修改生产 DAO / 数据库。

额外 QA 冷启动截图期间发生模拟器 GPU 无响应，主线程等待 RenderThread，RenderThread 停在 `qemu_pipe_read → glCreateProgram_enc`；与构建同时运行时本机空闲内存不足 1 GiB。保留诊断记录，不将当时的黑屏端部截图计为验证通过。构建期间退出本轮启动的模拟器，停止构建后重启并复查，最终结果见下节。

## 最终正式包复查与交付

停止构建进程并重启模拟器后，正式 Release 冷启动耗时 2132ms，四个 Hub 页面选择状态检查通过，实际按住文库的截图已检查，释放提交文库后返回首页。正式进程 crash 输出为空；模拟器 GPU 阻塞记录保留，不推断真机性能。

重启后的 QA 棋盘背景追加最左首页 / 最右设置实际按住截图，两端圆弧也进入玻璃内的放大折射画面，`held-首页.png` / `held-设置.png` 已视觉检查；这些成功截图替换了此前的黑屏采集结果。最终恢复正式包首页。

本次 `:app:assembleRelease` 成功；版本 1.0.0 / 32，7,024,976 bytes，SHA-256 `3b332703b0621921f2d9da79a153fac3db92913c702db0baba2fa61379865349`。唯一交付为 `.external_outbox/m1racle-reader-release-latest.apk`，时间戳快照 `m1racle-reader-release-v1.0.0-20261004-194316.apk`。正式签名 / 证书及 16 KiB 对齐检查通过；构建输出、Outbox、模拟器 Download 和已安装 base.apk 摘要一致。未连接真实手机，未声称真机交付 / 验收；没有提交或推送。

证据目录：`.local-backups/dock-risediary-edges-20261004/`。构建、测试日志：工作区 `output/dock-rim-verification.log`。
