# 设置页玻璃材质对比

状态：用户已选 B；对比界面及其预览路径已移除，正式落地见 [轻折射材质与打磨](GENTLE_FROST_AND_UI_POLISH.md)。下文是当时对比包的历史记录。

2026-09-06。用户反馈边缘色散不好看，要求在设置页放置多个胶囊方案，让图案与文字从后方划过，以便选择效果。

## 实现

设置页新增五个固定 168 × 44dp、圆角 22dp 的胶囊，背景包含文字、细线和彩色图形。左右拖动任一样例或操作进度滑杆，会同步移动所有背景；支持自动往返播放，手动拖动会暂停播放。设置页可纵向滚动，底部为导航栏留出空间。

| 样例 | 折射高度 / 折射量 | 色散 |
|---|---|---|
| A 纯磨砂 | 0 / 0dp | 关 |
| B 轻折射 | 4 / 6dp | 关 |
| C 当前 PDF | 10 / 20dp | 关 |
| D 当前底栏整块底板 | 24 / 24dp | 关 |
| E 色散对照 | 10 / 20dp | 开 |

所有样例继承当前 regularFrost 的染色层、8dp 模糊、vibrancy 和阴影；D 复用 Dock 配方，包括其高光差异。E 仅在 C 上开启色散，用于直接比较，不能当作实际底栏选中滑块的完整复刻。

正式 UI 的材质参数未变；不存储全局材质选择。新增预览配方位于 GlassMaterialEngine，预览与正式控件复用 drawGlassMaterial。每个样例只捕获自身背景，胶囊在捕获层之后作为兄弟节点绘制，避免递归采样。

## 验证

- implemented：设置页对比区与五种材质、同步拖动、滑杆、自动播放。
- automated_verified：compileQaKotlin 成功；GlassMaterialComparisonTest / GlassMaterialRegressionTest / GlassBackdropTopologyContractTest / ReaderChromeMaterialTest 共 30 项，失败 / 错误 / 跳过均 0。新交互测试验证拖动改变共享位置，以及拖动接管播放。
- device_verified：NOT_RUN。一次 adb devices -l 探测为空。
- pending：实机检查真实玻璃折射、彩边、滚动采样与主观方案选择。

证据目录：`.external_outbox/glass-comparison-20260906/`。风险为中：增加实时采样预览，复用正式渲染器；已有材质和采样拓扑回归通过。

## 安装包

签名 Release 构建成功；最终包 6,735,178 bytes，SHA-256 `71f184d1b567ed266297e7adc99d54d4a21031a058064277f5b0730121580d40`。production signer 已核对；outbox 与 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk` 内容一致。构建仍出现已记录的 lintVital ASM / NegativeArraySizeException，不声明完整 lint 通过。
