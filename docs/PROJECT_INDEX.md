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
| 全清单实现 | [FULL_BACKLOG_20261009.md](FULL_BACKLOG_20261009.md) | 用户当前范围与每项验证状态；Markdown 编辑等待 M1racle Note |
| 功能分层和验证入口 | [FEATURE_LAYERS_AND_VERIFICATION.md](FEATURE_LAYERS_AND_VERIFICATION.md) | 模块职责、开发检查和正式发布流程 |

以上入口冲突时，以用户当前指令优先，PRD 定范围、AGENTS.md 定工程规则、design.md 定设计要求、CURRENT_STATUS 定已验证现状。

## 操作手册

| 主题 | 文件 |
|---|---|
| 本机开发环境与最新工作树备份 | [环境修复说明](../../环境修复说明-20261003.md)；工作区根目录 `Reader构建.cmd` |
| 本机入口等价关系（2026-10-11 标注） | `Reader构建.cmd` ≡ `reader-build-local.ps1`；`Reader模拟调试.cmd` ≡ `reader-emulator-local.ps1`（两者均为参数透传的薄包装） |
| QA 连接测试唯一入口 | `scripts/run-qa-connected-tests.ps1`：先调用 `reader-emulator-local.ps1` 完成设备准备与 QA 构建，再执行 `:app:connectedQaAndroidTest` |
| Dock 拖动跟手与弹性形变 | 当前 [54版弹性与回弹](DOCK_ELASTIC_20261008.md)；[53版直接跟手](DOCK_DIRECT_DRAG_20261008.md) |
| 酷安底栏真实参考数据 | [真机尺寸、按压/拖动录屏与帧统计](COOLAPK_DOCK_CAPTURE_20261008.md) |
| 酷安玻璃复刻 | [底栏、阅读控件与独立动作反馈实现及验证](COOLAPK_GLASS_REPLICA_20261008.md) |
| 构建与交付 APK | [APK_DELIVERY.md](APK_DELIVERY.md)；本轮 [一体式蓝色玻璃Dock](OPTICAL_DOCK_20261008.md)；此前 [EPUB恢复43版与字体](EPUB_RESTORE43_FONT_20261007.md)；此前 [EPUB滑动与页边界修复](EPUB_SLIDE_BOUNDARIES_20261007.md)；此前 [EPUB 原书排版与正式包闭环](EPUB_PUBLISHER_LAYOUT_20261006.md)、[EPUB 返回闪帧与阅读器研究](EPUB_RETURN_RESEARCH_20261006.md)、[RiseDiary 底栏与阅读工具栏](RISEDIARY_TOOLBAR_20261006.md)；[签名记录](RELEASE_SIGNING_20261003.md) |
| 真机安装与 ADB 测试 | [DEVICE_INSTALL_AND_ADB_TEST.md](DEVICE_INSTALL_AND_ADB_TEST.md) |
| 验收清单 | [ACCEPTANCE.md](ACCEPTANCE.md) · [../CHANGELOG.md](../CHANGELOG.md) |
| 测试数据安全 | [ANDROID_TEST_DATA_SAFETY.md](ANDROID_TEST_DATA_SAFETY.md) |
| 滚动性能 | [SCROLL_PERF.md](SCROLL_PERF.md) |

## 设计与架构

设计要求先读根目录 `design.md`，其中汇总页面职责、排版、阅读 / 编辑 / 搜索交互、材质规则及历史冲突裁决。下列架构文档保留范围与实现研究；其中的早期交互建议须按 `design.md` 和后续有效修正理解，不能直接视为当前界面方案。

| 主题 | 文件 |
|---|---|
| PDF 阅读器技术交接（仍在演进的核心子系统） | [PDF_VIEWER_HANDOFF.md](PDF_VIEWER_HANDOFF.md) |
| EPUB 当前核心与范围 | 当前 [恢复43版原生翻页与字体](EPUB_RESTORE43_FONT_20261007.md)；45–50版研究与实现为历史：[滑动与页边界修复](EPUB_SLIDE_BOUNDARIES_20261007.md)；[翻页看门狗](EPUB_TOUCH_WATCHDOG_20261007.md)；[外部字体](EPUB_FONT_TURN_20261006.md)；原书核心基线 [EPUB_PUBLISHER_LAYOUT_20261006.md](EPUB_PUBLISHER_LAYOUT_20261006.md)；当前设计见根目录design.md / PRD补充。早期方案见 [EPUB_SUPPORT_ARCHITECTURE_DESIGN.md](EPUB_SUPPORT_ARCHITECTURE_DESIGN.md)，旧锚点背景见 [EPUB_ANCHOR_20261004.md](EPUB_ANCHOR_20261004.md) |
| PDF 正文编辑 | [PDF_BODY_EDITING_DESIGN.md](PDF_BODY_EDITING_DESIGN.md) |
| 全项目设计规范（含来源索引） | [../design.md](../design.md) |
| 设置二级页液态玻璃调参工作台 | [GLASS_WORKBENCH_20261004.md](GLASS_WORKBENCH_20261004.md) |
| 液态玻璃实现与阶段证据 | [按压玻璃完整底栏采样](DOCK_SAMPLED_RIM_20261004.md)；[底栏 iOS 通透度与按压放大](DOCK_IOS_TRANSPARENCY_20261004.md)；[底栏视频参考动效](DOCK_VIDEO_MOTION_20261004.md)；[底栏折射与缩放恢复](DOCK_GLASS_RESTORE_20261004.md)；[DOCK_CAPTURE_CONSISTENCY_20261003.md](DOCK_CAPTURE_CONSISTENCY_20261003.md)；历史审计：[R36_GLASS_GROUND_TRUTH_AUDIT.md](R36_GLASS_GROUND_TRUTH_AUDIT.md) · [R36_2_OPTICAL_MATERIAL_REPORT.md](R36_2_OPTICAL_MATERIAL_REPORT.md) |
| 滚动与手势物理 | [APP_WIDE_SCROLL_AND_MAIN_THREAD_STALL_ISOLATION_REPORT.md](APP_WIDE_SCROLL_AND_MAIN_THREAD_STALL_ISOLATION_REPORT.md) |
| 导入身份权威 | [D1_1_IDENTITY_ARCHITECTURE_DECISION.md](D1_1_IDENTITY_ARCHITECTURE_DECISION.md) · [D1_ZIP_IDENTITY_AUDIT.md](D1_ZIP_IDENTITY_AUDIT.md) |

## 审查与修复报告

| 主题 | 文件 |
|---|---|
| EPUB R04 合入、完整测试与正式包模拟器验收 | [R04_INTEGRATION_20261005.md](R04_INTEGRATION_20261005.md) |
| Apple Books 视频滑动翻页复刻 | [EPUB_APPLEBOOK_SLIDE_20261005.md](EPUB_APPLEBOOK_SLIDE_20261005.md) |
| EPUB 实机反馈：沉浸系统栏与全屏纸页圆角 | [EPUB_IMMERSIVE_CORNERS_20261005.md](EPUB_IMMERSIVE_CORNERS_20261005.md) |
| EPUB 固定设备圆角：去除手势半径形变 | [EPUB_FIXED_CORNERS_20261005.md](EPUB_FIXED_CORNERS_20261005.md) |
| EPUB 静止页全窗口填充、固定尺寸菜单与全屏遮罩 | [EPUB_FILL_MENU_20261005.md](EPUB_FILL_MENU_20261005.md) |
| EPUB 侧边贴合、深色高光与统一目录 / 设置菜单 | [EPUB_CARD_POLISH_20261005.md](EPUB_CARD_POLISH_20261005.md) |
| EPUB 六项细节：下层跟随 / 渐变提亮 / 设备圆角 / 小白条 / 单标题 / 锚定设置 | [EPUB_REFERENCE_FIDELITY_20261005.md](EPUB_REFERENCE_FIDELITY_20261005.md) |
| EPUB 长按拖动松手闪烁与 Apple Books 滑动细节 | [EPUB_RELEASE_HANDOFF_20261005.md](EPUB_RELEASE_HANDOFF_20261005.md) |
| EPUB 原书换行、目录空白页与缩进修复 | [EPUB_LAYOUT_20261005.md](EPUB_LAYOUT_20261005.md) |
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
