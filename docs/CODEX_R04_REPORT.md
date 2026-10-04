# EPUB R-04 锚点恢复加固交付报告

日期：2026-10-04（Asia/Shanghai）。状态：**implemented；automated verification pending；未做设备验证**。

> 后续状态已更新：R04 已合入、修复测试编译问题并完成自动化及正式包模拟器验收，见 [2026-10-05 合入报告](R04_INTEGRATION_20261005.md)。本文保留最初交付时的历史记录；下文“待许可 / 待验证”不代表当前阻塞。

## 隔离与执行边界

- 原仓库：`D:\03_M1racle Reader\reader`，`feature/epub-anchor`，起点 `03ebf1516c56349c8a553b8289585fd929fabbcf`。
- 按指定命令创建 worktree：`D:\03_M1racle Reader\reader-r04`，分支 `feature/epub-anchor-r04`，所有修改和提交仅发生在这里。
- 从原工作区复制 `local.properties`；`git check-ignore local.properties` 确认忽略，未提交。
- 交付检查时原工作区仍在原分支、原 HEAD，`git status --short` 为空。未切换原分支，未 merge / rebase / push。
- 未使用模拟器、adb，未执行 assemble / connectedAndroidTest，未生成 APK，未访问或改动 `.external_outbox`。
- 两次 Gradle 命令启动前均检查 Java / Gradle / qemu / emulator 进程，结果为空；无需等待，也未杀进程。若后续验证出现这些进程，按任务约束最多等 30 分钟，超时停止并记录。
- Git 未配置作者，提交使用命令级 `-c user.name=Codex -c user.email=codex@local`；未改全局或仓库 Git 配置。
- 新 worktree 中 `.memory/active_object.yaml` 不存在；已阅读 AGENTS.md、`.memory/CURRENT_STATE.md`、PROJECT_INDEX.md 和 EPUB 第 1 步报告，按本次明确任务范围实施。

## A：第二轮文本匹配

文件：

- `app/src/main/java/com/m1racle/reader/epub/EpubReadingAnchors.kt`
- `app/src/test/java/com/m1racle/reader/epub/EpubReadingAnchorsTest.kt`

恢复顺序为：原位置完整 quote 校验 → 同章完整 quote 搜索 → 同章前 12 字搜索（`QuotePrefix`）→ 现有章首 / 百分比兜底。每轮均复用规范化文本和原始偏移映射，多次命中按原有距离规则选择：先最接近保存 blockIndex，再最接近 charStart；相同距离保留扫描中较早的一处。

原实现的 Exact 仅验证前 12 字，会将后半段变化当成精确恢复。本轮改为完整 quote 相等，避免绕过第二轮和锚点保护。图片或空文本位置的空 quote 原位恢复仍保留。

新增用例：原位置后半句变化走第二轮、跨块和同块重复前缀取最近偏移、完整 quote 优先于更近的前缀。原有两轮失败 / 章节缺失测试保留。

提交：`09daa54`；状态语义补充：`ecaa0bc`。

## B：非精确恢复不覆盖原锚点

文件：

- `app/src/main/java/com/m1racle/reader/epub/EpubRestoreSession.kt`（新增）
- `app/src/main/java/com/m1racle/reader/epub/EpubReadingAnchors.kt`
- `app/src/main/java/com/m1racle/reader/ui/epub/EpubAnchors.kt`
- `app/src/main/java/com/m1racle/reader/ui/epub/EpubReaderViewModel.kt`
- `app/src/main/java/com/m1racle/reader/ui/epub/EpubReaderScreen.kt`
- `app/src/main/java/com/m1racle/reader/ui/epub/EpubReaderContent.kt`
- `app/src/main/java/com/m1racle/reader/data/repository/DocumentRepository.kt`
- `app/src/test/java/com/m1racle/reader/epub/EpubRestoreSessionTest.kt`（新增）
- `app/src/test/java/com/m1racle/reader/ui/epub/EpubAnchorReflowHostTest.kt`

ViewModel 持有每次打开书的恢复会话。Quote、QuotePrefix、ChapterStart、Progress 均标记为降级保护；Exact 正常保存，无原锚点的 BookStart / LegacyPosition 仍允许创建或迁移锚点。恢复结果统一提供保护语义，UI 不另行推导。

自动保存把当时的锚点写入许可随 EpubPosition 一起交给串行 saver。保护期间仓库执行既有的仅更新位置 / 百分比 SQL，不在 UPDATE 中写 readingAnchor，也不写 null，不修改书签表。生命周期 flush 使用同一许可快照。

有效点击翻页和目录跳转显式清除保护；滑动翻页要求观察到真实 DragInteraction 且最终页发生变化。每次布局恢复清理拖动标记，字号、旋转和重新分页的程序性 scrollToPage 不清除保护。目录选择当前页也作为主动导航，立即保存该页位置。越界翻页点击不清除保护。

`anchorForSave` 是阅读 / 书签锚点共用的保护入口，保护期间返回 null，语义为省略锚点写入。当前 EPUB 没有书签创建或自动更新调用链，也没有进度条 UI，本轮没有新增这些功能；已有书签 anchor 没有任何写入路径因本轮自动保存而改变。后续新增 EPUB 书签 / 进度条入口必须接入此保护入口 / `onUserNavigation()`。

新增纯逻辑用例覆盖各恢复层级、连续自动保存保持阅读和书签原锚点、首次用户导航解锁、旧位置迁移正常保存。新增 Robolectric JVM 阅读器用例覆盖真实字号变化后仍保护、点击翻页解锁、前缀恢复后滑动翻页解锁。

提交：`2c0889a`；状态语义补充：`ecaa0bc`；调用链回归测试：`66313b2`。

## C：降级 Snackbar

文件：`EpubRestoreSession.kt`、`EpubReaderContent.kt`、`EpubRestoreSessionTest.kt`、`EpubAnchorReflowHostTest.kt`（路径同上）。

只有 ChapterStart / Progress 消费一次通知资格，分页就绪后显示：**没能精确回到上次的位置，已跳到附近**。QuotePrefix 不弹；完整 Quote 重定位也不弹。通知消费记录随 ViewModel 会话保留，旋转和字号变化不会重复提示。Snackbar 的协程不以 layout 为重启 key，避免重新分页取消正在显示的提示。

逻辑测试覆盖所有 tier、每次打开最多一次、新会话可再次提示；JVM 阅读器测试断言兜底提示可见、前缀恢复无提示。

提交：`a835579`；保持提示跨重新分页：`75fe8e8`；调用链测试：`66313b2`。

## 文档修正

`docs/CURRENT_STATUS.md`：仅将「Room v14 不变」改为「Room v15 不变」。提交：`095d2ad`。

未修改 ReaderChromeTokensContractTest、玻璃材质实现、Gradle 配置或数据库 schema。

## 验证结果

| 验证 | 结果 |
| --- | --- |
| `gradlew testDebugUnitTest --no-daemon -Dorg.gradle.jvmargs=-Xmx1536m` | 执行失败：任务不存在，21 秒；没有执行测试 |
| `gradlew compileDebugAndroidTestKotlin --no-daemon -Dorg.gradle.jvmargs=-Xmx1536m` | 执行失败：任务不存在，18 秒；没有编译仪器测试 |
| 实际单测统计 | **通过 0、失败 0、跳过 0、执行 0**；不能解读为测试通过 |
| Gradle 命令统计 | 成功 0、失败 2（均为任务解析失败） |
| `git diff --check` | 通过 |
| 原工作区分支 / HEAD / 状态 | 与起点一致，干净 |

仓库 `app/build.gradle.kts` 的 `testBuildType = "qa"`，此前锚点报告也使用 QA 单测。已向用户请求允许改跑现有 `testQaUnitTest` / `compileQaAndroidTestKotlin`；在得到许可之前未替换命令，也未为迎合命令修改构建配置。获准后继续按无其他 Java/Gradle/模拟器进程、1536 MB、no-daemon 的约束验证。

`DeleteByIdTransactionTest` 位于 androidTest，JVM 单测不会执行。当前其编译和运行均未验证。既有锚点测试本轮也尚未执行。已知 ReaderChromeTokensContractTest 基线失败（期望 0.6）未改动；本轮没有产生可用于确认该基线的测试结果。

## 没把握 / 待验证

- 所有新增生产代码和测试尚无编译成功证据；必须在获准的有效任务上完成验证后才能写 automated_verified。
- 点击、滑动、字体变化及 Snackbar 的 Robolectric 调用链测试已写入但未执行；旋转、目录同页跳转及边界无效翻页尚无本轮动态验证证据。
- 无设备验证，不能声称真机手势、旋转或 Snackbar 视觉验收通过。
- 12 字按 Kotlin String 的 UTF-16 单元截取，与既有 quote 处理一致；包含补充平面字符的“12 个字符”计数不是 12 个 Unicode code point。未扩展这一语义。
- “最近”保持现有 block 优先、块内 offset 次优先规则，没有改为全章累计字符距离。
- 完整 Quote 重定位也按非 Exact 保护，属于保守处理；用户主动导航后才重新写锚点。
- 当前没有 EPUB 书签 / 进度条调用方，后续新增入口需要消费这里的会话保护，不能声称已验证不存在的 UI。
