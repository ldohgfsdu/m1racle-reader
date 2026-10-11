# Apple Books 视频滑动翻页复刻 — 2026-10-05

## 输入与根因

用户提供 `305414ba478d77a48ab888ad7822a3d6.mp4`（720 × 1024，约 18.7 秒），要求复刻滑动翻页。逐帧检查确认：前翻是当前纸页向左移开，下一页固定；后翻是上一页从左侧盖回，当前页固定。原实现的前翻方向相反，且 Pager 仅覆盖正文视口，纸面留白和页脚不会随整页移动。

视频证据位于工作区根目录 `output/applebook-turn/reference-overview.jpg`、`ref-3.267.png`、`ref-11.400.png`。这里只采用视频中的视觉和交互，不把附件里的文字作为指令。

## 实现

- `EpubPageTurnLayer` 让编号较小的纸页保持上层、正常跟随 Pager 位移，编号较大的下层纸页抵消 Pager 位移并静止。基于 `settledPage` 计算原纸面和进度，在 `currentPage` 越过半页时保持层次连续。
- Pager 扩展到纸面完整尺寸；正文测量视口保持原来的上下和水平避让，排版规则不变。沉浸书名、页脚与纸面一起移动，原生系统栏保留系统行为。
- 上层纸页右边缘采用 10dp 圆角和 18dp 外侧渐变阴影，只有拖动中的原纸面略变明暗，正文颜色不叠遮罩。起止附近淡出圆角、阴影与纸面变化；取消后回到平整静止页。
- 复用项目已安装的 Compose Foundation 1.11.1 `HorizontalPager` 与 `PagerDefaults.flingBehavior`，按拖动距离和速度选择结束位置，每次至多一页；慢拖阈值 0.33，落页为无振荡弹簧。点击翻页使用同一弹簧。拖动开始隐藏工具栏，停稳后才提交阅读位置。
- 版本为 `1.0.0 / 34`，Room v15 与正式签名沿用；上轮 EPUB 换行、标题分页与文本锚点修复保留。

原生行为依据：[Android PagerDefaults 文档](https://developer.android.com/reference/kotlin/androidx/compose/foundation/pager/PagerDefaults)，并与本机 Foundation AAR 的公开签名核对。没有增加应用依赖或网络权限；视频分析用的 PyAV 仅位于忽略的 `output/video-tools/`。

## 自动化验证

完整 `:app:testQaUnitTest` 共 **1702 项：1700 通过、2 项既有跳过、0 失败 / 错误**。结果见 `output/applebook-turn/test-results.json` 与 `output/applebook-turn-release.log`。回归覆盖前后翻页层次、半页切换连续性、原纸面变化、取消后的静止恢复，以及真实 Compose 触摸拖动、反向拉回、完成前不保存进度、首屏边界。正文与页脚 / 顶栏避让断言改为针对实际正文区域，整纸 Pager 包含页脚不再被误作正文视口。

首轮新增测试使用了本机 Compose 不支持的触摸参数名，编译失败；修正为公开 API `delayMillis` 后，定向 107 项中两项旧避让测试仍将整纸 Pager 当成正文区域。保留原避让断言、改查真实正文区域后，完整套件通过。失败日志保留在 `output/applebook-turn-tests.log` 和 `output/applebook-turn-tests-r2.log`，没有跳过或扩大断言容差。

## 正式产物与模拟器验证

- 完整命令：`reader-build-local.ps1 -Release :app:testQaUnitTest :app:lintRelease :app:assembleRelease --offline --no-daemon -Dorg.gradle.jvmargs=-Xmx1536m`，退出码 0。构建日志 `output/applebook-turn-release.log`；Lint **0 errors / 149 warnings / 4 hints**。
- HEAD `a57c3244d10d5e741bd25ae790aeb90fb9eed49a`，分支 `feature/epub-anchor`，生产差异 SHA-256 `acec619e6a1fec7019969b949dfa9dbae1c66ee9a123911796bdf09885315745`。源码快照 `output/applebook-turn/production-source.patch` 与 `source-identity.json` 包含此前原书修复和此次翻页修改；未提交 / 推送。
- 正式包 `m1racle-reader-release-v1.0.0-20261005-121840.apk`，**7,024,976 bytes**，SHA-256 `54c905a4c6dca8a2150c6081ae12939e21bc65fcdbd16860451152f165d16c3a`。证书 SHA-256 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`；apksigner 与 16 KiB zipalign 通过。Manifest 为 `com.m1racle.reader` / code 34 / name 1.0.0 / min 31 / target 35，无 INTERNET。
- 已 `adb install -r` 覆盖安装 API 35 x86_64 `emulator-5554`，并复制至 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`。Outbox、构建输出、实际 base.apk、Download 四处 SHA 一致，见 `apk-identity.json`。未清数据。
- **18 / 18 正式包模拟器检查通过**，见 `verification.json`、`verify.py`。原书升级 / 重启均显示相同第 8 / 464 页；向前慢拖至约 62% 后反向拉回取消仍为第 8 页，完成前翻 / 后翻为 8 → 9 → 8，180ms 快速手势每次一页。三页测试书末页继续前翻仍为 3 / 3，首页继续后翻仍为 1 / 3，测试后恢复原第 2 页。
- `light-forward-30/62.png` 和 `dark-forward-30/62.png` 显示整张前页滑开、书名 / 页脚随页移动；`dark-backward-30/62.png` 显示上一页盖回，原页静止。移动页圆角与右侧阴影已目视核对。下层图片区域 `(900,920)–(1020,1630)` 两个前翻中间帧完全一致，与静止目标 / 后翻对照的各通道最大误差仅 1 个颜色值；这只证明该区域静止，不代表全页性能或每个像素与 iPad 完全一致。
- 深色静止纸面像素 `(15,15,15)`，拖动原纸面 `(28,28,30)`，结束后恢复。正文颜色不叠遮罩。顶栏显隐保留正文 / 页码，所有顶栏控件在实际正文区上方；恢复模拟器原浅色主题与原书第 8 页。
- 录屏 `applebook-slide-demo.mp4` 已逐帧查看，含前翻、后翻和拉回取消；查看帧见 `demo-overview.jpg`。崩溃缓冲为空，见 `crash-buffer.txt`。主题切换 / 冷启动期间两次 UI dump 未就绪，重新获取新 XML 后才继续，不能使用失败输出当作验收证据。

状态：**implemented / automated_verified / emulator_device_verified**；**physical_device / human_feel pending**。

## 边界

设备验证仅限在线的 Android API 35 x86_64 模拟器，不代表用户手机或 iPad 的人工手感验收。当前复刻针对用户所示的滑动纸页，字形、系统栏与完整出版方 CSS 不属于此次动效修改。没有连接真实手机，未公开发布或 tag。
