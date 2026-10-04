# 文档索引

`docs/` 下有 200+ 篇文档，绝大多数是**某一阶段当时的证据**。本文只回答一个问题：现在该读哪一篇。

## 唯一事实源

| 主题 | 文件 | 说明 |
|---|---|---|
| 当前状态 | [CURRENT_STATUS.md](CURRENT_STATUS.md) | 版本、验证基线、已交付产物、设备验收状态、未决项 |
| 会话对象 | [.memory/active_object.yaml](../.memory/active_object.yaml) | 当前 active object 的 id / status / scope |
| 产品范围 | [PRD.md](PRD.md) | 做什么、不做什么（Markdown/PDF/EPUB 有限阅读、PDF 正文有限编辑） |
| 界面设计规范 | [../design.md](../design.md) | 页面职责、主题排版、阅读 / 搜索 / 编辑交互、Liquid Glass、历史冲突裁决与验收边界；阶段报告仅保留来源和证据 |
| 工程规则 | [../AGENTS.md](../AGENTS.md) | 分层、验收纪律、构建与交付规则 |
| 发布门禁 | [../RELEASE_CHECKLIST.md](../RELEASE_CHECKLIST.md) | 公开产物前的硬门禁，含未勾选项 |

以上入口冲突时，以用户当前指令优先，PRD 定范围、AGENTS.md 定工程规则、design.md 定设计要求、CURRENT_STATUS 定已验证现状。

## 操作手册

| 主题 | 文件 |
|---|---|
| 本机开发环境与最新工作树备份 | [环境修复说明](../../环境修复说明-20261003.md)；工作区根目录 `Reader构建.cmd` |
| 构建与交付 APK | [APK_DELIVERY.md](APK_DELIVERY.md)；本轮 [底栏 iOS 通透度与按压放大](DOCK_IOS_TRANSPARENCY_20261004.md)；此前 [底栏视频参考动效](DOCK_VIDEO_MOTION_20261004.md)、[EPUB 章节与目录签名验收包](EPUB_CHAPTERS_20261003.md) 与 [签名记录](RELEASE_SIGNING_20261003.md) |
| 真机安装与 ADB 测试 | [DEVICE_INSTALL_AND_ADB_TEST.md](DEVICE_INSTALL_AND_ADB_TEST.md) |
| 验收清单 | [ACCEPTANCE.md](ACCEPTANCE.md) · [../CHANGELOG.md](../CHANGELOG.md) |
| 测试数据安全 | [ANDROID_TEST_DATA_SAFETY.md](ANDROID_TEST_DATA_SAFETY.md) |
| 滚动性能 | [SCROLL_PERF.md](SCROLL_PERF.md) |

## 设计与架构

设计要求先读根目录 `design.md`，其中汇总页面职责、排版、阅读 / 编辑 / 搜索交互、材质规则及历史冲突裁决。下列架构文档保留范围与实现研究；其中的早期交互建议须按 `design.md` 和后续有效修正理解，不能直接视为当前界面方案。

| 主题 | 文件 |
|---|---|
| PDF 阅读器技术交接（仍在演进的核心子系统） | [PDF_VIEWER_HANDOFF.md](PDF_VIEWER_HANDOFF.md) |
| EPUB 有限阅读架构与范围 | [EPUB_SUPPORT_ARCHITECTURE_DESIGN.md](EPUB_SUPPORT_ARCHITECTURE_DESIGN.md) |
| PDF 正文编辑 | [PDF_BODY_EDITING_DESIGN.md](PDF_BODY_EDITING_DESIGN.md) |
| 全项目设计规范（含来源索引） | [../design.md](../design.md) |
| 设置二级页液态玻璃调参工作台 | [GLASS_WORKBENCH_20261004.md](GLASS_WORKBENCH_20261004.md) |
| 液态玻璃实现与阶段证据 | [底栏 iOS 通透度与按压放大](DOCK_IOS_TRANSPARENCY_20261004.md)；[底栏视频参考动效](DOCK_VIDEO_MOTION_20261004.md)；[底栏折射与缩放恢复](DOCK_GLASS_RESTORE_20261004.md)；[DOCK_CAPTURE_CONSISTENCY_20261003.md](DOCK_CAPTURE_CONSISTENCY_20261003.md)；历史审计：[R36_GLASS_GROUND_TRUTH_AUDIT.md](R36_GLASS_GROUND_TRUTH_AUDIT.md) · [R36_2_OPTICAL_MATERIAL_REPORT.md](R36_2_OPTICAL_MATERIAL_REPORT.md) |
| 滚动与手势物理 | [APP_WIDE_SCROLL_AND_MAIN_THREAD_STALL_ISOLATION_REPORT.md](APP_WIDE_SCROLL_AND_MAIN_THREAD_STALL_ISOLATION_REPORT.md) |
| 导入身份权威 | [D1_1_IDENTITY_ARCHITECTURE_DECISION.md](D1_1_IDENTITY_ARCHITECTURE_DECISION.md) · [D1_ZIP_IDENTITY_AUDIT.md](D1_ZIP_IDENTITY_AUDIT.md) |

## 审查与修复报告

| 主题 | 文件 |
|---|---|
| 2026-10-02 Windows 备份审查修复 | [PROJECT_AUDIT_FIXES_20261002_WINDOWS.md](PROJECT_AUDIT_FIXES_20261002_WINDOWS.md) |
| 首页底栏采样与加号长按反馈 | [DOCK_GLASS_FEEDBACK_20261003.md](DOCK_GLASS_FEEDBACK_20261003.md) |
| 基于 design.md 的当前界面优化 | [UI_DESIGN_POLISH_20261003.md](UI_DESIGN_POLISH_20261003.md) |
| 2026-10-02 旧设备审查修复 | [PROJECT_AUDIT_FIXES_20260930.md](PROJECT_AUDIT_FIXES_20260930.md) |
| 2026-09-23 全面审查修复 | [PROJECT_AUDIT_FIXES_20260923.md](PROJECT_AUDIT_FIXES_20260923.md) |
| 架构审计 | [R31_ARCHITECTURE_AUDIT_REPORT.md](R31_ARCHITECTURE_AUDIT_REPORT.md) · [R28_ANNOTATION_AUDIT_REPORT.md](R28_ANNOTATION_AUDIT_REPORT.md) |
| 崩溃与发布加固 | [R32_RELEASE_HARDENING_REPORT.md](R32_RELEASE_HARDENING_REPORT.md) · [R32_2_NATIVE_CRASH_INVESTIGATION.md](R32_2_NATIVE_CRASH_INVESTIGATION.md) |

## 历史

- [history/](history/) — 归档的状态流水与旧交接
  - [history/CURRENT_STATUS_202609_STAGE_LOG.md](history/CURRENT_STATUS_202609_STAGE_LOG.md) — 2026-09 逐日状态流水，原样保留
- [releases/](releases/) — 历史版本说明

## 其余阶段报告

`docs/` 根目录下未在上面列出的 `R*.md` / `*_REPORT.md` / `*_AUDIT.md` 都是一次性阶段记录：
它们描述当时的设计决策、实验矩阵和证据，**不是当前状态**。
需要追溯某个具体改动为什么这样做时再按文件名查阅；不要用它们推断现状。
