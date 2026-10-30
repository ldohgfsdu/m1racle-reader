# Reader 稳定性修复与验收

2026-10-30。承接当前项目审查，范围为书签原子切换、EPUB 命名锚点、PDF 原生失败及渲染到阅读位置的集成验证、源码基线和入口文档。

正式签名、压缩的 89 版已交付并在模拟器保留数据覆盖安装。本轮确认的代码缺陷已修复，相关原生回归 40/40 通过；状态为 automated_verified。滚动遥测已采集，但模拟器帧耗时偏高，性能验收和物理手机/人工验收仍 pending。

## 行为修复

- 书签切换在 ReadingRepository 的同一 Room 事务内查询并新增/移除，两个重叠操作顺序执行，不会再各自插入同位书签。移除仍清理该位置的全部重复记录，其他位置和文档保持独立。不需要改变 Room schema。
- EPUB 块级与内联使用同一锚点登记逻辑，支持 a 元素的非空 name 和元素 id；同名片段取文档第一处，不让普通 name 属性占用目录目标。
- PDF 整页使用显式等比缩放矩阵，与瓦片的页面到像素变换保持一致。旧 null transform 会按已经取整的 bitmap 宽高分别缩放，595×842 页面拟合到宽 256 时，纵向会偏约 0.272 px。非整数缩放档的像素参考也保留浮点宽度，未放宽像素阈值。
- PDF 缩放结束的 450ms 防抖由拥有该手势的协程负责。移除第二套墙上时间判断，避免协程延时结束后又被另一时钟否决，导致稳定状态未提交；手势代次、取消及单次提交保护仍保留。
- 新增实际渲染器、ViewModel、缓存、生产视口和数据库联动用例，覆盖原始比例、窗口变化、保存位置及退出重开。首轮测试发现真实尺寸晚到后按视口顶部重映射会偏移已恢复的页内锚点，改为在已有测量尺寸时保留页面内阅读中心。
- QA 原有 MarkdownScrollBenchmarkActivity 未注册，直接启动会返回 Error type 3。已仅在 QA 清单注册，实测可以启动并采集滚动数据；正式 Release 的清单不含该入口。

## 六项历史原生失败的归因

| 用例 | 根因与处理 |
|---|---|
| startupAndModeEntryRestoreApplyTargetBeforeAnyPageZeroFeedback | 夹具只有 30 页，却要求恢复 47 和 42 页；为该用例提供 60 页夹具，保留原目标和全部断言 |
| p0c14TileSwapDoesNotMoveDocumentTransform | 采样时最后一帧拖动事件尚未处理；先完成注入事件，再严格比较交换前后的完整变换 |
| p0c12StaleGeometryTileIsSuppressedWithoutChangingDocumentTransform | 把未测量的初始坐标与已适配的坐标比较；改为在实际几何就绪后注入旧 geometry generation，严格验证没有位移且瓦片被拒绝 |
| activeRealGestureIgnoresOldVmScaleEmissionAndCommitsFinalScaleOnce | 两个移动事件尚未全部消费就截取缩放；等输入消费完成后再发旧 VM 状态，保留缩放及单次提交断言 |
| pointerEpochSinglePinchSinglePinchSkipsTransitionFramesAndSettlesOnce | 450ms 延时后的第二套时间判断会丢弃提交；由同一个协程调度器完成防抖 |
| rapidSecondRealPinchCancelsOldSettleAndCommitsOnlyFinalTransform | 同上；保留快速第二次手势取消旧提交、只提交最终变换的断言 |

## 已执行验证

修复前在原 QA88 上重新运行 19 项生命周期原生测试，复现同样六项失败。首轮修复候选原生 25 项中 24 项通过，新增集成用例在重开后发现 fraction 0.5→0.943343 偏移；该候选没有交付。保留阅读中心的根因修复后，集成用例独立通过，随后进入同源完整验证。

| 检查 | 最终结果 | 证据（相对 v89 目录） |
|---|---|---|
| QA JVM | 1853 项，1851 通过、2 跳过，0 failures/errors | testQaUnitTest/、verification.json |
| Release JVM | 1696 项，1694 通过、2 跳过，0 failures/errors | testReleaseUnitTest/、verification.json |
| Release lint | 0 errors、179 warnings、6 hints | lint-results-release.xml |
| 最终隔离 QA 原生 | 40/40 通过，156.917 秒 | native-final.log、native-summary.json |
| 生产签名与 16KiB 对齐 | 通过，正式证书一致 | signature.txt、alignment.txt |
| R8、资源与包身份 | 89、非 debuggable、无 INTERNET，35 个 assets 字节一致 | verification.json、badging.txt、permissions.txt |
| 保留数据安装与交付摘要 | 构建、Outbox、交付、已安装和 Download 一致 | install-verification.json、final-device-verification.json |

两种 JVM 变体各自跳过的都是既有环境用例：未挂载的《老子》人工夹具，以及本机无法创建符号链接的资源越界用例。没有新增跳过、删除断言或扩大容差；瓦片像素阈值仍为 2.0。lint 的既有警告保留原报告，未禁用检查。

40 项原生覆盖：PDF 真实手势生命周期 19、配置恢复 1、瓦片像素 1、渲染尺寸 1、渲染到保存重开集成 1；真实 Room 的 100 次并发书签切换 1、EPUB 命名目录点击 1、Markdown 编辑器旋转 1、编辑桥接 1、QA 数据隔离 1、Room17 升级 1、PDF 位置原子事务 6、删除事务 5。书签 JVM 另覆盖挂起交错、跨控制器和 101 次切换奇偶性，EPUB JVM 覆盖真实 XHTML parser。

完整检查首次通过的记录为 full-checks-retry.log。补 QA 清单后再次调用 QA/Release 单测、lint、QA 及测试 APK 构建任务，增量复用未变化输入并通过（qa-performance-entry-retry.log）；随后重新安装最终 QA 包，实际重跑全部 40 项原生。清单补齐前的 40/40 记录保存在 before-performance-entry/。

中途一次完整检查因主机内存不足导致 JVM 原生 malloc 失败，保留 qa-performance-entry-checks.log、hs_err_pid16808.log 和 replay_pid16808.log；不是断言失败。仅停止本轮空闲构建进程，以单 worker、1GiB Gradle 堆和进程内 Kotlin 编译重试，完整任务通过。没有关闭用户其他应用。

## 正式产物与模拟器检查

- 版本：1.0.0 / 89，Room17；包名 com.m1racle.reader。
- 唯一交付：D:/03_M1racle Reader/apk/M1racle-Reader-1.0.0-89.apk。
- 实际大小：27605878 字节，26.33 MiB。
- SHA-256：82591CDDD4C83CA0A00EBF23E39885630DD62C055EA2E09E94D4AC795B46D8A1。
- 正式证书 SHA-256：2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1。
- 保留 profileable android:shell=true；QA 性能页未进入正式包，不交付 QA 或 Debug。

仅 emulator-5554 在线，API35 x86_64。正式包使用 install -r，升级前后 firstInstallTime 均为 2026-10-03 06:05:17；首页原文档与阅读记录保留。最终已安装包和 Download 的摘要再次核对一致。

| 正式包场景 | 结果 | 证据 |
|---|---|---|
| PDF 竖屏→横屏→回竖屏 | 始终 2/3 | rotation-verification.json 及 release-pdf-* 截图/XML |
| PDF 左右翻页→上下连续 | 始终 2/3，恢复原阅读模式 | mode-verification.json |
| PDF 退出重开、后台恢复 | 均 2/3 | reopen-verification.json、release-final.png/xml |
| 书签添加→移除 | 动作状态正确，删除本轮临时书签 | bookmark-ui-verification.json |
| Markdown 打开 | 合成夹具标题、中文正文和 rotation-check-body 正常 | release-markdown-open.png/xml、release-resume.png/xml |
| EPUB 打开 | R04 Anchor Check 恢复第 10/18 页，正文含 Chapter 2/C2P01W01 | release-epub-open.png/xml |
| 设备方向还原 | accelerometer_rotation=0、user_rotation=0，与原值相同 | final-device-verification.json |

验收脚本的三次诊断误报也已核清：模式菜单遮住顶栏页码，关闭菜单后验证通过；缺少明确组件的启动 Intent 未解析，改用可见启动器入口后恢复通过；方向校验误用 JSON 字段名，按原记录字段重查后确认系统设置一直为 0/0。均保留截图/原始输出，不归为产品缺陷。

## 滚动采集与性能边界

QA 合成 Markdown 页使用生产正文组件，实际执行 6 次向前和 6 次向后 300ms 滑动。gfxinfo 为 176 帧、99 帧 janky（56.25%），P50 44ms、P90 105ms、P95 133ms、P99 150ms；PSS 从 127227KB 到 138946KB。framestats 随后多采到一帧，共 177 帧，其统计窗口与首份 gfxinfo 不完全相同。原始数据、页面截图和 XML 在 markdown-performance/。

采集后的主机快照为总内存 14181MiB、空闲 1760MiB、CPU 31%；它不是滑动期间的同步资源记录，不能据此断定高帧耗时来自主机。没有修改前对照，不能声称性能达标、确认本轮性能回退，或由一次 PSS 增量诊断泄漏。采集入口缺失已修复，帧耗时偏高仍作为诊断和后续真机复测项，性能验收保持 pending。

## 源码与文档基线

原有全部 WIP 已保存在 codex/reader-stability-20261030 的 a9a979d 检查点，未删除原 reader、reader-audit-fixes-20261024 工作树。编辑器 assets 与摘要锁定的源码关闭 Git 文本换行转换；在 Windows autocrlf=true 的干净检出中，28 个 bundle 文件和 14 个源码摘要均一致。

补回 `.memory/active_object.yaml`，与 CURRENT_STATUS、PROJECT_INDEX、ACTIVE_OBJECT 的入口保持一致。当前状态仍以 CURRENT_STATUS 为准，阶段证据写在本报告。

实现分为本地提交：9650dc8（原子书签）、dfbc62f（EPUB 锚点）、99cc68a（PDF 几何、提交及恢复）、b651325（QA 性能入口）。版本与最终文档另有收尾提交；未推送、未公开发布。

source-tested-v89.zip 与 source-tested-sha256.json 保存正式构建候选；source-final-v89.zip 与 source-final-sha256.json 保存最终 QA 入口。两次冻结的编译输入仅 app/src/qa/AndroidManifest.xml 不同，正式包编译输入没有改变，source-final.json 记录该差异。最终核验逐项检查 654 个编译输入及 assets 摘要；不以当时 HEAD 名字代替未提交源码的实际摘要。收尾后的完整提交源码另存 delivery-source-v89.zip 和 delivery-source.json。

## 设备与验收边界

所有 instrumentation 只操作 com.m1racle.reader.qa；正式包保留数据覆盖安装，未清库或卸载。原 QA 没有 databases/shared_prefs 目录，已单独保存实际存在的 files 目录、tar 摘要及清单。

物理手机安装、真实中文输入法、持续阅读手感、低内存和厂商系统截图流程仍需连接物理手机后完成；模拟器记录不会代替这些验收。

本轮实现和回归状态为 automated_verified；device_verified 仅能注明 emulator，physical_phone_verified=false，human_acceptance=pending。自动测试通过不代表全部发布门禁或人工验收通过。

证据根目录：D:/03_M1racle Reader/output/stability-fixes-20261030；最终候选子目录：v89。88 当前状态已原样存入 docs/history/CURRENT_STATUS_88_before_stability_20261030.md。
