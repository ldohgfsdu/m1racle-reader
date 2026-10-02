# 设置页液态玻璃参考实现 · 2026-09-24

用户在查看 lightmemo 源码对照后明确要求“借鉴”。本轮替换设置页默认玻璃预览；不将未经设备验证的新参数推广到阅读器、主页或弹窗生产材质。保留先前审查修复工作区。

## 来源与边界

参考 [lightmemo 83d5320](https://github.com/Cclicking/lightmemo/tree/83d53208794a93d1eff3a6ee9b492cb1805d6878)：`LiquidTopBarButton.kt`、`LiquidBottomTabs.kt`、`BlurBottomSheet.kt`。吸收小控件低模糊/折射/按压、高密度大面板不折射的角色区分，以及先处理背景再绘制表面色的顺序。未复制对方 vendored Backdrop、边缘光 shader、业务组件或本地 miuix 依赖；零新增依赖，使用本项目既有 Backdrop 2.0.0 的 vibrancy/blur/lens/方向高光。

## 实现

- 默认入口由“生产映射”替换为“液态玻璃”，删除 SoftGlassMapping 及其旧解析/说明代码。H0/H1/H2 仍是对照选项，不是新方案的运行 fallback。
- 胶囊：4dp 模糊、8dp 折射边带/24dp 折射量，浅色白 tint .60、深色中性 tint .64，方向高光、8dp 外部投影；按住时最大放大 3.5%，释放/取消时 spring 回弹。变换只交由 Backdrop layerBlock 处理一次，避免外层缩放破坏采样补偿。
- 面板：24dp 模糊、.90 表面色、不做折射，不响应胶囊按压动画。深浅色各自使用明确表面色。
- 光学顺序 vibrancy → blur → lens → surface；删除默认方案的双重提亮/降对比度组合。折射只在 Android 13+ 启用；Android 12 保留平台模糊与库高光能力。
- 完整/仅模糊/无模糊诊断阶段继续隔离；旧柔光对照的 PreviewTemporalIsolation 不变。新预览提示与按住反馈入口对应实际行为。
- 测试将旧映射的数值快照替换为新角色契约：深浅色两组、胶囊有折射、面板无折射且可读性 tint 更高、重复解析稳定。其余冻结生产配方和 H0/H1/H2 回归继续保留。

## 验证检查点

- implemented：以上实现完成。
- automated_verified：82 项材质/Hub 单测全部通过，完整 lintRelease PASS（0 errors / 158 warnings / 4 hints）；未新增 lint 屏蔽。日志 `.external_outbox/settings-liquid-glass/verify2.log`。
- device_verified：NOT_RUN；本任务一次 `adb devices -l` 为空。
- pending：人工在设置页比较深浅色、正文/图案、播放背景、按住/释放/滑走取消，检查边缘闪烁与清晰度。效果未经过同机对照，不宣称视觉通过。

## Release 交付

- 正式签名 assembleRelease PASS（1m 9s），APK 6,950,677 bytes / 6.63 MiB；1.0.0/23。零新增依赖，压缩包体与上一版相同，DEX 内容已更新（4,652,452 bytes；含新预览标识、不含旧生产映射文案）。
- SHA-256 `61b04675646a38ee77e4dea03c3c928e315637657b6070b9a1e84f7dda5584e3`。签名、manifest、无 INTERNET 检查通过；构建输出 / Outbox / Download 摘要一致，已覆盖 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`。
- 日志及摘要：`release.log`、`delivery.json`、`signature.log`、`apk-badging.log`、`source-hashes.json`。未安装，device_verified=NOT_RUN；未提交代码。
