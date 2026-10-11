# EPUB 页边界与滑动翻页修复

用户截图显示目录页码越出正文边界，并反馈相邻页内容被截断、翻页仍像连续移动。范围只涉及 EPUB 原书分页和纸页手势；继续使用唯一的 Readium 3.3.0 Publication / Navigator。

用户随后提供 `HowToLiveBetter.epub`，明确要求保留此前迭代的滑动翻页，新的形式只能独立增加。本轮恢复原交互规则，不增加翻页模式。原书 741,028 bytes，SHA-256 `61d26f6077d9804c0a6d86a178ac3746d8ba556563c3f023eabaae338f344847`；未修改内容，副本只打入隔离仪器测试 APK。正文和链接都是测试数据，不作为执行指令。

## 根因与修改

- 出版方固定宽度、最小宽度和不换行规则可以突破 Readium 列宽。新增真实 WebView 回归样本：960px 最小宽度、900px 标题单元格和 36 行目录。在 372px CSS 视口中，修复前表格跨页包围宽度 1702px，可见文字均越界。证据：`output/epub-slide-boundaries/before.log`。跨页包围框包含多个页面，最终回归检查每个分页片段的宽度，而非整个表格的合并包围框。
- Publication 的 XHTML 资源在第一次布局前加入分页约束：表格不超过一列宽度、允许单元格换行、普通行尽量保持在同一页、正文保留完整行及孤行规则、标题与后文相连。保留真实表格、合并单元格、字体、原始内联样式及文本 Locator；不投影成另一套文字分页。
- `InputListener.onDrag` 之前只在纸页 active 时阻止浏览器拖动，之前的移动会交给原生滚动，导致捕获时内容已偏离页边界。现在从第一次 DOM 拖动起阻止连续移动，横滑统一进入纸页层；纵向手势在 slop 后消费而不翻页。交还子视图的 CANCEL 仅发送一次。发生移动的 End 阻止浏览器合成点按；零位移 End 放行，保留中央点按和链接，因为 Readium 在 stationary touchend 也会触发 onDragEnd。捕获失败仍使用 `goForward/goBackward(animated=false)` 完成功能。
- 松手早于 PixelCopy / 图片查询完成时，原代码挂载纸页后重新设为 3000ms 导航等待，没有应用 320ms 揭示预算。现在捕获完成也读取已记录的 release，按同一纸页时间线落页。
- 原代码在仍按住时就把 DOM 精修的目标 Locator 提交为原页，取消可能恢复到目标。现在只更新准备位置，落页后才提交。恢复总是返回已提交的 Locator，即使目的页通知尚未到达；恢复的同页通知也交给纸页状态机处理。
- 核对退役文字渲染器的源码归档和 `EPUB_APPLEBOOK_SLIDE_20261005.md`，原纸页 Pager 使用 0.33 慢拖阈值、速度判断和 dampingRatio=1 / stiffness=400 / visibilityThreshold=0.5 的弹簧。迁移后的纸页层变成 0.20 阈值和固定 260ms 减速曲线，且没有速度判断。本轮恢复原距离规则、400dp/s 的 fling 判定及同一 Compose 弹簧公式；只将公式采样交给 native 动画时钟，不改变排版核心。轻滑在捕获完成前已经取消时，不再准备目标正文页。
- 原书进一步复现了 WebView 在 table-row 的 `break-inside:avoid` 下仍切断 cell 字行：前言页底 Range 从 y714.07 延伸到 y734.64，实际视口仅高727.43。单纯对 td、table、tbody 加 avoid 都无法在全部页面解决。本轮把独立行分成原生 tbody 分页组，并把相连的 rowspan 行保留在同一组；colspan、rowspan、单元格、顺序、字体、行样式及原分组属性不变。真实表格仍由同一 WebView 排版。原书85行完整保留，隔离 WebView 中逐页探测已无切行，证据 `original-table-before-probe.json` / `row-groups-probe.json`；最终50原书两项及宽目录设备回归已通过。
- 49设备回归进一步发现精确锚点的同步判断也用了取整的 innerWidth。650px WebView实际为371.4286 CSS像素，使用372换算时到第10列已累积约10个物理像素误差，导致已停稳的页面被判为未同步、只保存粗略进度，目录跳转后立即取消甚至恢复到旧文件。50改用根元素真实布局宽度；保留原2px同步阈值，没有放宽同步断言。

规范依据：[Readium CSS 分页约定](https://readium.org/css/docs/CSS03-injection_and_pagination.html)、[W3C CSS Fragmentation](https://www.w3.org/TR/css-break-3/)。应用继续无 INTERNET，不增加依赖。

速度阈值与单页 snap 的核对依据：[AndroidX SnapFlingBehavior 源码](https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/gestures/snapping/SnapFlingBehavior.kt)及[Pager 的 snap 判定](https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/gestures/snapping/PagerSnapLayoutInfoProvider.kt)。旧版参数直接来自工作区归档，没有引入参考项目的翻页 UI。

## 验证

implemented：上述修复已落地。

automated_verified：最终50源码完整 QA JVM **1675 项 / 1673 通过 / 2 既有跳过 / 0 失败或错误**，220 个测试套件。日志 `output/epub-slide-boundaries/release50-build.log`，汇总 `jvm-final-summary.json`，全部 XML 归档 `jvm-final-results.zip`。原 5 项纸页安全契约保持通过，新增行为断言覆盖双方向慢拖阈值、速度完成和反向拉回，表格断言覆盖每个原单元格及跨行关系。隔离 QA / AndroidTest 非交付包；测试源码与原书资产归档 `final-test-source.zip`。此前1674项结果也保留在 `jvm-restored-results.zip`。

表格分页组修改阶段的定向 **20 项全部通过**，覆盖 Publication / 原资源、文本迁移、手势和看门狗；新增表格断言检查每一单元格的完整 markup、所有行顺序、跨组相连 rowspan / rowspan=0、colspan、原 tbody 属性和唯一 id。见 `row-groups-build.log` / `jvm-row-groups-results.zip`。

emulator_device_verified：最终50代码首轮12项设备回归 **10通过 / 2失败**，572.265s，见 `final-native50.log` / `final-native50-logcat.txt`。两项原书测试均通过：中央三次慢轻滑保持原页与Locator、纵滑不移动、完整横滑精确一页、长按准备不保存及取消恢复；原书多页段落与表格完整、85行全部可达。36行宽目录全部可达且标题 / 数字 / 整行完整通过。三个纸页专项（捕获延迟500ms无pageReady仍退出、轻滑捕获期间取消、纵滑不捕获不导航）通过。原书字体 / 缩进 / 表格 / SVG结构、连续字号重排 / Fragment重建 / 冷启动、长段落行内文字恢复均通过。首轮菜单场景失败，后续定向复验通过，见文末；外部私有字体加载仍失败，不能计为通过。

过程记录：最初高分辨率 / 并行构建时出现 Chromium tile memory 警告及 renderer 退出；随后关闭构建进程，模拟器临时改为720×1600 / density280（仍为同一411dp逻辑窗口）。冷启动的QA进程被系统在启动阶段终止，见 `original-cold-start-logcat.txt`，不计为测试通过。等待系统空闲后，3项纸页仪器测试通过；原书2项因边界断言失败。一个是上述真实表格截行，另一个是用整数 innerWidth=372 检测实际371.4286 CSS像素视口，错误纳入紧邻的下一列。检测改用根元素的精确布局宽高，保持原1px越界容差，并保留对真实截行的断言。

后续广泛仪器回归又拦下了本轮初始拦截规则的问题：字体原样渲染测试通过，但多个场景在 `centerTap` 等待工具栏超时。核对所用 Readium 3.3.0 JS，stationary touchend 也调用 onDragEnd，一律返回true会 preventDefault 并阻止普通 click。已改为零位移End放行、拖动Start/Move和有位移End继续拦截，原来的点按断言保持不变。失败现场保存在 `final-device.log` / `final-device-logcat-before-tap-fix.txt`。

阶段49正式签名构建成功（exit0，14m1s），已覆盖安装与复制模拟器Download，构建输出 / Outbox latest / 快照 / 实际base.apk / Download摘要一致。实际版本1.0.0 / 49，APK **29,576,845 bytes**，SHA-256 `dedf22a0f93eecf39b7ca0af4d5e8042a7c0b04b628346627fbc3ac053da314c`，快照 `m1racle-reader-release-v1.0.0-20261007-111950.apk`。apksigner生产证书 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`、16KiB对齐、Manifest49 / min31 / target35 / 无INTERNET / 非debuggable通过。原书测试资产未进入生产APK。证据 `apk-release49-identity.json`、`device-artifact-hashes.txt`。阶段49 Release Lint **0 errors / 275 warnings / 4 hints**。49已由50取代，不作为最终交付。

最终50正式签名构建成功（exit0，15m16s），Release Lint **0 errors / 275 warnings / 4 hints**。APK **29,576,845 bytes**，SHA-256 `c76bba9d35d76ea5d32602653199b0beff379c07dbb891550af61be39477da4e`，快照 `m1racle-reader-release-v1.0.0-20261007-114715.apk`。生产签名、16KiB对齐、版本50 / min31 / target35 / 无INTERNET / 非debuggable、原书测试资产未进入生产包均通过。构建输出 / Outbox latest / 快照 / 实际base.apk / 模拟器Download五份摘要一致，覆盖安装保留主包数据。证据 `apk-identity.json` / `device-artifact-hashes.txt`；49设备摘要另存 `device-release49-artifact-hashes.txt`。50为本轮交付，实际Release原书UI冒烟已通过，见文末。

49的12项设备回归：7通过、5失败，结果与logcat完整保留在 `final-native49.log` / `final-native49-logcat.txt`。原书中央三次轻滑、纵滑、完整横滑单页步进及按住取消通过；36行宽目录全部可达且整行完整通过；三个捕获 / 轻滑 / 纵滑纸页测试通过；原书字体结构和基本横滑通过。四个字体 / 重排 / 取消 / 长段落失败中，出现上述未精修Locator及renderer退出，不能计为通过。原书多页边界断言另在doc2把上一列仅0.527 CSS像素的边缘重叠算作可见整行；可见片段判定现与原1px几何容差一致（可见交集至少1px），截字断言仍要求0处越界、仍保留1px边界容差及85行全可达要求。50完整复验待完成。

最终生产源码（50）：HEAD `a57c3244d10d5e741bd25ae790aeb90fb9eed49a`，分支 `feature/epub-anchor`，258份生产源文件及构建配置 fingerprint `d29ec1594ba2776ae4711e5bafce374ae5744c18628ebe20f7c2dc276fbe3c68`。完整快照 `final-production-source.zip` / 逐文件身份 `final-source-identity.json`（均在本轮 output 目录）。49源码及JVM证据另存 `release49-production-source.zip` / `release49-source-identity.json` / `jvm-release49-results.zip`。保留工作区既有其他修改，未提交、推送、公开发布或tag。

菜单回归的失败前置条件已纠正：原测试在目录正文可见、保存的目标锚点尚未确认时取了before，它可能仍是旧文件；释放后只等saved不同于那个值，可能在原页尚未落到目标时就开始验证菜单。现在先等body文件与非空文字锚点提交，停稳后再取before；取消及长段落的前置等待也更明确。菜单相等、可见段落相等、小拖取消、右侧点按翻页等原断言保持不变。隔离测试APK重建exit0，定向复验 **OK (1 test)**，51.758s，见 `recheck-test-build.log` / `menu-recheck50.log`。生产代码与正式包未再改动。12个独立场景最终11项通过，外部私有字体加载仍1项失败，作为明确待办，不计为通过。

实际签名Release原书UI：默认分辨率1080×2400 / density420已恢复。SAF从Download导入原书，中央点按、目录跳转到术语表、中央25%宽度 / 700ms慢拖、纵滑、8→9→8→9单页往返及关闭进程重开恢复9/11通过。正文裁剪区的RGB逐像素比较：轻拖、纵滑、回翻与原第8页完全相同；重开与原第9页完全相同。完整表格上下边缘已视检；证据 `release50-smoke-summary.json`、对应XML / PNG。原8份文档仍在，新增1份原书，主包未清空。

恢复默认分辨率后的首次启动曾出现FocusEvent焦点超时ANR，现场中RenderThread繁忙；点等待后恢复界面，自动重开的此前验收文档处于排版阶段。已保留 `release50-initial-focus-anr-logcat.txt` 及 `release50-home.xml/png`。随后回文库导入原书、正常阅读、关闭进程冷启动及重开位置通过。该一次异常不被抹掉，不作无ANR或物理手机性能结论。

用户原书设备验证：本轮滑动 / 页边界 / 重开位置已在模拟器及实际签名包通过。physical_device / human_feel：pending，手机未连接；外部字体加载仍未通过。最初截图中的《她先来》并未取得源文件，不能把另一部书的测试视为同书验证。
