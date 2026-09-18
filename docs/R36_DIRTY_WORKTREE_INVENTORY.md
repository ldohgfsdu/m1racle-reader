# R36 Dirty Worktree Inventory — 2026-09-18

冻结基线：`docs/R36_RECOVERY_SNAPSHOT_20260918.md`。本文件只归类、不提交。
总数：74 tracked 修改 + 63 untracked。逐文件记录 category / purpose / 生产代码 / 验证 / checkpoint 归属 / contamination。

图例：`[P]`=生产代码，`[T]`=测试，`[D]`=文档/契约，`[S]`=schema/fixture/脚本。

## A. R36 Glass Engine / optical material

| path | T | purpose | 已有验证 | checkpoint | contamination |
|---|---|---|---|---|---|
| `app/.../ui/material/GlassMaterial.kt` [P] (M, +336/-201) | R36.2 核心：regularFrost blur 26dp、工作候选 light tint .45（readerOverlayFrost）、panelFrost、fallbackPlate、preview preset 隔离 | compileReleaseKotlin PASS；QA 4 失败均为旧冻结断言（见§4），非实现崩溃 | glass checkpoint（当前候选态，非最终 authority） | 无：未动交互/导航/数据 |
| `app/.../ui/material/GlassAuthority.kt` [P] (new) | authority 说明/Role→recipe 映射边界 | GlassAuthorityContractTest（new） | glass checkpoint | 无 |
| `app/.../ui/material/GlassBackdropTopology.kt` [P] (M) | Document-only capture 语义：Hub/Home 不捕获 document backdrop；新增 DocumentBackdropSource | GlassBackdropTopologyContractTest（M） | glass checkpoint | 需确认：Home/Dock 调用方是否同步（见 B） |
| `app/.../ui/material/GlassAlertDialog.kt` [P] (M, +1) | Dialog 加 `interaction=true` | 间接（Dialog 相关测试） | glass checkpoint | 无 |
| `app/.../ui/viewer/ReaderGradient.kt` [P] (new) | 生产 readerGradient，预览复用同一渐变，删重复 AGSL 遮罩 | ReaderFixedGradientUiTest（new） | glass checkpoint | 无 |
| `app/.../ui/theme/DesignSystem.kt` [P] (M) | 接入 M1racleTypography/Surface/CornerRadius/Elevation/GlassPolicy/Motion | M1racleTokensContractTest、M1racleMotionContractTest（new） | glass checkpoint（token 层） | 注意：一次引入多个 token 面，需在 commit message 明确 |
| `app/.../ui/theme/Theme.kt` [P] (M) | CompositionLocal 接入 surface/typography/glass env+capability、LocalReaderDarkTheme | 同上 | 同上 | 同上 |
| `app/.../ui/theme/M1racleMotion.kt` [P] (new) | motion token | M1racleMotionContractTest（new） | 同上 | 无 |
| `app/.../ui/theme/M1racleSurface.kt` [P] (new) | surface token | M1racleTokensContractTest（new） | 同上 | 无 |
| `app/.../ui/theme/M1racleTypography.kt` [P] (new) | typography token | M1racleTokensContractTest（new） | 同上 | 无 |
| `app/.../ui/hub/GlassDock.kt` [P] (M, +61/-159) | Dock 收进引擎/实色条选中标识（删孤儿 DockSelection 配合） | DockMotionTest（M）、GlassDockUiTest（M, +21） | glass checkpoint | 无 |
| `app/.../ui/hub/DockMotion.kt` [P] (M) | press/motion 配合 | DockMotionTest（M） | 同上 | 无 |
| `app/.../ui/hub/HubTabTransitions.kt` [P] (new) | hub tab 转场 | HubTabTransitionsTest（new） | glass checkpoint（或独立 UI batch，commit 时二选一并注明） | 轻度：与 optical 无关，建议独立 commit |

## B. PDF glass / controls（含阅读器 chrome、批注、高亮）

| path | T | purpose | 已有验证 | checkpoint | contamination |
|---|---|---|---|---|---|
| `app/.../ui/pdf/AnchoredNestedPopover.kt` [P] (M) | 子面板 SettingsCard 角色/高度判定 | ReaderMoreHostUiTest（M）、PdfMenuAccessibilityTest（new） | pdf-controls checkpoint | 无 |
| `app/.../ui/pdf/PdfViewerScreen.kt` [P] (M) | 胶囊/菜单材质角色、press indication、backdrop host 接线 | 同上 + ReaderChromeMaterialTest（旧断言，见§4） | 同上 | 无 |
| `app/.../ui/pdf/PdfViewerViewModel.kt` [P] (M) | 阅读逻辑配合 | 间接 | 同上 | 无 |
| `app/.../ui/pdf/ReaderMoreHost.kt` [P] (M) | 菜单行高/语义/语义角色直连引擎 | 同上 | 同上 | 无 |
| `app/.../ui/pdf/PdfChromeVisibility.kt` [P] (new) | 淡入淡出节点 3×阴影缓冲（截边修复） | PdfChromeVisibilityTest（new, 2/0/0/0） | 同上 | 无 |
| `app/.../ui/annotation/AnnotationOverlay.kt` [P] (M) | 批注覆盖配合 | AnnotationOverlayContractTest（M）等 | annotation checkpoint（PDF 编辑关联） | 注意：与正文编辑 batch 相邻，commit 可并入 PDF 编辑批 |
| `app/.../ui/annotation/PdfHighlightGeometry.kt` [P] (M) | 高亮几何 | 同上 | 同上 | 同上 |
| `app/.../ui/annotation/PdfHighlightLayer.kt` [P] (M, +67/-127) | 高亮层重写 | PdfHighlightLoopTest（M）等 | 同上 | 同上 |
| `app/.../ui/annotation/SelectionModel.kt` [P] (M) | 选择模型 | SelectionAnchorConversionTest（M） | 同上 | 同上 |
| `app/.../pdf/PdfTextSelection.kt` [P] (new) | 文字选择 | PdfTextSelectionTest（new） | 同上 | 无 |
| `app/.../pdf/edit/PdfBodyEditor.kt` [P] (new) | 正文编辑核心 | PdfBodyEditorTest（new） | pdf-edit checkpoint（独立） | 无 |
| `app/.../pdf/edit/PdfSourceWriteStore.kt` [P] (new) | 源文件写入/备份/恢复 | PdfSourceWriteStoreTest（new） | 同上 | 无 |
| `app/.../ui/pdf/edit/PdfBodyEditorScreen.kt` [P] (new) | 编辑 UI | PdfBodyEditorTest（new） | 同上 | 无 |
| `app/.../ui/pdf/edit/PdfBodyEditorViewModel.kt` [P] (new) | 编辑 VM | 同上 | 同上 | 无 |
| `app/.../markdown/TocItem.kt` [P] (M, +4) | `activeAt` 共享（drawer + chrome） | 间接 | pdf-controls checkpoint | 无 |
| `app/.../ui/viewer/ViewerScreen.kt` [P] (M, +156/-116) | Markdown 阅读器配合（含渐变/topbar） | ReaderFixedGradientUiTest（new）等 | markdown checkpoint（可并入 glass） | 轻度：Markdown+PDF 同文件？以实际 diff 为准，commit 时注明 |
| `app/.../ui/viewer/ReaderTopBarScroll.kt` [P] (M) | 顶栏滚动 | ReaderTopBarScrollStateTest（M, +15） | 同上 | 无 |
| `app/.../ui/viewer/components/TocDrawerContent.kt` [P] (M, +2/-1) | 目录抽屉配合 | 间接 | 同上 | 无 |
| `app/.../ui/navigation/NavGraph.kt` [P] (M) | PDF_EDITOR 路由/编辑退出保护（MainActivity 配合） | 间接 | pdf-edit checkpoint | 无 |
| `app/.../MainActivity.kt` [P] (M, +11) | 编辑中外部打开等待退出 | 间接 | 同上 | 无 |
| `app/.../M1racleReaderApplication.kt` [P] (M, +1) | 启动恢复 `recoverPdfSourceWrites` | 间接 | 同上 | 无 |
| `app/.../ui/home/DocumentListRow.kt` [P] (new) | 行组件抽取（SOLID Dock 色） | HomeOperationsTest 等（new） | home checkpoint | 无 |

## C. Settings glass preview

| path | T | purpose | 已有验证 | checkpoint | contamination |
|---|---|---|---|---|---|
| `app/.../ui/hub/HubSettingsScreen.kt` [P] (M) | 设置页预览扩展（当前/渐变/折射参考 + tint 候选） | compileReleaseKotlin PASS；QA 见§4 | settings-preview checkpoint（实现 DONE，视觉 NOT_RUN） | 已确认：阅读页生产配方未动，不进入 R36.3 |
| `app/.../ui/material/BlurComparisonPreview.kt` [P] (new, A 共用) | 对照预览 UI（生产 glassMaterial/readerGradient 复用 + 手动滑动/播放） | GlassOpticalPolicyTest（new，通过） | 同上 | 预览 tint 候选经 Engine previewPreset 隔离，不泄漏生产（已测） |

## D. tests / frozen contracts

Tracked 修改（均为 [T]，配合上述生产变更；除注明外已有验证=随 QA 运行）：

- R31/R32/R33 contract：已更新至 v14（含 14.json hash 断言），见 E。应进入 Room checkpoint。
- `GlassMaterialRegressionTest`（M, +128/-109）：旧冻结断言（.86/.90/18dp）——**当前 3 失败来源，不改**（见§4）。
- `ReaderChromeMaterialTest`（M）：旧冻结断言——**当前 1 失败来源，不改**（见§4）。
- `GlassOpticalPolicyTest`（new, untracked）：候选隔离/降级测试通过（首轮 :27 失败在末轮已通过，不在处置集）。
- 其余 annotation/home/hub/pdf/reader/viewer 测试修改：配合 B/E/Home 变更；HomeLayoutContractTest（256 行级）对应 HomeScreen 重写（806 行级），属 home checkpoint。
- `ContentIdentityImportWiringTest`（+77）：配合 DocumentRepository 来源元数据，属 Room/pdf-edit checkpoint。

Untracked 新增 [T]：MigrationV13ToV14Test（E）、PdfTextSelectionTest、pdf/edit 2 个、PdfTextHighlightGestureTest、Home 3 个、HubTabTransitionsTest、FrostShadowRendererTest、GlassAuthorityContractTest、GlassOpticalPolicyTest、GlassThemeScopeTest、PdfChromeVisibilityTest、PdfMenuAccessibilityTest、pdf/edit PdfEditorSaveOptionsTest、theme 2 个、ReaderFixedGradientUiTest。均应随各自生产 batch 进入 checkpoint。

`app/src/androidTest/.../GlassDockPixelsInstrumentedTest.kt`：instrumented 像素测试，需真机；**不纳入离线 checkpoint 验证**，保留 untracked/或随 commit 进入但不执行（commit message 注明 NOT_RUN）。

## E. Room schema（合法变化，非 contamination）

- `AppDatabase.kt`（M）：`version 13→14`，新增 `MIGRATION_13_14`（documents 加 `sourceUri TEXT`、`sourceSha256 TEXT`），已接入 build 链。
- `DocumentEntity.kt`（M）：新增 `sourceUri` / `sourceSha256`；`contentSha256` 语义澄清为当前阅读修订。
- `DocumentRepository.kt`（M, +101/-2）：来源元数据写入/恢复（含 `recoverPdfSourceWrites`）。
- `AnchorCodec.kt` / `DocumentAnchor.kt`（M，小）：anchor 配合（diff 需 commit 前复核无 scope 外改动）。
- `14.json` [S]（new, 28KB）：schema 导出，SHA-256 `e5bd1ee2…` 与 R33 期望值一致，13.json 不变（已复核）。
- `MigrationV13ToV14Test.kt` [T]（new）：迁移测试。
- R31（链延长至 v14）、R32（`version=14` + 14.json 存在断言）、R33（14.json hash 断言）已同步更新。
- reason：PDF 正文编辑“保存覆盖/另存/备份恢复”需要区分“当前阅读修订”与“外部源版本”（PRD 2026-09-07 保存补充授权）。**允许进入 checkpoint，但必须独立于 glass batch（单独 commit）。**

## F. docs / reports

Tracked M：`docs/CURRENT_STATUS.md`（本 Recovery 同步修正）、`docs/PRD.md`（正文编辑/保存授权补充+首页结构更新）、`AGENTS.md`（Release-only 构建规则 2026-09-15）、`README.md`（assembleRelease）、`docs/APK_DELIVERY.md`、`docs/DEVICE_INSTALL_AND_ADB_TEST.md`（小）、`docs/DOCK_REFERENCE_OPTIMIZATION.md`（+57）、`.memory/active_object.yaml`（本 Recovery 同步修正）。

Untracked new（24）：R36 系列（R36_1、CLOSURE、R36_2 报告、GROUND_TRUTH_AUDIT）、PDF 正文编辑 4 篇、高亮/保存/覆盖、frost 系列 5 篇、HOME 4 篇、GLASS_BACKPLATE_ROLLOUT、GLASS_MATERIAL_COMPARISON、HYPERCHANGER_BLUR_REFERENCE、READER_FLOW_POLISH。均为历史报告/设计记录，**进入 docs checkpoint（单独 commit），不删**。

## G. fixtures / scripts

- `scripts/generate-r36-optical-fixtures.py`（new）：R36-2 A–E fixture 生成（可重复，cmp 一致已验证）。
- `app/src/test/resources/fixtures/pdf-edit/`（new 3 文件）：ChineseFixture.ttf + OFL.txt + README（测试字体，OFL 合规）。
- 不进入 APK（Gradle 未引用），进入 scripts/fixtures checkpoint（可并入对应 batch）。

## H. unrelated / suspicious

1. `images/r36-home.png`（new, 966KB）：**NOT-IGNORED，大图片在源码树**。公约 1.1/1.5 禁止未使用资源与截图进正式源码树。处置：**不 commit**；移入 `.external_outbox/` 或确认无用后删除（需用户确认，本 Recovery 仅记录，见§5）。
2. 绝对路径 grep 命中：测试 helper 回退路径 `/root/code/m1racle-reader/...`（GlassMaterialRegressionTest.kt:350-354 等）为历史写法，非本轮新增泄漏；docs 中 `/storage/emulated/0/...` 为既定 Inbox/Download 路径，非个人数据泄漏。`git status` 无 build 输出/APK 被跟踪。结论：**无待清理的临时日志/绝对路径/个人环境数据**。
3. `strings.xml`（M）：首页文案（最近阅读）+ PDF 无障碍（已展开/已收起）+ 跳页范围——分属 home 与 pdf-controls batch，commit 时拆到各自 batch，禁止单 commit 混入 glass。
4. `ContinueReadingCard/HomeScreen/HomeViewModel/HomeDesignTokens/BookmarksScreen/LibraryScreen`（M）：home checkpoint，与 glass 无关，**禁止塞进 glass commit**。

## §4 QA 失败 disposition

### 4 个 optical 冻结失败（不改，batch 未完成）

末轮对照（84/4）：GlassMaterialRegressionTest ×3（:62、:100、:131）、ReaderChromeMaterialTest ×1（:34）。根因：实现已走到工作候选（Control light .45、blur 26dp、panel 26dp），测试仍断言旧冻结（.86/.90/18dp；dialog.blur>=card.blur 在 panel 26 vs dialog 18 下不成立）。

**决策：选第二种——`.45` 尚未成为正式 authority，不改测试，标记 optical batch 未完成。**

理由：R36_2 报告与 CURRENT_STATUS 均记录 Batch 2 为“暂留 .45”，dark 未独立调，Control 被 readerOverlayFrost 连带问题未拆，lens/edge/shadow 未开始；且设置页对照任务自身声明“暗色候选不代表已验收”。此时把 `.45` 写进冻结断言等于用未验收候选覆盖 gate，违反“测试 authority”要求。

- 不为绿测试改数字；不断言 `.45` 为最终配方。
- 4 失败保留为 batch 未完成的正确 gate 信号；后续 LIGHT TINT 收口正式批准后，再以 token/authority 引用方式统一更新断言（避免多处 magic number）。
- 首轮第 5 条 GlassOpticalPolicyTest :27 在末轮已通过，不在处置集。

### 第 5 个失败（全量 1503/5 中新发现，已对齐 authority 并修复）

`M1racleMotionContractTest > pdf chrome circles fade and slide without scale-enter leftovers`：`StringIndexOutOfBoundsException`（:40）——测试在 `PdfViewerScreen.kt` 内 `indexOf("AnimatedVisibility(")` 返回 -1。根因：截边修复已把 fade 节点搬入 `PdfChromeVisibility.kt`（单一事实源），调用方只传 enter/exit（`fadeIn(tween(200)) + slideInVertically…` 仍在原文件，spec 未变）。

处置：测试改读 `PdfChromeVisibility.kt` 做无 scale-enter 断言，fade/slide spec 断言仍读 `PdfViewerScreen.kt`；未放宽任何阈值（另修复取头越界 `substring(0,400)`→按实际长度）。复测该类 5/5 通过。这与 4 个 optical 失败性质不同：后者是未批准候选 vs 冻结 gate，前者是测试引用了已搬迁的 authority 文件。

## §5 checkpoint 拆分建议（供 §7 commit 用）

1. `docs(r36): recovery snapshot and worktree inventory`（本文件 + snapshot + authority 同步）
2. `refactor(glass): R36 optical material working checkpoint`（A + C 生产代码；C 注明 preview-only）
3. `feat(pdf): body-edit source tracking and chrome visibility`（B 中 pdf-edit + E Room v14 + 相关测试）
4. `feat(home): layout polish and tokens`（HomeScreen 系 + strings 首页部分 + theme tokens 若归 home——以实际依赖为准）
5. `test(glass): optical policy and authority contracts`（D 中 glass 新测试；旧冻结测试不改）
6. `docs(r36): historical reports and fixtures`（F untracked + G）
7. `images/r36-home.png`：不 commit（见 H.1）。

最终 commit 划分以实际依赖与 `git diff` 复核为准，可合并但禁止把 E/Home/strings 混入 glass commit。
