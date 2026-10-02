# 悬浮液态玻璃 · 2026-09-25

用户要求“把悬浮全换成液态玻璃”。基于仓库已记录的 lightmemo 参考与现有设置页方案推广，无新增依赖，无 PDFium 或文档逻辑修改。

## 实现与边界

- LiquidGlassStyle 是正式悬浮材质与设置液态预览的共同参数来源；删除正式 readerOverlayFrost、panelFrost、appOverlayPlate 配方。
- Control / Capsule / Dock：4dp 模糊、8dp 边带/24dp 折射，浅色 .60、深色 .64；Menu / ExpandedMenu / SettingsCard：24dp 模糊、.90 表面，不折射文本密集面板。共享方向高光和 8dp 阴影。
- 保留现有点击/展开/拖动状态及生产按压 spring，不引入预览的 pointerInput 手势拦截。Android 12 按平台能力使用模糊，Android 13+ 使用折射。
- Hub 底栏改为 glassMaterial。导航内容经过 GlassBackdropSource 的隔离边界采样，底栏为后绘制的同级节点；阅读路线禁用此额外捕获，保留阅读器自身采样边界。底栏尺寸、选中条、点击与拖动选页不变。
- 独立平台窗口（Dialog、原生 Popup）不能采样主窗口，继续由能力解析器生成可读表面；Markdown 的整幅渐变顶栏不是悬浮玻璃组件，本轮不重做。H0/H1/H2 柔光实验及残余闪烁调查保持原状。

风险：中（新增 Hub 背景采样）；验证集为材质/采样隔离/底栏交互定向测试、lintRelease、签名 Release 与安装包完整性检查。

## 验证检查点

- implemented：完成；正式 Kotlin 编译通过。
- automated_verified：首轮发现新增可选捕获参数的转发错误，已改为命名参数；首次定向 92 项中 90 通过，2 条旧冻结值断言失败，按用户授权的新配方修订，未删测试或放宽容差。最终独立进程串行重跑 92 项全部通过（0 failures/errors/skipped），完整 lintRelease PASS（0 errors / 158 warnings）。中途 lint 分析器异常及语法树遍历停滞已通过重启本轮进程恢复，未新增屏蔽或跳过检查。
- device_verified：NOT_RUN；本轮单次 adb devices -l 为空。
- pending：用户安装后检查深浅色 PDF 悬浮按钮/页码胶囊/菜单和二级设置，以及 Hub 四入口的滚动采样、点按和横拖。无证据声称真机视觉/闪烁已通过。
- 日志与交付元数据：.external_outbox/floating-liquid-glass/。既有未提交工作保留，未提交 Git 或变更远端。

## 正式 Release 交付

- assembleRelease PASS（4m 18s）；正式签名验证、ZIP 完整性、无 INTERNET 检查通过。版本 1.0.0/23。
- 产物 **6,950,677 bytes / 6.63 MiB**；SHA-256 `064d5200738683a8939c91230ec307b2cc41f36434d9ce60fc18d057d51e5009`。
- 构建输出 / Outbox / Download 摘要一致；已自动覆盖 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`。
- 总包体与此前相同，DEX 实际已更新：4,656,140 bytes，SHA-256 `c8611428e3768516f1405093db3d5ef166213c8023aab8709987543334b02f5f`，区别于前包 DEX 4,653,204 bytes；并非复制旧包。
- 最终源码摘要与验证时一致；验证记录 `verification.json`、`test-results/`、`lint-release.xml`、`delivery.json`、`signature.log`、`source-hashes.json`。
- device_verified：NOT_RUN；用户实机视觉验收仍待完成。
