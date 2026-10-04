# 首页底栏玻璃与导入加号按压修复 — 2026-10-03

用户反馈底栏玻璃消失、长按加号没有按压效果。本轮定位并修复两处根因，使用用户已授权的生产证书构建 **1.0.0 / 25** 正式签名侧载验收包。真机与人工视觉 / 手感验收仍 pending，没有公开发布或 tag。

## 根因与修改

首页 `HomeContent` 原先给整个 Column 预留底栏高度，最近阅读列表在玻璃上方结束。真实页面采样因而只能获取底栏下方空白，白色背景上看起来像普通白底；这不是 Release 专用的光学降级。现在首页只在外层保留系统安全区，列表延伸到悬浮底栏后方，以尾部 `DockContentClearance` 保证最后一项可滚至无遮挡的位置，固定顶栏和空态避让保留。

底栏底座的 Dock 配方关闭通用缩放，由导航透镜自己承担按压形变。独立加号沿用底座，但没有自己的形变，因此按住只微调高光。现在加号使用共享 `M1racleMotion.dockPickup` 弹簧，玻璃圆钮与独立前景加号同步最多放大 12%；触摸范围不变，取消 / 松手回弹。点击仍由标准 `clickable` 在释放时触发，忙时禁用；没有把加号纳入横向拖动导航。

产品源码仅修改 `ui/home/HomeScreen.kt`、`ui/hub/GlassDock.kt` 和版本文件。`GlassDockUiTest`、`HomeViewportUiTest`、两个已有结构 / 运动契约和版本断言同步真实行为；新增 QA 专用 `DockGlassHarnessActivity`，复用实际首页与底栏，用内存长列表或图案检查硬件光学，未写入 Room，也未进入 Release。PDF 材质、竖排模式选项和 Markdown 顶栏没有新修改。

## 自动化验证

- 根因：修复前运行 `:app:testQaUnitTest --tests '*GlassDockUiTest.held import*' --tests '*HomeViewportUiTest.header stays*'`，两个测试均失败，退出码 1；修复后完整套件均通过。
- 回归：列表确实延伸到玻璃后方、顶部固定、末项可完整露出；加号按住 900ms 有界放大，期间不导入，取消复原不导入，释放导入一次；原忙碌状态和窄屏大字号约束保留。
- full：`gradlew.bat :app:testQaUnitTest :app:assembleQa :app:lintRelease --console=plain`，退出码 0；**1636 项 / 0 failures / 0 errors / 2 skipped**，实际通过 1634 项。两项原有跳过为外部教材 fixture 与 Windows 符号链接能力，未新增跳过或删除测试。
- static：Release Lint **0 errors / 158 warnings / 4 hints**，没有忽略警告。
- release：`gradlew.bat :app:assembleRelease --console=plain`，退出码 0；R8 minify / resource shrink 开启。版本 25、生产证书连续性、签名、16 KiB 对齐和无 INTERNET / 非 debuggable Manifest 检查通过；QA 夹具不在正式 Manifest 中。

定向测试首次仍有一个旧水平轨道文本断言，按新的等价 start / end PaddingValues 写法修正，仍检查两端同一内容轨道，没有降低约束；完整测试随后重跑。设备验证脚本首次把 Markdown / EPUB 原始文件名误当作首页解析标题，已改为核对升级前保存的真实标题与进度，错误记录保留。

## 模拟器证据

Android 15 `emulator-5554`，1080×2400：

- QA 复用生产首页，40 行内存列表的第十行进入玻璃后方；滚动后采样随内容变化。浅 / 深色图案可见实时模糊、边缘折射和高光，前景图标没有进入采样源。
- QA 浅色加号像素宽度 37 → 41 → 37，深色 35 → 39 → 35（静止 / 按住 / 取消）；栅格取整与抗锯齿使像素比例略小于连续 12% 变换。
- 正式包由 24 覆盖安装为 25，生产加号像素宽度 37 → 41 → 37；按住 1.2 秒仍在 Reader，移出取消不打开选择器，释放点击打开 SAF，取消返回首页。冷启动后保留三种文档、Markdown 24% 及 PDF 1 / 3 位置。当前 crash buffer 无条目。
- 生产包、Outbox、模拟器 Download 文件与安装 base.apk 摘要一致。模拟器结果不称为真机验收；短列表下方原本就是纯白背景，玻璃仍合理呈白色，不以纯白静态截图断言光学失效。

## 签名产物与交付

版本 **1.0.0 / 25**；大小 **6,993,653 bytes**（6.67 MiB）。

SHA-256：`f37c8746b12a6b194a64233dd44d423817ff71885b61f9a59e8623509f72f105`。

证书 SHA-256：`2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`，与原备份正式包一致。继续使用用户明确授权的签名附件，通过四项环境变量临时注入；签名临时副本在仓库外并于构建退出后移除，不输出口令或私钥。

主机 `.external_outbox/m1racle-reader-release-latest.apk` 与同目录 `.sha256`；来源本轮 `app/build/outputs/apk/release/app-release.apk`。设备固定交付位置 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`，复制时间 **2026-10-03T06:37:30.671801+00:00**；当前只有模拟器在线，没有宣称用户手机已收到。

## 身份、证据与剩余事项

unmanaged；runId / projectId / machineId unknown；Codex，模型 GPT-6，版本 unknown，Windows。项目契约和中央工程公约在本机不可用。

分支 `codex/fix-project-audit`，HEAD `a314247d9cc27f7b3adc1edb3074b65eee3afbfe`；保留既有未提交修改，无提交或推送。构建时 dirty fingerprint `f54430abd13d13b4d01890940769d275a51de90523c798629d36e1af9282636c`；构建时间 **2026-10-03T06:35:19.546814+00:00 至 06:36:04.207442+00:00**。构建后仅整理文档，源码指纹单独核对。

外部证据目录 `C:/Users/21234/.devhub/runs/unmanaged-reader-dock-feedback-20261003` 保存修复前失败日志、完整单测 XML、Lint、构建、签名 / 对齐 / Manifest、安装、UI XML、静止 / 按住 / 取消截图、像素测量、源码指纹与 handoff。前一包证据及原 APK 备份保留。

implemented / automated_verified / emulator_verified；physical_device 与 human_acceptance **pending**。待用户真机确认滚动时玻璃、加号按压手感及原长书性能门禁；公开发布、tag、提交和推送未执行。
