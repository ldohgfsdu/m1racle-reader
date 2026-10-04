# 分段滑块玻璃材质 — 2026-10-03

用户追问“玻璃呢”。此前仅实现平面绘制的底座、渐变高光和形变，未接入实时采样折射；此次补齐真正玻璃渲染。

PillSegmentedControl 复用 glassMaterial(Capsule) 与现有 Backdrop：隐藏的 plain track / label Row 独立捕获至 LayerBackdrop，玻璃滑块作为兄弟层采样，局部 GlassBackdropHost 使 Hub 捕获子树及阅读设置弹窗均能正确采样，玻璃本体不进入自身来源。前景在透镜范围内使用同一几何清除，避免原文字与折射文字重复；按压采样文字放大 12%，镜片横向放大 6%、纵向放大 12%。沿用 Capsule 配方的按压透镜高度 10dp / 强度 14dp、边缘高光、色散与内阴影；该配方保持 blur=0 以维持标签清晰，不声称毛玻璃模糊。静止时轻灰选中态，按压后更通透。低能力路径保留前景文字在玻璃表面上方，避免被实体表面遮住。没有添加依赖、没有改动全局材质数值。

触摸语义、38dp 默认视觉高度、至少 48dp 点击区域、连续拖动和松手提交 / 取消均保留。共用实现覆盖文库与阅读字号 / 行高 / 边距。代码仅修改 PillSegmentedControl.kt；同步 design.md、CHANGELOG.md、候选版本说明及当前状态。其他用户未提交修改保留。

验证：

- 首次 :app:compileReleaseKotlin :app:testQaUnitTest（控件 / 首页布局 / ViewModel 三组）:app:assembleQa：退出码 0，30 项通过。
- 最终 :app:compileReleaseKotlin :app:testQaUnitTest --tests '*PillSegmentedControlTest' --tests '*GlassMaterialRegressionTest' --tests '*GlassBackdropTopologyContractTest' --tests '*GlassOpticalPolicyTest' :app:assembleQa：退出码 0，34 项 / 0 失败 / 错误 / 跳过。
- QA 包 com.m1racle.reader.qa 安装 emulator-5554 成功。文库静止与按压拖动：镜片跟手、实时采样文字、边缘高光 / 折射可见，松手 EPUB 过滤正确。最终 APK 录制 6 秒 glass-demo.mp4。阅读设置弹窗“字号大”按压截图证实独立窗口光学采样；深色 200% 字号按压截图已核对，主题和字号恢复浅色 / 100%。不是实机验收。
- :app:validatePublicReleaseConfiguration：退出码 1，缺少正式签名环境注入，未生成 / 交付 Release、未访问私钥。
- git diff --check：退出码 0。完整套件、真机、TalkBack、性能专项与 Android 12 显示验收未执行。仍为未公开候选 1.0.0 / 23。

unmanaged；本机项目契约及中央工程公约不可用。受监管 runId / projectId / taskId / machineId 均 unknown。HEAD a314247d9cc27f7b3adc1edb3074b65eee3afbfe，分支 codex/fix-project-audit。外部证据在用户目录 .devhub/runs/unmanaged-reader-segment-glass-20261003/：日志、UI 树、截图、录屏、Git 指纹、QA 大小 / SHA-256、handoff。

剩余：正式签名注入后构建 / 交付 Release，真机验收。旧正式 APK 不含当前修改。