# 重构方案：轻量 / 稳定 / 流畅（2026-11-14）

状态：**只读审计已完成，未改任何生产代码**。本文件仅为方案，需按批次单独授权后执行。

## 0. 审计事实（全部可复现）

| 指标 | 实测值 | 取数方式 |
|---|---|---|
| 交付包 | 1.0.0 / 97，26.45 MiB（27,732,541 B） | `apk/M1racle-Reader-1.0.0-97.apk` |
| APK 未压缩构成 | assets 29.19 MiB / dex 4.96 MiB / native .so 0.06 MiB / res 0.03 MiB | `unzip -l` 分项汇总 |
| 单项最大资产 | `assets/fonts/NotoSerifCJKsc-Regular.otf` = **23.41 MiB** | 同上 |
| 该字体引用点 | **仅 1 处**：`EpubReaderFonts.kt:21` `Typeface.createFromAsset(...)` | `grep -rn NotoSerif` |
| 基线剖面 | **不存在**（无 `baseline-prof.txt`，未依赖 `androidx.profileinstaller`） | `find -name baseline-prof*` |
| 冷启动基线 | 中位 **2304 ms**（QA debug / API35 模拟器 / 空沙箱，三次 force-stop 2304/2352/1987） | CI run 43564358416 `baseline.json` |
| 源码规模 | app/main 45,921 行 212 文件；core/database 20,249；core/pdf 14,016；pdf-tool 8,009 | `find + wc` |
| 测试规模 | QA1495 / Release1310 单测，core/pdf 465；lint 98 warnings + 6 hints | `docs/CURRENT_STATUS.md` |
| 单文件最大 | `PdfViewerScreen.kt` 3653 行（13 Composable / 32 fun / 376 val-var） | `wc + grep` |
| 次大 | `PdfViewerViewModel.kt` 2888 行（83 fun / 322 字段）、`ContinuousDocumentViewport.kt` 1749、`ViewerScreen.kt` 1923、`DocumentRepository.kt` 1321 | 同上 |
| 测试对热点文件的绑定 | Screen 22 / VM 10 / Viewport 10 / ViewerScreen 23 / Repository 29 个测试文件引用 | `grep -rl` |

**结论先行**：这个项目的“重”有 88% 不在代码里，在一个字体文件里。26.45 MiB 的包里 23.41 MiB 是同一个 CJK 衬线字体，而它只服务 EPUB 的 Serif 选项一行调用。反过来，“流畅”缺的不是优化技巧，是连基线剖面都还没有；“稳定”缺的不是重构勇气，是把三个 2000–3600 行的巨型文件拆开，且现有 1495 条测试正好是拆分的安全网。

## 1. 三条主线的真实瓶颈

### 轻量
- 字体资产 23.41 MiB 是唯一压倒性项，dex 4.96 MiB 已被 R8 full mode 压过（`isMinifyEnabled=true`、`isShrinkResources=true`）。
- 次要项：pdfbox 的 `fontbox/cmap` 全套 CJK 编码表（UniCNS/Adobe-Japan1/Adobe-GB1 等）随包分发，实际只用到其中一部分。
- 第三项：`material-icons-extended` 全量依赖，R8 会裁剪，但拖构建时间与 dex 方法数；对 APK 影响小，属“构建轻量”而非“交付轻量”。

### 稳定
- 三个巨型文件是唯一的结构性风险：`PdfViewerScreen` 3653 行、`PdfViewerViewModel` 2888 行、`ContinuousDocumentViewport` 1749 行。任何改动都要在 3000 行上下文中判断影响面。
- lint 98 warnings 长期挂账，没有分组清零口径。
- 真机验收长期 pending（物理手机未连接），稳定性只能靠模拟器 + 单测。

### 流畅
- 无 Baseline Profile：Android 每条冷启动路径都要现场做 JIT/AOT 决策。这是启动与首帧最便宜的一档收益。
- `benchmark` 模块已有 Macrobenchmark 骨架（`StartupTimingMetric` + `FrameTimingMetric`），但脚本要求真机 + 手工导入文档，尚不能作为门禁。
- 冷启动只有 QA debug 数字（2304 ms），**Release 包在模拟器/真机上的数字一次都没有测过**。

## 2. 分批方案

每批独立授权、独立门禁、独立回滚。未获授权不进入下一批。

### B0 度量基线固化（零风险，只读 + 一次性脚本）
- 产出 `output/refactor-20261114/metrics-baseline.json`：APK 分项体积、dex 方法数、字体/资源清单、Release 冷启动在三档编译模式（None/Speed/SpeedProfile）下的模拟器数字、lint 分类计数。
- 门禁：同一 commit 连续两次测量偏差 < 5%，否则口径不成立、不进入 B1。
- 回滚：只新增 `output/` 文件，不碰源码。

### B1 字体瘦身（P0 轻量，预期 −18 ~ −23 MiB）
两条路径，先小后大：
1. **系统字体替代**：`FontFamily.Serif` 或 `Typeface.create("serif", NORMAL)` 取代 assets 字体。minSdk31 的 AOSP 自带 Noto Serif CJK，覆盖中日韩。
2. **保底子集化**：若真机/模拟器实测系统 serif 覆盖不足（缺字、字形差异不可接受），则把字体子集化到 GB2312 + 常用标点 + 日文假名，目标 ≤ 4 MiB。

- 门禁（缺一不可）：
  - 字形覆盖断言：中/日/韩样本 + 用户样张逐字比对，缺字率 0；
  - 视觉对照：EPUB Serif 页面改动前后截图逐张比对，允许字形差异但必须显式列出；
  - APK：26.45 MiB → **≤ 9 MiB**（路径 1）或 **≤ 12 MiB**（路径 2）；
  - QA1495 / Release1310 单测零失败、lint 不新增；
  - 真机未连接则标注 `device_verified: pending`，不得写“已验收”。
- 回滚：单个 assets 文件 + 一行调用，逆操作成本最低的一批。

### B2 启动与流畅（Baseline Profile）
- 接入 `androidx.profileinstaller`，用 Macrobenchmark 的 `BaselineProfileRule` 生成 `baseline-prof.txt`，覆盖冷启动 + 首页 + PDF 打开首帧。
- 门禁：`StartupTimingMetric` 在模拟器上 None vs SpeedProfile 对比，报告真实数字；无提升就如实记录并撤掉。
- 注意：profile 生成依赖设备/模拟器，CI 的 `reader-qa-emulator` 可复用，但**不得**把模拟器数字写成真机结论。

### B3 巨型文件拆分（稳定，分 4 个子批）
- B3.1 `PdfViewerViewModel`（2888 行）按职责拆为 selection / scroll / chrome / export 四块状态持有者，对外 API 不变；
- B3.2 `PdfViewerScreen`（3653 行）按区域拆 chrome / selection overlay / toolbar 子组件；
- B3.3 `ContinuousDocumentViewport`（1749 行）拆测量与绘制；
- B3.4 `ViewerScreen` + `DocumentRepository` 同法。
- 硬约束：**纯搬移，不改行为**。每个子批只允许移动代码与调整可见性，禁止顺手改逻辑、改命名、改依赖。任何行为变更必须单独立项。
- 门禁：每子批后 1495/1310 单测零失败、lint 不新增、`git diff --stat` 中删除行 ≈ 新增行（搬移特征），并附拆分前后公开符号清单 diff 为空。

### B4 依赖与告警收口
- `material-icons-extended` 替换为按需图标集；pdfbox cmap 裁剪；lint 98 warnings 按规则分组清零（每组一批，不合并）。
- 门禁：APK 不增大、构建时间不退化、lint 计数下降且无新增。

### B5 迭代速度（2026-11-14 实测后追加）
实测数据见本文件第 6 节。结论：**测试分层不需要新建机制，现有的两个 variant 已经是分层**，问题只是默认跑了贵的那条。

| 循环 | 命令 | 实测 |
|---|---|---|
| 改纯逻辑 | `:app:testReleaseUnitTest` | 1310 用例 / 65.4 s |
| 改 UI / 手势 / 玻璃 | `:app:testQaUnitTest` | 1495 用例 / 126.3 s |
| 单类定向 | `--tests "com.m1racle.reader.<pkg>.*"` | 秒级 |
| 提交前 | QA + Release + core/pdf | ~200 s |

- 待做（各 1 项，独立成批）：`zipPackageImporterTest` 28.58 s / 14 用例（大字符串 repeat 构包，可改流式写入）；`PdfSelectionReferenceRenderTest` 11.41 s / **1 用例**（单测成本异常）。
- 可加 3 行别名任务 `fastTest → testReleaseUnitTest`，让快循环不用记 variant 名。
- 注意：测试只占迭代时间的一部分，单文件改动的编译 + 打包要 76 s，配置与启动另计。B0 的 build 配置收敛优先级高于 B5。

## 3. 明确不做的事

- 不做全量重写、不换渲染栈、不迁 PDFium（AGENTS.md 明确禁止）。
- 不在重构批次里顺手升级 Compose BOM / Room / AGP。
- 不为“将来可能的多格式”预留抽象。
- 真机未连接期间，任何“更流畅”“更省电”的结论只能标 `emulator_verified`，不准写成 `device_verified`。

## 4. 风险与前提

| 风险 | 影响 | 缓解 |
|---|---|---|
| OEM 系统 serif 缺字或字形差异 | B1 路径 1 失败 | 预先准备子集化路径 2，字体文件保留在 quarantine 不删原件 |
| 巨型文件拆分引入隐性行为变更 | 稳定性回退 | 纯搬移约束 + 测试零失败 + 公开符号 diff 为空 |
| 无真机 | 流畅/触控结论不可交付 | 明确 pending，不用模拟器数字替代 |
| 与当前 active object（PDF 选择手柄）冲突 | 改动打架 | B1–B2 不触碰 PDF 选择代码路径；B3 待 active object 关闭后启动 |

## 5. 建议执行顺序

`B0 → B1 → B2 → （B3 待 PDF 选择 active object 关闭）→ B4`

B1 是唯一能在一个批次内把包体砍掉三分之二、且回滚成本几乎为零的动作，建议先做。

## 6. 本机实测数据（2026-11-14，同一次会话）

### 6.1 构建

参数（命令行临时覆盖，未改仓库 `gradle.properties`）：`-Xmx1536m -XX:MaxMetaspaceSize=640m -XX:ReservedCodeCacheSize=384m -XX:+UseG1GC`、`workers.max=4`、`parallel=true`、`caching=true`。

| 场景 | 耗时 | 说明 |
|---|---|---|
| 无改动 `:app:assembleQa` | 28 s | 98 task，2 executed |
| 新增 1 个 .kt 后 | 1 m 16 s | 6 executed |
| 修复态全量 | 2 m 05 s | 16 executed |

剖面（全量）：startup 10.61 s / settings 1.40 s / load 0.22 s / config 6.20 s / **artifact transforms 81.85 s** / task execution 累计 139 s。

- 现行提交配置（`-Xmx1g` + SerialGC + `workers=1`）在 181 s 时 **native OOM 崩溃**；放宽到 2.5 GB 时 47 s **mmap 失败**。两次 `hs_err` 已存 `output/crash-logs-20261114/`。
- 系统制约：物理 13.8 GB、页面文件固定 8 GB、commit 上限 21.8 GB；余量 1.7 GB 时必崩，4.1 GB 时可过。
- `--configuration-cache` **不可用**：`:app:assembleQa` 与 `:app:verifyMarkdownEditorBundle` 为脚本内 `DefaultTask`，无法序列化脚本引用。需先改造这两个任务。

### 6.2 测试（97 版最终快照 `output/pdf-selection-acrobat-20261113/unit-results/`）

| 套件 | 类 | 用例 | 耗时 | 失败 |
|---|---|---|---|---|
| app QA | 216 | 1495 | 126.3 s | 0 |
| app Release | 184 | 1310 | 65.4 s | 0 |
| core/pdf | 41 | 465 | 6.7 s | 0 |
| core/database | 15 | 48 | 1.6 s | 0 |
| pdf-tool | 9 | 51 | 0.3 s | 0 |

- 耗时高度偏斜：中位类 0.03 s；最慢 `ZipPackageImporterTest` 29.18 s（占 22.3%），其后 `PdfSelectionReferenceRenderTest` 11.41 s（仅 1 个用例）、`PillSegmentedControlTest` 11.41 s。
- QA 与 Release 的 61 s 差值 ≈ `app/src/testQa` 的 186 个用例（32 个 Robolectric + Compose UI 类）。**`app/src/test` 里的 31 个 Robolectric 类只占 24.9 s / 223 用例**，不是主要成本。
- 玻璃主题是最大脆弱面：`ui/material/*Glass*` 7 文件 1523 行 67 用例，另加 `GlassDockPixelsInstrumentedTest` 1341 行 / 35 用例。
- `app/src/androidTest` 180 用例需设备，日常不可回归。

## 7. 冗余清点（2026-11-14，只读扫描）

用户要求“把没用的都去了、避免重复”。扫描结论与预期相反：**这个仓库几乎没有垃圾文件，冗余是结构性的。**

### 7.1 扫过且确认干净的部分

| 检查项 | 结果 |
|---|---|
| 源文件完全重复（md5 相同） | 0 组 |
| 同名文件跨包（不同源集覆盖） | 3 组：`MainActivity.kt` / `QaGlass.kt` / `QaExperimentScreens.kt`，均属 main 与 qa/production 源集覆盖，非重复 |
| `res/` 未引用资源 | 0（drawable 2 个、values 3 个，全部被引用） |
| `assets/` 未引用资产 | 0（`sample-image.png` 被 `docs/sample-large.md`、`docs/sample-stress.md` 的 `asset://` 夹具引用） |
| 主代码 TODO / FIXME / XXX / HACK / deprecated 标记 | **全部 0** |
| 跨文件同名 `@Test` | 0（8 个 Glass 测试文件 68 用例，无重名） |
| `repro` 包 | 3 文件 169 行，是已修复缺陷的回归守卫，非垃圾 |

### 7.2 真正的结构性冗余（四处）

| 编号 | 内容 | 证据 | 代价 | 风险 |
|---|---|---|---|---|
| R1 | CI 把 1309 个共享用例在 QA 与 Release 两个变体各跑一遍 | `reader-verify.yml`：`testQaUnitTest :app:testReleaseUnitTest` 同一行 | 约 61 s + 双份报告 | 低（需先快进到 master 才有该文件） |
| R2 | `material-icons-extended` 仅使用 58 个图标 import，artifact 含约 2000 个 | `grep '^import androidx.compose.material.icons'` 去重 58 条 | 全量构建 artifact transforms 81.85 s 的主要来源之一 | 中（需 vendor 58 条 path，视觉需比对） |
| R3 | `DETAIL_TILE_OVERLAY_ENABLED` / `_BASE_LAYER_ENABLED` / `_DIAGNOSTICS_ENABLED` / `_OVERLAY_ALPHA` 四个 build 开关 | 仅在 `ContinuousDocumentViewport.kt:164-167` 被读取；来源提交 `4241b3b`（实验已接受） | 4 个 provider + 4 个 buildConfigField，违反 AGENTS「不要多此一举的配置层」 | 中（改动影响 debug 渲染路径） |
| R4 | `docs/` 跟踪 668 个文件，多为一次性阶段报告 | `git ls-files docs` 计数；AGENTS.md 自述「most are one-off stage reports」 | 检索噪音，不影响构建 | 高（属历史证据，需用户裁决） |

### 7.3 处置建议

R1 先进（纯 CI，可回滚）；R2 与 B4 合并；R3 单独一批并附 debug 渲染截图对照；R4 不擅自删，按 AGENTS 的 `docs/history/` 归档约定处理。
