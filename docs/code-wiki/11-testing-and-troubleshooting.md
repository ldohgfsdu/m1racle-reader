# 11 测试与故障排查

[返回目录](README.md)

## 测试层次

| 层次 | 源码/任务 | 能证明什么 |
|---|---|---|
| 纯 JVM | `app/src/test`，QA / Release host tests | 模型、解析、几何、算法、取消/并发及源代码契约 |
| Robolectric/Compose UI JVM | `app/src/testQa`，包含 Android resources 与 QA 测试宿主 | 页面状态与语义交互；公共源集仍保留非 UI 的 Robolectric 检查 |
| 主机 SQLite | testImplementation sqlite-jdbc | 精确迁移 SQL、索引/约束、事务数据语义 |
| QA 专属 JVM | `app/src/testQa`，`testQaUnitTest` | 依赖 QA Activity / Compose 测试宿主与材质对比 UI 的测试；完整保留 QA 覆盖，不编入 Release host tests |
| Release 专属 JVM | `app/src/testRelease`，`testReleaseUnitTest` | 公共逻辑、Release QA 开关、取消工作台后的编译产物 / 路由 / 资源契约；不打 Release APK |
| Android instrumentation | `app/src/androidTest`，`connectedQaAndroidTest` | 真实 Android framework、Room/原生渲染、设备手势与 UI |
| Macrobenchmark | `benchmark/src/main` | 隔离目标包启动耗时与帧时序 |

静态基准为 211 个 `test` Kotlin 文件、26 个 `testQa` 文件、1 个 `testRelease` 文件、21 个 `androidTest` 文件，以及 1 个 benchmark 文件。文件数量包含公共测试辅助类。`@Test` 文本数量也不等于实际运行 case 数；参数化测试、动态过滤、环境失败等会改变执行数。完整表见 [测试索引](reference/test-index.md)。

## 按变更定位测试

| 变更范围 | 首要回归入口 |
|---|---|
| 内容身份/重复导入 | ContentIdentityResolver、ContentIdentityImportWiring、R57AtomicIdentityAuthorityContract、LegacyContentHashMigrationV1 |
| 外部打开 | ExternalOpenAtomicSession、ExternalOpenPendingNavigation、ExternalOpenSessionRules、ImportAcceptanceParityGolden |
| ZIP/资源路径 | ZipPackageImporter、MarkdownAssetResolverSecurity、ImportSizeLimitBoundary |
| 数据修改/迁移 | MigrationV*、DocumentMigrationSql、ReadingRepositoryTransaction、DeleteByIdTransaction |
| Markdown 搜索 | DocumentSearchUnicodeOffset、DocumentSearchIsolation、WholeBlockHighlightRegression |
| PDF 渲染/缓存 | PdfResourceCloseOrder、PdfPageBitmapCache、PdfTileLruEviction、TileWorkQueue |
| PDF 几何/手势/恢复 | Continuous*、R7*、PdfViewportSessionState、FinalPdfProgressFlusher |
| PDF 编辑/导出 | PdfBodyEditor、PdfSourceWriteStore、ExportCancellation*、ChapterPackage* |
| EPUB | EpubPackageImporter、EpubReadingAnchors、EpubPagination、EpubAnchorReflowHost、EpubReleaseHandoffHost |
| 玻璃/Dock | GlassAuthorityContract、GlassBackdropTopologyContract、GlassOpticalPolicy、DockMotion、GlassDockPixelsInstrumented |
| 书签/高亮/笔记 | BookmarkTarget、BookmarkRepository、AnnotationAnchorRepository、AnnotationCreationFlow |

上表使用测试名主题前缀；精确类名和路径以索引为准。源码契约测试通常扫描生产代码文本，能锁定架构约束，但不能替代真实运行路径与像素/性能验证。

## 主机验证命令

配置本机工具链后，在 `reader/` 执行：

```powershell
# 全部 QA JVM 测试与 Release 静态检查
.\gradlew.bat :app:testQaUnitTest :app:lintRelease

# 示例：按类精确筛选，使用确实存在的类
.\gradlew.bat :app:testQaUnitTest --tests 'com.m1racle.reader.identity.ContentIdentityResolverTest'
.\gradlew.bat :app:testQaUnitTest --tests 'com.m1racle.reader.pdf.FinalPdfProgressFlusherTest'

# Release 特有契约测试
.\gradlew.bat :app:testReleaseUnitTest
```

JVM 资源支持已启用。Robolectric 的运行时依赖独立通过 Maven Central 获取，不能仅根据 Gradle 普通依赖已缓存就断言首次 JVM 测试可离线运行。

AGP 9 默认只创建 testBuildType（本仓为 QA）的单测组件；本轮已在 androidComponents.beforeVariants 中通过 HasUnitTestBuilder 显式启用 Release 单测，才能执行 src/testRelease。该默认行为见 [Android 官方发布说明](https://developer.android.com/build/releases/agp-9-0-0-release-notes)。Release JVM 使用生产 BuildConfig 与 production 源集，不能用 QA 通过替代。

常见报告位置：`app/build/reports/tests/testQaUnitTest/index.html`、`app/build/test-results/testQaUnitTest/`、`app/build/reports/lint-results-release.html`；设备报告在 `app/build/reports/androidTests/connected/` 下，具体变体层级由 AGP 输出决定。

## 设备安全和性能

QA 的 applicationId 为 `com.m1racle.reader.qa`，与用户主包隔离。工程拒绝对主包运行 connected instrumentation；Windows 入口 `scripts/run-qa-connected-tests.ps1` 还读取产物 metadata 复核包名。不要通过修改门禁、卸载主包或清数据来让测试通过。

[ReaderScrollBenchmark](../../benchmark/src/main/java/com/m1racle/reader/benchmark/ReaderScrollBenchmark.kt) 固定测量 `com.m1racle.reader.benchmark`：冷启动 5 次，滚动路径 5 次，使用 StartupTimingMetric/FrameTimingMetric。需要先在 benchmark 包中导入压力文档并让其出现在最近列表；没有样例时仍可能只采到首页帧，不能声称测试了长文滚动。

```powershell
# 仅在确需性能测量时使用内部隔离变体
.\gradlew.bat :app:assembleBenchmark
.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest
```

Macrobenchmark 模块不是 app 模块的 connected 主包测试。运行前仍需明确目标设备、准备目标包与数据。模拟器结果不能自动等价为物理手机性能。

## 常见问题

| 现象 | 优先检查 | 下一步 |
|---|---|---|
| Java/Gradle/AGP 启动失败 | JAVA_HOME、`gradlew --version`、本机 helper 路径 | 使用匹配 JDK 17 和 Wrapper，不先升级全仓依赖 |
| SDK 或 aapt2 缺失 | local.properties、Platform 37、Build Tools 36、主机架构 | 修复本机配置；ARM override 只留本机 |
| Release 拒绝打包 | 四个签名变量是否完整、store 是否存在、是否正式 alias | 使用正确 signer；不退回 Debug/临时签名 |
| 构建成功但手机未出现 APK | Outbox 与 build hash、唯一在线设备、helper 参数 | 区分自动导出与设备 push；记录待交付 |
| APK 无法覆盖安装 | 包名、versionCode、正式证书连续性 | 不卸载用户包；先查 signer/版本 |
| 安装 native libraries 失败 | APK 对齐、是否被重新签名 | 使用 AGP 原产物，核对 16 KiB alignment |
| 文件打不开 | documentId、当前 URI、provider 授权/可读性、内容哈希 | 区分失效权限、文件丢失、外部内容变化和格式拒绝 |
| 图片不可见 | 图片来源、基目录能力、规范化路径、网络 URI | 检查 AssetResult；单文件授权不等于目录授权 |
| EPUB 回到错误位置 | readingAnchor、章节 href、parserVersion、恢复 tier | 核对是否走降级与保存保护，不直接覆盖旧锚 |
| PDF 黑页/闪页 | renderer 关闭时序、generation、底图保留、缓存预算 | 复现模式/缩放/切换场景，检查实际截图与原生日志 |
| PDF 编辑被拒绝 | 签名/加密、字体编码、原行宽、内容结构 | 属于有限编辑能力边界时保留原文件并显示原因 |
| PDF 源写回冲突 | sourceSha256、当前源哈希、write ticket/backup | 拒绝覆盖外部改动；检查恢复结果与可导出备份 |
| 搜索少于预期 | 文内/跨文档范围、结果上限、query 空格、索引来源 | 不把标题/摘录/笔记搜索当作正文全文检索 |
| 玻璃崩溃/递归采样 | 捕获节点与 overlay 的父子/兄弟拓扑 | 核对 LocalGlassBackdropHost 边界与能力路径 |

## 本轮验证边界

Wiki 初次生成仅做静态校验；后续稳定性核查的当前全量 JVM、lint 与原生复验详见 [逐候选验证报告](../STABILIZATION_20261013.md)。文档引用校验与应用测试的统计口径不同；有未通过项或物理手机待验时不能宣布验收完成。
