# 厚雾轮廓预览

2026-09-09；用户要求先制作一版对比。当前状态见 [CURRENT_STATUS.md](CURRENT_STATUS.md)。

## 本版调整

- 共享悬浮材质的浅色底从纯白改为 `#F5F5F5`，保留 62% 表面不透明度和 18dp 背景模糊。大面板保持 72% 浅色底。
- 浮层原生轮廓阴影为 6dp，浅色阴影色从 10% 黑提升至 35% 黑；独立对话框、Dock 内部选中层不套用这一阴影强度。
- PDF 保留白纸上的局部浅色主题与深色文字。折射、色散、亮边继续关闭，不恢复有兼容风险的自定义蒙版外阴影。
- 设置里的“厚雾 · 当前”、首页底栏与 PDF 控件共用这一配方。

参考 HyperChanger 当前 main：`c428b96224d911d7be8621dc1e778f2a5aa49669`。其 [悬浮胶囊](https://github.com/ColdP/HyperChanger/blob/c428b96224d911d7be8621dc1e778f2a5aa49669/app/src/main/java/btm/m/liquidglass/hook/GlassNavigation.kt#L904) 使用 F5F5F5 浅色底及 6dp / 35% 黑色原生阴影。本版仅吸收其轮廓处理，保留用户选定的厚雾模糊，不复制其高光和镜片效果。

## 验证

- implemented：配方已更新。
- automated_verified：全量 QA 单测 1461 项，0 失败/错误/跳过；Debug/Release 构建成功，生产签名、无 INTERNET 权限及 Download 摘要一致性检查通过。Release lintVital 成功；完整 lintQa 仍保留上轮检测器 ABI 阻塞记录，本轮未重复运行。
- Release：6,883,046 bytes；SHA-256 `742c4e8d531e9cdb947c53e491bfbe7a1cac82d652536bc30e26f892f11c0647`。已覆盖 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`，附带 `.sha256`。Debug 基线 79,490,851 bytes。
- device_verified：NOT_RUN；本轮一次 ADB 探测无在线设备。
- pending：用户安装后在同一白底 PDF 对比按钮、页码胶囊和展开菜单的轮廓，再检查正文经过时的模糊与滚动稳定性。

证据 `.external_outbox/frost-visible-outline/`。
