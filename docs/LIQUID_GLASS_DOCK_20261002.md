# iOS 风格液态玻璃底栏

本页保留 10 月 2 日阶段记录。当前配方与新增验证见 [10 月 3 日参考调整](LIQUID_GLASS_REFERENCE_20261003.md)。

目标：按用户要求调整底栏，保留前序审查修复与 UI 排版优化。参考 [Apple Materials](https://developer.apple.com/design/human-interface-guidelines/materials)，Android 使用既有 Compose / Kyant Backdrop，实现实时背景采样、模糊、边缘折射与高光。

## 改动范围

- `LiquidGlassStyle` 为 Dock 设置独立配方：12dp 模糊，浅色 0.48 / 深色 0.50 透色，8dp 边缘透镜高度、24dp 折射量，10dp 柔和阴影；其他阅读控件配方保持原值。
- `GlassDock` 采用完整胶囊外形，选中透镜的附加灰色覆盖减至 0.025，选中图标与标签使用浅/深色蓝色令牌。
- 底栏 68dp 高、左右 16dp 留白、内边距 6dp；标签 11sp / 14sp，图标仍为 24dp；屏幕内容避让继续使用共享底栏尺寸令牌。
- 保留导航所有者、单一选中状态、弹簧滑动、按压形变、拖动预览/释放导航及 RTL 路径。系统光学能力不足时沿用现有可读性降级。

## 验证

Windows / JDK 17：`:app:testQaUnitTest` 针对 `ui.material.*`、`ui.hub.*`、`ui.home.*`，112 项、0 失败、0 错误、0 跳过；新增系统 200% 字号标签边界与最小触摸高度回归。既有点击、拖动取消、快速切换和 RTL 测试通过。

`:app:assembleQa`、`:app:lintRelease` 退出码 0；Lint 0 errors / 156 warnings。底栏修改后未重跑全套，前一 UI 阶段全套基线不能冒充本轮完整验证。

Android 15 x86_64 模拟器 `emulator-5554`：已安装隔离包 `com.m1racle.reader.qa`，首页、文库、书签、设置切换正常；浅色、深色和系统 200% 字号截图已查看，标签未裁切。检查结束恢复 100% 字号和浅色模式。

源码 Git HEAD、dirty 指纹、命令退出码、APK 大小/摘要与截图记录在外部账本 `~/.devhub/runs/unmanaged-reader-dock-20261002/`。此会话为 unmanaged，受监管 runId、机器 ID、任务 ID 均 unknown。没有提交、推送或操作签名私钥。

正式签名 Release、真机光学观感及性能验收仍待完成，QA 不是正式交付包。
