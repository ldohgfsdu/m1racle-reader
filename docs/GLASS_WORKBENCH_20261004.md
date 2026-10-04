# 设置二级页：液态玻璃工作台

用户 2026-10-04 明确要求：以 Aave 参数工作台截图、Apple Materials 指南和 AndroidLiquidGlass 为参考，将原生调参工作台直接加入 M1racle Reader 设置的二级页面。

## 实现

- 设置新增“液态玻璃工作台”入口，独立 `settings/glass-workbench` 路由，进入后隐藏 Hub 底栏，返回恢复设置。
- 复用当前 Backdrop 2.0.0 的捕获、模糊、vibrancy、高光和阴影；透镜通过该库的 `runtimeShaderEffect` 接入扩展 AGSL。SDF 和七通道色散采样来自 Kyant 的 Apache-2.0 实现，保留版权和改动说明，完整许可随 APK 打包。
- 原生滑杆与数值输入共用一份参数状态；支持宽高、圆角、缩放、厚度、曲率、折射、Splay、Chroma、模糊、Opacity、高光、阴影和光线角度。
- 网格 / 光谱 / 文字三种背景，玻璃拖动、外观 / 光学法线分屏、动态光线、深色背景、Regular / Clear 实验预设、重置与 JSON 复制。
- 只保存专用工作台 SharedPreferences，不改实际阅读器 / Dock 材质、阅读偏好或 Room。普通背景与玻璃是兄弟层，使用现有 GlassBackdropSource 所有权边界，禁止自采样。
- 动态光线只在页面 STARTED 时运行，尊重系统动画关闭；玻璃超过视口时等比适配，圆角限制到实际几何半径。
- Android 13+ 启用完整 AGSL 折射和法线；Android 12 禁用相应控件并显示说明，仍支持几何、模糊和高光。

Regular / Clear 是参考 Apple 指南语义的实验配方；这些滑杆不是 Apple 原生公开参数。当前工作台不直接修改生产材质。

## 验证

- automated_verified：28 项 JVM 测试通过（参数 5、工作台 UI 3、既有背景滑杆 3、材质权威 7、Backdrop 捕获拓扑 10）；0 failures / 0 errors。报告独立输出到 `.local-backups/glass-workbench-20261004/unit/`，避免与其他会话构建互相覆盖。
- automated_verified：API 35 模拟器 Android 仪器测试 1 项通过。实际输入验证 Width 滑杆、越界拒绝、精确数值应用和重置；数值输入从 Robolectric 移到 Android 测试，保留原断言。结果在同目录 `native-input-results/`。
- device_verified（模拟器）：正式包从设置入口进入；网格背景实际折射 / 高光、光谱背景、外观 / 法线分屏、固定预览与滚动参数、横屏两栏，以及 JSON 导出弹窗已检查。截图：`release-grid.png`、`release-spectrum-normals.png`、`release-landscape.png`。`release-export.png` 被其他会话的设备操作打断，显示启动器，不作为验收截图。
- Release Lint：0 errors / 159 warnings / 4 hints，包含本轮一项 UseKtx 风格提示。正式签名 v2 和 16 KiB 对齐验证通过。
- 本轮 Release：`.external_outbox/m1racle-reader-release-v1.0.0-20261004-135921.apk`，7,076,184 bytes；SHA-256 `b67739a53588003550f467c61b6bebb61b8b9c9d663954152297adb523cfe4c9`。构建输出、Outbox、模拟器 Download 及安装 base.apk 摘要一致。使用覆盖安装，未清理 Reader 数据。
- pending：真实手机、Android 12 实际设备、动态光线连续帧与大字体设备人工验收尚未完成；未重跑完整项目测试套件。API 31 的控件禁用行为已由 JVM UI 测试覆盖。

## 参考

- [Apple Materials](https://developer.apple.com/cn/design/human-interface-guidelines/materials)
- [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)
- [Backdrop 文档](https://kyant.gitbook.io/backdrop)

现有工作树包含此前多轮 dirty 修改，均保留。本次文件改动限于设置入口、导航路由、工作台文件、字符串、许可和相关测试 / 说明。
