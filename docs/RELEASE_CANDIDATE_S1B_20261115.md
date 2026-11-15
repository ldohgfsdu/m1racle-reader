# S1b 阶段验收候选包

日期：2026-11-15
定位：**阶段验收候选包，不是正式 Release。** 用于真机验证 S1b 的结构性删除是否破坏了 PDF 行为。

---

## 1. 基线冻结

| 项 | 值 |
|---|---|
| 分支 | `refactor/pdf-only` |
| HEAD | `891b683ac41f4ea72ae643c8c5a0edaba8e48a24` |
| HEAD 标题 | `refactor(pdf-only): delete the EPUB parser and the last reader entry into it` |
| 工作树 | 干净（构建前后均为空 `git status`） |
| `master` | `fc4788e`，未动 |
| 分支领先 master | 28 个提交 |
| 远端同步 | 已推送至 `github/refactor/pdf-only` |

S1b 的五个提交：

| 提交 | 内容 |
|---|---|
| `48540e3` | 入口切断（选择器 MIME、书签导航、墓碑路由）+ PDF-only 文案 |
| `aa5a86c` | 准入边界 8 用例 + 门禁 KDoc 对齐 |
| `a804cad` | QA 宿主迁出 `ui/epub` + 清 manifest 悬空注册 |
| `7065edb` | 删 `ui/epub` 13 文件 2,122 行 + 12 个测试 + pagecurl 依赖 |
| `891b683` | 删 EPUB 解析器 7 文件 1,359 行 + 6 个测试 + `openEpubBook` 调用点 |

---

## 2. 构建产物

| 项 | 值 |
|---|---|
| 文件 | `M1racle-Reader-1.0.0-97-s1b-candidate-20261115-1110.apk` |
| 大小 | 6,745,924 字节（6.43 MiB） |
| 包名 | `com.m1racle.reader` |
| versionCode | **97**（与已安装的 97 正式包相同） |
| versionName | `1.0.0` |
| SHA-256 | `e94ddf8a41493c3a7ca662dce289c8184468fc19811c0de8670f9badcedbbe09` |

构建命令（本机，含 lint）：

```
:app:assembleRelease --offline
  -Dorg.gradle.jvmargs=-Xmx1700m -XX:MaxMetaspaceSize=768m -XX:+UseSerialGC
  -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false
```

本次**没有跳过 `lintVitalRelease`**，完整构建 3 分 55 秒成功。

---

## 3. 校验结果

| 检查 | 结果 |
|---|---|
| 签名证书 DN | `CN=M1racle Reader, OU=Android, O=M1racle, L=China, C=CN` |
| 证书 SHA-256 | `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1` |
| 与 97 正式包证书 | **完全一致** |
| 签名方案 | v2（与 97 正式包一致：v1=false / v2=true / v3=false） |
| 16 KiB 对齐 | `zipalign -c -P 16` 通过 |
| `assets/markdown-editor` | 0 项 |
| CJK 字体 | 0 项 |
| epub / pagecurl 条目 | 0 / 0 |
| dex | 1 个，4.42 MiB |

体积对比：

| 包 | 字节 | MiB |
|---|---|---|
| 97 正式包 | 27,732,541 | 26.45 |
| S1b 候选包 | 6,745,924 | 6.43 |
| 减少 | 20,986,617 | **−75.7%** |

---

## 4. 覆盖安装与回退

**versionCode 保持 97 是刻意的**，不是漏改：

- 同 versionCode 覆盖安装是允许的（`INSTALL_FAILED_VERSION_DOWNGRADE` 只在更低版本时触发）。
- 不升到 98，是为了**保留回退路径**：如果这版有回归，可以直接用 97 正式包同版本覆盖回去，不需要卸载、不丢数据。
- 代价是系统设置里两个包的版本号相同，只能靠文件名和 SHA-256 区分。这是候选包，可以接受。

安装方式（真机）：

```
adb install -r M1racle-Reader-1.0.0-97-s1b-candidate-20261115-1110.apk
```

`-r` 是覆盖安装，不清数据。**不要**先 `adb uninstall`。

回退方式：

```
adb install -r -d M1racle-Reader-1.0.0-97.apk
```

---

## 5. 已知未通过项（不标全绿）

```
QA       1236 用例 / 6 失败 / 1 跳过
Release  1104 用例 / 6 失败 / 1 跳过
```

6 个失败全部是 `MarkdownPackageExportTest`，均停在 `MarkdownPackageExportTest.kt:70`：

1. `failedIdentityCommitCleansBothExtractAndRetainedArchive`
2. `originalPackageCannotBeSelectedAsExportDestination`
3. `corruptedRetainedOriginalIsRejectedBeforeOpeningDestination`
4. `newImportExportsOriginalBytesAndRoundTripsAllResources`
5. `allVerifiedSourcesStayProtectedAfterSamePackageIsOpenedFromAnotherFile`
6. `deletingPackageRemovesRetainedOriginalButPreservesUserSource`

这 6 个断言的是「Markdown 教材包导出」这条已退出范围的功能路径，随该功能实现一起退役，不在本批处理。**除此之外没有其他失败。**

测试轨迹（每批全量跑）：

| 阶段 | 用例 | 失败 |
|---|---|---|
| S1a 完成 | 1376 | 6（同上） |
| 删 ui/epub | 1288 | 6 |
| 删解析器 | 1236 | 6 |

减少的 140 个用例全部是 EPUB 测试，无新增失败。

**注意**：Release 套件第一次跑只报 333 用例（app/src/test 有 1103 个 `@Test`），无崩溃日志，判断为 worker 提前退出。重跑得 1104 用例。上面引用的是重跑数字。

---

## 6. 真机回归清单

按风险排序。前四项是这轮改动直接触及的。

### 必测

| # | 项目 | 预期 |
|---|---|---|
| 1 | 覆盖安装后旧文档仍在 | 文库列表、最近阅读、书签、PDF 进度全部保留 |
| 2 | 点击旧的 Markdown / EPUB 文档 | 不打开阅读器、不崩溃、给出格式提示 |
| 3 | 书签页点击旧格式书签 | 弹「该格式已不在支持范围」，不跳转、不崩溃 |
| 4 | 首页「⋯」菜单 | 只剩重命名 / 导出 / 信息 / 删除；无「编辑正文」 |
| 5 | 导入按钮 | 系统选择器只列 PDF |

### PDF 主循环

| # | 项目 | 预期 |
|---|---|---|
| 6 | 导入 PDF | 成功入库，列表可见 |
| 7 | 打开大型教材 PDF | 首屏时间可接受，不闪退 |
| 8 | 连续滚动 | 跟手，无空白页，无跳页 |
| 9 | 横向翻页 / 缩放平移 | 缩放中松手前界面有反馈 |
| 10 | 目录跳转 | 落到正确页 |
| 11 | 书签、标注 | 能建、能定位、重开后还在 |
| 12 | 进度恢复 | 杀进程重开，回到原页 |
| 13 | 导出（拆分 / 章节 / 完整文件） | 产物可打开 |

### 旧数据兼容

| # | 项目 | 预期 |
|---|---|---|
| 14 | 数据库能正常打开 | 无迁移崩溃 |
| 15 | 已有 EPUB/Markdown 记录 | 不丢失、不被静默删除 |

---

## 7. 本包不包含的变更

- 未合并 `master`。
- 未创建正式 Release，未改 `versionCode`。
- 未开始 S1c 的删除。
- S3 的两组依赖（`Reading Assets → App Shell`、`PDF Engine → Library`）一行未动。
- `markdown` 包 2,552 行仍在包里。

---

## 8. S1c 只读分诊（未执行任何删除）

`markdown` 包 12 个文件、2,552 行。外部引用面：

| 文件 | 行数 | 外部引用方 |
|---|---|---|
| `MarkdownParser` | 622 | `DocumentRepository` |
| `InlineMarkdownParser` | 717 | 4 个文件（含 `DocumentListTitle`、`MarkdownSearchTextExtractor`） |
| `MarkdownAssetResolver` | 478 | `image/LocalImageLoader` |
| `DocumentListTitle` | 207 | `ui/home/HomeScreen` |
| `MarkdownPerformance` | 178 | 2 个文件 |
| `MarkdownAnnotationRemapper` | 136 | `DocumentRepository` |
| `MarkdownBlock` | 81 | 见下方陷阱 |
| `MarkdownLinkRouter` | 40 | 1 个文件 |
| `AnchorRegistry` | 34 | 1 个文件 |
| `MarkdownSource` | 29 | 0 |
| `TocItem` | 18 | 1 个文件 |
| `MarkdownAnchorSlug` | 12 | 2 个文件 |

### 分诊必须先解决的一个陷阱

裸名 grep 会把两个同名类型混在一起：

- `com.m1racle.reader.markdown.MarkdownBlock` —— 解析器块模型，随 Markdown 退役。
- `DocumentAnchor.MarkdownBlock` —— **锚点枚举分支**，在 `core/model`，是已存书签和标注的坐标类型，必须保留。

`ui/annotation/SelectionModel` 和 `AnnotationOverlay` 命中的是后者，不是前者。S1c 必须按完全限定名统计，否则会误判 PDF 标注依赖 Markdown 解析器，进而删掉 PDF 还在用的类型。

### 初判（待 S1c 逐项确认）

| 类别 | 文件 | 判断依据 |
|---|---|---|
| PDF 共用，需迁移 | `DocumentListTitle` | 服务首页列表标题，PDF 也用；但内部调 `InlineMarkdownParser.toPlainText`。PDF 无 H1，应退化为文件名，可去掉解析依赖 |
| 需确认 | `MarkdownBlock`、`TocItem`、`AnchorRegistry` | 可能是锚点/目录的公共坐标，需看 FQN |
| Markdown 专属，可删 | `MarkdownParser`、`MarkdownAnnotationRemapper`、`MarkdownLinkRouter`、`MarkdownAnchorSlug`、`MarkdownSource`、`MarkdownPerformance` | 只服务 Markdown 导入与渲染 |
| 依赖 PDF 侧 | `MarkdownAssetResolver` | 被 `LocalImageLoader` 用，需确认 PDF 是否走同一图片加载器 |

另外，文库筛选条仍提供 `LibraryFilter.MARKDOWN` / `EPUB` 两个已退出格式的筛选项（`hub_filter_markdown` / `hub_filter_epub` 文案），属 S1c 范围。

---

## 9. 结论

代码层面 S1b 已完成并通过双变体测试，但**不能宣布发布验收通过**：仍有 6 个已知失败属于待退役功能。本候选包用于验证 PDF 行为是否被这轮结构性删除破坏。真机发现问题先修，修完再进 S1c。
