# PDF 正文编辑首个闭环

更新：2026-09-07。状态：implemented / automated_verified；device_verified = NOT_RUN（无在线设备）。

风险等级：高（PDF 内容写入）。验证采用文件级回归、全量 QA 单测、lint 尝试、Debug/Release 构建与产物核验；设备不可用部分单列待验。

## 目标与当前实现

用户已批准方案并要求继续。保留 PdfRenderer 与已有 PDFBox-Android；无新增依赖。PRD / AGENTS 范围已同步本次授权。

- `pdf/edit/PdfBodyEditor.kt`：原始 Tj/TJ 操作符映射，使用原字体编码替换文字，以 TJ 位移补偿保留后续文字矩阵；新建页面内容流隔离共享页。输出后重开核验文字。
- `ui/pdf/edit/PdfBodyEditorViewModel.kt`：不可变来源快照、串行编辑/预览、撤销重做、SavedStateHandle 恢复已应用文字、SAF 副本输出并经既有 repository 导入。
- `ui/pdf/edit/PdfBodyEditorScreen.kt`：独立编辑页、文字框/列表选择、输入弹窗、页码/撤销工具、另存副本与未保存返回提醒。弹窗复用统一玻璃材质。
- `NavGraph.kt` / `PdfViewerScreen.kt`：三点 → 编辑正文 → 另存后新文档阅读。

## 当前验证与交付检查点

- automated_verified：全量 QA 单测 **1424 / 0 / 0 / 0**（174 suites），其中正文编辑专项 **10 / 0 / 0 / 0**。证据：`.external_outbox/pdf-body-editor/final-tests.log`、`final-tests-summary.json`。
- 专项覆盖：英文、中文与混排，完整及子集字体，可编码/缺字，Tj/TJ 字距，后续文本与换行矩阵，多页共享内容流隔离，无操作保存，连续编辑/回退的不可变来源快照，特殊结构及密码保护拒绝，失败不输出且原文件字节不变。
- 测试用中文字体 3,980 bytes，位于 `app/src/test/resources/fixtures/pdf-edit/`，附上游来源、摘要与 OFL；不进入运行包。
- lintQa 已执行但未通过：现有 `NoteEditDialog.kt` 的 Compose 检测器出现 KaFunctionCall 类/接口 ABI 不兼容；另有 ASM NegativeArraySizeException。没有停用新检查或声称 lint 通过。证据：`regression.log`。
- device_verified：NOT_RUN。任务一次 ADB 探测为空（`adb.txt`），不再轮询。
- Debug 与 production 签名 Release 均构建通过。Release **6,817,510 bytes**，较上版增加 **114,688 bytes**（约 1.7%）；Debug 79,490,851 bytes，仅作构建验证，未交付 Debug 包。
- Release SHA-256：`de487e3c6c95ec85542da06b36050ac7b3a834be3d778e5b0bc66b8b81ba764f`。production signer SHA-256：`2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`；apksigner 验证通过。
- 每次 assemble 成功后均自动执行 Download 复制。最终 `.external_outbox/m1racle-reader-release-latest.apk` 与 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk` 摘要一致，Download 同附 `.sha256`。
- 版本仍为 1.0.0 / 23，无 INTERNET 权限，测试字体未进入 APK。证据：`debug.log`、`release.log`、`signature.txt`、`apk.json`、`badging.txt`、`source-hashes.json`。
- 最后轻量 UI 调整：切换编辑页时重置页面滚动位置；已由 Debug / Release 编译验证，未声称实际手势验收。

## 会话与保存语义

- 已应用编辑通过 SavedStateHandle 恢复；屏幕重建保留当前输入和文字选择，进程恢复不承诺保留撤销栈。
- 操作串行，应用/撤销先生成并渲染独立候选，成功后才更新历史。失败保留前一份预览与修改。
- SAF 输出后重新读取并比对 SHA-256，成功才交给既有导入链路。输出失败保留编辑状态，可另选位置重试；外部提供者可能留下不完整文件，不能承诺外部原子写入。
- 保存成功但导入失败，明确提示文件已保存，可手动打开。原文档、书签和应用批注保持不变；不向副本复制未经校验的旧锚点。
- 编辑期间同一文档的外部打开保留编辑页；其他外部文档导入后等待退出编辑再导航，防止直接销毁草稿。
- 返回存在已应用修改时确认放弃；临时文件关闭后清理。

## 首版边界

仅已验证的水平原生文字片段；新文字须能由原字体编码并容纳在原宽度内。不提供整段删除、样式更改、换字体、多行重排、OCR/扫描件编辑。受保护或有数字签名文档拒绝编辑；旋转/特殊缩放、嵌套/裁剪/特殊语义页拒绝，普通图片对象不阻止同页原生文字编辑。复杂排版字符也明确拒绝。原文件及其书签批注不修改，副本不迁移旧锚点。

实机待验：页面选框坐标、键盘及字体缩放、PDF 实际渲染、SAF 取消/失败/重试、另存重开、后台恢复及资源使用。自动化几何验证不能替代实机或 Acrobat 对照。

## 安装后人工验收

1. 打开文字型 PDF，三点 → 编辑正文；当前页预览出现可编辑框。
2. 从框或文字列表选择一段，改成原字体已有、较短的文字，应用。检查后续段落、图片和下一行位置；再撤销和重做。
3. 输入明显超宽或缺失字符，应用应提示具体原因；关闭输入后仍显示上一次成功预览。
4. 横竖屏、键盘展开收起、小字体文字列表选择；前后翻页检查滚动从页面顶部开始。
5. 另存副本取消后继续编辑；完成另存后自动打开副本，在其他 PDF 阅读器检查复制文字、页面外观；原文档和原应用批注仍在。
6. 编辑时从外部打开另一本 PDF，原编辑页应保持，退出后再打开外部文档。

以上是待执行的真实场景，不能把文件级测试当作这些场景已通过。
