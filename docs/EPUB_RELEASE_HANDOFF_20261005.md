# EPUB 长按拖动松手闪烁 — 2026-10-05

## 输入与根因

用户实机视频 `a7bc27304fd75a85790f5605109ab6a5.mp4`（592 × 1280，5.27 秒）显示前翻与后翻在松手后发生整片纸面明暗突变。补充的 Apple Books 浅色 / 深色细节视频分别为 `8dfc0091287de7b0565ba9d51132f8ef.mp4`（720 × 1024，9.53 秒）和 `25476a9cc843a544757d842ac1834769.mp4`（720 × 1024，17.63 秒）。附件只用作视觉和手势证据，书中文字不作为指令。

原实现按 `PagerState.settledPage` 选择原纸面、页脚和保存位置。Compose Foundation 1.11.1 在拖动结束、开始 fling 的衔接处重设 settledPage，虽然此时偏移仍非零。拖过半页后，前翻原纸面的提亮瞬间换到下层目标页，后翻原纸面的提亮瞬间消失，然后再次淡出，形成闪烁；页脚与持久化也可能提前切换。

根因不是推测：新增真实 Compose / Robolectric Native 回归在旧源码上两项均失败，松手第一帧前翻的目标纸面红通道 **15 → 28**，后翻原纸面 **28 → 15**。旧报告 / 失败日志保存在工作区根 `output/epub-release-flash/before-test-results.xml`、`before-tests.log`；原书旧正式包录屏为 `before.mp4`，逐帧色值与截图一并保存。

核对依据为本机实际 1.11.1 AAR 的 `PagerState` 字节码，以及 [Android Pager 状态文档](https://developer.android.com/develop/ui/compose/layouts/pager) / [AndroidX PagerState 官方源码](https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/pager/PagerState.kt)。报告针对本机版本，不把官方文档对 settledPage 的概要当作此交接点的运行证据。

## 修改

- EPUB 内容维护一个已停稳的 `readingPage`：仅当 Pager 未滚动且实际偏移为零时更新。原纸面、页脚、正文活动区域和阅读位置保存都消费同一值，删除原先用 settledPage 重新推导的路径。
- 拖动、松手落页、再次按住打断、反向拉回期间保持原纸面身份；使用同一无振荡弹簧，保留系统距离 / 速度落页规则、24dp 圆角、轮廓阴影、全窗口沉浸和文本锚点。
- 停稳流过滤交接期间的非停稳状态，并去重相同页。首轮修复已通过颜色回归，但既有取消手势测试发现重复保存同一位置（2 次变 3 次）；保留断言，修正流去重后相关 38 项全部通过，没有弱化测试。
- 增加前翻 / 后翻松手逐帧颜色、页脚和保存位置的回归，以及落页未结束时重新按住拉回取消的回归。
- 正式版本 `1.0.0 / 36`，Room v15、依赖和权限不变。生产签名不变。

## 验证与交付

完整命令 `reader-build-local.ps1 -Release :app:testQaUnitTest :app:lintRelease :app:assembleRelease --offline --no-daemon -Dorg.gradle.jvmargs=-Xmx1536m`，**8m 34s，退出码 0**。日志 `output/epub-release-flash/release-build.log`。

- QA JVM **1708 项：1706 通过、2 既有跳过、0 失败 / 错误，220 suites**。新增三项真实 Native Compose 回归均通过，保留松手逐帧精确色值、提前提交页脚 / 保存位置与再次按住反向取消的断言。
- Release Lint **0 errors / 149 warnings / 4 hints**。
- HEAD `a57c3244d10d5e741bd25ae790aeb90fb9eed49a`，分支 `feature/epub-anchor`，保留此前未提交的 EPUB 修复。生产差异 SHA-256 `51f8feb6541e9e734588b194dbfef2cafe6d25a779baf82eb9969d142ea31ef0`，包含新增系统栏源码；快照 `production-source.patch` 与 `source-identity.json` 在工作区根目录 `output/epub-release-flash/`。
- 正式包 `m1racle-reader-release-v1.0.0-20261005-144922.apk`，**7,041,360 bytes**，SHA-256 `40b03b786119a5705d5d29df059bf1f9b4c948ce038147e2e71b18be5c202084`。apksigner、16 KiB zipalign、Manifest 检查通过：code 36 / name 1.0.0 / min 31 / target 35，无 INTERNET。证书仍为 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`。
- 已自动复制 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk` 并 `adb install -r` 覆盖安装 `emulator-5554`，未清数据。构建输出、Outbox、实际 base.apk、Download 四处 SHA 一致，见 `apk-identity.json`。

## 正式包运行证据

工作区根目录 `output/epub-release-flash/` 保存原生 XML / PNG / 系统 InsetsSource 状态、触摸事件时间、未改帧的原生 screenrecord、逐帧对照与色值。

- 新包升级后打开原书，录屏首帧已是原第 **8 / 464 页**；纸面 `[0,0]–[1080,2400]`，正文 `[53,315]–[1027,2226]` 和文字均保持。第一次升级打开的早期 XML 曾捕获恢复中的第 1 页，未将该帧认作最终恢复或声称冷开没有中间帧；录屏和停稳核对采用实际第 8 页。
- `after-dark.mp4`（17.97 秒 / 96 帧）与 `after-light.mp4`（18.86 秒 / 96 帧）含长按拖过半页、停住再松手的前翻 / 后翻，以及不足阈值的双向取消。触摸由新 XML 的 Pager 范围计算，未从图片猜点击坐标。每轮返回原第 8 页，正文 / 页码 / 主题恢复已核对。
- 前翻松手至落页，深色采样 `(970,100)` 的解码 RGB 在第 11–23 帧始终 `(13,13,13)`，浅色第 11–25 帧始终 `(255,255,255)`；旧包同段出现 `(13,13,13) → (27,27,27)`，随后再次淡回。后翻原页保持提亮直到被上层盖住，未再出现原包松手时提前变暗；轮廓、跟手层次和收尾逐帧检查。这里的视频数值经过 H.264 色彩转换，不与原始 PNG / Native 单测色值混用，不代表全页性能或每个像素与 Apple Books 相同。
- 浅 / 深色双向取消的实际正文 PNG 与原停稳页 **零像素差异**；菜单显隐仍为同一文字、正文边界和页码。显示控件时双原生系统栏可见，隐藏后双栏隐藏；Home 恢复双栏 DEFAULT；冷开最终第 8 页、正文和栏策略相同。
- `verify.py` 的本轮专项证据检查 **20 / 20** 通过，含产物身份、源文件、完整测试、原页恢复 / 文本边界、系统栏、取消像素和松手采样。它不替代上一轮的 25 项诊断，也未把上一轮控件显隐 / 图片采样的两个严格零差异失败重写成通过。
- 冷启动 / 主题切换期间几次 UI dump 返回 null root；未采用失败的 XML，重新获取就绪状态后继续。崩溃缓冲无条目，未运行 connected 仪器测试。

状态：**implemented / automated_verified / emulator_verified**；**physical_device / human_feel pending**。只有 API 35 x86_64 模拟器在线，手机未连接；未公开发布、推送或打 tag。
