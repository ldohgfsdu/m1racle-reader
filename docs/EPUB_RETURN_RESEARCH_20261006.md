# EPUB 返回闪帧与阅读器参考研究（Release 44）

范围：修复用户新视频中的 EPUB → 首页跳动，分析五张对比截图；按用户确认查找网上 / GitHub 项目，并检查用户提供的 Morealm Reader 与 Lumi Books。沿用上轮 RiseDiary 底栏、正式版工作台与紧凑分段菜单。

## 返回跳动的根因与改动

用户视频 `2a1bd41fe3bb58fe03cd88275fd2b2fe.mp4` 共 106 帧 / 3.533 秒。第 62 帧（2.067 秒）已显示首页，但状态栏尚未恢复，标题贴近窗口顶端；其后整页下移。不是凭主机测试推断已修复。

安装的旧 Release 43 在同一模拟器、已加载的长篇 EPUB 中复现：录屏先有阅读页，再有退出过程。首页标题模板首次位于 y=40，后移至 y=176，136px 位移等于实际状态栏高度。详见 `output/epub-return-v44/baseline-exit.mp4`、`baseline-exit-handoff.jpg` 与 `header-frame-positions.json`。

真实调用链：`EpubSystemBars.onDispose` / `HomeScreen.LaunchedEffect` 请求恢复系统栏；`HomeContent` 在系统栏恢复前已挂载，其 `WindowInsets.statusBars` 顶部边距暂为 0，然后随原生显隐变化。上轮已去掉导航默认交叉淡化，但没有解决这条边距路径。

本轮生产改动仅将首页顶部边距改为 `WindowInsets.statusBarsIgnoringVisibility`：第一帧就保留完整状态栏安全区。没有计时遮罩、延时导航或固定机型像素常数。

## 回归证据

- `HomeReturnInsetsHostTest` 向实际 Compose View 分发原生 WindowInsets：完整顶部安全区为 32dp，可见状态栏高度从 0 → 8 → 16 → 32dp。修复前在 0 高度时断言失败（标题 top=0，应为32）；修复后同一断言通过。红灯 XML 保存在输出目录。
- `ReaderReturnHostTest` 新增加载真实 EPUB2 测试包，经真实 Repository / ViewModel / NavGraph 返回首页的 20 帧回归；保留已有三类阅读路由与工作台测试。
- 新测试首次把“首页存在于语义树”当作旧阅读器必须立即退出组合的条件。零时长 AnimatedContent 的首个状态交接帧仍可组合两个目的地，因此该额外条件不成立。回归以每帧首页标题坐标为根因断言，并在实际 Dock 挂载时断言已加载的 EPUB Pager 不存在；没有放宽坐标容差或改掉红灯测试。
- 完整 QA 1722：1720 通过 / 2 既有跳过 / 0 失败或错误，223 suites。每帧坐标断言与真实已加载 EPUB 返回通过。
- 首次完整构建因 Windows 可用内存不足导致 Gradle daemon 20320 原生 malloc 失败；终止其遗留测试 worker 27116，按原代码、原签名降低构建并发重试。没有更换签名或交付旧包。重试完整检查 / Release构建成功，9m36s；Lint0 errors / 161 warnings / 4 hints。

实际 Release44 录屏和图片均包含退出前的阅读页，避免上轮录屏首帧已是首页的证据缺口：

| 场景 | 录屏解码帧数 | 首页识别帧数 | 标题 y | 位移 |
|---|---:|---:|---|---:|
| Release43 深色工具栏返回（修复前） | 27 | 3 | 40 → 176 | 136px |
| Release44 深色工具栏返回 | 43 | 16 | 始终176 | 0px |
| Release44 深色系统返回 | 45 | 20 | 始终176 | 0px |
| Release44 浅色工具栏返回 | 47 | 19 | 始终176 | 0px |

`analyze_return.py` 对同尺寸截图标题模板进行逐帧识别，原始位置 / IoU记录在 JSON 中，断言三种 Release44 退出都没有标题位移。已人工查看退出帧图；录屏为VFR，不外推为FPS或所有设备性能结论。

## 截图中的排版差异

代码确认的差异：

| 截图现象 | 当前实现 |
|---|---|
| 图片页中间有大块留白 | `EpubPageView` 对单图片页明确按剩余高度垂直居中 |
| 标题默认靠左，字体与原书不同 | XHTML Heading 映射到应用统一 TextStyle；书内 CSS / 字体没有应用 |
| 表格变成“问题 · 去哪看”及连续正文 | `EpubXhtmlParser.tableLines` 把单元格清洗后用 ` · ` 连接；表格结构已丢失 |
| 原书段落缩进 / 间距不同 | 段落应用统一 `TextIndent.None` 与应用段间距，不读取作者 CSS |

正文默认 16.5sp / Normal，书内 `<b>` / `<strong>` 会产生 Bold span，系统字号与字体设置也影响实际显示。截图本身不足以判定本书粗体来自哪一条路径，不能以统一缩小字号冒充排版修复。原始《高性价比人生指南》尚未取得，本轮不声称复现或还原了该书的完整排版。

## 网页与源码参考

以下为工程判断；没有在本应用中接入或验证这些引擎。

| 项目 | 适合参考什么 | 接入判断 |
|---|---|---|
| [Morealm Reader](https://github.com/keys-cherish/morealm-reader) | Android Compose 中文阅读界面与精细排版设置 | 本地检查快照 `7c97b9d`。源码有结构化章节、表格布局与字体注册路径，但核心解析依赖额外的 `epub-lib`，不能只凭此仓库确认可独立复用 |
| [Lumi Books](https://github.com/huangder/Lumi_Books) | 保留原书排版、用户覆盖设置，以及 Compose 阅读器的交互 | 本地检查快照 `9ec74bf`（2026-10-01）。书版模式用 WebView，阅读器模式用 Canvas / StaticLayout；当前仓库包含相关实现，比 Morealm 更便于追踪完整调用链。属于完整应用，并非独立 EPUB SDK |
| [Readium Kotlin Toolkit](https://github.com/readium/kotlin-toolkit) | Android EPUB 核心、书内样式与用户阅读设置 | 优先评估的引擎候选；检查快照 `1b1f6b3`。需要真实接入样本测试，尤其旧锚点转换和当前翻页动效 |
| [Readest](https://github.com/readest/readest) | 成品排版、阅读设置与多平台体验 | Web / Tauri 路线；[依赖源码](https://github.com/readest/readest/blob/main/apps/readest-app/package.json)引用 foliate-js，接入现有原生界面的工作量更大 |
| [KOReader](https://github.com/koreader/koreader) | 阅读控制与字体 / 排版调节 | 可作成熟产品参考；并非可直接嵌入当前 Compose 页面的 Android SDK |

Morealm 的构建文件明确 `includeBuild("../epub-lib")`，app 依赖 `com.morealm.epub:epub-core` / `epub-compat`：[构建配置](https://github.com/keys-cherish/morealm-reader/blob/main/settings.gradle.kts)。本轮访问作者同名 epub-lib URL 返回 404，只能记录“未找到可访问核心源码”，不能断言源码永久不存在或为私有。

### Lumi Books 源码核对

检查日 2026-10-06，完整快照 `9ec74bfd1afe75aa47be2cb65983731d9b86a283`；只做网页与源码检查，没有构建、安装或运行 Lumi，也没有验证用户同一本书。

- [开发文档](https://github.com/huangder/Lumi_Books/blob/9ec74bfd1afe75aa47be2cb65983731d9b86a283/README.dev.md)与实际调用链一致：`ReaderScreen` 根据 `BOOK_LAYOUT` 挂载 `EpubWebViewReader`；统一阅读器布局使用原生 Canvas / StaticLayout。两种模式是 Lumi 的产品选择，不意味着我们需要照搬双引擎。
- [文档转换代码](https://github.com/huangder/Lumi_Books/blob/9ec74bfd1afe75aa47be2cb65983731d9b86a283/app/src/main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt)保留样式表链接、内联样式、表格和 SVG 结构，移除书内脚本 / 活动内容，再注入阅读器分页样式与脚本。自然对齐保留作者的标题居中、段落对齐；用户明确设置字体 / 对齐 / 字重时才叠加相应覆盖。它也有封面与纯图片页的特殊处理，因此不能把“书版布局”等同于零修改原书或保证所有 EPUB 完全还原。
- [本地资源会话](https://github.com/huangder/Lumi_Books/blob/9ec74bfd1afe75aa47be2cb65983731d9b86a283/app/src/main/java/com/huangder/lumibooks/util/epub/EpubRenderSession.kt)用 ZipFile 与 WebViewAssetLoader 提供 EPUB 资源、字体和背景。`EpubWebViewReader` 关闭 file / content 访问、启用 `blockNetworkLoads`，未命中资源返回403；这是可以研究的离线资源组织方式。Lumi 整个应用的 Manifest 仍声明 INTERNET，不能整体搬入我们的无 INTERNET 应用。
- [阅读页源码](https://github.com/huangder/Lumi_Books/blob/9ec74bfd1afe75aa47be2cb65983731d9b86a283/app/src/main/java/com/huangder/lumibooks/ui/reader/ReaderScreen.kt)明确记录 Compose 玻璃背景无法可靠采样 WebView，书版模式不启用对应 backdrop，并对部分菜单 / 按钮使用实底。我们已有 Backdrop 2.0.0，Lumi 使用1.0.6；没有理由为参考界面替换现有依赖。
- 原书模式的位置保存涉及 DOM 路径、文字及上下文 / progression；原生阅读器还有章节分数、字符偏移等位置语义。现有 M1racle 的 EPUB 块锚点不能直接当作 Lumi 位置，迁移必须验证重开与字号重排。上游有转换、资源、定位、翻页相关测试，本轮仅阅读相关测试，未运行它们。
- 原创源码标注 [GPLv3](https://github.com/huangder/Lumi_Books/blob/9ec74bfd1afe75aa47be2cb65983731d9b86a283/LICENSE)，第三方组件另有来源声明。本轮未复制上游实现代码。

工程判断：Lumi 是目前更贴近截图问题的 Android 成品参考，重点参考“保留原书样式 + 明确的用户覆盖”、封面处理及设置组织。原书模式能保留结构，正好避免我们当前 CSS 丢失和表格转普通段落的方向性问题；能否还原用户这一本 EPUB，仍要实际样本验证。引擎集成仍优先评估 Readium 的独立 SDK，不把完整 Lumi 应用中的双引擎、网络功能和状态链整体移植进来。

Readium 的 [偏好设置说明](https://readium.org/kotlin-toolkit/3.4.0/guides/navigator/preferences/) 区分原书样式与用户覆盖设置。[WebViewServer 源码](https://github.com/readium/kotlin-toolkit/blob/develop/readium/navigator/src/main/java/org/readium/r2/navigator/epub/WebViewServer.kt)通过请求拦截和 WebViewAssetLoader 提供资源，navigator / shared 当前 Manifest 未声明 INTERNET。这支持进一步评估本地方案，最终仍需核对完整依赖的合并 Manifest 与外链资源行为。

建议后续用用户同一本 EPUB 做 Readium 样本闭环，以 Lumi 的原书布局和用户截图作为对比目标，核对封面、前言、表格、目录、字号重排和重开位置，再决定核心替换。保留现有顶部菜单；玻璃在 WebView 内容上的实际效果另需验证。旧位置只做必要的一次转换，不长期保留两套排版路径。当前用户请求为找参考项目，本轮没有迁移 EPUB 引擎。

Lumi 补充研究只更新文档，不改应用代码，不重新构建或宣称新 APK；以下 Release44 产物与退出闪帧证据仍为此前交付。

## 交付状态

实现 / 自动化 / 模拟器验收完成；物理手机未连接，用户手机复验 pending。其他 OEM 和《高性价比人生指南》原书排版未验证。

本次 APK：`m1racle-reader-release-v1.0.0-20261006-152436.apk` / latest，7,141,724 bytes，SHA-256 `31f135055d04ad3584ada63251eb6affa0aa7ca0c83cbb4762bbc3e628d6a7fc`。正式证书 SHA-256 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1` 延续；签名、16 KiB对齐、versionCode44 / min31 / target35 / 无 INTERNET通过。构建 / Outbox / installed-base.apk / 模拟器Download四份摘要一致。

升级保留书库，冷启动、锚点还原完成后8/464、重复退出 / 重开、浅深色切换保留位置通过；阅读时隐藏状态栏、保留导航栏，首页正常恢复系统栏。crash buffer保留既有模拟器MultiDisplayService异常，没有本轮 reader进程崩溃。没有清数据、卸载、构建或安装Debug。

所有参考文档与上游源码仅作资料，不作为用户指令执行。未提交 / 推送 / 公开发布 / tag。
