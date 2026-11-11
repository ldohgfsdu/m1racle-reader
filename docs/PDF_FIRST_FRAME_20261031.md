# PDF 首屏完整显示优化

本文保留 91 版当时的实现与验证记录。当前 92 正式包及既有拖动失败关闭结果见 [固定视口手势报告](PDF_INPUT_ROUTING_20261031.md)；当前原生整轮已达 39/39，以下历史结果不回写。

状态：READY_WITH_LIMITATIONS。implemented、首屏专项 automated_verified 和正式包模拟器 device_verified 已完成；完整原生回归 37/38，物理手机 / 人工验收 pending。

## 输入与问题

用户提供的 7.50 秒录屏连续三次打开同一本 PDF，出现深色首页后空白亮屏、封面矩形块依次出现。第三次约 5.9 秒开始加载，6.2–6.7 秒仍为不完整分块，6.8 秒完整。抽帧证据：D:/03_M1racle Reader/output/video-review-20261031。录像没有包版本号，不用于认定当前 90 真机回归范围。

## 开源方案比较

按用户本轮要求先检索维护中的开源实现、许可证、依赖和已知问题。2026-10-31 查阅：

| 项目 | 维护与许可证 | 设计和依赖 | 已知问题及本轮取舍 |
|---|---|---|---|
| [AndroidX PDF](https://github.com/androidx/androidx/tree/androidx-main/pdf) | 官方持续发布，2026-08-26 发布 1.0.0-beta01；Apache-2.0 | BitmapFetcher 使用完整低清整页覆盖分块空隙，复用上一轮完整底图；依赖 AndroidX PDF 文档与视图栈 | 发布记录有大型选区状态恢复、位图序列化与缩放相关修复；仅借鉴底图承接设计，复用 Reader 的 PdfRenderer / pageCache，不引入整个查看器 |
| [MJ PDF](https://github.com/mudlej/mj_pdf) | 官网标明维护中，GitHub 为说明与问题镜像，源码在 GitLab；GPLv3 | app + AndroidPdfViewer + PdfiumAndroid，原生依赖 PDFium / FreeType / libpng / JNI | [#103 分屏后水平锁失效](https://github.com/mudlej/mj_pdf/issues/103)、[#100 文字清晰度反馈](https://github.com/mudlej/mj_pdf/issues/100) 均提示要专门验证几何和清晰化；不复制其代码，不迁移 PDFium |

主要实现依据：[BitmapFetcher.kt](https://github.com/androidx/androidx/blob/androidx-main/pdf/pdf-viewer/src/main/kotlin/androidx/pdf/view/BitmapFetcher.kt)，保留整页底图后逐块细化。依赖依据：[AndroidX build.gradle](https://github.com/androidx/androidx/blob/androidx-main/pdf/pdf-viewer/build.gradle)、[MJ PDF 模块和许可证说明](https://github.com/mudlej/mj_pdf#how-to-build)。维护和修复记录：[AndroidX 发布记录](https://developer.android.com/jetpack/androidx/releases/pdf)、[MJ PDF 项目说明](https://mudlej.com/projects/mj-pdf/)。上述问题是项目反馈，不等于 Reader 的已证实缺陷。

## 根因和修改

- openDocument 原来在 PdfRenderer.open 成功后立即发布空 pageBitmaps / pageImage 的 Ready，整页预览、几何恢复与分块渲染在此后竞争同一个原生 renderer。
- 现在保持 Loading，先以已有 SCROLL_PREVIEW 有界宽度渲染恢复目标页；完整位图、原始页宽高比和缓存元数据一次发布，再启动邻页和高清请求。持久化 viewport session 页优先于旧 lastScrollIndex，避免先画错页。
- 连续阅读在测量几何并应用恢复位置期间保留主题加载面，不暴露默认页零的中间帧。初次录屏发现邻页晚约两帧，随后将切入条件扩展为所有可见页有底图或明确失败。逐页渲染错误集中保存在 pageRenderErrors，当前页 renderError 由该表派生；失败页显示既有重试入口，不把加载面无限留在屏幕上。
- DocumentPage 的高清分块必须有整页底图才能显示；QA 明确设置的 tile-only 诊断能力保留。已有底图在高清更新时持续显示。横向模式本身已有 bmp 分支保护，同样预备首屏底图。
- 不增加依赖，不更换引擎，不增加持久化缓存或双重阅读位置来源。

## 验证目标

新增原生测试 PdfFirstFrameInstrumentedTest：

1. 订阅第一条非 Loading 状态，验证其完整底图和两个远隔区域像素；纵向和横向各重开三次，并覆盖 viewport session 页与旧 lastScrollIndex 不一致。不能以等待后续成功帧替代首帧。
2. 真实 Compose 页面先给孤立红色 tile、不给底图，应保持空纸而不显示散块；底图到达后同一区域显示高清红块，未覆盖部分保持完整蓝色底图。

3. 实际 Compose 可见两页：只有主页底图不能报告首屏就绪；邻页底图到达后才能报告。
4. 同一可见邻页若明确渲染失败，应显示重试并结束等待，不无限加载。

继续执行已有 PDF 锚点、缩放、瓦片像素、模式切换、选字和数据隔离回归。正式包须来自同一份最终源码；手机未连接则不复用历史手机结论。

## 边界

本轮修正首屏发布时序与分块展示，不承诺固定启动提速。仍应以连续录屏检查闪白、分块和清晰化。录屏中的大块页下空白无法确认是原文件内容还是布局，未裁切页面。录像中的系统栏与版本无法核实；当前代码沉浸策略隐藏状态栏并保留导航手势条，本轮不擅改系统导航行为。

## 诊断记录（不作为最终通过证据）

- 初轮同源候选 36 项原生回归为 35/36；REAL_1X_HOLD_NO_TWITCH 在第 73 行 directionalFrames >= 30 失败。旧 90 QA 在同一模拟器、相同测试 APK 下同一行复现。未更改路径、阈值、断言或跳过测试。
- 新增 firstReadyAlreadyContainsTheCompleteRestoredPageOnEveryReopen 在旧 90 精确报 first Ready must not expose an empty page，证明能检出原来的空底图发布。
- 并行完整检查在 R8 期间因主机原生内存不足中断，hs_err_pid16032.log 明确记录 G1 mmap 分配失败。候选包没有据此交付；清理确认属于已崩溃父进程的残留检查进程后，改为串行、单 worker 构建。
- 重开录屏使用从用户录屏封面提取的三页合成 PDF，仅导入模拟器 QA。它不是原书文件，也不支持原书真机验收或固定性能倍数结论。
- 第一轮视觉复验仍发现可见邻页空白，此后才加入整个可见区域的就绪条件；此前录屏保留作发现问题的证据。

## 最终验证与交付

| 项目 | 本轮证据 |
|---|---|
| 最终源码 | source-tested-v91.zip、source-tested-sha256.json；661 个编译输入核对一致 |
| QA / Release 单元测试 | 1880 / 1717 项，均 0 failures / 0 errors，各 2 项既有环境跳过；final-serial-checks.log |
| lintRelease | 0 errors、179 warnings、6 hints |
| 新增首屏原生测试 | 4/4，通过第一状态、跨模式重开、底图 / 瓦片像素、可见邻页等待和失败重试验证 |
| 完整相关原生整轮 | native-final38.txt、native-summary.json；37/38、1 failed、0 skipped，未拼接诊断通过结果 |
| 瓦片像素和既有功能 | 本轮真实原生渲染、选字、视口 / 手势和三页条其余检查通过；仅拖动采样计数失败 |
| QA 连续画面 | after91-complete.mp4；first-visible-frames.png 与 video-first-visible-summary.json：三次首次正文同时具备主页与可见邻页 |
| 正式包模拟器升级 | 89 -> 91，保留 firstInstallTime，原合成 PDF 仍为 2/3；release-emulator-smoke.json |
| 正式包深色重开 | release91-dark.mp4；三次从深色加载面完整切入，无白屏散块；当前正式进程 crash 缓冲无 fatal |
| 签名 / 对齐 / 资源 | verification.json、signature.txt、alignment.txt，生产证书、16 KiB、35 assets 和安装包一致性通过 |
| 手机与人工验收 | 未连接物理手机；未安装或推送到手机 Download，pending |

交付：D:/03_M1racle Reader/apk/M1racle-Reader-1.0.0-91.apk，27638646 字节。SHA-256：5F83C0BA19C120DC51FAB8ACBB75B13C839A64AD07DFC297C778600A72494A13。构建、Outbox、交付、模拟器安装包及其 Download 哈希相同。本次没有公开发布或推送。

### 未关闭的既有检查

拖动采样断言在旧 90 与新候选上均失败。主线程同步的诊断尝试仍仅得到 27 帧，未能证明或消除原因，故已撤回无效同步改动。只补充失败消息显示实际计数，仍严格要求至少 30 帧；没有改轨迹、跳过该项或降低断言。最终 native-summary.json 保留 allPassed=false，交付候选明确为 READY_WITH_LIMITATIONS，不把它说成整轮通过。

模拟器全局夜间模式已从测试 yes 恢复 no；方向 0/0、字体比例 1.0 保持，测试用模拟器已正常关闭并保留数据。所有真机结论以未来连接物理设备后的本轮证据为准。

补充诊断：参照 Android 平台测试中连续 postOnAnimation 等待显示帧的方式，在原轨迹每步后等待两个显示帧再采样，仍为 27 帧；display-sync-diagnostic.txt 保留失败。该尝试不能解释原因，已完整撤回，最终测试源码与 native-final38.txt 的已执行版本逐文件摘要一致。没有以额外等待制造通过。
