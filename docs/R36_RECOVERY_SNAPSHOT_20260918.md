# R36 Recovery Snapshot — 2026-09-18

冻结时间：2026-09-18（UTC）。本文件只记录现场事实，不做诊断、不改代码。

## 1. Git 现场

- branch: `feat/risediary-inspired-dock`
- HEAD: `5284f801fffa62904ce80101ca687cfd415ea8c6`
- tracked 修改：74 文件，`2175 insertions / 2271 deletions`（`git diff --stat` 全量见 §5 摘要，numstat 已存档于本会话记录）
- untracked：63 条（`git ls-files --others --exclude-standard | wc -l`），含 1 个 Room schema、16 个生产源码、17 个测试、3 个 fixture、1 个脚本、24 个 docs、1 张图片。完整清单见 `docs/R36_DIRTY_WORKTREE_INVENTORY.md`。
- tag/远端：本轮未改动（沿用 CURRENT_STATUS 记录：本地与远端 `v1.0.0` 指向 `a43633a`，无 `v1.0.1`）。

**明确：HEAD = 5284f80 是基线提交，不是当前实现状态。当前工作区是 5284f80 + 上述 74 tracked + 63 untracked 的脏工作区。不得把 5284f80 描述成当前实现状态。**

## 2. git status --short（全量，冻结时）

Tracked（74）：

```
M .memory/active_object.yaml
M AGENTS.md
M README.md
M app/src/main/java/com/m1racle/reader/M1racleReaderApplication.kt
M app/src/main/java/com/m1racle/reader/MainActivity.kt
M app/src/main/java/com/m1racle/reader/data/anchor/AnchorCodec.kt
M app/src/main/java/com/m1racle/reader/data/anchor/DocumentAnchor.kt
M app/src/main/java/com/m1racle/reader/data/local/AppDatabase.kt
M app/src/main/java/com/m1racle/reader/data/local/DocumentEntity.kt
M app/src/main/java/com/m1racle/reader/data/repository/DocumentRepository.kt
M app/src/main/java/com/m1racle/reader/markdown/TocItem.kt
M app/src/main/java/com/m1racle/reader/ui/annotation/AnnotationOverlay.kt
M app/src/main/java/com/m1racle/reader/ui/annotation/PdfHighlightGeometry.kt
M app/src/main/java/com/m1racle/reader/ui/annotation/PdfHighlightLayer.kt
M app/src/main/java/com/m1racle/reader/ui/annotation/SelectionModel.kt
M app/src/main/java/com/m1racle/reader/ui/home/ContinueReadingCard.kt
M app/src/main/java/com/m1racle/reader/ui/home/HomeDesignTokens.kt
M app/src/main/java/com/m1racle/reader/ui/home/HomeScreen.kt
M app/src/main/java/com/m1racle/reader/ui/home/HomeViewModel.kt
M app/src/main/java/com/m1racle/reader/ui/hub/BookmarksScreen.kt
M app/src/main/java/com/m1racle/reader/ui/hub/DockMotion.kt
M app/src/main/java/com/m1racle/reader/ui/hub/GlassDock.kt
M app/src/main/java/com/m1racle/reader/ui/hub/HubSettingsScreen.kt
M app/src/main/java/com/m1racle/reader/ui/hub/LibraryScreen.kt
M app/src/main/java/com/m1racle/reader/ui/material/GlassAlertDialog.kt
M app/src/main/java/com/m1racle/reader/ui/material/GlassBackdropTopology.kt
M app/src/main/java/com/m1racle/reader/ui/material/GlassMaterial.kt
M app/src/main/java/com/m1racle/reader/ui/navigation/NavGraph.kt
M app/src/main/java/com/m1racle/reader/ui/pdf/AnchoredNestedPopover.kt
M app/src/main/java/com/m1racle/reader/ui/pdf/PdfViewerScreen.kt
M app/src/main/java/com/m1racle/reader/ui/pdf/PdfViewerViewModel.kt
M app/src/main/java/com/m1racle/reader/ui/pdf/ReaderMoreHost.kt
M app/src/main/java/com/m1racle/reader/ui/theme/DesignSystem.kt
M app/src/main/java/com/m1racle/reader/ui/theme/Theme.kt
M app/src/main/java/com/m1racle/reader/ui/viewer/ReaderTopBarScroll.kt
M app/src/main/java/com/m1racle/reader/ui/viewer/ViewerScreen.kt
M app/src/main/java/com/m1racle/reader/ui/viewer/components/TocDrawerContent.kt
M app/src/main/res/values/strings.xml
M app/src/test/java/com/m1racle/reader/R31ArchitectureReleaseContractTest.kt
M app/src/test/java/com/m1racle/reader/R32ReleaseHardeningContractTest.kt
M app/src/test/java/com/m1racle/reader/R33ProductIdentityMigrationTest.kt
M app/src/test/java/com/m1racle/reader/data/anchor/AnchorResolverTest.kt
M app/src/test/java/com/m1racle/reader/data/anchor/DocumentAnchorCodecTest.kt
M app/src/test/java/com/m1racle/reader/data/local/ReadingModelAuthorityTest.kt
M app/src/test/java/com/m1racle/reader/data/repository/AnnotationAnchorRepositoryTest.kt
M app/src/test/java/com/m1racle/reader/data/repository/ContentIdentityImportWiringTest.kt
M app/src/test/java/com/m1racle/reader/data/repository/SearchRepositoryTest.kt
M app/src/test/java/com/m1racle/reader/pdf/ReaderChromeGeometryTest.kt
M app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationCreationFlowTest.kt
M app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationOverlayContractTest.kt
M app/src/test/java/com/m1racle/reader/ui/annotation/AnnotationPerformanceContractTest.kt
M app/src/test/java/com/m1racle/reader/ui/annotation/SelectionAnchorConversionTest.kt
M app/src/test/java/com/m1racle/reader/ui/home/HomeImportEventTest.kt
M app/src/test/java/com/m1racle/reader/ui/home/HomeLayoutContractTest.kt
M app/src/test/java/com/m1racle/reader/ui/home/HomeViewModelTest.kt
M app/src/test/java/com/m1racle/reader/ui/hub/BookmarkScreenTest.kt
M app/src/test/java/com/m1racle/reader/ui/hub/DockMotionTest.kt
M app/src/test/java/com/m1racle/reader/ui/hub/GlassDockUiTest.kt
M app/src/test/java/com/m1racle/reader/ui/material/GlassBackdropTopologyContractTest.kt
M app/src/test/java/com/m1racle/reader/ui/material/GlassInteractionContractTest.kt
M app/src/test/java/com/m1racle/reader/ui/material/GlassMaterialRegressionTest.kt
M app/src/test/java/com/m1racle/reader/ui/pdf/R25ReaderFoundationTest.kt
M app/src/test/java/com/m1racle/reader/ui/pdf/ReaderChromeMaterialTest.kt
M app/src/test/java/com/m1racle/reader/ui/pdf/ReaderChromeTokensContractTest.kt
M app/src/test/java/com/m1racle/reader/ui/pdf/ReaderMoreHostUiTest.kt
M app/src/test/java/com/m1racle/reader/ui/reader/AnnotationManagementTest.kt
M app/src/test/java/com/m1racle/reader/ui/reader/PdfHighlightLoopTest.kt
M app/src/test/java/com/m1racle/reader/ui/reader/ReaderNoteControllerTest.kt
M app/src/test/java/com/m1racle/reader/ui/viewer/ReaderTopBarScrollStateTest.kt
M docs/APK_DELIVERY.md
M docs/CURRENT_STATUS.md
M docs/DEVICE_INSTALL_AND_ADB_TEST.md
M docs/DOCK_REFERENCE_OPTIMIZATION.md
M docs/PRD.md
```

Untracked（63，`git ls-files --others --exclude-standard` 全量）：

```
app/schemas/com.m1racle.reader.data.local.AppDatabase/14.json
app/src/androidTest/java/com/m1racle/reader/ui/hub/GlassDockPixelsInstrumentedTest.kt
app/src/main/java/com/m1racle/reader/pdf/PdfTextSelection.kt
app/src/main/java/com/m1racle/reader/pdf/edit/PdfBodyEditor.kt
app/src/main/java/com/m1racle/reader/pdf/edit/PdfSourceWriteStore.kt
app/src/main/java/com/m1racle/reader/ui/home/DocumentListRow.kt
app/src/main/java/com/m1racle/reader/ui/hub/HubTabTransitions.kt
app/src/main/java/com/m1racle/reader/ui/material/BlurComparisonPreview.kt
app/src/main/java/com/m1racle/reader/ui/material/GlassAuthority.kt
app/src/main/java/com/m1racle/reader/ui/pdf/PdfChromeVisibility.kt
app/src/main/java/com/m1racle/reader/ui/pdf/edit/PdfBodyEditorScreen.kt
app/src/main/java/com/m1racle/reader/ui/pdf/edit/PdfBodyEditorViewModel.kt
app/src/main/java/com/m1racle/reader/ui/theme/M1racleMotion.kt
app/src/main/java/com/m1racle/reader/ui/theme/M1racleSurface.kt
app/src/main/java/com/m1racle/reader/ui/theme/M1racleTypography.kt
app/src/main/java/com/m1racle/reader/ui/viewer/ReaderGradient.kt
app/src/test/java/com/m1racle/reader/data/local/MigrationV13ToV14Test.kt
app/src/test/java/com/m1racle/reader/pdf/PdfTextSelectionTest.kt
app/src/test/java/com/m1racle/reader/pdf/edit/PdfBodyEditorTest.kt
app/src/test/java/com/m1racle/reader/pdf/edit/PdfSourceWriteStoreTest.kt
app/src/test/java/com/m1racle/reader/ui/annotation/PdfTextHighlightGestureTest.kt
app/src/test/java/com/m1racle/reader/ui/home/HomeDocumentActionsUiTest.kt
app/src/test/java/com/m1racle/reader/ui/home/HomeOperationsTest.kt
app/src/test/java/com/m1racle/reader/ui/home/HomeViewportUiTest.kt
app/src/test/java/com/m1racle/reader/ui/hub/HubTabTransitionsTest.kt
app/src/test/java/com/m1racle/reader/ui/material/FrostShadowRendererTest.kt
app/src/test/java/com/m1racle/reader/ui/material/GlassAuthorityContractTest.kt
app/src/test/java/com/m1racle/reader/ui/material/GlassOpticalPolicyTest.kt
app/src/test/java/com/m1racle/reader/ui/material/GlassThemeScopeTest.kt
app/src/test/java/com/m1racle/reader/ui/pdf/PdfChromeVisibilityTest.kt
app/src/test/java/com/m1racle/reader/ui/pdf/PdfMenuAccessibilityTest.kt
app/src/test/java/com/m1racle/reader/ui/pdf/edit/PdfEditorSaveOptionsTest.kt
app/src/test/java/com/m1racle/reader/ui/theme/M1racleMotionContractTest.kt
app/src/test/java/com/m1racle/reader/ui/theme/M1racleTokensContractTest.kt
app/src/test/java/com/m1racle/reader/ui/viewer/ReaderFixedGradientUiTest.kt
app/src/test/resources/fixtures/pdf-edit/ChineseFixture.ttf
app/src/test/resources/fixtures/pdf-edit/OFL.txt
app/src/test/resources/fixtures/pdf-edit/README.md
docs/EXPANDED_READER_MENU_FROST.md
docs/FROST_EXPLICIT_DROP_SHADOW.md
docs/FROST_VISIBLE_OUTLINE_PREVIEW.md
docs/GENTLE_FROST_AND_UI_POLISH.md
docs/GLASS_BACKPLATE_ROLLOUT.md
docs/GLASS_MATERIAL_COMPARISON.md
docs/HOME_APPLE_DESIGN_GUIDANCE.md
docs/HOME_FUNCTIONAL_FIXES.md
docs/HOME_LAYOUT_POLISH.md
docs/HOME_MENU_CORRECTION.md
docs/HOME_SOLID_MENU.md
docs/HYPERCHANGER_BLUR_REFERENCE.md
docs/MARKDOWN_FIXED_GRADIENT_AND_THICK_FROST.md
docs/PDF_BODY_EDITING_DESIGN.md
docs/PDF_BODY_EDITOR_FIRST_LOOP.md
docs/PDF_CONTROLS_POLISH_20260915.md
docs/PDF_EDITOR_SAVE_AND_OVERWRITE.md
docs/PDF_FROST_COLOR_AND_SHADOW.md
docs/PDF_TEXT_HIGHLIGHT.md
docs/R36_1_GLASS_AUTHORITY_CLOSURE.md
docs/R36_2_OPTICAL_MATERIAL_REPORT.md
docs/R36_GLASS_GROUND_TRUTH_AUDIT.md
docs/READER_FLOW_POLISH_20260906.md
images/r36-home.png
scripts/generate-r36-optical-fixtures.py
```

## 3. 当前 Release / QA 产物（冻结时，不重建）

- Release latest：`.external_outbox/m1racle-reader-release-latest.apk`
  - size：6,953,186 bytes，timestamp 2026-09-18 13:13
  - SHA-256：`e4acceec6fe687b31c70d77489ef399058e852db2431444a451c1943e2b636fb`（与同名 `.sha256` 文件及本次 `sha256sum` 复核一致）
  - 版本：`versionCode=23 versionName=1.0.0`，包名 `com.m1racle.reader`（aapt2 badging）
  - 签名：production signer SHA-256 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`（apksigner verify 通过）
  - 当日同系版本：`...-080223`、`...-123925`、`...-124130`、`...-124410`、`...-131357`（latest 即 131357）
- QA latest：`.external_outbox/m1racle-reader-qa-latest.apk`，79,345,504 bytes，2026-09-18 07:31（隔离测试能力，不交付）
- 构建状态（`.external_outbox/glass-comparison/` 日志，设置页对照轮）：
  - `compileReleaseKotlin`：PASS（2 条既有 Compose icons deprecated 警告）
  - `testQaUnitTest`：84 tests / 首轮 5 failed → 末轮 **4 failed**（见 §4）
  - `assembleRelease`：PASS（`RELEASE_OK 6953186 bytes`，OUTBOX 自动归档 + latest 覆盖 + 旧版 prune）
  - 既有 lint ASM `NegativeArraySizeException` 仍出现，不声称干净 lint 通过

## 4. QA 失败（冻结时，不改 optical 断言）

末轮对照 `tests-final.log`：`84 tests completed, 4 failed`（对照子集）。

1. `GlassMaterialRegressionTest > surface alpha values stay at frozen recipe numbers`（GlassMaterialRegressionTest.kt:131）— 断言旧冻结 `.86/.90`，实现为工作候选 `.45`（Control/Capsule/Menu light）。
2. `GlassMaterialRegressionTest > expanded reader panels thicken the backdrop with the shared thick blur`（:62）— 断言 Control `.86` + blur 18dp，实现为 `.45` + 26dp。
3. `GlassMaterialRegressionTest > in-app frost plates share one recipe and dialog stays denser`（:100）— `dialog.blur(18dp) >= card.blur(26dp)` 不成立（panel 已随 Batch 1 走到 26dp，Dialog 保持 18dp）。
4. `ReaderChromeMaterialTest > semantic roles preserve regular frost hierarchy`（ReaderChromeMaterialTest.kt:34）— 断言 Control `.86` / Menu `.90`，实现为 `.45`。

首轮另有第 5 条 `GlassOpticalPolicyTest > comparison candidates stay local…`（:27）在末轮通过，不计入失败集。

Recovery 全量 `testQaUnitTest` 另发现第 5 个失败 `M1racleMotionContractTest > pdf chrome circles…`（`StringIndexOutOfBounds`，测试在已搬迁 fade 节点的旧文件里查找）：已按单一事实源对齐测试引用并复测通过，见 inventory §4。4 个 optical 失败保留不改。

处置见 `docs/R36_DIRTY_WORKTREE_INVENTORY.md` §4：**不改 optical 测试，标记 optical batch 未完成**（`.45` 尚未成为正式 authority）。

## 5. git diff --stat（冻结时）

```
 .memory/active_object.yaml                         |  10 +-
 AGENTS.md                                          |  14 +-
 README.md                                          |   5 +-
 .../com/m1racle/reader/M1racleReaderApplication.kt |   1 +
 .../main/java/com/m1racle/reader/MainActivity.kt   |  11 +
 .../com/m1racle/reader/data/anchor/AnchorCodec.kt  |  37 +-
 .../m1racle/reader/data/anchor/DocumentAnchor.kt   |   4 +-
 .../com/m1racle/reader/data/local/AppDatabase.kt   |  12 +-
 .../m1racle/reader/data/local/DocumentEntity.kt    |   7 +-
 .../reader/data/repository/DocumentRepository.kt   | 103 ++-
 .../java/com/m1racle/reader/markdown/TocItem.kt    |   4 +
 .../reader/ui/annotation/AnnotationOverlay.kt      |   6 +-
 .../reader/ui/annotation/PdfHighlightGeometry.kt   |  65 +-
 .../reader/ui/annotation/PdfHighlightLayer.kt      | 194 ++---
 .../m1racle/reader/ui/annotation/SelectionModel.kt |  42 +-
 .../reader/ui/home/ContinueReadingCard.kt          | 143 ++--
 .../com/m1racle/reader/ui/home/HomeDesignTokens.kt |  71 +-
 .../java/com/m1racle/reader/ui/home/HomeScreen.kt  | 806 ++++-----------------
 .../com/m1racle/reader/ui/home/HomeViewModel.kt    |  45 +-
 .../com/m1racle/reader/ui/hub/BookmarksScreen.kt   |  47 +-
 .../java/com/m1racle/reader/ui/hub/DockMotion.kt   |  27 +-
 .../java/com/m1racle/reader/ui/hub/GlassDock.kt    | 220 ++----
 .../com/m1racle/reader/ui/hub/LibraryScreen.kt     | 122 +---
 .../m1racle/reader/ui/material/GlassAlertDialog.kt |   1 +
 .../reader/ui/material/GlassBackdropTopology.kt    |  14 +-
 .../m1racle/reader/ui/material/GlassMaterial.kt    | 537 +++++++++-----
 .../com/m1racle/reader/ui/navigation/NavGraph.kt   |  67 +-
 .../reader/ui/pdf/AnchoredNestedPopover.kt         |  16 +-
 .../com/m1racle/reader/ui/pdf/PdfViewerScreen.kt   |  53 +-
 .../com/m1racle/reader/ui/pdf/PdfViewerViewModel.kt|  31 +-
 .../com/m1racle/reader/ui/pdf/ReaderMoreHost.kt    |  54 +-
 .../com/m1racle/reader/ui/theme/DesignSystem.kt    |  22 +
 .../main/java/com/m1racle/reader/ui/theme/Theme.kt |  19 +-
 .../main/java/com/m1racle/reader/ui/viewer/ReaderTopBarScroll.kt |  16 +-
 .../com/m1racle/reader/ui/viewer/ViewerScreen.kt   | 272 ++++---
 .../ui/viewer/components/TocDrawerContent.kt       |   3 +-
 app/src/main/res/values/strings.xml                |  20 +-
 .../reader/R31ArchitectureReleaseContractTest.kt   |   7 +-
 .../reader/R32ReleaseHardeningContractTest.kt      |   6 +-
 .../reader/R33ProductIdentityMigrationTest.kt      |   3 +-
 .../reader/data/anchor/AnchorResolverTest.kt       |   6 +-
 .../reader/data/anchor/DocumentAnchorCodecTest.kt  |   6 +-
 .../reader/data/local/ReadingModelAuthorityTest.kt |   2 +-
 .../repository/AnnotationAnchorRepositoryTest.kt   |   2 +-
 .../repository/ContentIdentityImportWiringTest.kt  |  77 ++
 .../reader/data/repository/SearchRepositoryTest.kt |   2 +-
 .../m1racle/reader/pdf/ReaderChromeGeometryTest.kt |   2 +-
 .../ui/annotation/AnnotationCreationFlowTest.kt    |   9 +-
 .../ui/annotation/AnnotationOverlayContractTest.kt |   8 +-
 .../AnnotationPerformanceContractTest.kt           |   6 +-
 .../ui/annotation/SelectionAnchorConversionTest.kt |  34 +-
 .../reader/ui/home/HomeImportEventTest.kt          |   1 +
 .../reader/ui/home/HomeLayoutContractTest.kt       | 256 ++++---
 .../reader/ui/home/HomeViewModelTest.kt            |   6 +-
 .../reader/ui/hub/BookmarkScreenTest.kt            |  52 +-
 .../reader/ui/hub/DockMotionTest.kt                |  32 +-
 .../reader/ui/hub/GlassDockUiTest.kt               |  21 +
 .../material/GlassBackdropTopologyContractTest.kt  |  79 +-
 .../ui/material/GlassInteractionContractTest.kt    |   7 +-
 .../ui/material/GlassMaterialRegressionTest.kt     | 237 +++---
 .../reader/ui/pdf/R25ReaderFoundationTest.kt       |  23 +-
 .../reader/ui/pdf/ReaderChromeMaterialTest.kt      |  48 +-
 .../reader/ui/pdf/ReaderChromeTokensContractTest.kt|  53 +-
 .../reader/ui/pdf/ReaderMoreHostUiTest.kt          |  28 +-
 .../reader/ui/reader/AnnotationManagementTest.kt   |  12 +-
 .../reader/ui/reader/PdfHighlightLoopTest.kt       |  39 +-
 .../reader/ui/reader/ReaderNoteControllerTest.kt   |   6 +-
 .../reader/ui/viewer/ReaderTopBarScrollStateTest.kt|  15 +
 docs/APK_DELIVERY.md                               |  11 +-
 docs/CURRENT_STATUS.md                             | 108 ++-
 docs/DEVICE_INSTALL_AND_ADB_TEST.md                |   4 +-
 docs/DOCK_REFERENCE_OPTIMIZATION.md                |  57 ++
 docs/PRD.md                                        |  51 +-
 74 files changed, 2175 insertions(+), 2271 deletions(-)
```

`git diff --numstat` 全量 74 行已在本会话核对（总数 2175+/2271-），不再重复粘贴；需要时以冻结时工作区为准重新生成。

## 6. adb 状态（冻结时，单次探测）

- `adb devices -l`：`List of devices attached`（空）。本次 Recovery 不循环探测、不等待设备。
- 结论：DEVICE_NOT_RUN。Release 可构建 ≠ 实机视觉通过。

## 7. 证据目录（冻结时，不删除）

- `.external_outbox/pdf-glass-flicker/`：before.mp4、menu-before.mp4、fast-before.mp4、human.mp4、contact 图、checkpoint.md 等（约 25M）
- `.external_outbox/glass-comparison/`：tests.log、tests-final.log、release.log
- `.external_outbox/r36-2/`：baseline、fixture contact sheet、compile/tests/assemble 日志
- `.external_outbox/pdf-shadow-clip/`、`pdf-controls-polish/` 等历史证据保留

## 8. 本轮未做事项（明确记录）

- 未 reset / checkout / stash / 删除证据
- 未改任何生产代码与测试断言
- 未重建 APK、未覆盖 Download、未安装设备
- 未进入 R36.3、未启动 Interaction Architecture 实现
