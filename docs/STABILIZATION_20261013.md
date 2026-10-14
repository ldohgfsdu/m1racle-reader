# 稳定性核查与验收矩阵（2026-10-13）

本页记录上午首轮核查的原始结果。用户随后明确取消工作台，冲突已按新要求处理；后续源码与验证见 [工作台取消报告](WORKBENCH_REMOVAL_20261013.md)，不能把本页的 Release 编译失败继续当作当前结论。

本轮承接 Code Wiki 后的下一步：保留开发成果、固定可回溯基线、校正发布门禁、执行当前全量测试并定位实际失败。当前产物仍是 **1.0.0 / 71，Room 17**，没有构建或分发新生产 APK。当前状态见 [CURRENT_STATUS](CURRENT_STATUS.md)。

## 结论

- 四项过期 JVM 契约已修正；全量 QA **1,787 项 / 0 失败 / 0 错误 / 2 环境跳过**。不是“1,787 项均执行通过”。
- 删除事务 instrumentation 类原先因非 void 返回值整体无法启动，现已修正；核心原生回归 **24/24 通过**。
- Release lint **0 errors / 198 warnings / 4 hints**，没有关闭检查。
- Dock 原生 **27/29**，两项浅色左端帽检查仍失败。未改动像素断言、阈值或生产材质。
- 已启用此前未生成的 Release host tests；首次实际编译暴露 QA 测试归属及工作台要求冲突，**Release 单测未通过，发布门禁保持未通过**。
- 仓库内验证入口可独立调用 Wrapper；Reading 与两类归位后的 QA 测试合计 **56/56 通过**。
- 当前仅 API 35 模拟器在线，物理手机验收 pending；未 commit / push。

## 源码检查点

| 内容 | 值 |
|---|---|
| HEAD | 1150aa6239fdb7c4b383a6d58861f1bb217cae66，包含既有未提交修改 |
| 检查点 | 工作区 backups/20261013-075653-stabilization/ |
| source.zip | 1,104 个非忽略文件，33,652,707 bytes |
| ZIP SHA-256 | 0ce499f8d51250843d14d12c4e8ec945c572459e233ba650a5b179471b17f2b0 |
| history.bundle | 63,286,219 bytes，Git bundle 校验通过 |
| bundle SHA-256 | 8b60953713a987c932a9bec3a1f342f101806c50da29905aede3d6c8375179b0 |
| 逐文件摘要 | 检查点 source-manifest.json |
| 完整工作树指纹 | 1a5e02db72ccaaa3020391dc0f02164cd95672d2679dae8145314f8f9e7314d9 |

ZIP 解压、逐文件摘要及生成前后源码一致性均通过。检查点包含所有非忽略源码/文档和 Git 历史，不含忽略目录、工具链、缓存、签名私钥和口令；恢复方式见检查点 RESTORE.md。未 reset / stash / 提交现有用户修改。

核查前 APK 同时保存为 release71-before.apk：27,131,998 bytes，SHA-256 **e67a0a9701b0879c627698b1d07c47fd313b4d9ad9bb8f94983be17607e71cde**。它用于回溯，不能冒充本轮新构建。

本轮比较检查点逐文件摘要，**260 个 main / production 文件未变**；QA 应用 APK 摘要未变，原生删除测试修复只重建 instrumentation APK。证据为工作区 output/stabilization-20261013/verification.json 与 production-source-unchanged.json。

## 已修正的验证问题

| 问题 | 根因与修正 | 验证 |
|---|---|---|
| 版本身份测试写死 57 | 改为校验正整数 / SemVer，并和实际编译 BuildConfig 比较；保留禁止 Git 派生版本与签名检查 | 全量 QA 通过 |
| 导入图标仍要求 Icons.Filled.Add | 当前批准实现为两条圆头线绘制加号；改为检查实际绘制、无障碍语义、按钮角色、禁用和点击链路 | 相关四类 32/32；全量 QA 通过 |
| Dock 导入调用契约漏了真实捕获来源 | 校验中央槽、禁用状态和 importBackdrop 参数；保留独立反馈、禁止导入槽拖动与无 FAB 等约束 | 同上 |
| 材质契约只计 9 个角色 | 实际已加入 DockLens；精确校验 10 个角色及其零模糊/零阴影等预期，继续遍历全部角色 | 同上 |
| Release host tests 没有任务 | AGP 9 默认只创建 testBuildType（QA）host tests；通过 HasUnitTestBuilder 显式启用 Release | 已实际生成并进入 compileReleaseUnitTestKotlin；后续暴露下述源集/要求冲突 |
| 删除事务类无法运行 | runBlocking 推导出最后一次 File.delete() 的 Boolean，JUnit 要求 void；显式声明 Unit，断言全部保留 | 修复前 initializationError；修复后同一核心组 24/24 |
| QA 专用夹具测试放在公共源集 | BackgroundPositionSliderTest 与 ReaderReturnHostTest 依赖 QA UI / Activity，移到 testQa；没有删除或跳过测试 | 两类 6 项全部执行；和 Reading 回归合计 56/56 |
| 验证入口强制依赖外层 helper | 直接调用仓库 gradlew.bat；Full 包含 QA / Release 单测与 lint，默认不构建 APK；确需设备测试才加 PrepareDeviceTests | PowerShell 解析通过，独立入口 Reading 56/56；Full 按实际 Release 编译失败返回非零 |
| 历史结果被当作本轮发布勾选 | 旧 51 版 / Room 15 / 1,730 项清单归档，门禁改为逐候选执行；README 校正权限、PDFBox、导入位置与版本入口 | 文档与 Wiki 静态校验 |

AGP 默认变化见 [Android 官方发布说明](https://developer.android.com/build/releases/agp-9-0-0-release-notes)。没有新增依赖、没有拆分 PDF 大类，也没有通过禁用检查制造通过。

## 当前未通过项

### Dock 左端帽

本轮重新安装隔离 QA 并执行完整 29 项，稳定复现：

- lightPickupShowsCapturedRailOutlineInsideLens
- lightLeftCapHasClearSpaceInsideHeldLens

两项均为浅色左端帽局部亮边：**16/42 行**达到原 .08 局部峰值要求，未满足原大于 60% 的比例。截图中的轮廓偏弱，不能当作已解决或仅称误报。具体采样、抗锯齿与折射链路的根因仍需隔离；不凭局部截图调阈值、不补画假内轮廓、不把色散加宽来凑对比。PDF 控件共用材质与用户要求的真实来源/等比投影继续保持。

日志 dock-native.log，实际像素 native-pixels/，诊断 crop 为 left-cap-before.png；cap-contrast-diagnostic.json 仅是从截图估算的诊断，不代替原生断言。原参考对照仍见工作区 output/dock-subtle-dispersion-20261013/reference-vs-71.png。

### 正式版工作台与测试归属

[design.md](../design.md) 的“液态玻璃工作台裁决”和“阅读器退出/工作台”要求正式设置提供独立工作台；当前代码却把工作台放在 app/src/qa，并通过 QA_GLASS_VARIANTS_ENABLED 隐藏正式入口。公共 GlassLabParametersTest / GlassLabScreenTest 因此无法编译到 Release，ReleaseHygieneHostTest 与 GlassLabReleaseFlagTest 也仍要求正式包包含工作台。

这是一处**产品要求、生产实现与测试之间的冲突**，不能仅把正式版测试改成“应该没有工作台”来制造通过。已经向用户提出两个明确目标：按设计恢复正式版独立工作台，或明确只留 QA 并同步设计及测试归属；该选择尚待回答。其他 QA 夹具测试已经独立归位和验证。

首次失败日志为仓库 output/verification/full-20261013-081050.log；归位后剩余编译检查见工作区 release-unit-remaining.log。没有把“任务已创建”写成“Release 测试已通过”。

## 运行证据

所有本轮工作区证据在 output/stabilization-20261013/；可移植脚本的日志在仓库 output/verification/。

| 检查 | 结果 | 证据 |
|---|---|---|
| 初始全量 QA | 1,787 项、4 失败、2 跳过 | full-qa-lint.log、baseline-jvm.json、baseline-test-results.zip；失败后 lint 未执行 |
| 四类契约修正 | 32/32 | contracts-lint-qa-build.log |
| 修正后全量 QA | 1,787 项、0 失败/错误、2 跳过 | full-20261013-081050.log、final-qa-jvm.json、full-qa-passing-results.zip |
| 源集归位后 Reading + 两类 QA 测试 | 56/56，含归位测试 6 项 | reading-20261013-082741.log、portable-reading-jvm.json |
| Release host tests | 编译失败，未执行 | full-20261013-081050.log、release-unit-remaining.log |
| Release lint | 0 errors / 198 warnings / 4 hints | qa-native-repair-lint.log、lint.json、app/build/reports/lint-results-release.xml |
| Dock 原生 | 27/29 | dock-native.log、native-apks.json、native-pixels/ |
| 核心原生初测 | 19 通过 + 1 类初始化错误 | core-native.log；不能写成该类已执行 |
| 核心原生修复后 | 24/24，161.476 秒 | core-native-repaired.log、core-repair-test-apk.json |
| Wiki | 20 篇、3,146 个相对链接、3,054 个源码链接、113 个关键 API 通过 | docs/code-wiki/reference/validation.json |
| 新生产 Release | 未构建 / 未发布 | 已有 71 版摘要未变 |

两个环境跳过分别为 LaoziAnchorFixtureTest 的外部夹具未挂载，以及 MarkdownAssetResolverSecurityTest 的环境不支持符号链接；本轮没有新增 @Ignore / assume 或放宽断言。

机器约 14 GiB RAM，开始剩余不足 1 GiB、D 盘约 3 GiB 空闲。使用 --max-workers=1 --no-daemon -Pkotlin.compiler.execution.strategy=in-process；命令行 Gradle heap 为 1,536 MiB，测试 JVM 512 MiB、每 40 个测试类 fork、有效处理器数 2。未改仓库默认 heap。命令在原始日志和会话中保留，未记录签名秘密。

## 核心功能验收矩阵

implemented 表示源码链路存在；自动测试与模拟器场景只覆盖表内说明，不能替代物理手机、厂商长截图面板或动态视觉验收。

| 能力 | 实现 | 本轮自动验证 | API 35 模拟器 | 物理手机 |
|---|---|---|---|---|
| 导入/内容身份、改名/导出/路径/封面 | implemented | QA JVM 通过 | 完整用户流程 pending | pending |
| 共享存储扫描/默认打开引导 | implemented | QA JVM 通过 | 设置授权与提供者流程 pending | pending |
| Markdown 阅读、选字、批注、进度/顶栏 | implemented | QA JVM 通过 | 逐字/代码/表格选区、圆环及 shader 通过；完整批注 UI pending | pending |
| PDF 连续/单页、反色、高清图块 | implemented | QA JVM 通过 | 系统连续视口捕获通过；两模式完整阅读/反色 pending | pending |
| PDF 选字、高亮/笔记、有限编辑与返回 | implemented | QA JVM 通过 | 用户流程 pending | pending |
| PDF 创建/加密/合并/转 Markdown | implemented | QA JVM 通过 | 用户流程 pending | pending |
| 书签、位置/缩放/偏移、搜索跳转 | implemented | QA JVM 通过 | 全格式用户流程 pending | pending |
| 有限 EPUB 模式/目录/字号/私有字体 | implemented | QA JVM 通过 | 私有字体绘制/重排/重建/移除、原书导航和根定位通过；连续/卷页完整验收 pending | pending |
| 系统滚动截图 | implemented | 原生协议通过 | Markdown / PDF 连续视口三屏及位置保护通过；厂商截图面板 pending | pending |
| Room 历史迁移、删除与位置事务 | implemented | QA JVM 通过 | Room 15/16→17、删除回滚/源文件保留、PDF 快照原子性通过 | pending |
| Dock 真实来源、端帽与日夜光学 | implemented | QA JVM 通过，原生 27/29 | 浅色左端帽两项失败 | pending |
| 正式版玻璃工作台 | 要求冲突 | Release 编译失败 | QA 工作台测试通过，不代替正式入口 | pending |
| Markdown 编辑/草稿入口 | deferred | 当前无发布入口 | — | — |

## 下一步顺序

1. 明确工作台目标，按相应产品要求修正源集/入口与测试，不静默改变已批准范围；再次执行 Full，形成真实 Release 基线。
2. 隔离 Dock 浅色左端帽的渲染根因，只修改对应采样/折射链路，保留原端帽、色散上下限、图文及输入断言；复验全部 29 项并对照参考媒体。
3. 自动门禁通过后，若生产代码改变则递增 versionCode，构建正式签名 Release，核对 build / Outbox / Download 摘要并保留数据覆盖安装。
4. 连接物理手机完成阅读、迁移、系统面板、动态视觉与手感验收；再考虑按职责拆分 PDF 大类。

旧发布清单、全清单阶段状态与 71 版旧当前状态分别保存在 docs/history/ 对应 before_stabilization 快照中。授权范围和 Markdown 编辑等待 M1racle Note 的决定继续有效。
