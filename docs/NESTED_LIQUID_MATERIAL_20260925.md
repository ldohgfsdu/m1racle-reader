# 二级面板液态材质 · 2026-09-25

用户指出教材目录等二级界面的展开问题，随后明确要求“先改材质”。本轮仅调整材质，动画问题保留待后续授权继续。

## 改动

教材目录、教材页码、页面缩略图、导出章节均由 AnchoredNestedPopover 的 SettingsCard 渲染。将 SettingsCard 接入与 Menu 相同的液态配方：4dp 模糊、8dp 边带/24dp 折射、浅色 .60 / 深色 .64、共享高光/阴影。材质 family 使用 CONTENT，保留 RootAndMenu 层叠采样及原有平台能力约束。

实际运行代码只改 LiquidGlassStyle.kt、GlassMaterial.kt。未改 ReaderMoreHost.kt、AnchoredNestedPopover.kt、AnchoredNestedGeometry.kt，修改前后摘要一致。无新依赖，风险低。

## 验证与交付

- implemented：材质完成；动画没有修改。
- automated_verified：95 项材质测试通过，0 failures/errors/skipped；新增二级与一级材质光学一致性及层叠采样回归。完整 lintRelease 0 errors / 158 warnings。
- 正式签名 Release、ZIP 完整性、无 INTERNET 校验通过。版本 1.0.0/23，6,950,677 bytes / 6.63 MiB。
- SHA-256 `a3bbf8f1c7fe2b324610c700a237112f705b50e2cb284f18e49d48eddc2445bc`；构建输出 / Outbox / `/storage/emulated/0/Download/m1racle-reader-release-latest.apk` 摘要一致，已自动覆盖。
- device_verified：NOT_RUN（一次 adb devices -l 为空）。pending：用户安装后确认四类二级面板材质；展开动画另行处理。

证据 `.external_outbox/nested-liquid-material/`：verification.json、test-results/、lint-release.xml、release.log、delivery.json、unchanged-motion-hashes.json。未提交 Git，既有工作区保留。
