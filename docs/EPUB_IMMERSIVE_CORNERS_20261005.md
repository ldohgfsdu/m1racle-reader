# EPUB 沉浸系统栏与纸页圆角 — 2026-10-05

## 输入与根因

用户实机视频 `a4c409a229c6acb87bcde95961375065.mp4`（592 × 1280，约 11.63 秒）与截图 `0fffb26019327f0b4ddce7a83b636af7.jpg` 显示：翻页纸面始终位于状态栏和导航栏之间，系统图标 / 手势条未隐藏；10dp 页角在窄屏上不明显。视频逐帧图见工作区根目录 `output/epub-immersive/phone-overview.jpg`。附件只作为视觉参考。

根因是 `EpubReaderContent` 对整个 Pager 使用 `WindowInsets.safeDrawing` 外层 padding，而且没有 EPUB 原生系统栏策略。上一包只将正文视口扩为安全区内的整页；透明系统栏不会自动隐藏图标或手势条。此前阶段报告的圆角截图不能替代此次手机反馈。

## 修改

- 纸面 Pager 覆盖整个窗口，包括原来上下系统栏占用的区域。正文测量、书名、页脚、控件仍使用 `systemBarsIgnoringVisibility ∪ displayCutout` 的稳定安全区；不因控件显隐或系统栏临时显示重新分页。
- EPUB 在无控件阅读时隐藏全部原生系统栏，控件 / 目录 / 设置期间恢复。使用 Android `WindowInsetsControllerCompat` 和 `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`，边缘滑动可以临时呼出系统栏。恢复窗口焦点 / 前台时重新应用当前策略，离开阅读时恢复普通栏行为及原窗口 cutout 模式。
- 移动纸面改为 24dp 圆角，阴影改由同一原生 outline 生成，避免矩形阴影盖住曲线。前后翻页覆盖关系、落页物理、停稳保存和文本锚点保持。
- 使用现有 Compose / AndroidX Core 依赖，没有新增应用依赖、联网权限或数据库变更。版本 `1.0.0 / 35`，Room v15，正式签名沿用。

原生系统行为依据：[Android 沉浸模式](https://developer.android.com/develop/ui/views/layout/immersive)、[Edge to edge](https://developer.android.com/develop/ui/views/layout/edge-to-edge)、[Display cutout](https://developer.android.com/develop/ui/views/layout/display-cutout)。

## 自动化验证

完整 `:app:testQaUnitTest` 共 **1705 项：1703 通过、2 项既有跳过、0 失败 / 错误**。新增 `EpubImmersiveHostTest` 三项：非零刘海 / 系统区下整纸仍铺满窗口，正文 / 控件 / 页脚保持安全尺寸和锚点；实绘位图验证移动纸面上下角露出下层，排除只写 shape 参数但未裁切；原生双栏请求、控件 / 目录 / 设置、焦点及退出还原。

首轮默认参数需要 `ExperimentalLayoutApi` 的显式 opt-in，编译未通过后补齐。新 Native host 用例初次遇到 Kyant lens 的 host SkSL 编译限制，采用项目其他 Native render 用例同样的 `SOLID_FALLBACK`，只替换 host 的玻璃材质，保留真实布局 / 手势 / 裁切 / Android Window 控制；正式 APK 的玻璃在模拟器实际运行。Robolectric 不把 Window attributes 的 behavior 同步到 ViewRoot getter，因此行为断言改查 AndroidX 对该 Window 的公开 getter，仍断言精确 transient / default 值；原生请求位掩码、窗口 cutout 恢复和其他断言保留。最终三项全部通过，完整套件无新增跳过。失败与重试日志保留在 `output/epub-immersive-*.log`，正式系统行为另由模拟器 dumpsys 证明。

## 正式包与模拟器验证

- 完整命令 `reader-build-local.ps1 -Release :app:testQaUnitTest :app:lintRelease :app:assembleRelease --offline --no-daemon -Dorg.gradle.jvmargs=-Xmx1536m`，退出码 0，约 9 分 27 秒。Lint **0 errors / 149 warnings / 4 hints**。
- HEAD `a57c3244d10d5e741bd25ae790aeb90fb9eed49a`，分支 `feature/epub-anchor`；生产差异 SHA-256 `331c09aa3fb77312ea93209a7ffa1a45d1d4fec3d218b03fd74243eae123275a`，源码快照包含未跟踪的新增 `EpubSystemBars.kt`；构建后逐文件 SHA 再核对一致。
- 签名 APK **7,024,972 bytes**，code 35 / name 1.0.0 / min 31 / target 35，无 INTERNET。SHA-256 `bca00ca81d1791f134c28470326ee387e2297c8b4fd2cf9cf3ff5cc5713dc985`；证书 SHA-256 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`，apksigner 和 16 KiB zipalign 通过。
- 本轮快照 `m1racle-reader-release-v1.0.0-20261005-133442.apk`。`adb install -r` 覆盖安装 API 35 x86_64 模拟器并复制固定 Download 路径；构建输出 / Outbox / base.apk / Download 四处摘要一致。没有清数据，书库标签与升级前一致。
- 原 Pager 范围 `[0,136]–[1080,2337]`，新 Pager `[0,0]–[1080,2400]`；原书正文仍为 `[53,315]–[1027,2226]`，升级后的第 8 / 464 页文本与正文像素完全一致。纸面在原状态栏、导航栏区域也随手势移动。
- `*-bars.json` 和对应 `*-window.txt` 来自 Android Window / InsetsSource：默认双栏 `visible=false`，显示控件 / 目录 / 设置时双栏 `true`；边缘滑动临时呼出双栏后自动隐藏，保持 MainActivity 焦点。返回桌面停稳后是双栏显示 / DEFAULT，返回阅读重新隐藏；退出阅读也是双栏显示 / DEFAULT。
- 浅深色前翻和深色后翻的上下轮廓，距离竖直纸边各内收 **38px**（采样 y=5 和 y=2394），证明两个圆角实际裁切。`light-forward-30.png`、`dark-forward-30.png`、`dark-backward-30.png` 已目视核对；正文 / 页码随纸面，原系统区域没有固定白带。
- 慢拖 8 → 9 → 8，180ms 快滑每次一页；浅色越过半页后拉回取消仍为第 8 页。控件 / 目录 / 设置显隐，前后台和主题切换保留正文 / 坐标 / 页码。冷开刚进入分页恢复阶段时采到临时第 1 页，停稳后自动恢复第 8 页并与原正文一致；此证据只证明最终锚点恢复，未认定冷开首帧无闪现。
- 录屏 `verified-slide-demo.mp4` 为正式包 1080 × 2400、约 3.77 秒、1,128,069 bytes，含前后翻页，逐帧图 `demo-overview.jpg` 已查看。最终恢复原浅色主题、原书第 8 页。崩溃缓冲为空。

## 严格像素诊断与边界

`verify.py` / `verification.json` 保留 **25 项严格检查，23 项通过**，另外两项零容差像素检查仍失败，没有削弱断言或扩大容差：

1. 控件显示前后正文逐像素不完全一致。文本、正文边界、页码都相同；差异集中于字形边缘，最大通道差 4，21,210 / 1,861,314 个像素变化，平均通道差约 0.014。隐藏控件并结束测试后，正文与升级前完全一致。本轮未进一步归因颜色差异，不能宣称控件切换全程像素完全一致。
2. 下层图像采样 `(900,920)–(1020,1630)`：浅深色前翻两阶段完全一致；深色后翻两阶段 92 / 85,200 个像素最多差 1 个颜色值。对应 Pager 静止层的几何验证通过，但逐像素零差异未通过。原阶段报告也记录过该级别的 GPU 采样差异，本轮仍按实际值保留。

详细值见 `pixel-comparison.json`。这些颜色对照结果不作为全窗口圆角 / 系统栏隐藏的失败或像素完全一致的通过证据；后两者分别由实际轮廓像素和原生 InsetsSource 状态证明。

状态：**implemented / automated_verified / emulator_verified_for_corners_and_system_bars**；**physical_device / human_feel pending**。当前只连接 `emulator-5554`，用户提供的视频是上一包实机失败证据，未用它声称新版手机通过。真实手机 GPU / OEM 系统栏、刷新率、手感和长书性能尚未验收；未公开发布或 tag。
