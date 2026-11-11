# 文档索引

`docs/` 下有 200+ 篇文档，绝大多数是**某一阶段当时的证据**。本文只回答一个问题：现在该读哪一篇。

## 唯一事实源

| 主题 | 文件 | 说明 |
|---|---|---|
| 当前状态 | [CURRENT_STATUS.md](CURRENT_STATUS.md) | 版本、验证基线、已交付产物、设备验收状态、未决项 |
| PDF 大文件、滑动与拆分导出 | [PDF_LARGE_FILE_SPLIT_20261106.md](PDF_LARGE_FILE_SPLIT_20261106.md) | 94 正式包交付、三层模块边界、章节/页数拆分、回读清理、同源验证、59 项原生测试和正式包模拟器检查；当前验收以 CURRENT_STATUS 为准 |
| PDF 目录后台导入与 93 交付（历史） | [PDF_AUTOMATIC_OUTLINE_20261031.md](PDF_AUTOMATIC_OUTLINE_20261031.md) | 默认后台合并、读取开销、40/40 模拟器回归、93 手机安装与实际目录验收边界 |
| 92 PDF 首屏与横向输入基线 | [PDF_INPUT_ROUTING_20261031.md](PDF_INPUT_ROUTING_20261031.md) | 92 正式包、固定视口手势入口、39/39 原生回归与同源交付；[91 首屏实现](PDF_FIRST_FRAME_20261031.md) 保留底图与可见邻页依据 |
| 92 手机安装与有限启动检查 | [PHONE92_INSTALL_20261031.md](PHONE92_INSTALL_20261031.md) | 90 → 92 保留数据升级、Download / 安装摘要、手机显示与验收边界 |
| 当前选字、标注与沉浸交互 | [TEXT_SELECTION_AND_IMMERSION_20261031.md](TEXT_SELECTION_AND_IMMERSION_20261031.md) | 90 正式包、逐字选区与句柄、整组点击显隐、默认沉浸和 47 项真机回归 |
| 89 稳定性基线 | [STABILITY_FIXES_20261030.md](STABILITY_FIXES_20261030.md) | 原子书签、EPUB 命名锚点、PDF 像素及恢复链路、当时源码基线与验收边界 |
| 87 界面与品牌基线 | [HOME_POLISH_20261029.md](HOME_POLISH_20261029.md) | 首页、五条预览、Radix、Note 图标占比及同源正式包 |
| 88 渲染优化与历史交付 | [PDF_RENDER_METADATA_20261030.md](PDF_RENDER_METADATA_20261030.md) | PDF 重复开页优化、BOM、忙碌反馈及当时同源验证 |
| 85 旋转修复基线 | [EDITOR_ROTATION_20261029.md](EDITOR_ROTATION_20261029.md) | 编辑器选区 / 历史及 PDF 阅读锚点、既有验收边界 |
| 83 界面基线与历史交付 | [UI_RELEASE_20261025.md](UI_RELEASE_20261025.md) | 82 完整基线合入界面整理、原图标、完整回归；当前正式包见 CURRENT_STATUS |
| 设置品牌头与首页文件菜单（81历史） | [SETTINGS_MENU_20261024.md](SETTINGS_MENU_20261024.md) | 81 正式包、品牌头滚动折叠、实色菜单、取消路径、模拟器证据与源码冻结 |
| 设置页纯母标品牌区（当前） | [SETTINGS_BRAND_20261025.md](SETTINGS_BRAND_20261025.md) | 82 正式包、纯母标 M、单次 Reader、深浅反色、折叠回顶与源码冻结 |
| 会话对象 | [.memory/active_object.yaml](../.memory/active_object.yaml) | 当前 active object 的 id / status / scope |
| 产品范围 | [PRD.md](PRD.md) | 做什么、不做什么（Markdown/PDF/EPUB 有限阅读、PDF 正文有限编辑） |
| 界面设计规范 | [../design.md](../design.md) | 页面职责、主题排版、阅读 / 搜索 / 编辑交互、Liquid Glass、历史冲突裁决与验收边界；阶段报告仅保留来源和证据 |
| 工程规则 | [../AGENTS.md](../AGENTS.md) | 分层、验收纪律、构建与交付规则 |
| 发布门禁 | [../RELEASE_CHECKLIST.md](../RELEASE_CHECKLIST.md) | 公开产物前的硬门禁，含未勾选项 |
| 首轮稳定性基线与验收矩阵 | [STABILIZATION_20261013.md](STABILIZATION_20261013.md) | 原始源码检查点与核心能力验收矩阵；当前复验结果见下列工作台取消报告 |
| 工作台取消与正式版复验 | [WORKBENCH_REMOVAL_20261013.md](WORKBENCH_REMOVAL_20261013.md) | 按用户最新决定彻底删除工作台；保留当前 iOS 参考材质，复验 QA / Release |
| 全清单实现 | [FULL_BACKLOG_20261009.md](FULL_BACKLOG_20261009.md) | 用户当前范围与每项验证状态；独立 Markdown 编辑已按 2026-10-24 授权接入 Note 引擎 |
| 功能分层和验证入口 | [FEATURE_LAYERS_AND_VERIFICATION.md](FEATURE_LAYERS_AND_VERIFICATION.md) | 模块职责、开发检查和正式发布流程 |
| Code Wiki | [代码百科入口](../CODE_WIKI.md) · [分篇目录](code-wiki/README.md) | 74工作树的架构与模块索引；77顶栏最新差异见下列连续过渡报告，不替代当前状态 |

以上入口冲突时，以用户当前指令优先，PRD 定范围、AGENTS.md 定工程规则、design.md 定设计要求、CURRENT_STATUS 定已验证现状。

## 操作手册

| 主题 | 文件 |
|---|---|
| 本机开发环境与最新工作树备份 | [环境修复说明](history/workspace-2026/环境修复说明-20261003.md)（2026-10-11 归档）；工作区根目录 `Reader构建.cmd` |
| 本机入口等价关系（2026-10-11 标注） | `Reader构建.cmd` ≡ `reader-build-local.ps1`；`Reader模拟调试.cmd` ≡ `reader-emulator-local.ps1`（两者均为参数透传的薄包装） |
| QA 连接测试唯一入口 | `scripts/run-qa-connected-tests.ps1`：先调用 `reader-emulator-local.ps1` 完成设备准备与 QA 构建，再执行 `:app:connectedQaAndroidTest` |
| 玻璃材质与来源（阅读控件） | [PDF 材质统一与 RiseDiary 来源移除](OPTICAL_GLASS_20261011.md)；阅读控件统一基准；Dock以当前独立报告为准 |
| Dock 材质与拖动（当前） | [折射、细色散与点击动画修正](DOCK_OPTICS_20261013.md)；72 色阶 / 端帽为阶段证据，折射和拖动基线见本报告；最新强调色、叠层及转场见 [87 报告](HOME_POLISH_20261029.md) |
| Reader 应用图标（当前） | [87 Note 同尺寸画板](HOME_POLISH_20261029.md)，72dp、放大 12.5%、原路径及真实启动器检查；[80 历史](READER_ICON_20261024.md) |
| 设置页品牌头（当前） | [纯母标 M、单次 Reader 与折叠验收](SETTINGS_BRAND_20261025.md)；无白底、onSurface 深浅反色，折叠为左上母标 + 设置；82正式包模拟器验收 |
| 首页文件菜单（当前） | [实色锚定菜单与取消路径](SETTINGS_MENU_20261024.md)；Markdown 编辑入口按类型显示，危险移除独立分隔，不破坏原始文件 |
| Markdown 当前页编辑模式（当前） | [阅读页内编辑模式](MARKDOWN_INLINE_MODE_20261024.md)；点顶栏编辑原地进入，完成后原地回阅读，不创建新路由。此前 [Note 引擎移植](MARKDOWN_NOTE_PORT_20261024.md) 为 78 版历史证据 |
| Dock 视觉新标准 | [iOS App Store 日间 / 夜间原录屏与逐状态标准](IOS_MATERIAL_STANDARD_20261013.md)；2026-10-13 用户指定，优先于旧参考；用户对72的光学与点击动画补充见最新修正报告 |
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
| 已取消的液态玻璃工作台（历史） | [GLASS_WORKBENCH_20261004.md](GLASS_WORKBENCH_20261004.md)；2026-10-13 用户取消，当前要求见根目录 design.md |
| 液态玻璃实现与阶段证据 | [视频复刻与底栏随动验收](DOCK_VIDEO_REPLICA_20261011.md)； [按压玻璃完整底栏采样](DOCK_SAMPLED_RIM_20261004.md)；[底栏 iOS 通透度与按压放大](DOCK_IOS_TRANSPARENCY_20261004.md)；[底栏视频参考动效](DOCK_VIDEO_MOTION_20261004.md)；[底栏折射与缩放恢复](DOCK_GLASS_RESTORE_20261004.md)；[DOCK_CAPTURE_CONSISTENCY_20261003.md](DOCK_CAPTURE_CONSISTENCY_20261003.md)；历史审计：[R36_GLASS_GROUND_TRUTH_AUDIT.md](R36_GLASS_GROUND_TRUTH_AUDIT.md) · [R36_2_OPTICAL_MATERIAL_REPORT.md](R36_2_OPTICAL_MATERIAL_REPORT.md) |
| 滚动与手势物理 | [APP_WIDE_SCROLL_AND_MAIN_THREAD_STALL_ISOLATION_REPORT.md](APP_WIDE_SCROLL_AND_MAIN_THREAD_STALL_ISOLATION_REPORT.md) |
| 导入身份权威 | [D1_1_IDENTITY_ARCHITECTURE_DECISION.md](D1_1_IDENTITY_ARCHITECTURE_DECISION.md) · [D1_ZIP_IDENTITY_AUDIT.md](D1_ZIP_IDENTITY_AUDIT.md) |

## 审查与修复报告

当前审查接手入口：[2026-10-11 审查现状复核](AUDIT_TAKEOVER_20261011.md)，包含原报告逐项状态、Git 恢复边界与本轮验证结果。

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
