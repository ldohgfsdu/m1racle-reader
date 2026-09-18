# 厚雾显式投影

2026-09-09。当前状态见 [CURRENT_STATUS.md](CURRENT_STATUS.md)。

## 实机输入

用户截图 `/storage/emulated/0/DCIM/Screenshots/Screenshot_2026-09-09-11-50-30-825_com.m1racle.reader.jpg`：圆按钮及胶囊形状存在，这一帧未见此前方形灰框；浅灰底已生效，但投影极淡，白纸上的悬浮层次不足。此次不把现象描述为“完全没绘制阴影”或“形状丢失”。

上一版使用 `Modifier.shadow(elevation=6dp)`。该 API 按高度和光源计算阴影，传入阴影颜色的 alpha 不等于屏幕上可见投影的 alpha，也不能直接设定模糊半径和偏移。官方说明见 [Compose 阴影](https://developer.android.com/develop/ui/compose/graphics/draw/shadows)。

## 修改

复用项目已经解析到的 Compose UI 1.11.1 的 `dropShadow`，无依赖升级。

- 删除公共材质中的 elevation 外阴影路径，采样玻璃和弹出窗口底板统一使用一个投影函数。
- 常规浮层直接绘制 6dp 模糊半径、向下 2dp、35% 黑色投影；圆形和胶囊分别使用实际形状，投影位于底板裁剪之前。
- 保留 F5F5F5 浅灰底、62% 表面不透明度、18dp 背景模糊、PDF 局部浅色主题、尺寸和布局。不增加色散、折射或高光边缘。
- Kyant 蒙版外阴影继续关闭，没有与旧阴影叠加。

## 验证

- implemented：显式投影已接入。
- automated_verified：全量 QA 单测 1462 项，0 失败/错误/跳过；Debug/Release、生产签名、无 INTERNET 权限及 Download 摘要检查通过，Release lintVital 成功。新增回归检查确认圆形、胶囊的投影实际使用绘制节点，而非只设置 elevation 的层节点；不将此检查称为像素或实机视觉验收。
- device_verified：NOT_RUN；本轮一次 ADB 探测无在线设备。用户截图仅证明上一版观感，不代表本版已验收。
- pending：相同 PDF 白底下对比悬浮层次、按压及显隐动画，检查菜单展开、阴影边缘和滚动稳定性。

证据 `.external_outbox/frost-drop-shadow/`。

交付：`/storage/emulated/0/Download/m1racle-reader-release-latest.apk`，已覆盖并附 SHA-256 文件。
Release 6,899,430 bytes（比上一版增加 16,384 bytes）；SHA-256 `b9790b30203295d15e780f3e3566b3fb6bef650fd7be236577d0981455bd233a`。Debug 基线 79,490,851 bytes。完整 lintQa 的既有检测器 ABI 阻塞未在本轮处理或重复运行。
