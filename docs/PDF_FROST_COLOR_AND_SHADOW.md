# PDF 厚雾颜色与阴影修正

2026-09-09。当前交付状态见 [CURRENT_STATUS.md](CURRENT_STATUS.md)。

## 输入与定位

用户提供 DCIM/Screenshots 下 2026-09-08 23:57:21、23:57:25、23:57:28 的 PDF 截图。收起控件呈深灰色，部分截图按钮/胶囊外出现方形灰影，胶囊亮度随截图变化。此前猜测“展开后透明度不足”不对应这组截图，不作为本轮修复依据。

颜色路径已确认：PDF 保持原始页面颜色，没有夜间反色；浮层与材质却直接跟随系统夜间模式，以 58% 黑色覆盖白页。材质直接读取系统模式，也无法正确响应局部主题。

阴影路径：现有 Kyant 2.0.0 ShadowNode 使用离屏 GraphicsLayer、BlurMaskFilter 和 Clear 蒙版。Android 官方将 Paint.setMaskFilter 列为硬件绘制的不支持操作，见 [硬件加速支持表](https://developer.android.com/develop/ui/views/graphics/hardware-accel)。结合截图，将该蒙版阴影路径判定为需要替换的渲染兼容风险；尚无该手机 GPU 上的修复后复现证据，不能宣称方影已实机消除。截图中亮度变化也不能仅凭静态图片归因为同一问题。

## 实现

- M1racleReaderTheme 提供当前深浅色状态；材质和设置预览读取该状态，不再绕过主题直接查询系统。
- PDF 仅在文档捕获节点以外，为浮动控件提供浅色主题；纸张渲染、书库和 Markdown 的主题不变。返回、页码、三点菜单和展开内容保持一致。
- 厚雾保持 18dp 模糊、无折射/色散/亮边。白页控件为 62% 白色与深色内容，大面板仍保留 72% 白底。
- 公共外阴影改用 Compose/Android 原生轮廓阴影，关闭 Kyant 自定义外阴影，不保留双重绘制。

## 验证

- implemented：上述修改已接入。
- automated_verified：全量 QA 单测 1461 项，0 失败/错误/跳过；新增系统深色下局部浅色主题与外层恢复测试，以及 PDF 捕获/局部主题和原生阴影接线检查。Debug/Release 构建、生产签名、无 INTERNET 权限、Download 摘要一致性通过；Release lintVital 成功。
- Release：6,883,046 bytes；SHA-256 `6cfa514b7edf7ff0d8910f37e4a2d48b55c8eddd1741b86043d350c4dd452307`；已覆盖 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk` 并写入 `.sha256`。Debug 基线 79,490,851 bytes。
- 完整 lintQa 上轮受既有 Kotlin/Compose 检测器 ABI 不兼容阻塞；本轮没有升级或绕过检测器，不声称完整 lint 通过。
- device_verified：NOT_RUN；一次 ADB 探测无在线设备。
- pending：在同一 PDF 页面重复显示/隐藏、按压、展开/收起及滚动，对照方形灰影和亮度变化；检查浅色厚雾是否符合用户期望。

证据目录 `.external_outbox/pdf-frost-correction/`。
