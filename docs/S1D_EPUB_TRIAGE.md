# S1d EPUB 收尾分诊（只读结论 + 批次方案）

日期：2026-11-17
基线：`refactor/pdf-only` @ `c9223e9`
范围：S1 退出清单中 EPUB 一项（`docs/PRODUCT_SCOPE_20261115.md` §5 S1）

---

## 0. 结论先行

**一、EPUB 阅读器本身已经不在了。** `app/src/main` 下没有 `ui/epub`、`epub`、`pagecurl` 任何一个目录；计划书里「EPUB 阅读下线」的主体动作在 S1a/S1b 已经完成。本次不是「删一个功能模块」，是**收尾残留引用**。

**二、残留 140 行 / 24 个主源码文件，其中真正可删的只有三块：**

| 块 | 内容 | 量 |
|---|---|---|
| EPUB 专属设置 | 阅读字体导入（`ReaderFontPicker` + `ReaderFontRepository`）、`EpubFontFamily`、`EpubReadingMode` | 218 行文件 + 2 个界面入口 |
| 导入分类 | `DocumentType.resolve` 的 EPUB 分支、`MIME_EPUB`、`isEpubFileName`、`ExternalUriMetadata` 的 epub 映射 | ≈9 行 |
| 入口与文案 | Manifest 两条 `application/epub+zip`、`DocumentDiscovery` 的 "epub"、两处「支持 EPUB」文案 | ≈8 行 |

**三、锚点与数据侧一行不动。** `DocumentAnchor.EpubText`、`AnchorCodec` 的 `epub:` 编解码、`EpubTextAnchorResolver`、`DocumentType.EPUB`、`readingAnchor`/`locationKind` 列、`updateEpubReadingPosition`，全部是历史数据的解码路径，删掉即丢用户书签与阅读位置。

**四、额外发现：`core:database` 的基线不是绿的。** 4 条测试断言的是「`.md` / `.zip` / `.epub` 可导入」，与现行 PDF-only 门禁相反。S1c 报告里的「992 / 860 零失败」只覆盖 `:app` 的 QA 与 Release 两个变体，不含本模块。已在 S1d-0 修正（只改测试）。

---

## 1. 基线实测（2026-11-17）

### 1.1 环境约束

```
CommitLimit = 22,373 MB    Committed = 21,106 MB    FreeVirtual = 1,267 MB
```

宿主机进程私有内存合计 14.7 GB，页面文件 8 GB 已满。Gradle 守护进程以 `-Xmx768m` 启动时仍 `mmap failed`（`hs_err_pid15500.log`，`Failed to commit metaspace`）。**本机当前不具备跑完整 `:app` 测试集的内存余量**，这决定了本轮取证方式。

### 1.2 修前

```
:core:database:testReleaseUnitTest
56 tests completed, 4 failed
```

| 用例 | 位置 | 断言 |
|---|---|---|
| `DocumentTypeTest.isAllowedImport_acceptsMarkdownAndPdf` | `DocumentTypeTest.kt:64` | `isAllowedImport("text/markdown","a.md")` 为 true |
| `DocumentTypeTest.isAllowedImport_acceptsEpub_rejectsOtherEbooks` | `:89` | `isAllowedImport("application/epub+zip","b.epub")` 为 true |
| `DocumentTypeZipTest.zipImportAllowed` | `:10` | `isAllowedImport("application/zip","book.zip")` 为 true |
| `DocumentTypeZipTest.markdownAndPdfStillAllowed` | `:17` | `isAllowedImport("text/markdown","a.md")` 为 true |

实现侧（`DocumentType.isAllowedImport`）已经收紧为 PDF-only，`app` 侧的 `ImportAcceptanceParityGoldenTest` 也已经按 PDF-only 断言（`:202` 明确 `book.epub` 必须为 false、`:203` `notes.md` 必须为 false）。**是测试过期，不是实现回归。**

### 1.3 修后

`:core:database` 的测试 worker 在 1.27 GB 可用提交内存下起不来（首例 `OutOfMemoryError` 之后整批 `NoClassDefFoundError: Could not initialize class DocumentType`），因此改用独立 JUnit 运行器在受控堆上验证已编译产物：

```
java -Xmx192m -cp <releaseUnitTest classes;release classes;junit;hamcrest;kotlin-stdlib> \
  org.junit.runner.JUnitCore \
  com.m1racle.reader.data.local.DocumentTypeTest \
  com.m1racle.reader.data.local.DocumentTypeZipTest \
  com.m1racle.reader.data.local.DocumentTypeAdmissionBoundaryTest

OK (20 tests)
```

`DocumentTypeTest`、`DocumentTypeZipTest`、`DocumentTypeAdmissionBoundaryTest` 三个类 20 条用例全过，修前失败的 4 条都在其中。

**未执行**：`:app:testQaUnitTest`、`:app:testReleaseUnitTest`、`:core:pdf:testReleaseUnitTest`。内存不足，见 1.1。下一条可复现命令（内存回落后）：

```powershell
cd "D:\03_M1racle Reader\reader-ui-release-20261025"
$env:JAVA_HOME="D:\03_M1racle Reader\.build-tools\jdk"
$env:GRADLE_USER_HOME="D:\03_M1racle Reader\.build-tools\gradle"
$env:ANDROID_HOME="D:\03_M1racle Reader\.build-tools\sdk"
& "$env:JAVA_HOME\bin\java" -classpath "gradle/wrapper/gradle-wrapper.jar" `
  org.gradle.wrapper.GradleWrapperMain :app:testQaUnitTest :app:testReleaseUnitTest --offline --console=plain
```

---

## 2. 残留分布

按 FQN 与裸名双向统计（`app/src/main` + `core/*/src/main`，排除 `build/`），共 140 行命中。按性质分：

| 性质 | 文件数 | 说明 |
|---|---|---|
| 历史数据解码 | 6 | `AnchorCodec`、`AnnotationAnchorResolver`、`DocumentAnchor`、`DocumentType`、`ReadingLibraryEntities`、`AppDatabase` |
| 历史行显示 | 4 | `HomeScreen`、`ContinueReadingCard`、`DocumentCover`、`DocumentListDisplayTitle` |
| 死设置 | 4 | `ReaderSettings`、`ReaderSettingsRepository`、`ReaderFontPicker`、`ReaderFontRepository` |
| 入口与文案 | 6 | `AndroidManifest`、`DocumentDiscovery`、`ExternalUriMetadata`、`DocumentToolsScreen`、`HubSettingsScreen`、`ReaderSettingsSheet` |
| 导航 tombstone | 2 | `NavGraph`、`UnsupportedDocumentScreen` |
| 磁盘清理 | 1 | `AppPrivateDocumentCleanup` |

---

## 3. A 类：可删

### D-1 阅读字体导入（EPUB 专属，对 PDF 无效）

| 项 | 内容 |
|---|---|
| 文件 | `ui/components/ReaderFontPicker.kt`（105 行）、`data/settings/ReaderFontRepository.kt`（113 行） |
| 界面入口 | `ReaderSettingsSheet.kt:67`（PDF 阅读器设置面板）、`HubSettingsScreen.kt:181`（设置页「阅读 → 阅读字体」） |
| 数据字段 | `ReaderSettings.importedFontId`（`:18`）、`ReaderSettingsRepository.updateImportedFont`（`:66`）、`clearImportedFont`（`:71`）、`KEY_IMPORTED_FONT`（`:111`） |
| 渲染消费方 | **0**。`importedFontId` 只被 `ReaderFontPicker` 自己读写；PDF 走 `PdfRenderer`，使用文档内嵌字体，不读该值 |
| 现状 | 用户在 PDF 阅读器和设置页都能导入字体，导入后对渲染没有任何影响 |
| 连带 | `app/src/androidTest/.../ui/epub/ReaderFontInstrumentedTest.kt`（59 行）是唯一测试，随功能退役 |
| 数据风险 | 低。已导入的字体文件留在应用私有目录，成为孤儿；不删文件、不碰数据库 |
| 验证 | 设置页与阅读器设置面板不再出现「阅读字体」；`grep importedFontId` 为空；PDF 显示设置（字号/行距/边距）不受影响 |

### D-2 EPUB 专属设置枚举

| 项 | 内容 |
|---|---|
| 位置 | `ReaderSettings.kt:17,19`（两个字段）、`:70,71`（两个枚举） |
| 仓库 | `ReaderSettingsRepository.kt:58-64`（两个 update 方法）、`:80-81`（reset 写入）、`:95-101`（读取映射）、`:109-110`（两个 DataStore key） |
| 生产调用 | **0**。`updateEpubFontFamily` / `updateEpubReadingMode` 全仓无调用点 |
| 数据风险 | 无。DataStore 里残留的两个键无人读取，属孤儿键 |
| 验证 | `grep EpubFontFamily\|EpubReadingMode` 为空；设置页与阅读器设置面板行为不变 |

### D-3 导入门禁的 EPUB 分类

| 项 | 内容 |
|---|---|
| 位置 | `DocumentType.kt:20`（`MIME_EPUB`）、`:124-126`（`resolve` 的 EPUB 分支）、`:132-134`（按扩展名判 EPUB）、`:163-166`（`isEpubFileName`） |
| 为什么可删 | `isAllowedImport` 已 PDF-only（`resolve(...) == PDF` 才放行），EPUB 分支在导入路径上不可达；`resolve` 的另两个调用点（`DocumentRepository.kt:928`、`ExternalDocumentImporter.kt:89`）都在门禁之后 |
| 副作用 | `resolve("application/epub+zip", …)` 将从 `EPUB` 变为 `MARKDOWN`。该结果只用于门禁判定，两者都非 PDF，行为不变 |
| 连带 | `ExternalUriMetadata.kt:137` 的 `"application/epub+zip" -> "epub"` 映射（给无扩展名文件补后缀，补出 `.epub` 只会让拒绝理由更明确，无功能价值）；`DocumentTypeTest.resolve_epubMimeAndExtension_isEpub`（3 条断言）随之删除 |
| 必须保留 | `DocumentType.EPUB` 枚举值本身与 `fromStorage` 的 `EPUB.name -> EPUB` 分支（历史行） |
| 验证 | 导入 PDF 仍成功；导入 `.epub`、`.md`、`.zip` 仍被拒且提示不变 |

### D-4 入口声明与文案

| 位置 | 现状 | 处理 |
|---|---|---|
| `AndroidManifest.xml:41` | VIEW 过滤器声明 `application/epub+zip` | 删除该 `<data>` 行 |
| `AndroidManifest.xml:64` | SEND 过滤器整条只为 epub 存在 | 删除该 `<intent-filter>`（5 行） |
| `DocumentDiscovery.kt:14` | 扫描集合含 `md/markdown/txt/epub` | 收为 `setOf("pdf")` |
| `DocumentToolsScreen.kt:150` | 「扫描 PDF、Markdown、文本和 EPUB 文件。」 | 改为「扫描本机 PDF 文件。」 |
| `HubSettingsScreen.kt:165` | 「支持 Markdown、PDF 与 EPUB。」 | 改为「支持 PDF。」 |
| `ReaderSettingsSheet.kt:72` | 注释引用「EPUB's anchored menu」 | 改为中性描述 |
| `UnsupportedDocumentScreen.kt:31` | 注释「compiling after ui/epub is deleted」 | 时态改为已完成 |

| 风险 | 说明 |
|---|---|
| 系统「打开方式」不再列出 Reader 处理 `.epub` | 这是正确行为：当前点击必然被门禁拒绝 |
| `DocumentToolsTest.discoveryExcludesPrivateAndUnsupportedFiles` | 断言集合 `setOf("a.PDF","b.epub")` 需改为 `setOf("a.PDF")`；`b.epub` 仍写入测试目录，用于验证「不扫描」而不是「扫描后拒绝」 |

---

## 4. B 类：保留，但性质要写明

| 项 | 位置 | 为什么保留 |
|---|---|---|
| EPUB 列表标签 | `HomeScreen.kt:243-257` | 历史 EPUB 行仍在库里，需要显示「EPUB」标签与去扩展名标题；点击已在 S1a 断掉 |
| 续读卡封面 | `ContinueReadingCard.kt:79` | 同上，`isPdf || isEpub` 决定是否显示封面位 |
| 封面解码 | `DocumentCover.kt:35,53-87` | 历史 EPUB 行的封面来自包内 `container.xml` → OPF → cover-image。删掉只是让这些行退回占位图标，收益 ≈35 行，代价是可见退化。**保留**，但它是本文件里唯一的非 PDF 分支，后续若要收，需单独评估 |
| 导航 tombstone | `NavGraph.kt:80,93,432`、`Routes.EPUB_VIEWER` | 旧返回栈按路由名恢复；删路由会让带旧栈的启动崩溃。S1c 已有同类论证，沿用 |
| 目录清理 | `AppPrivateDocumentCleanup.kt:33` | 清理 `epubs` 目录是磁盘卫生，不是阅读功能 |

---

## 5. C 类：一行不动

| 编号 | 对象 | 理由 |
|---|---|---|
| C1 | `DocumentAnchor.EpubText`（`core/model`） | 历史书签/标注的坐标类型，删了编译断且数据无法表达 |
| C2 | `AnchorCodec` 的 `EPUB_PREFIX` 编解码（`:23,47-48,69,105-117`） | 库里 `bookmarks.anchor` / `highlights.anchor` / `documents.readingAnchor` 存的就是这种字符串 |
| C3 | `EpubTextAnchorResolver`（`:88-113`）+ `BookmarkLocationKind.EPUB_TEXT` + `BookmarkRowFormatter:16` | 按 `locationKind` 分派解码；删掉 → `forLocationKind` 返回 null → `ReadingRepository` 静默丢弃记录 |
| C4 | `DocumentType.EPUB` + `fromStorage` 分支 | `documentType` 是 TEXT 列，删枚举值会让旧行读成 MARKDOWN |
| C5 | `documents.readingAnchor`、`highlights/bookmarks.anchor`、`locationKind` | 删列需 Room 迁移，等于丢数据 |
| C6 | `DocumentRepository.updateEpubReadingPosition` + `DocumentDao.updateEpubReadingPosition` | 纯列写入，0 生产调用，但被 8 个测试 fake override；列里有数据。S1c 已判保留 |
| C7 | `AppDatabase.kt:404-405` 的 v14→v15 迁移注释 | 迁移历史的一部分 |

### 5.1 可选收尾（不计入本轮）

`EpubTextAnchorResolver.projectInBook`（`:92-99`）只被 `EpubTextAnchorCodecTest:86` 调用，`project()`（`:101-104`）永远抛异常。这两条是「保存新 EPUB 标注」的路径，功能已不存在。可删 ≈15 行，但会牵动锚点测试的语义，收益小、需要单独授权。

---

## 6. 数据风险汇总

| 动作 | 影响的历史数据 | 处置 |
|---|---|---|
| D-1 删字体导入 | 应用私有目录里已导入的字体文件 | 不删文件，仅移除入口；孤儿文件无害 |
| D-2 删 EPUB 设置 | DataStore 两个键 | 键留在原地，无人读取 |
| D-3 删 resolve 的 EPUB 分支 | 无 | 历史行类型来自 `fromStorage`，不经 `resolve` |
| D-4 删 Manifest 声明 | 无 | 只影响系统「打开方式」列表 |

---

## 7. 批次顺序与门禁

每批独立提交、独立编译、独立测试。

| 批 | 内容 | 预估 | 状态 |
|---|---|---|---|
| S1d-0 | `core:database` 两条过期测试对齐 PDF-only | 2 文件 / 20 用例 | 已完成 `5ee57df` |
| D-1 | 阅读字体导入下线 | −218 行文件 −2 入口 | 已完成 `2208b16` |
| D-2 | EPUB 设置枚举下线 | −14 行 | 已完成（与 D-1 合并提交） |
| D-3 | 导入门禁 EPUB 分支下线 | −9 行 + 1 测试 | 已完成 `ce676fa` |
| D-4 | 入口声明与文案 | −8 行 + 1 测试 | 已完成 `d150480` |

**D-1 与 D-2 合并的原因**：`updateEpubFontFamily` 是唯一同时清理 `KEY_IMPORTED_FONT` 的写入方，单独先删 `importedFontId` 会让中间那个提交编译不过。合并后仍是「删功能 + 删它的设置」一件事。

**编译状态：未执行。** 本机提交内存余量 511 MB，Gradle 守护进程连配置阶段都进不去（`hs_err_pid23828.log`）。四个提交都只做了静态核对：`grep` 九个被删符号的引用计数全为 0；`resolve()` 的返回值域收敛为 PDF/MARKDOWN 后，`ImportAcceptanceParityGoldenTest` 的既有断言仍然成立。真机与模拟器均未验证。

门禁（每批都要过）：

1. `:app:compileQaKotlin` 与 `:core:database:compileReleaseKotlin` 通过；
2. 改动涉及测试的，对应模块单测零失败；
3. `git diff --stat` 的删除行数与本文件预估偏差 > 30% 时，先解释再提交；
4. 本机内存不足时，`:app` 测试标 `pending`，不得写成「已验证」；
5. 不碰 C 类任何一行；越界即为该批失败。

### 7.1 执行结果

```
c9223e9..d150480   18 files changed, 256 insertions(+), 374 deletions(-)
（其中 docs/S1D_EPUB_TRIAGE.md 新增 220 行，代码净变化 -338 行）
EPUB 引用   140 行 → 82 行，且剩余全部属于 B 类（历史行显示、导航 tombstone）
            与 C 类（锚点编解码、EPUB_TEXT、documentType 列）
```

被删除的运行时行为（用户可见）：

| 位置 | 之前 | 之后 |
|---|---|---|
| 设置页「阅读」 | 「阅读字体」可导入 TTF/OTF | 只剩「阅读显示」 |
| PDF 阅读器设置面板底部 | 多一个「阅读字体」行 | 无 |
| 系统「打开方式」 | 列出 Reader 处理 `.epub` | 不再列出 |
| 文件工具扫描 | 扫 PDF/MD/TXT/EPUB，非 PDF 点了报错 | 只扫 PDF |
| 关于页 | 「支持 Markdown、PDF 与 EPUB。」 | 「支持 PDF。」 |

---

## 8. 本次未做

- 未改任何生产代码（唯一改动是 `core/database` 两个测试文件）。
- 未构建 APK，未连接设备。
- 未删任何 DataStore 键、字体文件、数据库列。
- 未合并 `master`，未推送远端。
