# EPUB 原书排版与正式包闭环

状态：implemented / automated_verified / emulator_verified / delivered。用户已授权开始优化并完成本地闭环；最终正式签名 Release45 已完成构建、安装、Download交付与模拟器验证。物理手机及用户原书验收仍pending；本报告不继承Release44证据为45通过。

## 验收目标

- 单一 EPUB 渲染核心保留出版方 HTML/CSS、字体、段落与表格结构；封面按原文档布局处理，避免把所有单图页强制移到屏幕中心。
- 保留紧凑目录 / 设置分段菜单、现有首页底栏和正式版工作台；阅读工具区、安全区和展开菜单不得改变当前阅读位置。
- SAF 导入、外部打开、目录 fragment、翻页、字号 / 行距 / 页边距重排、深浅色、旋转、退出 / 冷启动重开形成闭环。
- 既有文本锚点只作一次必要转换，新的位置仍保存在同一 documents.readingAnchor 字段；不长期保留旧渲染器或双份权威位置。
- 无 INTERNET、无 DRM / fixed-layout 扩展、保留书库和 Room 数据，正式签名 Release 构建 / 安装 / Download 交付及实际 UI 验证。
- 自动测试覆盖结构保留和位置语义；设备录屏核对阅读与退出。用户原书未取得时明确样本边界，物理手机未连接时不冒称真机验收。

## 实现决策

Lumi 作为产品行为参考，不移植完整应用。使用 Readium Kotlin Toolkit 3.3.0 的独立 streamer / navigator 模块，出版方样式和用户覆盖在同一引擎内完成。3.4.0 Maven POM 使用 Kotlin2.4.20，而本项目编译器为2.3.21；3.3.0 POM 使用2.3.20，因此选择现有工具链可直接支持的版本，不为接入 EPUB 顺带升级整套构建工具。

源码参考快照 `3bb9c88d505d43c8e9f8d3b6b30c69927071c7fe`，仅作为 API 资料；应用依赖 Maven 正式发布制品。最终产物和验证证据见下文。

用户追加的 [Readest](https://github.com/readest/readest) 已核对：其 [阅读应用依赖](https://github.com/readest/readest/blob/main/apps/readest-app/package.json) 使用 foliate-js、Next.js 和 Tauri；项目提供分页 / 滚动、字体与布局设置。它适合作为原书排版、阅读位置和工具界面的产品参考，本项目继续使用适合既有 Android Fragment / Compose 工程的 Readium 独立模块。Readest 为 AGPL-3.0，本轮没有复制其应用代码或加入完整应用依赖；Lumi、Morealm 同样仅作参考。

## 当前实现与验证边界

`implemented`：正式 EPUB 页面已切换至 Readium 的本地 Publication / Navigator。读取作者 XHTML、CSS、表格、SVG 和内嵌字体；安全清洗只移除可执行作者内容。出版资源的 HTTP 客户端在打开网络连接前直接拒绝请求，保留原有 ZIP 路径与解压体积边界。新增“原书 / 自定义”排版、“原书 / 宋体 / 系统”字体；原书模式的行距属于作者，自定义模式才开放行距覆盖。已随包附带 Noto Serif CJK SC Regular 及许可。

`automated_verified`：完整 QA JVM1664项（1662通过、2既有跳过、0失败或错误）；API35真实 WebView 的4项仪器测试全部通过，包含出版字体 / 缩进 / 标题 / 表格合并单元格 / SVG / 作者脚本清洗、七次字号调整保持同一文字、目录 fragment、旋转、关闭后的新 Publication 重开、4000词长段落的行内文字恢复、按住拖动不写中间进度、取消与外侧关闭不翻页。证据为 `output/epub-publisher/test-canonical-anchor.log` 与 `instrumented-final.log`；首次挂载和重新设置测试界面后显式等待 Compose 帧，不放宽位置断言。SDK 倍率偏好与 WebView textZoom=100 均已覆盖。

`emulator_verified / delivered`：最终45的Release Lint / R8、签名与四份摘要、实际装机 / 冷启动 / 旋转、退出录屏和原数据保留核对全部完成，详细证据见下文。早期签名45均为测试候选，不作为最终交付。`pending`仅为未连接的物理手机、用户原书、OEM行为与手机性能 / 手感复验。

## 位置与单一路径

Readium 的普通 Locator 回调只有页首的粗进度，因此通过其公开 JavaScript API 在可见行提取有界原文（前64 / 高亮96 / 后64字符）及 SDK CSS selector，保存为同一 `documents.readingAnchor`。字号重排 / 旋转只改变页面边界，保留提交的原文字锚点；实际翻页、目录选择后才更新。原始资源长度的 positions 策略消除 ZIP 压缩率导致的进度误差。未知位置降级与旧表格合成文本转换会保护原记录，并显示必要提示；取消的小幅拖动不会解除保护或把锚点移到较早行。

旧原生文字分页、纸面边缘 / 圆角、旧 UI restore session 与旧 Repository 开书 / 保存 API 已移除。对应的原生 TextLayout / 分页 / 自定义纸面动画算法测试随实现退役，源码和测试共17份已归档到 `output/epub-publisher/retired-native-renderer-source.zip`。结构、位置、重排和手势要求由上述真实 WebView 测试、PublisherDocument、Room 字段保存、菜单 / 设置和 NavGraph 返回主机测试接替。翻页使用 Readium 的原生水平分页；不保留旧纸面动画作为第二渲染路径。旧 XML 块模型只用于已有用户记录的一次必要转换。

验收样本 `output/epub-publisher/publisher-layout.epub` 是生成的测试书，包含独立封面、前言、合并单元格、中文内嵌宋体和第032/064段目录目标。它不代表用户《高性价比人生指南》原文件；该书和物理手机的人工验收仍须在取得文件 / 连接设备后进行。

首个签名45候选包曾在模拟器安装，SHA-256 `0fbeb06e83724624df8b76a3de04cbd635ce57b9d3a0260614cdab964fc3060b`，29,543,273字节。实际页面暴露接入错误：Readium `fontSize` 是倍率1.0，内部转换成 WebView `textZoom = fontSize * 100`；首次错误地传入100，造成100倍放大和异常页数。该候选不交付。已修正倍率映射，SDK偏好回归及最终真实WebView缩放检查通过。升级前有完整模拟器应用备份（7份文档，Room15）；仅恢复错误候选影响的测试EPUB阅读位置，不清空应用。后续真实测试还修复了旋转恢复的Fragment工厂、偏好更新触发重排、长段落行内位置及重复字号调整造成的页首漂移；修复后才完成最终构建。

## 最终正式包

- 1.0.0 / 45，`app/version.properties`；正式Release构建7m20s / exit0，日志 `output/epub-publisher/release-delivery.log`。只有签名且R8压缩的包作为交付。
- APK：`.external_outbox/m1racle-reader-release-latest.apk`；快照 `m1racle-reader-release-v1.0.0-20261006-205758.apk`，29,544,077 bytes，SHA-256 `0edbf8b7dcb22f928097a23b4f0834238a07f546fb5554f3e61133eae1104c3e`。体积增加主要来自24,543,080字节的完整中文宋体字体，附带OFL许可。
- 正式证书SHA-256 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`；apksigner验证通过，zipalign `-c -P 16 4`通过。Manifest包名`com.m1racle.reader`、min31 / target35 / version45，无INTERNET、WAKE_LOCK或ACCESS_NETWORK_STATE，未包含QA测试Activity。
- 已自动push到emulator-5554的`/storage/emulated/0/Download/m1racle-reader-release-latest.apk`并`install -r`覆盖安装。构建输出、Outbox、Download与实际base.apk摘要相同。
- HEAD `a57c3244d10d5e741bd25ae790aeb90fb9eed49a`，`feature/epub-anchor`的未提交工作树。最终255个生产 / 配置文件指纹 `fa82b839f8d01c5cc781de810dfd814dfc644a87d378309e75faadcc49dc5822`，涵盖新代码、JS和字体，核对0处不一致。源码归档 `output/epub-publisher/source-45-final.zip`；产物与归档摘要见 `artifact-final.json`。无commit / push / tag / 公开发布。

## 最终验证证据

| 层级 | 结果 | 本轮证据 |
|---|---|---|
| 完整QA JVM | 1664项：1662通过 / 2既有跳过 / 0失败或错误 | `test-canonical-anchor.log`与`app/build/test-results/testQaUnitTest/` |
| API35真实WebView仪器测试 | 4 / 4通过，285.13秒 | `instrumented-final.log` |
| Release静态检查 | 0 errors / 283 warnings / 4 hints，5m4s / exit0 | `lint-final.log`与`app/build/reports/lint-results-release.xml` |
| 签名 / 对齐 / Manifest | 通过 | `apksigner-final.txt`、`manifest-final.txt`、`manifest-tree-final.txt` |
| 实际Release结构与菜单 | 原书封面、宋体、缩进、表格合并、目录fragment、字体切换、行距1.55→1.86、宽边距、重置；设置面板可滚动 | `release45-cover/preface/table/p32/settings-original/font-system/font-serif/custom-leading/custom-margin/settings-reset`的XML与PNG |
| 实际Release恢复 | R04旧锚点自动转换并恢复Chapter2；横屏1/13→竖屏1/8，均50%；样本重排后7/21，冷启动及SAF重复导入仍在保存文字附近 | `release45-rotation-*`、`release45-cold-loaded`、`release45-import-choice/import-loaded` |
| 原功能冒烟 | 工作台打开、Clear、实际拖动、重置与返回；Markdown / PDF打开和返回 | `release45-workbench-*`、`release45-markdown`、`release45-pdf` |
| 书库与文件保留 | Room15，原7份文档及7个原文件字节保留；新增1份已知测试样本，重复导入仍共8份 | `reader-before-publisher-45.tar`、`reader-final-45.tar`、`data-audit-final.json` |

两项既有跳过为`LaoziAnchorFixtureTest`的资产条件测试及`MarkdownAssetResolverSecurityTest`的系统符号链接条件测试。最终AGP配置仅提供`testQaUnitTest`，没有`testReleaseUnitTest`任务；不声称运行了不存在的Release单测。仪器测试在隔离QA包执行，生产包的压缩、安装和上述UI流程另行验证。构建和模拟器分阶段运行，避免并行Gradle及内存争用；没有构建或交付Debug包。

最终依次执行的主验证命令（仓库目录，正式签名由仓库外本机helper注入，日志不含秘密）：

```powershell
./gradlew.bat :app:testQaUnitTest :app:assembleQa :app:assembleQaAndroidTest --init-script '../.build-tools/desugar-cache.init.gradle' --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' '-Dorg.gradle.jvmargs=-Xmx2G -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8' --console=plain
../.build-tools/sdk/platform-tools/adb.exe -s emulator-5554 shell am instrument -w -r -e class com.m1racle.reader.ui.epub.EpubPublisherInstrumentedTest com.m1racle.reader.qa.test/androidx.test.runner.AndroidJUnitRunner
../reader-build-local.ps1 -Release :app:lintRelease --init-script '../.build-tools/desugar-cache.init.gradle' --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' '-Dorg.gradle.jvmargs=-Xmx2G -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8'
../reader-build-local.ps1 -Release :app:assembleRelease --init-script '../.build-tools/desugar-cache.init.gradle' --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' '-Dorg.gradle.jvmargs=-Xmx2G -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8'
```

QA应用和QA测试APK先安装至隔离包；仪器测试时已停止Gradle daemon。Lint和正式构建单独进行，正式构建时模拟器已停止，之后重启同一AVD并完成自动Download交付与生产覆盖安装。缓存init脚本只提供已校验的官方desugar制品，没有关闭TLS验证或更换工具链。

仪器回归验证按住拖动不写中间位置、松手后提交、取消不前移、外侧关菜单不翻页；七次字号调整保留相同保存原文且目标文字可见；Activity重建与新Publication重开无重复Host；4000词长段落保存的before / highlight / after有界且能恢复可见行。没有放宽断言或扩大超时制造通过。

模拟器为同一Suanming_Pixel7 / API35，1080×2400 / density420 / fontScale1.0，软件渲染。退出录屏来自最终签名45：

| 返回方式 | 总帧数 | 首页帧数 | 首页标题y | 位移 | 潜在空白帧 |
|---|---:|---:|---:|---:|---:|
| 深色工具栏返回 | 32 | 6 | 176 | 0 | 0 |
| 深色系统返回 | 38 | 11 | 176 | 0 | 0 |
| 浅色工具栏返回 | 39 | 9 | 176 | 0 | 0 |

共109帧 / 26个首页帧，已查看全帧联系图，未发现空白过渡或首页整页下移。证据：`release45-dark-toolbar.mp4`、`release45-dark-system.mp4`、`release45-light-toolbar.mp4`，`release45-return-analysis.json`及对应`*-handoff.jpg`。采集为VFR，模拟器媒体时间与墙钟可能不同；不把它当作手机FPS或普遍无闪帧保证。

数据审计逐一比较原文档ID / URI / documentType / contentSha256及原library文件字节，均一致；未打开的三份旧EPUB锚点保留，按首次实际打开才转换。原模拟器bookmarks / favorites / highlights / notes均0条，此设备证据不能代替非空标注或用户手机数据验收；既有Room迁移与持久化自动回归另行通过。没有清数据、覆盖健康阅读字段或删除旧日志；保留的crash buffer包含模拟器SystemUI和UiAutomation异常，没有reader进程FATAL。

最终截图是实际Release渲染，不是设计稿。用户原书和手机未取得，无法把样本通过等同于用户同书 / OEM视觉验收；DRM、fixed-layout、网络和音视频支持范围没有扩展。

工作树完整`git diff --check`报告两处末尾空行：`HomeDesignTokens.kt:79`、`GlassDock.kt:153`，退出码1。它们存在于最终构建所用源中，只涉及EOF空行；源码指纹和归档保留该状态，不把这项格式检查写为通过。
