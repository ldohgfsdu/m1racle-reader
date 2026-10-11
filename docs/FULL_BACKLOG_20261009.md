# 全清单实现与验收

用户于2026-10-09在逐项核查后明确要求“全部实现”。这构成本轮范围授权，覆盖所附markDown1791523727279.md及核查出的缺口，取代旧范围中与本清单冲突的Markdown编辑、更多EPUB阅读方式和文件管理限制。不扩展账号、网络同步、广告、OCR或DRM解除。

当前状态：in_progress。交付基线55版；只有本轮实现并验证的事项才更新完成标记。原始附件保持不变。

| 工作 | 实现 | 自动验证 | 真机验收 |
|---|---|---|---|
| 书签位置、缩放、偏移恢复 | pending | pending | pending |
| 文件改名、原文件导出、路径展示、真实封面 | pending | pending | pending |
| 文件扫描与默认查看器引导 | pending | pending | pending |
| PDF反色、进度拖动、沉浸与玻璃闪烁 | pending | pending | pending |
| PDF创建、加密、合并、转Markdown | pending | pending | pending |
| PDF选字复制、多样高亮、笔记与有限正文编辑 | partial | pending | pending |
| Markdown选字与批注（编辑等待 M1racle Note） | implemented | partial | partial |
| Markdown渐进模糊、顶栏动画、预见式返回与进度拖动 | partial | pending | pending |
| 系统长截图适配 | implemented | Android 原生协议通过 | device_verified：Markdown、生产 PDF 连续视口三屏捕获及原位置恢复；系统厂商截图面板人工流程尚待确认 |
| EPUB连续阅读与拟真卷页 | pending | pending | pending |
| 搜索展开、UI排版、图标、体积与流程整理 | partial | pending | pending |

持续保留生产数据，不卸载/清库；处理历史书签时只进行必要的一次数据补全。正式交付仅生产签名Release。复用现有PDFBox、Compose、PdfRenderer及字体/导入/存储层，新增依赖前先核对现有能力。

2026-10-09开发记录：书签快照与跳转、文件操作及实际封面、共享存储扫描、PDF工具、Markdown双向段落编辑已接入。PDF反色覆盖两种阅读模式及高清图块；进度拖动、文件信息和EPUB模式正在联调，尚未发布。本轮第一组28项测试通过（BookmarkTarget 3、ContentIdentityImportWiring 18、DocumentTools 4、MarkdownSource 3），日志位于工作区output/backlog-tests-1.log。它们只证明对应数据与处理链路，不能代替新增UI的真机验收。

卷页组件选用Maven Central的io.github.oleksandrbalan:pagecurl:1.5.1，复用页背、折线、阴影和手势实现，License保存在LICENSES/pagecurl-Apache-2.0.txt。既有Compose Pager只承担滑动模式；当前模式是唯一阅读输入来源，统一保存文字锚点。

2026-10-09用户进一步调整：Markdown编辑等待M1racle Note实现，本轮暂缓发布。编辑及草稿代码保留待统一，移除导航和用户入口；本轮继续Markdown阅读、精确选字、批注、长截图、渐进模糊、圆环进度和预见式返回。

2026-10-09用户澄清：长截图指系统截图工具的滚动捕获，不是应用内截图功能。删除自制入口、拼图和导出路径；通过 Compose 原生 ScrollCapture 通道适配阅读页面，验证系统能识别滚动正文、继续捕获及恢复阅读位置。

系统捕获初次真机证据：output/backlog-md-device-system-2.log。7项中6项通过，包括 Android 原生 ScrollCapture 三屏、位置恢复、逐字段落选择、圆环拖动、实际AGSL编译和QA数据隔离；表格选择1项失败，正在修正并复验，不计为全部通过。全量回归曾因本机 Java 原生内存不足中断（hs_err_pid2364.log），不能作为通过证据。

最新真机复验：output/backlog-native-system-3.log，8项全部通过。覆盖实际 Android ScrollCapture 三屏捕获、生产 PDF 连续视口三屏与原位置/缩放保护、段落/代码/表格精确文字范围、圆环、AGSL及QA隔离。表格选择使用指针绝对坐标，修复累加移动导致的选区误差。截图时顶栏、悬浮控件和固定页脚只停止绘制，保留原布局，避免拼接时重复与重排。验证使用系统原生接口，不含自制拼图。正式交付仍为55版，本轮尚未发布。
