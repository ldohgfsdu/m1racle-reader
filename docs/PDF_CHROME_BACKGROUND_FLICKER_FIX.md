# PDF 顶部控件背景闪烁

2026-09-05。基线 ca40a89（产品源码 691cb65）。

## 调查检查点

用户确认：文字或图案从控件下方经过时，移动与缩放均触发。
旧录像 more-sheet.png 中按钮边界和图标连续存在，变化是背景明暗；旧 capsule-sheet.png 中的黑色大字是 PdfInfoCapsule 自身标题跑马灯，不是透出的 PDF 正文。旧归档调查的“blur 消失/正文印刷对比度穿透”不能当作已确认根因。

现有配方直接保留采样内容 contrast=1，浅色 Control/Capsule surfaceAlpha=0.50、深色 0.68。12dp Gaussian blur 降低字形边缘频率，但无法消除大块图案或放大字形的平均亮度变化。两主题的黑白采样会造成约 0.40/0.26 的最大控件背景通道变化。该机制与用户描述、旧录像亮度起伏一致，不是控件显示状态变化。

连续模式还有确定的合成缺口：父 Box 背景未进入 root capture，纸边/页缝可采样到透明区域。修复应让实际可见背景和采样内容一致。

采用既有 Backdrop colorControls 能力限制 PDF 控件采样的对比度，保持浅色白场与深色黑场基准；实时模糊/折射保留，不按手势切换材质或冻结正文。设计参考：[Apple Meet Liquid Glass](https://developer.apple.com/videos/play/wwdc2025/219/) 对滚动内容下方控制层的可读性处理。库源码已核对项目正在使用的 backdrop-android 2.0.0 sources。

ADB 单次探测为空。尚无本轮真机验收；没有证据证明驱动/RenderEffect 失效，也不声称该类问题已排除。

## 验证

- implemented：PDF host 标记文档采样上下文；材质引擎将该上下文 contrast 固定为 0.20，以浅色白场/深色黑场修正 brightness；背景回调按背景色 remember，捕获背景后再捕获正文。
- automated_verified：完整 `:app:testQaUnitTest --no-daemon` 1396 / 0 / 0 / 0（JDK17）；新增 3 项回归覆盖黑白背景调制幅度、手势/按压参数恒定与采样源背景接线。既有非文档配方测试原样通过。
- 模型范围：黑白大片采样的最终背景通道跨度降至原来的 20%，浅色约 0.080、深色约 0.052；这是颜色合成计算，不是实机帧测结果。未改变 alpha、blur、lens、阴影或按压弹簧。
- device_verified：NOT_RUN，本轮 ADB 为空。
- pending：两种 PDF 阅读模式的移动/缩放、浅深主题及页缝场景真机验收。

## 构建与交付

- 源码提交：`e75b7c432dad081223bc56bcd45322f13cbf3a08`；版本 1.0.0 / 23（本地修复候选，未改 tag/Draft）。
- `:app:assembleRelease --no-daemon`（JDK17）：exit 0，3m 27s，53 tasks。
- APK：6686026 bytes；SHA-256 `8a12f7df3c3c1590c8bec98a02f259cc8479c0dbcfb7f97a20cc3179c9b7e267`。
- 既有 production signer，v2 验签通过；Manifest 包名与版本正确，无 INTERNET。
- outbox latest、Download latest、按 source/hash 命名的归档副本字节一致；checksum sidecar 已更新。
- 安装入口：`/storage/emulated/0/Download/m1racle-reader-release-latest.apk`。
- 构建/测试/签名/Manifest/身份 JSON 证据：`.external_outbox/pdf-chrome-flicker-20260905/`（gitignored）。
- `lintVitalAnalyzeRelease` 再次出现既有 ASM `NegativeArraySizeException`，构建 exit 0；没有关闭检查或调整依赖，不声明完整 lint 通过，该独立问题继续 pending。
- ADB 本轮为空，未安装、未清数据；旧录像只能证明旧版本现象，不能作为本修复的 device PASS。

本轮风险为中：改动位于共享材质合成，但文档上下文仅由 PDF host 启用。非文档配方、按压参数与拓扑回归均通过。最终视觉验收仍需要同一文档文字/图案下的移动和缩放对照。
