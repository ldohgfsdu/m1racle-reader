# PDF 页面渲染重复开销优化

2026-10-30，已进入正式签名、压缩 Release 1.0.0 / 88。以下四轮耗时保留开发阶段测量，最终打包验证另列。

## 实现与范围

PdfViewerViewModel.requestPageRender 原先依次调用 pageSizePts、renderPage：两次进入 IO 调度与互斥锁，两次打开和关闭同一 PDF 页面。现在 renderPage 成功结果携带本次已打开页面的 PdfPageSize，调用方直接据此计算原始宽高比。每次页面预览或清晰度替换由两次打开页变为一次。

没有调整渲染分辨率、位图预算、预加载范围、手势、瓦片策略或 PDF 引擎。比例仍优先复用已有缓存；新页使用原始 PDF 尺寸，避免以取整后的位图尺寸推算几何。删除不再使用的默认比例回退常量。pageSizePts 仍用于独立原生几何测试。

## 验证

- Release PDF JVM：648 项，0 failures / 0 errors / 0 skipped，包含连续视口、旋转锚点、缓存与瓦片策略。随后仅删除未使用常量；最终源码另经 QA 原生构建及 Release lint 编译。
- 新增 PdfRenderMetadataInstrumentedTest，在 API35 Suanming_Pixel7 模拟器的隔离 QA 包运行四次，全部通过。横竖两个合成页面、137/800px 两种输出宽度的原始尺寸及逐像素结果一致；无效页、关闭后调用通过。未操作正式包的数据。
- 原生测试首轮编译因 PdfDocument 不适用 use 而失败，改为 try/finally 关闭后通过；原失败日志保留。
- 没有进行整段阅读 UI 的帧率、滚动或物理手机验收，不能把渲染专项等同于完整阅读体验通过。

## 耗时证据

同一测试 APK、同一 PdfPageRenderer、同一合成 PDF，比较“pageSizePts + renderPage”与“直接 renderPage”。这模拟移除前置查询的调用差异，不是两个正式 Release 包的端到端比较。每轮先预热六对，再交替顺序采样二十对，计时覆盖协程调度和渲染，位图回收在计时外。使用 System.nanoTime，非 CPU 采样或帧时间。

| 轮次 | 前置查询 + 渲染中位数 ms | 直接渲染中位数 ms | 前置查询 P90 ms | 直接渲染 P90 ms |
|---|---:|---:|---:|---:|
| 1 | 14.25 | 31.28 | 125.09 | 114.02 |
| 2 | 19.90 | 16.01 | 31.64 | 31.97 |
| 3 | 14.56 | 11.42 | 20.25 | 14.62 |
| 4 | 9.13 | 7.68 | 12.37 | 9.67 |

后三轮中位数下降，首轮反向且长尾明显，轮间也持续变快；模拟器调度、运行时预热等因素未隔离。结论仅为减少确定的重复工作，并有局部收益迹象；不宣称固定提升比例或滚动帧率改善。没有删掉首轮不利数据或增加性能容差断言。

## 交付边界

证据目录：D:/03_M1racle Reader/output/pdf-render-metadata-20261030，含源码摘要、648 项 XML、原生 XML/日志、四轮原始耗时及 timing-summary.json。

88 已包含本次 PDF 优化、BOM 修复及导入忙碌反馈。手机未连接，真实阅读性能 pending；六项既有 PDF 扩展原生失败未在本轮关闭。未提交、未推送、未公开发布。

## 最终 88 同源构建与复验

- 冻结源码 source-tested-v88.zip 与 source-tested-sha256.json；完整 QA 单元测试 1847 项、Release 单元测试 1690 项，均零失败、零错误，各跳过两项既有环境用例（老子夹具缺失、Windows 符号链接）。lint 零错误、179 warnings、6 hints。此前 4G JVM 构建遇主机内存不足，最终使用 2G 完成，失败日志保留。
- 完整 QA 曾发现两项首页导入按钮禁用语义回归：进度指示器独立合并语义，导致“导入文件”节点缺少 disabled。改为由外层动作节点提供忙碌语义，指示器清空重复语义。没有放宽测试；首页与 Dock 定向 28/28 通过，随后上述完整回归通过。失败候选归档在 rejected-busy-semantics，未交付。
- 最终同源原生专项：PdfRenderMetadataInstrumentedTest 通过；R7P0c12TilePixelCompositionInstrumentedTest 失败。page=0 / rotation=0 / scale=1.0 / bleed=1 的 meanDelta=2.0479540745856353，超过既有 2.0 阈值。同一测试 APK 在旧 QA86 上复现完全相同数值；未放宽阈值，未计为通过。对比仅操作隔离 QA 包，随后恢复 QA88。见 native-final.log、native-results、pixel-baseline86.txt、pixel-baseline-package.json。
- m1racle workers 首次接口失败，重试完成只读审查，无 P0/P1/P2。P3：尚无 ViewModel 成功渲染到缓存、比例与锚点的完整集成用例；渲染器原生测试与已有几何单测不能替代这一覆盖。见上级目录 independent-review.md。
- 正式 APK：D:/03_M1racle Reader/apk/M1racle-Reader-1.0.0-88.apk；27605878 字节，SHA-256 8C64AAB0EFF1F0775252F82ED3B02E383394417DB28811B42438F4441F67A1CC。生产签名、16KiB、压缩、35 个 assets 同源及品牌资源校验通过，无 INTERNET；见 verification.json。
- API35 模拟器保留数据覆盖安装成功，firstInstallTime=2026-10-03 06:05:17 未变；交付、已安装包与 Download 副本摘要一致，见 install-verification.json。正式 PDF 竖屏打开和横屏均显示第 2/3 页，见 release-pdf-portrait、release-pdf-landscape 的 PNG/XML。系统启动曾出现 System UI 无响应，选择等待后恢复；该系统对话框截图不算 Reader 验收。
- 后续模拟器曾离线，调整本次模拟器启动的 SDK 路径后恢复，未改构建环境脚本。补充确认 PDF 退出重开与横屏再回竖屏保持第 2/3 页；Markdown 标题和正文可见；EPUB Chapter 2 恢复第 10/18 页。见 release-pdf-reopened-ready、release-pdf-landscape-resumed、release-pdf-portrait-return、release-markdown-open、release-epub-ready 的 PNG/XML。屏幕方向设置恢复为 accelerometer_rotation=0、user_rotation=0。物理手机和持续滚动帧率未验证，不能声称完整性能验收通过。

最终证据目录：D:/03_M1racle Reader/output/pdf-render-metadata-20261030/v88。发布后仅整理交付文档，编译源码应继续与冻结摘要一致。