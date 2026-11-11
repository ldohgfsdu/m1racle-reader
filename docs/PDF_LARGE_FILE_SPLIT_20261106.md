# PDF 大文件、滑动与拆分导出

2026-11-06。本轮目标为正式签名、压缩 Release 1.0.0 / 94。构建入口仍使用 reader-env-local.ps1 的 readerRepository，两个旧工作树保留，用户 WIP 和 Git 暂存区保持。93 是此前交付，不能代表本轮源码。

## 实现

- 完成 core:model、core:database、core:pdf。Room 实体、DAO、迁移、v6–v17 schema 只由 database 拥有；PDF 核心只依赖中性模型、Android、Compose、coroutines 和现有 PDFBox。数据层接受 OutlineImportPlan，不再引用 PDF 目录类型或导入器。旧 PDF importer 已移除，其四项行为测试迁至中性 planner。
- PDF 通过首页 SAF、VIEW、SEND 共用格式限额：PDF 为 2 GiB，其他格式仍为 200 MiB。身份复制和哈希使用固定缓冲区，本地 PDFBox 读取使用文件随机访问；临时缓冲固定在应用 cacheDir/pdfbox-scratch，无法使用时明确失败。
- 页尺寸按文档保存，与位图缓存淘汰分离。拖动、惯性和缩放事务冻结同一几何坐标；晚到尺寸在静止时一次提交，保留顶部阅读线，真实视口旋转保留阅读中心。去掉独立 clamp 写入者。适宽显示多个短页时，所有实际可见位图均受保护。
- 模拟器首跑进一步复现静止抬手后仍额外惯性移动 45 px。最终纵横向均采用 Compose 1.11.1 官方 addPointerInputChange，使用原始与历史移动数据，并由框架处理静止抬手；不再手工加入 UP 位置样本。显示镜像保留 VM 有界缓存中的预取页，避免同一快照反复加入和裁剪；占位页请求使用实际视口主页面，避免不同占位页争抢缓存窗口。
- 最终惯性中断回归进一步发现 DOWN 后未落地 gestureActive、要等 MOVE 才标记的问题。现在 DOWN 立即占有页面几何事务，按住不动时也保持冻结，直到 UP 后才统一重映射；测试使用 Android 原生事件打断活跃惯性，不等待 Compose 动画空闲。
- 旧阅读回归还捕获轻微点按的释放样本被拟合为极高速度；移除手工 UP 点，并禁止未越过点击抖动范围的手势启动惯性。新增原生断言限制一像素点按的实际位移并禁止产生 FLINGING；拖动本身仍立即跟手。
- 首次测量前不再把 1px 占位几何记为有效 epoch；首次有效布局只钳位已有变换，避免首次或重建时被推至文档底部。未测量时不发送视口驱动或阅读位置提交。重建测试同时检查明确的初始 scale / scrollX / scrollY 和重建后完整变换。
- 明确的恢复、导航与窗口尺寸变更会保留对应的布局锚点，直到用户接管滚动。这样目标页较短、视口顶部落在另一页时，邻页真实尺寸晚到也不会把已恢复的阅读点挤走。普通手势结束后的晚到尺寸仍保留顶部阅读线。
- 横向回拉的抬手采样曾在追加复跑中仍出现反向拟合，因此继续改用上述官方完整事件接口；保留 50% 距离阈值、原生回拉路径和全部结果断言。没有通过改路径、延迟释放或放宽断言规避失败。
- 阅读菜单新增“拆分 PDF”。按目录章节可选择层级，同页章节合并，封面和前置内容单独保留；按固定页数保留不足一份的尾组，支持页码范围。输出为用户选择目录中的独立 PDF，不改变源文档。
- 每份输出检查页数、内容流、页面尺寸、旋转和资源结构，并校验本地文件与 SAF 回读字节的 SHA-256。继承资源显式物化；原章节 ZIP / PDF 导出补充完整回读、失败目标清理。
- 取消使用每任务 token 与实时 coroutine 状态，逐页和流拷贝设置检查点；取消或失败删除本次创建输出与临时目录。提供方拒绝删除时按真实结果报告，删除失败可以重试。
- 全模块 build 目录由 Git 忽略，测试源码路由覆盖四个生产模块，扫描根只有一处。

## 自动化证据

本表只填写本轮实际结果。历史 11 月 5 日数字不纳入本轮。

| 范围 | 结果 | 证据 |
|---|---|---|
| core:database Debug / Release 单测 | 各 48 项，0 failures / 0 errors / 0 skipped | final-validated-release-build.log、unit-test-summary-final.json |
| core:pdf Debug / Release 单测 | 各 458 项，0 failures / 0 errors / 0 skipped | 同上 |
| app QA / Release 单测 | 1420 / 1256 项，0 failures / 0 errors，各 2 个既有环境跳过 | 同上、unit-results-final-v94 中的原始 XML |
| 全模块 Release lint | 0 errors，app 175 / database 4 / pdf 8 warnings | lint-summary-final.json、lint-results-final-v94 中的原始 XML |
| 原生大文件、滑动、拆分与既有回归 | 59 项，零失败、零跳过；3 项时序用例追加两轮均通过 | native-features-passed-v94.txt、native-regressions-v94.txt、native-final-timing-repeat-1/2.txt |
| 正式 Release 构建、签名和本地交付 | 94 构建成功，生产签名有效、16 KiB 对齐通过，构建 / Outbox / 交付副本摘要相同 | final-validated-release-build.log、release-verification.json |
| 正式 Release 模拟器检查 | install -r 成功、冷启动成功；首页、设置纯 M 品牌、既有 PDF 阅读及实际 2 + 1 页拆分检查通过；未见 Reader 崩溃或 ANR | release-emulator-smoke.json、release-cold-start.txt、release-cold-smoke-logcat.txt、release-*.png |

外部证据目录：D:/03_M1racle Reader/output/pdf-large-split-20261106。整合过程中编译错误和本机内存不足的首轮失败均保留，不计作通过。

正式交付为 [M1racle-Reader-1.0.0-94.apk](</D:/03_M1racle Reader/apk/M1racle-Reader-1.0.0-94.apk>)，27725374 bytes（26.44 MiB），SHA-256 为 `F674B911D4B0A5B9296A07C4E4F7396CC058192B77144F61F3478455FA28F81E`。证书 SHA-256 与 93 一致，公开包未包含 QA 宿主、调试标志或 INTERNET 权限；签名与对齐原始日志为 release-94-signature.log、release-94-zipalign.log。当前未进行 Git 提交或推送，源码交付基线以如下冻结清单为准。

## 可复现基线

scripts/source-baseline.ps1 按当前工作区字节冻结实际编译输入，包含 app、benchmark、core、Gradle 与内嵌编辑器源码/资源。排除 build、node_modules、dist、签名密钥、本机配置和用户输入。Git HEAD 的 90 版只作历史上下文，不能替代本轮 94 版工作区源码。最终冻结 705 项源码及构建输入，其中 core 137 项；清单和 ZIP 位于 output/pdf-large-split-20261106/source-final-v94。59 项原生测试及两轮时序复测完成后，编译输入与最终冻结清单一致。QA / Release 应用与对应核心模块分别合计 1926 / 1762 项，均无失败或错误，各有 2 项既有环境跳过。最终全量验证和正式打包使用同一清单；最终构建后、交付后分别核验，原生测试所用 QA 和仪器 APK 另以 SHA-256 核对身份。各次未通过的过程记录保留，不计入通过结论。

[最终源码包](</D:/03_M1racle Reader/reader-ui-release-20261025/output/pdf-large-split-20261106/source-final-v94/source-final-v94.zip>)的 SHA-256 为 `E809822BF9EC6CF5EB43F3D671F34D162A55E8AB7AED8E8896C9EDE3D945CCC0`。交付后的 705 项输入未发生增删或字节变化；APK 副本链、源码包摘要、空暂存区、生成文件排除和三个工作树的最终核验记录为 final-handoff-verification.json。

## 验收边界

实际界面已完成 96 页夹具的两种输出：章节 48 + 48 页；固定页数 20 + 20 + 20 + 20 + 16 页。七份文件从模拟器真实 Download/Reader94-QA-exports 目录回读，独立使用 Poppler 校验页数、逐页文字及渲染 PNG 的 SHA-256，192 个输出页面均与对应源页一致，首尾渲染目检正常。证据为 manual-export-independent-verification.json、manual-export-independent-verification.log、qa-chapter-completed.png、qa-pagecount-completed.png。

最终正式包另在既有 3 页 PDF 上完成固定 2 页拆分，实际 SAF 目录为 Download/Reader94-Release-exports，输出为 2 页和 1 页。界面确认两份回读完成；独立使用 Poppler 核验页数、各页原有页码文字，并渲染首个输出页面目检正常。证据为 release-export-completed.xml / .png、release-export-independent-verification.json、release-exported-pdfs。模拟器安装 APK 与 Download/m1racle-reader-release-latest.apk 均和本地正式交付摘要一致；只执行保留数据升级，没有卸载或清空生产包。

正式包冷启动记录为 COLD / Status: ok，单次 TotalTime 2656 ms。模拟器启动时出现 System UI 无响应对话框，已保存 release-systemui-preflight.xml / .png；依据当时界面选择 Wait 后恢复。该事件属于模拟器 System UI，后续 Reader 界面、PDF 及拆分检查正常；没有把系统无响应记录删除或当作 Reader 性能通过证据。

最终冻结源码的 QA 软件图形渲染模拟器上，已采集 24 次前滑、12 次后滑的帧及内存数据。370 帧中 308 帧错过期限（83.24%），P50 / P95 为 73 / 129 ms；PSS 从 205582 KiB 增至 307015 KiB。没有做物理手机或前后版本的等条件性能对比，不能据此宣称手机达到固定帧率。日志中未见 QA 崩溃。最终记录为 final-scroll-gfxinfo-framestats.txt、final-scroll-meminfo-before.txt、final-scroll-meminfo-after.txt；旧候选的采样保留作过程记录。

大文件原生测试使用至少 224 MiB 的合法稀疏 PDF，带有实际 2000 页混合尺寸页树并渲染末页；另有 200 页混排、跨页继承字体和图像、三种旋转及真实 SAF 40 页拆分用例。大文件夹具中的体积包含未引用流，不能等同于 224 MiB 高密度图像负载，更不代表完整 2 GiB 实书验收。2 GiB 是导入政策上限，不承诺所有该大小的 PDF 或固定提速倍数。

本轮为 READY_WITH_LIMITATIONS：implemented 已完成，automated_verified 为上表已通过范围，device_verified 仅限模拟器中的明确场景。当前无连接物理设备，本轮未做手机安装或人工阅读验收；模拟器数据不能替代手机阅读手感。使用 group/m1racle-workers 并行完成模块、导出、手势及测试分工，主进程整合并执行最终同源验证；没有提交、推送或公开发布。

API 边界依据：[Android PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer)、[PDFBox MemoryUsageSetting](https://pdfbox.apache.org/docs/2.0.13/javadocs/org/apache/pdfbox/io/MemoryUsageSetting.html)、[PDFBox Splitter 的跨文档链接处理](https://raw.githubusercontent.com/apache/pdfbox/2.0.27/pdfbox/src/main/java/org/apache/pdfbox/multipdf/Splitter.java)。页面显示继续使用系统 PdfRenderer，未迁移渲染引擎。

速度采样依据：[Compose VelocityTracker 事件接口](https://developer.android.com/reference/kotlin/androidx/compose/ui/input/pointer/util/package-summary)、[实际使用的 1.11.1 官方源码包](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-android/1.11.1/ui-android-1.11.1-sources.jar)。已核验其默认修正分支会跳过 UP 位置，在最后移动与抬手间隔超过 40 ms 时清空历史；依赖版本保持不变。
