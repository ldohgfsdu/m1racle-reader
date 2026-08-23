# CURRENT_STATE

**Updated:** 2026-08-23 深夜（Batch B 会话）

本文件只描述**当前**工程状态；过程细节以 `docs/` 已入库报告与 git 历史为准。

## 产品现状（code = source of truth）

墨阅 MarkLeaf：Android 本地优先文档阅读器（Markdown + PDF，无 INTERNET 权限）。

- **导入链（A3 收敛后单一事实源）**：ProviderMetadataSource（唯一采集器）→ ExternalUriMetadata.resolve（唯一解析）→ DocumentType.isAllowedImport（唯一接受权威，含 text/* fallback 封闭拒绝表）→ 200MiB source limit（双入口统一：hint 预检 + 无 hint 有界排水/copy-loop）→ Path A importDocument / Path B 两阶段 prepare-commit。zip VIEW/SEND 经 ZipPackageImporter 提取为 MARKDOWN_PACKAGE（B-0 修复）；zip 解压三限独立。
- **Markdown 阅读**：自研块解析器、TOC 锚点、文档内搜索+高亮、全文读取（32MiB）、安全相对路径图片、阅读位置恢复、字号/行高/边距。
- **PDF 阅读**：PdfRenderer 分块瓦片 + backdrop 液态玻璃 chrome；连续/单页、缩放手势、教材页码绑定、目录/缩略图/章节导出；连续视口 display mirror 已加 retention policy（B-1）。

## 工程状态

- HEAD `878209f`（Batch B-1），工作树干净。
- **A3 阶段全部完成**（详见 docs/A3_FINALIZATION_REPORT.md）：仓库卫生 → release hygiene → dead code → 导入基础设施收敛 → 导入策略三批（基线/统一/大小边界）→ 最终化审计。
- **Batch B 进行中**：
  - B-0 ✅ zip VIEW/SEND 契约修复（ce31f00，红基线 3 红→绿）
  - B-1 ✅ displayedBitmaps retention（878209f；策略纯函数 11 测；设备内存曲线 pending）
  - B-2 ⏸ Liquid Glass GPU profiling —— device_pending
  - B-3 ⏸ 设备证据闭环（导出取消人工验证/FrameTimeline/内存曲线/GPU 采样）—— device_pending
  - B-4 部分 ✅ .memory 三件套本次重写；zip identity model 与 ThumbnailLoader 结论见 BATCH_B_FINAL_REPORT.md（报告态，未经批准不改）

## 验证基线（automated_verified）

- `:app:testQaUnitTest` = **1145 tests / 0 failures**（JDK17 必需；JAVA_HOME=25 会失败）
- `:app:assembleRelease` PASS → `.external_outbox/markleaf-release-latest.apk`（11.2MB）→ 强制交付 `/storage/emulated/0/Download/markleaf-release-latest.apk`
- androidTest 源集编译通过（`:app:compileQaAndroidTestKotlin`）
- 隐私门禁：manifest 零权限，ReleaseHygieneHostTest 强制

## Pending（device_pending / 待决策）

- adb 离线：B-2 GPU profiling 全套、B-3 四项证据、Home/PDF 视觉校准、50 页连读内存曲线实测（B-1 验收的设备侧一半）
- 待批准决策：跨入口 zip 身份模型统一（FAB uri-id vs Path B c:sha，重复行场景）；SourceTooLarge 专属 UI 文案（UI 冻结中）
- 独立跟踪不变：docs/ISSUE_NETDISK_INTENT_COMPATIBILITY.md、docs/ISSUE_TOC_OUTLINE_UX_REDESIGN.md

## 历史索引

| 主题 | 文档 |
|---|---|
| A3 全程台账/导入链终审/Batch B 提案 | docs/A3_FINALIZATION_REPORT.md |
| Batch B 运行时硬化 | docs/BATCH_B_FINAL_REPORT.md |
| Home 布局迭代 | docs/HOME_*.md |
| PDF chrome R11–R26 | docs/R11..R26_*.md |
| 发布就绪快照（07-21 历史） | docs/RELEASE_READINESS.md |

## Active object

见 `.memory/active_object.yaml`（BATCH_B_RUNTIME_HARDENING）。
