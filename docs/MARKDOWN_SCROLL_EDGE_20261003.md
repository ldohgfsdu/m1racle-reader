# Markdown 顶栏滚动边缘修正

参考：[Apple Scroll views](https://developer.apple.com/design/human-interface-guidelines/scroll-views)。用户提供的说明要求边缘效果用于分隔滚动正文与工具栏，不能像遮罩一样压暗内容。

根因：ReaderGradient 使用 ProgressiveChrome 的固定浅灰 / 黑色 tint，旧过渡范围只有工具栏下 24dp，而且背景不随控件收起。

实现：ReaderGradient 使用 MaterialTheme 阅读背景色并保留引擎提供的模糊与透明度；ProgressiveChrome 透明度为 0.90，工具栏区域保留完整遮蔽，最后 48dp 渐变融入正文。随控件收起缩短过渡范围，仅保留状态栏下方 16dp 的可读性保护，工具栏外框与正文布局保持固定。PDF 材质和底栏角色未改变。

自动验证：Release Kotlin 编译、79 项相关 QA 单测、QA 构建与 Release Lint 全部通过；Lint 0 errors / 158 warnings / 4 hints。新像素测试直接绘制真实 Compose View，在浅色和深色页面验证顶部、中间、下缘均不产生额外暗色。临时恢复旧 tint 时测试失败（root-cause-regression.log），恢复修正后通过。最初 captureToImage 在 Robolectric 超时，改为 View.draw(Canvas) 后完成真实像素断言，未跳过断言。

模拟器验收：Android 15 emulator-5554，独立 QA 包安装；Markdown 长文实际滚动、收起、反向滚动显示、深浅色切换、搜索展开均检查，截图在外部证据目录。测试文件仅在 QA 包导入。

Release 配置门禁退出 1，缺少正式签名四项环境配置，未执行正式 Release APK 交付；未运行完整测试套件与真机验收。

证据：C:/Users/21234/.devhub/runs/unmanaged-reader-markdown-edge-20261003/。本轮为 unmanaged，runId / projectId / machineId unknown。
