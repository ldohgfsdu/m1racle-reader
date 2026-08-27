# CURRENT_STATE

**Updated:** 2026-08-27（R32.6 production source commit complete）

本文件只描述**当前**工程状态；过程细节以 `docs/` 已入库报告与 git 历史为准。

## 当前 Session Objective

- **R32.6 Production Commit Preparation（PASS / source_immutable）**：完整审查并纳入 R23–R32
  功能代码、Room v8–v12 schema/migration、50 个测试路径、公开文档与 release evidence；最终生产
  source set 共 155 个路径。强制重跑 `:app:testQaUnitTest` 为 1350/0/0/0，
  `:app:compileQaAndroidTestKotlin` PASS，提交后 worktree clean。未使用 signer、未运行
  `assembleRelease`、未创建 `v1.0.0` tag。报告见
  `docs/R32_6_PRODUCTION_COMMIT_PREPARATION_REPORT.md`。

- **R32.5 Production Release Gate（NO-GO / blocked）**：版本 `1.0.0 (23)` 与本轮强制重跑的
  `:app:testQaUnitTest` 通过（1350/0/0/0），但工作树仍有 52 个 tracked 变更和 70 个 untracked
  条目，HEAD `76ebea5` 不是包含 R23–R32.4 的 production commit；正式 signer 四项环境注入全部
  缺失。因此未构建/归档 production APK，也未创建 `v1.0.0` tag。Highlight fixture 尝试期间设备
  掉线，标记 `NOT_VERIFIED_DEVICE_DISCONNECTED`。报告见
  `docs/R32_5_PRODUCTION_RELEASE_GATE_REPORT.md`。

- **R32.4 Glass Backdrop RC Re-Acceptance（PASS / device_verified）**：R32.3 RC 已在原受影响的
  Xiaomi `25098PN5AC` Android 16 原位安装并回拉，设备 `base.apk` 与 outbox SHA-256 均为
  `387c0021…15f`。5/5 force-stop 冷启动均到达完整 Home，MarkLeaf crash buffer/fatal/native signal
  为 0，tombstone 无增量；R32.2 RenderThread blocker 已清除。Home/Library/Bookmarks/Settings Dock、
  Reader Chrome/Capsule/More/SettingsCard/Glass dialog、Annotation Sheet、Search 均可达。Highlight
  overlay 因文档无既有高亮 fixture 标记 `NOT_VERIFIED_NO_FIXTURE`。报告见
  `docs/R32_4_GLASS_REACCEPTANCE_REPORT.md`。

- **R32.3 Glass Backdrop Topology Fix（implemented / automated_verified /
  release_build_verified / device_verified）**：新增唯一 `GlassBackdropSource` ownership boundary；capture
  subtree 屏蔽自身 `LocalGlassBackdropHost`，Home/Hub Dock 与 Reader Chrome 均作为 capture source 的
  sibling overlay，消除 descendant 反向采样 ancestor 的 RenderNode cycle。`GlassMaterial.kt` 参数零改动，
  SHA-256 仍为 `591b03e0…b8ea`。报告见 `docs/R32_3_GLASS_BACKDROP_TOPOLOGY_REPORT.md`。
- 新增 6 项 topology 根因契约；定向 25 项 PASS；完整 `:app:testQaUnitTest assembleRelease` 在 JDK17
  下 PASS（1350 tests / 0 failures/errors/skipped）。新 verification APK 11,660,123 bytes，SHA-256
  `387c0021be6a68a0156fd23481f41614e0c7583d20390f3b12eae0a1fbbc215f`，outbox/Download 一致；一次性
  signer 已销毁。R32.4 已在原受影响 Xiaomi Android 16 设备完成 5/5 冷启动复验，未出现新的
  RenderThread native crash 或 tombstone；公开发布仍须由 production owner 完成正式签名、tag 与归档门禁。

- **R32.2 Cold Start Native Crash Investigation（调查完成 / 未修复 / release_blocked）**：冻结
  RC 在 Xiaomi Android 16 的完整 `tombstone_06` 显示 RenderThread 512 帧栈溢出，510 帧为
  `RenderNode::prepareTreeImpl` / `SkiaDisplayList::prepareListAndChildren` 经
  `MiBackgroundBlurBlend` 的递归。最小无条件触发拓扑已定位为 Home
  `layerBackdrop(rootBackdrop)` 内的 `GlassDock` 默认反向采样同一 `rootBackdrop`，形成循环
  render graph。报告见 `docs/R32_2_NATIVE_CRASH_INVESTIGATION.md`。
- 冻结 release binary 不含 Glass/Dock/Home-card 诊断开关；依任务禁令未改源码、未 patch APK、
  未伪造 a–d ablation build。系统 `disable_window_blurs=1` 对照仍崩溃，已恢复 `null`。R32.1+
  R32.2 合计 7/7 崩溃；RC hash、安装包、app data 均保持不变。公开发布继续 **NO-GO**。

- **R32.1 Release Candidate Device Acceptance（FAIL / release_blocked）**：RC 1.0.0 (23) 已在
  Xiaomi/POCO `25098PN5AC` Android 16 安装并回拉验签，APK hash/signer 与 outbox 一致；但有效
  冷启动 5/5 在 RenderThread 发生 native SIGSEGV/疑似栈溢出，栈进入 Xiaomi
  `MiBackgroundBlurBlend` 与递归 render-tree preparation。Home 未出现，Reader/Annotation/Search/
  performance 全部 `BLOCKED`。报告见 `docs/R32_1_DEVICE_ACCEPTANCE_REPORT.md`。
- 验收阶段未修复代码；系统 `disable_window_blurs` 诊断同样崩溃并已恢复原值。公开发布结论：
  **NO-GO**，必须另立诊断/修复任务并对新 RC 重跑完整矩阵。

- **R32 Release Hardening（implemented / host_verified / production_owner_pending）**：正式 release
  仅接受四个环境变量完整注入的独立 signer，缺失/部分注入/debug identity 全部 fail closed；下一公开
  identity 为 `1.0.0 / versionCode 23`；Room v12 为首个公开数据库基线。报告见
  `docs/R32_RELEASE_HARDENING_REPORT.md`，发布权威清单见根目录 `RELEASE_CHECKLIST.md`。
- 两次强制 release 构建均成功，238/238 payload entries 可重复；最终 signed APK 因 v2 signing block
  随机化不具备逐字节可重复性。R32 最终验证 APK 使用已销毁的一次性 signer，不是可发布生产包。

- **R31 Architecture Audit & Release Preparation（implemented / automated_verified /
  release_build_verified / device_not_run）**：架构、Room、Repository、测试/文档、R8/资源审计及
  主机构建门禁完成；报告见 `docs/R31_ARCHITECTURE_AUDIT_REPORT.md`。未修改 GlassMaterial、Anchor
  协议或 Reader 核心行为，未进入设备验收。
- R31 当时记录的版本/签名工程阻塞已由 R32 建立正式策略；真实 signer 所有权、immutable
  source/tag 与 Backdrop/Compose/Kotlin-AGP 工具链债务仍是发布前事项。

- **R30 Search Experience Integration（implemented / automated_verified / device_not_run）**：
  Home/Library 已提供本地搜索入口；结果展示 Document/Highlight/Note 的当前文档标题、来源、
  摘要和位置，并通过 canonical anchor 进入既有 Markdown/PDF Reader 定位 authority。报告见
  `docs/R30_SEARCH_EXPERIENCE_REPORT.md`。
- Document 导入/解析/删除与 Highlight/Note 创建、更新、删除均在 Repository 权威写入边界同步
  派生索引；UI 只调用 `SearchRepository`，不直访 DAO。
- 未修改 Anchor 协议或 GlassMaterial；历史数据回填只完成分批、幂等、可恢复方案，未启动后台
  全量任务；未引入 AI、云同步、FTS 或向量数据库。

## 产品现状（code = source of truth）

墨阅 MarkLeaf：Android 本地优先文档阅读器（Markdown + PDF，无 INTERNET 权限）。

- **导入链（A3 收敛后单一事实源）**：ProviderMetadataSource（唯一采集器）→ ExternalUriMetadata.resolve（唯一解析）→ DocumentType.isAllowedImport（唯一接受权威，含 text/* fallback 封闭拒绝表）→ 200MiB source limit（双入口统一：hint 预检 + 无 hint 有界排水/copy-loop）→ Path A importDocument / Path B 两阶段 prepare-commit。zip VIEW/SEND 经 ZipPackageImporter 提取为 MARKDOWN_PACKAGE（B-0 修复）；zip 解压三限独立。
- **Markdown 阅读**：自研块解析器、TOC 锚点、文档内搜索+高亮、全文读取（32MiB）、安全相对路径图片、阅读位置恢复、字号/行高/边距。
- **PDF 阅读**：PdfRenderer 分块瓦片 + backdrop 液态玻璃 chrome；连续/单页、缩放手势、教材页码绑定、目录/缩略图/章节导出；连续视口 display mirror 已加 retention policy（B-1）。

## 工程状态

- HEAD `76ebea5`；R23–R30、Glass Material Engine 与此前 Reader/Home 变更仍在同一未提交工作树。
- **R31 变更面**：5 个架构/release 契约测试；删除 2 个零引用兼容常量；release 移除 Log.d/v；
  release 产物每 variant 保留 5 份；README/PRD/CHANGELOG/APK delivery 一致性修订。无用户功能、
  无新依赖。
- **R32 变更面**：5 个 release hardening 契约；环境注入签名及负向门禁；版本 SSOT；Room v12
  首发策略；release checklist 与报告。未修改任何冻结功能/架构，无新依赖。
- **A3 阶段全部完成**（详见 docs/A3_FINALIZATION_REPORT.md）：仓库卫生 → release hygiene → dead code → 导入基础设施收敛 → 导入策略三批（基线/统一/大小边界）→ 最终化审计。
- **Batch B 进行中**：
  - B-0 ✅ zip VIEW/SEND 契约修复（ce31f00，红基线 3 红→绿）
  - B-1 ✅ displayedBitmaps retention（878209f；策略纯函数 11 测；设备内存曲线 pending）
  - B-2 ⏸ Liquid Glass GPU profiling —— device_pending
  - B-3 ⏸ 设备证据闭环（导出取消人工验证/FrameTimeline/内存曲线/GPU 采样）—— device_pending
  - B-4 部分 ✅ .memory 三件套本次重写；zip identity model 与 ThumbnailLoader 结论见 BATCH_B_FINAL_REPORT.md（报告态，未经批准不改）

## 验证基线（automated_verified）

- `:app:testQaUnitTest` = **1344 tests / 0 failures / 0 errors / 0 skipped**（JDK17）
- `:app:compileQaAndroidTestKotlin` PASS；`:app:lintRelease` PASS（0 errors / 167 warnings；Compose
  runtime 不兼容 lint registry 排除项见 R31 报告）。
- 注入一次性非 debug signer 后，`:app:testQaUnitTest assembleRelease` PASS；最终验证 APK
  11,660,123 bytes，SHA-256 `2cb388d84d64a7f477cff0bcd42b14b8201504a5bd788e4bef9b519ca4b92b31`，
  outbox/Download 一致，APK v2 验签通过。一次性私钥已删除，**禁止公开发布该验证包**。
- 两次 `assembleRelease --rerun-tasks` PASS；payload 238/238 entries identical；signed APK hash
  不一致且仅 signing block 有差异。无设备安装、启动或升级声明。
- 隐私门禁：无 `INTERNET`/网络能力；仅有 Android 自动生成的包内 dynamic-receiver 权限，
  ReleaseHygieneHostTest 持续约束

## Pending（待决策/待执行）

- R30 未执行历史索引全量回填；未来若启动，须按报告中的 document 批次/游标设计另立任务。
  本轮未执行设备验收且不作设备声明。
- R32 生产所有者 pending：在仓库外建立/备份真实 signer，归档证书 digest；将当前 R23-R32 dirty
  worktree 收敛成 reviewed immutable commit；以真实 signer 构建唯一 canonical artifact、归档 hash、
  创建不可移动 `v1.0.0` tag 并取得发布批准。
- 工具链 pending：协调升级 Kotlin/AGP/KSP/compileSdk/Compose/Backdrop 后移除 metadata/AAR/lint
  workaround；未来 public schema 变更必须从 v12 开始新增真实 migration/schema evidence。
- **R22 交互真机验收**（冒烟已过，交互项中断于设备掉线）：胶囊 spring morph/marquee、
  Menu 锚点出入、pinch/跳转期 chrome 锁定、横向快翻 H14/H15 取证（清单见报告）。
  注：无线调试随息屏断开 —— 验收期间请保持亮屏或开启「充电时屏幕不休眠」。
- **R22.3 zoom-leak 根因取证**：QA 包 + H14/H15 日志 + 快速连翻复现；可疑点已记录（同手势二次 settle 早退守卫）。
- Glass Material Engine：保持冻结与 PASS；本轮未触碰。
- Batch B：B-2 GPU profiling 全套、B-3 四项证据、50 页连读内存曲线实测仍未执行。
- 待批准决策：跨入口 zip 身份模型统一；独立跟踪：docs/ISSUE_NETDISK_INTENT_COMPATIBILITY.md、docs/ISSUE_TOC_OUTLINE_UX_REDESIGN.md

## 历史索引

| 主题 | 文档 |
|---|---|
| A3 全程台账/导入链终审/Batch B 提案 | docs/A3_FINALIZATION_REPORT.md |
| Batch B 运行时硬化 | docs/BATCH_B_FINAL_REPORT.md |
| Home 布局迭代 | docs/HOME_*.md |
| PDF chrome R11–R26 | docs/R11..R26_*.md |
| R27 轻量 Note 闭环 | docs/R27_NOTE_FOUNDATION_REPORT.md |
| R28 Annotation System 收口审计 | docs/R28_ANNOTATION_AUDIT_REPORT.md |
| R29 Local Search Foundation | docs/R29_SEARCH_FOUNDATION_REPORT.md |
| R30 Search Experience Integration | docs/R30_SEARCH_EXPERIENCE_REPORT.md |
| R31 架构审计与 Release 准备 | docs/R31_ARCHITECTURE_AUDIT_REPORT.md |
| R32 公开发布工程硬化 | docs/R32_RELEASE_HARDENING_REPORT.md；RELEASE_CHECKLIST.md |
| R32.1 最终 RC 真机验收 | docs/R32_1_DEVICE_ACCEPTANCE_REPORT.md |
| R32.5 生产发布最终门禁 | docs/R32_5_PRODUCTION_RELEASE_GATE_REPORT.md |
| R32.6 唯一生产提交准备 | docs/R32_6_PRODUCTION_COMMIT_PREPARATION_REPORT.md |
| 发布就绪快照（07-21 历史） | docs/RELEASE_READINESS.md |

## Active object

见 `.memory/active_object.yaml`（R32_4_RC_GLASS_BACKDROP_REACCEPTANCE）。
