# 柔光玻璃移动文字采样修复 · 2026-09-24

用户报告柔光玻璃掠过文字时闪烁，怀疑渲染不同步，随后要求继续。沿实际预览、Backdrop 2.0.0 Maven sources 和 Compose 布局链确认采样缺陷。没有 GPU 录像，不将之前文档的“上一帧纹理”解释当作已证实根因。

## 已确认的缺陷

1. 柔光管线先调用默认 Clamp blur。Backdrop 2.0.0 的 `effects/Blur.kt` 只有非 Clamp 或前置 renderEffect 非空时才自动增加 padding；当前柔光第一步没有前置效果，实际 padding=0。`DrawBackdropModifier.kt` 按控件尺寸 + 2×padding 录制中间层，因而控件外真实背景没有进入模糊缓冲。移动字形经过边界时会被边界夹取延展，存在非真实明暗变化的路径。
2. 预览先前用普通 width/height 声明比舞台大 96dp 的采样区域，但父布局仍限制它。真实 Compose 测量：360dp 舞台下 capture 实际宽 360dp，期望 456dp，回归测试失败。文字条的普通 width 同样受父约束，和三页总宽的位移计算不一致。

这些证据证明采样区域错误，不证明 GPU 读了上一帧，也不证明它们是用户所有闪烁的唯一原因。

## 修复

- 柔光效果链显式保留不小于 blurPx 的 padding，保持原 blur → colorControls → soft-light → lens 顺序、原数值和折射隔离开关。
- 采样源采用顶部起点的 unbounded wrap，再给出显式大小；三页文字条采用起点 unbounded wrap，实际宽度达到 960dp。舞台仍按原有圆角裁剪；绘制位移保持浮点，不在帧间改变尺寸。
- 提取既有效果 lambda 供真实库 scope 回归测试；生产控件与面板都调用该函数。测试用 Java fixture 构造 pinned Backdrop 的真实 EffectScopeImpl，不模拟 padding 或 blur 行为，不进入 APK。
- 修正 StressMotion 注释：数学帧模型不等于 GPU 同步证据。无依赖升级，不修改生产阅读器材质或上轮液态玻璃配方。

## 回归与检查点

- 修复前：SoftGlassSamplingTest 在 API 31/34 共 6 项，2 项生产采样范围断言失败；默认 Clamp padding=0 的库行为测试通过。日志 `before.log`。
- 修复前：GlassStressLayoutTest 真实 Compose 布局失败，`Actual width 360dp, expected 456dp`。证据 `layout-before.log`、`layout-before.xml`。
- 修复后：70 项材质测试全部通过；真实布局测试确认 360×300dp 舞台对应 456×396dp 采样源、左/上各 -48dp、960dp 文字条。效果测试覆盖 API 31/34、H0/H1/H2、胶囊/面板、深浅色、1/2/3×密度、完整/仅模糊，以及无模糊不膨胀。完整 lintRelease PASS（0 errors / 158 warnings / 4 hints）。日志 `after.log`、`test-results/`、`verification.json`。
- device_verified：NOT_RUN；本任务一次 `adb devices -l` 为空。
- pending：安装后在柔光玻璃里用正文背景播放及上下滚动，分别检查完整/仅模糊档是否仍闪。若仍闪，需帧级画面或在线设备继续区分 GPU/动画/边缘变化。

证据目录：`.external_outbox/soft-glass-sampling/`。

## 最终交付

- implemented：两处采样/布局缺陷已修复；风险为中等（设置预览 GPU 采样区域变化），未改材质配方。
- automated_verified：70/0/0/0 材质测试、完整 lintRelease、签名 assembleRelease、APK 完整性/签名/无 INTERNET 校验 PASS。
- Release **6,950,677 bytes / 6.63 MiB**；1.0.0/23；SHA-256 `92109187e23c44eb7491e4d61200d2ffa1611550f41e614bc35a2d136b8662f7`。
- 构建输出 / Outbox / Download 摘要一致，已自动覆盖 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`。证据 `release.log`、`signature.log`、`delivery.json`、`source-hashes.json`。
- device_verified：NOT_RUN；无法确认用户观测到的闪烁已完全消失，需同场景复验。未提交代码、未改远端发布状态。
