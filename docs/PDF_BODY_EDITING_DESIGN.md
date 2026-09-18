# PDF 正文编辑自研方案

日期：2026-09-06。

状态：本页记录方案形成时的设计。2026-09-07 已交付有限首个闭环，当前实现、文件级回归、包体与待验事项见 [实现报告](PDF_BODY_EDITOR_FIRST_LOOP.md)；尚无实机性能证据。本方案不宣称达到业界最先进水平。用户明确提出 Acrobat 式正文编辑并允许参考成熟实现，本次交付技术方案，不因原路线图自动启动引擎迁移。

## 1. 产品目标与技术选择

实现手机上直接选中 PDF 既有文字、修改内容和样式、撤销/重做、预览并另存可再次编辑的 PDF。优先保持原页面版式和未编辑内容。

推荐路线：复用现有 PDFBox-Android 做 PDF 内容解析和写入；保留系统 PdfRenderer 及现有瓦片阅读器；自研原始指令与文字选区的映射、局部排版和编辑事务。只有具体技术验证证明当前依赖不能满足写入要求时，才评估自带 PDFium；本轮不增加依赖、不替换阅读引擎。

真正需要投入的能力是：原生文字可定位、修改范围可约束、原内容可准确替换、保存结果可检验、手机上的资源占用可控制。是否引入机器学习由具体识别问题和实测收益决定。

## 2. 已核对的工程事实

| 工程部位 | 当前事实 | 对方案的影响 |
|---|---|---|
| `pdf/PdfPageRenderer.kt` | 使用 Android `PdfRenderer`，串行打开页面和绘制 | 保留显示与缩放链路，不把编辑任务塞进手势渲染队列 |
| `app/build.gradle.kts` | 已依赖 `pdfbox-android:2.0.27.0`，minSdk 31 | 先复用已有能力；不能只依赖新系统才提供的文本 API |
| `pdf/PdfBoxRuntime.kt` | 已集中初始化 PDFBox，文档处理使用临时文件内存策略 | 复用初始化；单个编辑文档仍须串行访问并独立管理生命周期 |
| `pdf/export/PdfPageExtractor.kt` | 已有加载、创建、保存 PDF 的调用 | 存在文件处理基础，但没有原文编辑功能 |
| 当前发布包 | 约 6.7 MB，应用无 INTERNET 权限 | 任何原生引擎、字体包或 OCR 模型须先测量增量，不默认装入基础包 |

通过对本地实际 AAR 的 `classes.jar` 执行 javap，确认以下接口存在：

- `PDFStreamParser.parse / getTokens / parseNextToken`
- `PDFStreamEngine.processPage / processOperator / showGlyph / showForm`
- `ContentStreamWriter.writeTokens`
- `PDFont.encode / toUnicode`
- `PDType0Font.load / addToSubset / subset`
- `PDDocument.save`

这证明可开展内容流编辑验证，不证明任意 PDF 都能无损编辑。上游源码与本地发布版本也不能混为一谈。

## 3. 借鉴成熟项目的具体内容

| 参考 | 借鉴内容 | 本项目决策 |
|---|---|---|
| Acrobat 正文编辑 | 页面内文字块编辑、明确编辑范围；扫描文档需 OCR | 阅读与编辑模式分开，选区及溢出必须可见 |
| PDFBox-Android | 操作符解析、图形状态、字形回调、内容流写回 | 作为优先验证的现有底座，自研其缺失的编辑语义 |
| PDFium 公开编辑 API | 页面对象增删、文字设置、生成页面内容、保存 | 作为有证据需要时的候选；Android 系统包装器不等同于可直接调用全部 PDFium C API |
| HarfBuzz | 字符到字形的 shaping、复杂文字定位、字体子集化 | 复杂文字排版的成熟候选，先检查 Android/现有依赖能力，验证缺口后才引入 |
| PaddleOCR 移动端方案 | 本地文字检测/识别、移动部署 | 仅用于扫描页研究；模型、运行时、耗时、内存另测，不进入原生文字编辑首版 |

PDFBox 官方明确说明它不提供完整的段落排版和自动换行；能提取文字也不代表能还原原始段落顺序。因此不把“提取文本后重新生成整页”当作保真编辑方案。

## 4. 核心数据与执行链路

### 4.1 从显示文字追溯原内容

每个可编辑文字片段必须关联：源文档版本、页面、内容流对象、Form XObject 调用路径、操作符及字节范围、原字符编码、Unicode 映射、字体资源、字形坐标、文字/页面变换矩阵、绘制顺序和影响它的图形状态。

用户看到的是行和文字块；写入器操作的是可追溯的 PDF 源对象。抽取顺序不直接当作阅读顺序；字形、基线和间距用于恢复行，段落合并必须经过结构规则检查。多栏、旋转文本、连字、裁剪与不可解码字符作为独立验证类别。

这份字形索引可以支撑两个真实功能：正文编辑命中和精准文字高亮。显示层消费同一份坐标与选区，不再各自猜测矩形。

### 4.2 编辑必须改变原文字内容

对用户实际修改的文字执行内容流替换，必要时把一个 `Tj/TJ` 片段拆分为未改部分和已改部分。相邻文字位置、后续指令的文字矩阵推进、行矩阵和间距必须被保留或显式重建；不能简单替换字符串，也不能假设 `q/Q` 会自动恢复全部文本定位状态。

共享 Form XObject / 资源采取修改时复制：只修改当前出现位置，不能让其他页或同页重复实例跟着变化。第一版遇到尚未验证的嵌套/裁剪结构直接说明不能编辑，不能悄悄执行不准确写入。

仅画底色遮住旧字，再叠新字，会留下错误的提取、搜索和复制语义，不能作为本项目的正文修改实现。全页栅格化也不作为保存路径。

### 4.3 字体与排版

- 修改前检查目标字符能否由原字体编码及绘制；PDF 内嵌子集字体未必包含新输入的字。
- 原字体无法承载新增字符时，提供用户可见的字体替换预览，或让用户导入合适字体；不能声称精确恢复缺失字形。
- 新嵌入字体按实际用字生成子集，并保留正确的 Unicode 映射，以保证复制、搜索和读屏。
- 源 PDF 的字形定位与新文字 shaping 是不同问题。引入 HarfBuzz 也不会自动解决段落识别、换行、字体编码或 ToUnicode 写回，需分别验证。
- 首个可运行版本约束为已验证的单行/独立文字块。长文本先在用户选定的块内排版；容纳不下时明确显示溢出，让用户调整框或字号，不能自动缩到难以阅读或推动整页内容。
- 跨块、跨栏、跨页重排属于后续独立能力，不能用“第一版以后整体推倒重来”作为当前边界选择。

### 4.4 编辑事务与预览

编辑会话以原文档快照和一份有序编辑记录为事实源，统一管理撤销、重做和待保存状态。临时文本框只承载输入草稿；提交后的页面来自真实 PDF 写入结果。

复用当前渲染器显示临时生成的 PDF，按编辑代次取消过时生成/渲染任务，并使旧瓦片缓存失效。不能只以 Compose 文字覆盖层看起来相似，就判定保存正确。为避免每敲一个字就重写整本书，可先在输入层即时反馈，停止输入后生成受控的候选预览；等待最终预览期间明确显示尚在处理。

PDFBox 文档由一个串行工作者拥有，渲染器继续读取已生成的独立快照。读取快照与写入事务各有清楚生命周期，不共享可变 PDDocument 或被覆盖中的文件。

### 4.5 保存与既有阅读资产

首版使用“另存副本”：先在私有临时目录生成，重新打开并核验，再经 SAF 输出。SAF 提供者不保证原子替换，因此不能承诺任意外部位置都可安全原子覆盖；输出中断报告失败并允许重试。

另存副本作为新文档导入，原文档、原书签及原批注不变。首版不把原锚点未经校验地复制到已编辑副本。以后做原地保存时，必须先设计编辑范围对应的锚点重定位，不能让旧批注默默指向错误正文。

普通编辑不等同于安全涂黑/脱敏；首版不提供这一承诺。数字签名、受保护文档和不可准确理解的内容，需给出明确可编辑性结论。

## 5. 扫描件与 AI 的位置

原生文字页优先解析 PDF 本身。扫描页需要独立的 OCR / 版面恢复过程，输出候选文本和位置，由用户确认后才能参与编辑。

OCR 找到文字并不等于恢复了原字体，也不会自动清除图像里的旧字。扫描件编辑还要处理图像区域、背景和新文字的一致性；复杂图案底色不能靠简单白块解决。这是独立实验方向，不用 OCR 演示冒充完整扫描件编辑。

本地模型只辅助识别和分组，不自行改写正文。任何模型包若获准使用，应支持通过现有本地文件入口导入，并明确缺少模型时能力不可用；保持无 INTERNET 权限。

## 6. 验证标准：以文件结果而非演示观感判断

先建立由自制、授权样本组成的固定集合，至少覆盖普通英文、中文、混排、子集字体、`TJ` 字距、旋转/裁切页面、双栏、重复 Form、透明背景以及不能编辑的图片页。第一轮要求每类明确写出支持或拒绝原因，不把不支持样本从统计中消失。

| 维度 | 验收方式 |
|---|---|
| 无操作保存 | 重新解析并渲染，与原文比较；建立工具自身的噪声基线 |
| 文本正确性 | 修改后提取/复制得到新字；目标旧字不继续在对应页面显示内容流中出现；不能只看截图 |
| 几何保真 | 对照编辑范围外的差异图；阈值来自无操作基线，不能因失败任意放宽 |
| 资源隔离 | 修改一个共享实例，其他实例和页面不变 |
| 字体正确性 | 新增中文、标点、缺字、子集字体分别验证，替换字体不能静默进行 |
| 文件可用性 | 用当前 Android 渲染器重新打开；用独立实现如 Acrobat 检查选取、复制及渲染 |
| 恢复与撤销 | 连续编辑、撤销/重做、取消、保存中断、重开文件 |
| 手机资源 | 记录所选设备、页复杂度、解析/预览/保存 p50/p95、峰值内存、掉帧和 APK 增量；当前没有测量值 |

自研优势必须由这些结果证明。可以把“英文和中文常见 PDF 的本地编辑准确性”作为最先追求的强项，而不是一开始声称覆盖 Acrobat 的全部文档。

## 7. 最小闭环与后续扩展顺序

1. **验证现有底座**：程序生成带字距/中文/子集字体的样本；建立原文位置映射；真正替换一处文字；保存后重新提取和渲染。此阶段回答核心写入是否可靠，而非先堆编辑工具栏。
2. **首个可安装闭环**：打开文字型 PDF → 进入编辑 → 点击已支持文字块 → 修改/撤销 → 用实际 PDF 预览 → 另存 → 重开验证。首版限定已验证结构，所有拒绝原因可见。
3. **扩大原生文字能力**：多行排版、字体选择、字号/颜色、移动/缩放文字块、共享 Form 等逐类接入同一编辑链路；字形索引用于精确高亮。
4. **扫描件专项**：单独测 OCR、字体/背景恢复及端侧成本，达到验收标准后再接入。

如果第 1 步证明 PDFBox 写入能力或性能达不到要求，记录具体失败样本，再做 PDFium 对照实验。最终选一条正式写入路径，不保留两套互相兜底的生产实现。改换底座必须先证明端到端行为和包体/内存收益，不能拆掉当前能运行的阅读器来赌新引擎。

## 8. 资料

- [Acrobat 文字编辑](https://helpx.adobe.com/acrobat/using/edit-text-pdfs1.html)
- [Android PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer) 与 [Page API](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer.Page)
- [PDFBox FAQ：排版、文字顺序、字体和线程约束](https://pdfbox.apache.org/2.0/faq.html)
- [PDFBox-Android 项目](https://github.com/TomRoush/PdfBox-Android)
- [PDFStreamEngine 源码](https://raw.githubusercontent.com/TomRoush/PdfBox-Android/master/library/src/main/java/com/tom_roush/pdfbox/contentstream/PDFStreamEngine.java)
- [PDFStreamParser 源码](https://raw.githubusercontent.com/TomRoush/PdfBox-Android/master/library/src/main/java/com/tom_roush/pdfbox/pdfparser/PDFStreamParser.java)
- [ContentStreamWriter 源码](https://raw.githubusercontent.com/TomRoush/PdfBox-Android/master/library/src/main/java/com/tom_roush/pdfbox/pdfwriter/ContentStreamWriter.java)
- [PDFium 编辑 API](https://pdfium.googlesource.com/pdfium/+/refs/heads/main/public/fpdf_edit.h) 与 [保存 API](https://pdfium.googlesource.com/pdfium/+/refs/heads/main/public/fpdf_save.h)
- [HarfBuzz shaping](https://harfbuzz.github.io/shaping-concepts.html) 与 [字体子集化](https://harfbuzz.github.io/harfbuzz-hb-subset.html)
- [PaddleOCR 移动端部署](https://paddlepaddle.github.io/PaddleOCR/main/en/version3.x/deployment/on_device_deployment.html)
