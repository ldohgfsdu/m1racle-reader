# CURRENT_STATE

**Updated:** 2026-08-23

本文件只描述**当前**工程状态；过程细节以 `docs/` 已入库报告与 git 历史为准（单一事实源），不再在此重复。

## 产品现状（code = source of truth）

墨阅 MarkLeaf：Android 本地优先文档阅读器（无 INTERNET 权限、无账号/广告/云同步）。

- **Markdown 阅读**：自研块解析器（标题/列表/引用/代码块+轻量高亮/表格/图片）、TOC 锚点跳转、文档内搜索+高亮、全文读取（32MiB 上限，无截断预览态）、安全相对路径图片、阅读位置恢复、字号/行高/边距设置。
- **PDF 阅读**：Android PdfRenderer 渲染 + PDFBox（元数据/大纲）+ backdrop 液态玻璃 chrome；连续/单页模式、缩放手势、教材页码绑定、目录/缩略图/章节导出（ZIP_PACKAGE / PDF_ANNOTATED）；沉浸式系统栏。
- **文档获取**：SAF 导入、外部 VIEW/SEND 打开（md/pdf/x-pdf/octet-stream/zip，pending/ack 导航防旋转丢失）、`.zip` Markdown 包导入（Zip-Slip/炸弹防护）。
- **数据**：Room（schema v6）最近阅读 + PDF 视口会话字段；删除记录清理 App 自管副本（`files/library`、`files/packages`），永不触碰用户源文件。

## 工程状态 2026-08-23

- HEAD `b840438`；工作树干净。审计整改两阶段已完成：
  - **Phase 0（A0 baseline，08-08）**：R10 后全部工作分类入库（commits d09e04a..abb31f4）。
  - **Phase 1（F1/F2/F3，08-23）**：15 天未提交工作按功能边界拆成 12 个显式路径提交（398536a..374df83，树与快照逐字节一致校验通过）；qa 变体补 `ui-test-manifest`，`:app:testQaUnitTest` 恢复为绿色默认测试入口；ReaderMoreHostUiTest 空态断言对齐现行设计。
  - **Phase 2（F4/F5，08-23）**：PRD/README/AGENTS.md 口径同步为「本地文档阅读器」现状。
  - **Phase 3（A2-P0，08-23，f15ed94..4ed0ade）**：P0-1 tile LRU 淘汰恢复（pinned 收窄为管线键 + 淘汰镜像清除 overlay 发布物）；P0-2 导出取消语义（zip/copy 逐块检查点、CE 一律重抛、部分 SAF 输出尽力删除）。性能只读审查矩阵见会话记录；其余 P1 未动。

## 验证基线（automated_verified）

- `:app:testQaUnitTest`：1082 tests / 0 failures（含 PdfTileLruEvictionTest、TileOverlayPurgeTest、ExportCancellationIoTest；JDK17 必需；默认 JAVA_HOME=25 会构建失败）。
- `:app:assembleRelease`：PASS → `.external_outbox/markleaf-release-latest.apk`（~11.5MB，minify+shrink，versionCode=21）→ 强制交付 `/storage/emulated/0/Download/markleaf-release-latest.apk`。
- 隐私门禁：manifest 零权限（含无 INTERNET），`ReleaseHygieneHostTest` 强制；QA 组件仅存在于 `.qa` 沙箱变体。

## Pending（device_pending / 待决策）

- adb 离线：Home 对齐+液态浮控视觉、PDF 沉浸栏实机行为、滚动帧率实测 —— 全部 device_pending。
- 审计批次未消化项（见 `.external_outbox/AUDIT_FULL_20260808_REVIEWED.md` §4.2）：A2 lifecycle → A3 chrome → A4 consolidation → B 性能（需设备 profiling）→ C 发布收口（keystore/体积 gate/benchmark 重建）。

## 历史索引（细节见对应已入库文档）

| 主题 | 文档 |
|---|---|
| Home 布局/密度/液态浮控迭代 | docs/HOME_*.md（6 份） |
| PDF chrome R11–R26 | docs/R11..R26_*.md |
| PDF 手势/几何 R7 系列 | docs/R7_*.md |
| Markdown 搜索/锚点/表格 R8–R9 | docs/R8_*.md, docs/MARKLEAF_MARKDOWN_*.md |
| 外部打开会话 | docs/MARKLEAF_EXTERNAL_OPEN_*.md |
| 发布就绪快照（07-21，历史） | docs/RELEASE_READINESS.md |
| 全面工程审计底稿 | .external_outbox/AUDIT_FULL_20260808*.md |

## Active object

见 `.memory/active_object.yaml`（HOME_ALIGN_UNIFIED_LIQUID_CONTROLS：implemented_automated_verified_device_pending，代码已随 F1 批次入库）。
