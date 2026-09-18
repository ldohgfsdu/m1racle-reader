# PDF 按文字逐行高亮

2026-09-07。用户确认现有高亮是跨行大矩形、涂满空白的现象，明确要求修复。状态 implemented_pending_device；2026-09-08 用户追加设置内 HyperChanger 模糊预览，参见 [实现核查](HYPERCHANGER_BLUR_REFERENCE.md)。

根因：PdfHighlightLayer 将拖拽起止点直接构造矩形，SelectionModel/DocumentAnchor 仅承载一个矩形；绘制还添加了常驻橙色描边。原手势在 finally 中提交，取消时也可能保存预览，selectionEnabled 未约束创建。

本轮范围：以已有 PDFBox 提取当前页原生横排文字与字符坐标，用起止字符形成逐行范围；矩形列表作为选区和存储的唯一事实源，整条高亮统一删除/附注；取消、滚动、双指加入不提交。未选中标注无描边，颜色采用 Multiply 混合避免把黑字漂白。页面仍由系统 PdfRenderer 渲染，无新增依赖或引擎迁移。

锚点编码在已有矩形字段中使用分号分隔多个范围，原有单矩形天然是长度为 1 的列表，原有整页锚为空列表。没有新版本分支、数据库列或 Room 迁移。保留用户已有标注，不猜测过去矩形原本想选的文字；新建标注使用逐行范围。原有注释仍为书库本地标注，本任务不引入 PDF 标注写回。

原生横排文字支持页旋转 0/90/180/270 和 CropBox 偏移；竖排/倾斜字形不猜测选区。扫描页无文字时提示无可选文字，不生成矩形。复杂无结构分栏的阅读顺序受 PDFBox 提取结果限制；加入分栏回归避免同栏选区串入相邻栏。

风险中：持久锚点与手势。测试覆盖字符定位、跨行/反向、空白、同栏、旋转/裁剪、编解码、单字符、取消与多指；沿用原仓储保存/恢复/删除测试。编译与专项测试通过。全量回归曾捕获文本引擎对 UI 选择模型的依赖，已改为返回领域锚点，由 UI 转成瞬时选区；未修改或削弱分层断言。修正后全量 QA 1457 / 0 / 0 / 0。新增预览后最终回归仍为 1457 / 0 / 0 / 0。Debug 和签名 Release 构建通过。lintQa 已执行，现有 Compose 检测器 / Kotlin KaFunctionCall ABI 不兼容导致失败，未减少检查项。

设备：本任务一次 adb devices 空列表，device_verified=NOT_RUN，不重复探测。已自动交付 Download；人工验证拖选手感、实际渲染对齐、扫描/复杂排版提示、标注重开/删除和设置模糊预览。

参考：
- Adobe 文字选择范式：https://helpx.adobe.com/acrobat/using/reusing-pdf-content.html
- PDFBox TextPosition 坐标语义：https://pdfbox.apache.org/docs/2.0.0/javadocs/org/apache/pdfbox/text/TextPosition.html
- 当前依赖对应 CropBox 处理源码：https://github.com/TomRoush/PdfBox-Android/blob/v2.0.27.0/library/src/main/java/com/tom_roush/pdfbox/text/LegacyPDFStreamEngine.java

## 最终交付（2026-09-08）

- implemented：按字符起止逐行高亮、摘录保存、整条标注恢复与删除；短选区按钮不重叠；设置页厚雾/渐变/胶囊/当前 B 对比、左右拖动与播放暂停。
- automated_verified：QA 1457 项通过，无失败/错误/跳过；包含 10 项 PDF 字符/几何测试、7 项实际 Compose 手势测试，以及跨层存储回归。Debug/Release 构建与生产验签通过；R8 mapping 和 Debug dex 均包含高亮及预览实现。完整 lint 未通过的原因见上文。
- device_verified：NOT_RUN；没有 ADB 设备，未安装/未声称实机视觉通过。
- pending：人工安装验证高亮位置和手势、实际背景采样、播放交互及用户选择最终模糊方案；未替换全局 B。

Release **6,883,046 bytes**，较前版 6,833,894 增加 49,152 bytes（约 0.72%）。SHA-256 `15b0a18d13b776427c1ea6f6f3bab837aa50d698426f3904da74ade947f11efe`。版本 1.0.0/23，Room14，无 INTERNET 权限、无新增依赖、测试字体未打包。

生产签名 SHA-256 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`；Download 与 outbox/build 输出完全一致，附 `.sha256`。Debug 测量为 79,490,851 bytes。证据目录 `.external_outbox/pdf-text-highlight/` 含 `debug-and-tests.log`、`release.log`、`regression-and-lint.log`、`test-summary.json`、`final-verification.json`、`signature.txt`、`badging.txt`、`source-hashes.json`、`adb.txt`。
