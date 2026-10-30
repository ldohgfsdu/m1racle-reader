# Markdown / PDF 旋转状态稳定性与 85 交付

2026-10-29，Release 1.0.0 / 85，Room17。源码工作树 `D:\03_M1racle Reader\reader-ui-release-20261025`，本机入口继续从 `reader-env-local.ps1` 读取 `$readerRepository`。

## 修正

Markdown 编辑的文字由 ViewModel 保留，但旋转会重建原有 Activity / WebView，从而丢失 CodeMirror 的选区和撤销 / 重做记录。按 [Android 官方 WebView 状态管理方式](https://developer.android.com/develop/adaptive-apps/cookbook/webview-state)，Activity 接收方向、窗口尺寸和键盘配置变化，Compose 在新尺寸下重新布局，继续使用原编辑器。保留原有 uiMode 处理及原文 / 草稿保存单一事实源。

84 候选的正式包跨格式复验发现 PDF 从第 2 页回退第 1 页，已拒绝交付并移入证据目录。原因是连续视口把尺寸变化也当成页高修正，保留旧视口顶端；旧中心在下一页时，新的窗口中心落回上一页。85 复用现有页面内锚点与视口会话：尺寸变化映射旧中心的页面、局部位置和缩放；真实页高替换占位页高仍按原规则保留顶端。

不承诺不同尺寸下相同滚动像素。进程终止或语言 / 字体等仍需重建页面的情形沿用既有草稿恢复，未增加跨进程撤销栈恢复能力。

## 验证与限制

| 检查 | 当前 85 结果 |
|---|---|
| 冻结源码全量 QA JVM | 1836 项，0 failures / 0 errors，2 skipped |
| 冻结源码全量 Release JVM | 1683 项，0 failures / 0 errors，2 skipped |
| lintRelease | 0 errors / 179 warnings / 6 hints；强制生成当前分析的报告 |
| 直接相关原生回归 | 5/5：Markdown 真实 MainActivity / 同页保存、源码切换保真、QA 数据隔离、PDF 宽高 / 单独高度 / 往返映射，以及 Activity 重建的完整变换恢复 |
| 扩展 PDF 原生组 | 17/23，6 项仍失败；同一测试 APK 在旧 84 上为 16/23、7 项失败。仅新增尺寸回归从失败转为通过，没有新增失败 |
| 最终正式 Release 模拟器 | Markdown 实际输入、横竖屏及窗口高度变化、撤销重做、保存重开和原文件保护通过；PDF 2/3→2/3→2/3；EPUB 重排保留 C2P01W01 锚点 |
| 包校验 | 生产签名、16KiB 对齐、R8 / 资源压缩；非 debuggable；无 INTERNET；35 个 assets 与源码一致；两份品牌向量与 83 APK 一致 |

Markdown 回归包含 BOM 和 CRLF / LF / CR 混合换行、跨行删除、双侧撤销 / 重做分支、反向选区、旋转、完成保存及新会话清空历史。PDF 新增回归从有效的页面索引 10 的内部中心开始，依次验证宽高变化、单独高度变化及返回原尺寸；同一测试在旧 84 明确失败于页面索引 10 变 9，在 85 通过。

两套 JVM 的既有跳过均为外部 Laozi 夹具未挂载及 Windows 符号链接能力不足。本轮完整日志的 Robolectric 临时目录 `.obsolete` 清理线程异常为 0 条，JUnit 没有用例失败或错误。没有削弱断言、扩大容差、删除或新增跳过。

以下 6 项扩展原生失败仍待独立核查，不能把扩展组称为通过。旧 84 和当前 85 的失败名称一致，详见 `native-comparison.json`：

- `startupAndModeEntryRestoreApplyTargetBeforeAnyPageZeroFeedback`：期望 47，实际 29；当前夹具仅有 30 页，含无效目标。
- `p0c14TileSwapDoesNotMoveDocumentTransform`：按住过程中 Y 差 18px。
- `p0c12StaleGeometryTileIsSuppressedWithoutChangingDocumentTransform`：预设变换与实际变换不一致。
- `pointerEpochSinglePinchSinglePinchSkipsTransitionFramesAndSettlesOnce`：时序条件未满足。
- `activeRealGestureIgnoresOldVmScaleEmissionAndCommitsFinalScaleOnce`：缩放断言不一致。
- `rapidSecondRealPinchCancelsOldSettleAndCommitsOnlyFinalTransform`：时序条件未满足。

物理手机未连接，手机安装、物理中文输入法及用户视觉 / 手感验收 pending。本轮为 READY_WITH_LIMITATIONS，不是公开发布批准。84 的 BOM 合成文件原生阅读首行仍显示标题标记，作为后续解析核查项保留；编辑源文保真与此阅读语义问题分别记录。

## 产物与来源

- 交付：`D:\03_M1racle Reader\apk\M1racle-Reader-1.0.0-85.apk`。
- 大小：27603994 字节（26.33 MiB）。
- SHA-256：`2DBF22BBBF95D530BF6E245BFDB2F3FE9010925B9D14E335D09BCAADB6A32668`。
- 生产证书 SHA-256：`2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`，与现场重新验签的 83 一致。
- 构建输出、Outbox、交付、最终模拟器安装 base.apk 和 Download 的摘要一致。
- 构建前冻结 1130 个非忽略文件，保存 `source-tested-v85.zip` 和逐文件摘要。测试、构建与交付使用同一份编译源码；交付后的状态、索引、报告及 CHANGELOG 单独记录文档摘要，其他冻结文件无漂移。
- 旧 84 包不在交付目录；源码、旧包和拒绝原因保存在本轮父证据目录。两个原工作树及当前工作树既有改动保留。未提交、未推送、未公开发布。

证据目录：`D:\03_M1racle Reader\output\editor-rotation-20261029\v85`。主要入口为 `verification.json`、`release-emulator.json`、`native-comparison.json`、`before-fix-pdf-native.log`、`after-fix-native.log` 和 `full-release.log`。
