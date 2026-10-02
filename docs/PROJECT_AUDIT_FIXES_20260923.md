# 2026-09-23 项目审查修复

用户授权：全面审查后明确要求“修复”。保留会话开始时已有的 EPUB 未提交变更；不调整冻结的 PDF 光学配方。

## 范围与实现

1. EPUB 覆盖翻页：方向使用 currentPage − settledPage + currentPageOffsetFraction，半页切换 currentPage 不再反转方向。前进/后退跨半页测试。
2. Markdown/EPUB 进度：Mutex 串行落库、成功版本标记、生命周期补写合并；失败保持 dirty，旧补写不能覆盖新位置。
3. 标注：复用既有 Room 事务执行器，泛化为 RepositoryTransactionRunner；高亮/笔记与派生搜索索引一同提交或回滚。生产必须注入真实 Room runner，测试显式注入 fake，无无事务生产 fallback。
4. EPUB 目录：NCX 保留所有嵌套层级；nav/NCX 链接相对各自文件解析，再以 canonical 路径匹配 spine。
5. EPUB 沙箱：目录与图片复用包内路径检查；nav/NCX 拒绝越界，XML 文件采用有界读取，parser 入口检查字节上限。
6. 删除：删除 documents 后清理 packages/{id} 与 epubs/{id}；源文件和其他书籍不受影响。
7. 图片：宽高同时缩放，保持比例并居中；同一 fit 规则用于分页与绘制。
8. 构建门禁：AGP 9.1.1、Gradle 9.5.0、Kotlin/Compose compiler 2.3.21、KSP 2.3.6、compileSdk 37。保持 targetSdk 35/minSdk 31；Room 2.6.1 的处理器在 KSP2 对 Unit 签名报 unexpected jvm signature V（栈指向 Room processing），配套升级到支持 KSP2 的 2.7.2，Compose BOM 对齐 2026.05.00（ui/foundation 1.11.1），其余产品库版本保持原值。新 AGP 区分 compile/runtime classpath，旧 BOM 的 compile ui 1.7.6 缺少实际源码已使用的 dropShadow；显式对齐避免依赖 Backdrop 的 runtime 传递版本；删除 AAR 全局禁用、Kotlin metadata/pre-release skip、Compose lint 禁用清单与 stdlib 排除。采用 AGP 内置 Kotlin。

工具链依据：Backdrop 2.0.0 AAR 实际 minCompileSdk=37；[AGP 9.1.1 官方兼容表](https://developer.android.com/build/releases/agp-9-1-0-release-notes)要求 Gradle ≥9.3.1、JDK 17，支持 API 37；本机现有 Gradle 9.5.0 满足要求。SDK 包名称为 platforms;android-37.0。

## 检查点

- implemented：上述修复已落地，等待完整构建验证。
- automated_verified：直接编译实际生产源码运行 importer/TOC/saver/cleanup 的 JUnit 31 项、分页/图片 13 项全部通过。非 Gradle/Android 全量验收。
- 回归反证：工作树不变，隔离编译 HEAD 旧 ReadingPositionSaver，新增测试 3 项失败（重复 final flush、旧位置晚提交、异常后不重试）；修复版通过。隔离恢复旧 NCX 递归，三级目录测试失败；恢复旧翻页方向与图片宽度，分页/图片测试 3 项失败。
- device_verified：NOT_RUN；本轮一次 adb devices -l 为空。未安装、未清数据。
- Release 源码编译已通过；完整 lint 正在执行。Kotlin 2.3 对测试 fixture 混合类型 arrayOf 的类型推断报错，已显式声明 Array<Any>，无断言改动。
- pending：最终 UI 复验、正式签名 Release 与交付校验；真机场景仍待人工验收。
- 日志：.external_outbox/audit-fixes/；pure-tests.log、regression-old/result.log、compile.log。

### 复验检查点

- QA 测试 Kotlin 源目录改用 AGP 内置 Kotlin 的 `kotlin.srcDirs`；仪器测试源码编译通过。
- Gradle 全量 1,565 项中 1,563 项通过，新增真实 Room 事务测试 2 项被 Robolectric ARM Linux 原生库支持限制阻塞；未删除/跳过测试。正在用已有 qemu-x86_64 与隔离 x86 Java 运行时执行同一测试字节码。
- 完整 lint 已恢复运行（不再发生旧 ASM/UAST 崩溃），报告 23 errors / 158 warnings / 4 hints。修正 22 处 Compose 资源读取，改用 LocalResources 订阅配置变化；明确 SAF 返回的读写权限常量。复验日志 lint2.log。

### 主机验证结果

- 全量 QA 单测（ARM）：1,565 tests / 2 environment failures / 0 errors / 0 skipped；全部其他 1,563 项通过。原始 XML 保留于 `full-test-results/`，失败只涉及 Robolectric SQLite native 对 Linux aarch64 的支持限制。
- 同一事务测试字节码在 qemu-x86_64 + Temurin 17 JRE 运行：**2 / 0 / 0 / 0**。真实 in-memory Room、真实 SQLite 与生产 RoomRepositoryTransactionRunner，无替换 DAO 存储或事务 fake；采用 Robolectric SQLite LEGACY 模式。证据 `transaction-x86.log`。不能将这个分环境结果描述成 ARM 全量 Gradle 零失败。
- 完整 `lintRelease`：**PASS，0 errors / 158 warnings / 4 hints**；报告 `lint-final.xml`、日志 `lint2.log`。非零 warnings 主要是未使用资源、KTX/Modifier/图标与依赖版本建议，未作为本任务扩展清理范围。无新增 lint suppression/baseline。
- Room schema v14 导出与已提交 schema 完全一致，没有数据迁移或 schema 变化。

- 最终受影响 UI 主机复验：**424 tests / 0 failures / 0 errors / 0 skipped**；`compileQaAndroidTestKotlin` PASS。证据 `ui-final.log`、`ui-final-results/`。正式签名 Release 构建进行中。

### 交付检查发现的构建链路缺陷

- 第一次 Release Gradle 成功但 APK 缺少 AndroidManifest.xml/resources.arsc；签名检查拒绝，不能视为可安装交付。保留 `rejected-release.log` 与 `rejected-delivery.json`。
- 根因：tools/aapt2 遍历 SDK 时选到 34.0.0；AGP 9 传 `--resource-path-shortening-map=...`，34 不支持此参数（退出 1、无输出），优化任务未检查退出码，仍报告成功。直接执行相同参数：34 失败且无产物；36 成功。
- 修复：包装脚本从 app/build.gradle.kts 读取唯一的 buildToolsVersion，使用匹配 SDK 工具；指定版本缺失直接失败。导出 APK 前实际检查 AndroidManifest.xml/resources.arsc/classes.dex 存在且非空，残缺包不能覆盖 Outbox。
- 回归：模拟同时安装 34/36，确认选 36；移除 36 后确认报错而非退回 34。直接执行生产 assembleRelease 的导出 action，对第一次残缺 APK 拒绝成功。证据 `aapt2-wrapper-test.log`、`export-gate.log`。修复后重建中。

## 最终交付（2026-09-24）

- implemented：八项审查修复及构建链路补充修复落地；保留开始时的未提交 EPUB 工作。
- automated_verified：完整 lintRelease、QA 仪器测试源码编译、424 项最终 UI 测试、44 项逻辑测试、1,563 项 ARM 全量用例及 2 项 x86 Room 事务测试分别通过。完整 ARM Gradle 测试仍有上述 2 项环境失败，未伪称全量单进程通过。
- 正式 `assembleRelease` PASS（24s）；APK **6,950,677 bytes / 6.63 MiB**，1.0.0/23；SHA-256 `0f60f3db5d9f9b6fbd9082455cc7c5b10107af8c5141ccd83000cad4e79fa886`。
- APK 完整性、production signer `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`、无 INTERNET 校验 PASS。构建输出、Outbox、Download 三份摘要一致，已自动覆盖 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`。残缺的首轮时间戳包已移除。
- device_verified：NOT_RUN；pending：真实 EPUB 手势、图片/目录观感、进度恢复及 Markdown/PDF 打开/标注场景。无设备，不声称安装或实机通过。
- 证据：`release.log`、`delivery.json`、`signature.log`、`apk-badging.log`、`source-hashes.json`；未提交代码，未发布 tag/Draft。
