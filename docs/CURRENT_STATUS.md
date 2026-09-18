# 当前项目状态

更新：2026-09-18。此文件是当前工程与发布状态的唯一入口；阶段报告只描述当时证据。

## 当前任务（唯一 active object：R36_2_LIGHT_TINT_CLOSURE）

### 2026-09-18 R36.2 Light Tint Closure（ENGINEERING_CANDIDATE，DEVICE_APPROVAL_PENDING）

- 起点：clean HEAD `bed8bf4`（`images/r36-home.png` 已移入 `.external_outbox/r36-2/recovery/`，不覆盖旧证据）。
- 单变量已证：对照只动 light tint α（blur 26dp 等全冻结），`GlassOpticalPolicyTest` 新增隔离契约 PASS；harness 新增 .75/.65/.55（preview 路径专用，生产调用不受影响）。
- 评估见 [R36_2_LIGHT_TINT_EVALUATION](R36_2_LIGHT_TINT_EVALUATION.md)：白底 5 候选无判别力（19.4–20.1:1）；深色底 .86→14.6:1、.45→5.3:1；.45 白页“好看”是 backdrop 的功劳，禁止据此批准。
- 裁决：**NOT APPROVED**。frozen authority 保持 `.86`，4 frozen tests 不改；站立工作候选仍 `.45`（不动，不回滚）。
- Audit（只读）：[R36_INTERACTION_ARCHITECTURE_AUDIT](R36_INTERACTION_ARCHITECTURE_AUDIT.md)，11 surfaces / 8 transitions / 6 risks；R11 picker 等 4 个缺口待下一阶段。
- 验证：`compileReleaseKotlin` PASS；`testQaUnitTest` **1504 / 4 failed**（恰为 frozen gate；harness 新测试通过）；`assembleRelease` Wilmington `BLOCKED_EXTERNAL_CONFIGURATION`（无签名环境，未绕过、未冒充）。证据 `.external_outbox/r36-2/light-tint/`。
- device_verified / human_visual：NOT_RUN（单次 adb 为空，不轮询）。

### 2026-09-18 R36 Recovery / State Reconciliation Gate（PASS，已归档，内容保留）

- authority：`.memory/active_object.yaml` = `R36_RECOVERY_AND_R36_2_CLOSURE / state_reconciliation_in_progress`；本节与该文件一致，不再以旧 `R36_2_OPTICAL_MATERIAL_REWRITE / foundation_verified…` 作为当前可执行任务。
- 现场：分支 `feat/risediary-inspired-dock`，HEAD `5284f80` + 74 tracked + 63 untracked 脏工作区；HEAD 不是当前实现状态。冻结见 [R36_RECOVERY_SNAPSHOT_20260918](R36_RECOVERY_SNAPSHOT_20260918.md)，归类见 [R36_DIRTY_WORKTREE_INVENTORY](R36_DIRTY_WORKTREE_INVENTORY.md)。
- 4 QA 失败处置：`.45` 尚未成为正式 authority，**不改测试断言**，标记 optical batch 未完成；4 失败为正确的 gate 信号（Control light .45 / blur 26 vs 旧冻结 .86 / 18dp；panel 26 vs dialog 18）。全量另见 1 个 motion 测试引用搬迁 authority（已对齐修复，非 optical），详见 inventory §4。
- Room 14.json 处置：合法变化（MIGRATION_13_14 加 sourceUri/sourceSha256，hash `e5bd1ee2…` 与 R33 期望一致），独立于 glass batch。
- PDF_GLASS_FLICKER：`BLOCKED_REPRODUCTION / WAITING_FOR_DEVICE_SCENE_CONFIRMATION`——保持 NOT_REPRODUCED，无复现证据不改代码（见下节）。
- SETTINGS_GLASS_COMPARISON：`IMPLEMENTATION_DONE / DEVICE_VISUAL_NOT_RUN`——BUILD_VERIFIED=PASS，DEVICE_VERIFIED=NOT_RUN，HUMAN_VISUAL=NOT_RUN（见下节）。
- Recovery Gate 完成前不恢复 R36.2 调参、不启动 R36.3 实现（Interaction 仅允许 audit）。
- 离线验证（本轮，证据 `.external_outbox/r36-recovery/`）：`compileReleaseKotlin` PASS；`testQaUnitTest` 全量 **1503 / 4 failed**，4 失败即上述 optical 冻结 gate；`assembleRelease` 被 `validatePublicReleaseConfiguration` 拒绝（本 shell 无四项 M1RACLE_RELEASE_* 签名环境，不在聊天中索取密码），**无本轮新 APK，不复制旧包冒充，不改用 Debug**，冻结产物仍为 13:13 `e4acceec…`。

### 2026-09-18 PDF 拖动背景闪烁排查（BLOCKED_REPRODUCTION，保持冻结）

- 用户报告 PDF 拖动时背景闪烁；未确认是玻璃采样背景还是正文整页。不可将“采样不同步”猜测写为已确认根因。
- device_verified：198.18.0.1:34609 为 device，当前安装正式 Release SHA-256 `e4acceec6fe687b31c70d77489ef399058e852db2431444a451c1943e2b636fb`；打开当前实际 PDF 第 281–282 / 403 页（6.2 系统性红斑狼疮，放大状态），录制慢拖、快滑、菜单外拖动。
- 观察：已检查整页采样图及胶囊连续帧，玻璃明暗/红黑色随正文移动，所检查帧未捕获明确背景消失或整页闪白；不能据此断言用户问题不存在。菜单外拖动会关闭菜单，未完成菜单保持展开时的滚动验证。手动复现等待录屏只录到静止页面。
- implemented：本轮无代码/配方改动、无重建/安装/清数据；automated_verified：无新增测试；pending：用户确认具体闪烁区域/可复现场景后继续定位，尚未修复。
- 证据 `.external_outbox/pdf-glass-flicker/`：before.mp4、menu-before.mp4、fast-before.mp4、human.mp4、UI XML、连续帧及 checkpoint.md。设备仍留在 PDF 页面，已停止自动触控。

### 2026-09-18 设置页玻璃配方对照（IMPLEMENTATION_DONE，DEVICE_VISUAL_NOT_RUN）

- 用户明确要求在设置页展示调参方案，供对照 iPad Apple Books。仅扩展已有预览，阅读页生产配方不变，不进入 R36.3。
- implemented：当前／渐变／折射参考，以及 26dp × tint .86/.80/.70/.60/.50/.45/.35、32dp/.45、40dp/.45；浅暗色、密集正文/图案、背景手动滑动及播放。暗色候选不代表已验收。
- 配方仍由 GlassMaterialEngine 解析，预览共用 glassMaterial / readerGradient；无采样能力仍使用不透明降级，不让试验 tint 泄漏正文。
- automated_verified：compileReleaseKotlin 通过；定向 QA 主机测试 84 项，80 通过、4 失败、0 errors/skipped。新增候选隔离/能力降级测试通过；4 条既有失败来自 GlassMaterialRegressionTest（3）及 ReaderChromeMaterialTest（1）仍断言此前 .86 等冻结配方，与本轮开始时已有 .45 调参不一致；未改断言。证据 `.external_outbox/glass-comparison/`。Release assembleRelease 成功（正式签名、minify/shrink）；既有 lint ASM NegativeArraySizeException 仍出现，不声称干净 lint 通过。
- 本轮 Release：6,953,186 bytes；SHA-256 `e4acceec6fe687b31c70d77489ef399058e852db2431444a451c1943e2b636fb`；signer `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`。构建输出、Outbox、Download 三处摘要一致，已覆盖 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`。未自动安装。
- device_verified：NOT_RUN。本轮 adb devices 为空，连接用户提供的 198.18.0.1:46825 返回 Connection refused。不重复探测；用户观感待新包手验。
- 本轮涉及 GlassAuthority、GlassMaterial、BlurComparisonPreview、GlassOpticalPolicyTest 与本状态文件。既有大量未提交修改保留。


### 2026-09-18 R36.2 Optical Material Rewrite（进行中，真机调参待接入）

- 用户明确启动 `/storage/emulated/0/r36.2.txt`；只做静态材质，不进入 R36.3。
- implemented：baseline、optical family、window/shape capability fallback、删除孤儿 DockSelection、progressive preview 复用生产 renderer；A–E PDF/Markdown fixture 已生成收件箱。
- automated_verified：Release 源码编译通过；隔离 QA 定向 83/0/0/0。首轮 Release UI 测试因缺少测试 Activity 启动失败，未跳过或削弱断言；细节见报告。
- ADB 无设备；逐变量 blur→tint→lens→edge→shadow 尚未执行，不能声称光学完成。
- `assembleRelease` 被正式签名 gate 拒绝：缺少四项 M1RACLE_RELEASE_* 环境；无本轮 APK，未覆盖 Download。构建还打印既有 lint ASM 异常，不能声称完整 lint 通过。
- 接续唯一报告：[R36.2](R36_2_OPTICAL_MATERIAL_REPORT.md)，含 baseline、实验顺序、待验收矩阵。

### 2026-09-17 R36.1 Glass Authority Closure（实现与自动化验证完成）

- 目标：生产玻璃路径单一事实源 `GlassMaterialEngine`；视觉与 R36.0 基线等价。未做 R36.2 光学调参。
- implemented：Role×Environment×Capability×Interaction 解析；Markdown 顶栏 / Settings preview / Dock / Home Dropdown 收进引擎；删除 Home/Hub 空捕获；Menu 不再恒 INTERACTION；`DockSelection` 标 NO_CURRENT_CONSUMER。
- automated_verified：定向 QA **93 / 0 / 0 / 0**；`compileReleaseKotlin` 成功。未全量 testQaUnitTest，未 assembleRelease。
- 报告：[R36.1 Glass Authority Closure](R36_1_GLASS_AUTHORITY_CLOSURE.md)。
- pending：R36.2 optical refinement；真机观感。

### 2026-09-16 PDF 控件按可读性收紧（Release 已交付，实机待装）

- 依据：Apple HIG 的对比与材质分层，以及 2026-09-15 DCIM 胶囊截图。不使用 Apple 模板/符号/Icon Composer。
- 截图问题：页码胶囊上的标题被 PDF 正文透出干扰；外影过重，像一块灰板。不是再改裁切几何。
- implemented：阅读浮层（返回/胶囊/菜单）表面浅色 0.86 / 深色 0.80，大面板 0.90 / 0.86；外影浅色 0.14 / 深色 0.28。底栏仍用较透的 0.62，因为叠在首页而不是教材页。18dp 模糊、6dp 半径和裁切留白未改。
- automated_verified：材质与 chrome 定向 **36 / 0 / 0 / 0**；`assembleRelease` 成功。
- 本轮 Release：**6,953,186 bytes**，SHA-256 `10b2181f2cc38c5cebd7a20a2791192feb5b42e184b4dd8dbc8d28bf2dc070c0`；生产签名证书 SHA-256 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`。已覆盖 Download。不是上一轮 `ba4fe2a0…` 截边包。
- device_verified：NOT_RUN，`adb devices` 为空。请安装后看胶囊标题是否还能被正文透出、外影是否还是大方灰块。

### 2026-09-15 PDF 阴影截边修复（实现与自动化验证完成，Release 已交付，实机待装）

- 用户报告阴影异常，并指定 DCIM 最新截图。已查看 Screenshots 内 18:26:58、18:27:01、18:27:04 三张 M1racle Reader 截图；最新一张收起胶囊外影出现矩形截边，另外两张为完整投影。截图不是二级面板，先前二级面板裁切排查不作为本轮根因。
- 代码链路：PdfInfoCapsule 外部 fade 改变整层 alpha，dropShadow 绘制越出控件边界。Compose 在半透明淡入淡出时按**发生动画的节点**分配矩形离屏缓冲；若该节点被收成控件原尺寸，外影会被裁成方框。
- implemented：`PdfChromeVisibility` 在淡入淡出节点内部预留 3× 阴影半径，外层 wrapper 把测量/位置抵消回原控件足迹。三个 PDF 控件入口接入；材质、阴影参数和阅读逻辑保持原值。
- automated_verified：`PdfChromeVisibilityTest` **2 / 0 / 0 / 0**，含淡入中途、淡入结束、淡出中途的缓冲覆盖与原位置/尺寸，以及整屏菜单约束。反向验证：临时 `shadowSpace=0` 时同一胶囊测试失败，已恢复 3×。相关 chrome/材质 **52 / 0 / 0 / 0**；`compileReleaseKotlin` 成功。本轮 `assembleRelease` 成功。日志 `.external_outbox/pdf-shadow-clip/`。
- 本轮 Release：**6,953,186 bytes**，SHA-256 `ba4fe2a0489df6a3745fe866d59aa00a538e570bdef710f6826dfe9c2eabceed`；生产签名证书 SHA-256 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`。Outbox 与 `app/build/outputs/apk/release/app-release.apk` 一致，已覆盖 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`。不是 2026-09-12 旧包。
- device_verified：NOT_RUN。单次 `adb devices` 为空，请手动安装 Download 中的新包后看胶囊滚动显隐时外影是否还被切成方框。

## 此前 PDF 菜单检查点

### 2026-09-15 PDF 控件完善（实现与自动化验证完成，Release 交付受阻）

- 用户要求完善 PDF 控件并明确“继续”，当前目标转为 PDF 控件可用性；首页不再是当前任务。
- 范围：菜单大字号文字、选中及展开状态语义，对应回归；保留现有磨砂/投影与阅读引擎。
- implemented：菜单行高与字体缩放联动，主容器复用同行高计算，标签最多两行；选择项使用 selectable/RadioButton，展开项增加状态语义；子面板准备判定使用动画目标高度，避免将受约束的实测高度与自然高度比较。
- automated_verified：最终 PDF 相关 QA 单测 **179 / 0 / 0 / 0**；compileReleaseKotlin、lintVitalRelease 退出成功，但 lintVitalAnalyzeRelease 仍打印既有 ASM NegativeArraySizeException，不声称完整或干净 lint 通过。
- 回归证明：修改前 selected 语义测试失败；两倍字号/260dp 矮屏测试在旧高度判定下卡于 PREPARING_CHILD_PAGE_MAPPING，修复后进入 CHILD_PAGE_MAPPING。文字布局测试修改前也通过，不作为裁字复现证据。日志/测试摘要/源码摘要见 `.external_outbox/pdf-controls-polish/`。
- pending：正式签名 Release APK 及设备验收。未打包 Debug、未覆盖 Download、未将旧包作为本轮产物。见 [本轮报告](PDF_CONTROLS_POLISH_20260915.md)。
- 只构建 Release；正式签名环境仍未注入，不得改用 Debug。当前会话 ADB 探测为空，实机验收待设备可用。

## 此前首页检查点

### 2026-09-15 首页设计改进检查点

- 用户以 Apple Design 为参考要求改进；范围继续限定 HOME_LAYOUT_POLISH。
- implemented：空首页增加文字“导入文件”按钮，复用 onImport / isImporting；空状态使用既有品牌文字样式和居中排版，支持矮屏/大字号滚动；空状态及最近阅读标题增加 heading 语义。
- automated_verified：首页定向 QA 测试 **39 / 0 / 0 / 0**（tests / failures / errors / skipped），日志 `.external_outbox/home-apple-guidance/tests.log`；包含空状态导入/忙碌一致性与两倍字号矮屏触达测试。
- device_verified：NOT_RUN，单次 `adb devices -l` 为空；本次无法完成基于当前截图的视觉审查。
- automated_verified：assembleDebug 成功；新 Debug APK `.external_outbox/m1racle-reader-debug-latest.apk`，79,490,851 bytes，SHA-256 `c526adfa199d5166a60d04d4e9677973485a4a090277cd27df037fb039565fb6`。未重跑完整 lint，不能声称 lint 通过。
- pending：Release 所需四项签名环境未注入，本机 signing/credentials 目录为空，正式包交付受阻；人工观感及 TalkBack 验收待设备可用。按 AGENTS 构建后规则已复制并核对既有 Release 到 Download；该包仍是下文 2026-09-12 候选，不含本次改动。详见 [本轮报告](HOME_APPLE_DESIGN_GUIDANCE.md)。


用户授权修复首页实机审查全部问题并改善观感。本轮固定顶栏搜索/导入、约束列表滚动视口避开系统栏及底部导航、移除遮挡内容的悬浮加号；整理继续阅读与列表的字号、间距和两行标题；三点菜单锚定按钮，使用不透明表面，新增独立文件信息入口查看及复制完整文件名。生产签名 Release 已交付，新界面真机及观感验收待无线 ADB 恢复。见 [首页实机问题修复与排版整理](HOME_LAYOUT_POLISH.md)。

## 源码与候选

- 工作目录 `/root/code/MarkLeaf`，分支 `feat/risediary-inspired-dock`；`5284f801fffa62904ce80101ca687cfd415ea8c6` 基础上的工作区修改，未提交，未改变 tag/远端/发布状态。
- 已包含主页功能修复，以及此前有限正文编辑、保存覆盖、逐行文字高亮、固定渐变、厚雾和显式投影。本轮改动源码摘要见 `.external_outbox/home-polish/source-hashes.json`；此前材质证据仍见 `.external_outbox/frost-drop-shadow/`。
- 版本 `1.0.0 / 23`，Room v14，应用无 INTERNET 权限。
- 当前 production 签名 Release：**6,953,186 bytes**，SHA-256 `10b2181f2cc38c5cebd7a20a2791192feb5b42e184b4dd8dbc8d28bf2dc070c0`；未新增或升级依赖。本轮含 PDF 浮层对比与轻影，已覆盖上一轮截边包。
- production signer SHA-256：`2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`，验签通过。
- 交付 `.external_outbox/m1racle-reader-release-latest.apk` → `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`，已覆盖并核对摘要一致，另附 SHA-256 文件。
- Debug 构建基线（2026-09-09）：`.external_outbox/m1racle-reader-debug-latest.apk`，79,490,851 bytes，仅作构建验证；用户交付仍是压缩 Release。

## 发布事实

2026-09-05 通过 GitHub API 与 `git ls-remote` 只读核准：

- `ldohgfsdu/m1racle-reader` 只有 `v1.0.0` Draft，`published_at=null`，尚未发布。
- 本地与远端 `v1.0.0` 指向 `a43633a`；Draft APK hash 为 `e8c0431ab2b38c522a6e12bd3e39349acf78269dd7189bd293b74514f7a64777`，不含最新四个产品提交。
- 本地及该远端均无 `v1.0.1` tag。旧 `POST_RELEASE_BASELINE_1_0_1.md` 的已发布描述与当前事实不符，已随旧工作区归档，不作为现行基线。
- `origin` 仍指向历史 `ldohgfsdu/markleaf`；本次未改远端、tag、Draft 或发布任何内容。

## 验证与剩余事项

- implemented：本轮首页安全滚动视口、固定操作顶栏、标题层级、紧凑继续阅读卡片、按钮锚定的实色菜单与独立文件信息已实现；此前主页移除确认与操作入口、完整文件名、导入状态与失败反馈已接入；此前有限原生 PDF 正文编辑、撤销/重做、实际 PDF 预览、保存当前书库文档、明确覆盖源文件、另存副本、持久备份恢复；保留书库身份及关联记录，外部打开等待编辑退出；逐行文字高亮、固定渐变 Markdown 顶栏、统一厚雾及设置预览已接入。
- automated_verified：最终全量 QA 单测 **1484 / 0 / 0 / 0**，包含新增视口几何/滚动、导入入口及独立信息查看行为测试；Release、lintVitalRelease、生产签名、Download 摘要和源码摘要通过。证据 `.external_outbox/home-polish/`。
- device_verified：本轮新版本 NOT_RUN。修改前已通过 `198.18.0.1:41681` 检查首页、菜单和滚动，证据 `.external_outbox/home-audit-live/`；随后端点失效，Connection refused，修改前截图不作为新版验收。
- lintQa：前轮已执行并失败，既有 Compose UnrememberedStateDetector / Kotlin UAST API 不兼容导致 IncompatibleClassChangeError（NoteEditDialog.kt、ReaderMoreHostUiTest.kt）；未降低检查项，不能声称完整 lint 通过。本轮 Release 构建及 lintVitalRelease 成功退出，未重复完整 lintQa；本轮 lintVital 分析仍打印既有 ASM NegativeArraySizeException。
- pending：新版首页顶端/滚动/末尾安全区、菜单锚点、文件信息、搜索导入触达及深浅色/横屏/字体缩放实际观感；主页真实 SAF 导入/耗时/超限、移除取消与确认/失败反馈、仅一本书及完整文件名查看；此前编辑选框与实际渲染、键盘/字体缩放、旋转/后台恢复、SAF 授权/取消失败重试/覆盖与另存重开、实际进程中断恢复、外部打开保护、内存耗时和独立阅读器对照；文字高亮实际对齐、Markdown 渐变观感与滚动流畅度、深浅色/横屏/字体缩放、全局厚雾及菜单视觉体验仍需人工验收；本轮 PDF 方影/亮度变化和浅色控件需在相同页面对照。发布 tag/Draft 与候选源码不一致仍待独立处理。

证据：[文字高亮与模糊预览](PDF_TEXT_HIGHLIGHT.md)、[保存与覆盖](PDF_EDITOR_SAVE_AND_OVERWRITE.md)、[正文编辑首个闭环](PDF_BODY_EDITOR_FIRST_LOOP.md)、[此前阅读流程完善](READER_FLOW_POLISH_20260906.md)、[展开菜单加厚](EXPANDED_READER_MENU_FROST.md)、[B 材质与界面打磨](GENTLE_FROST_AND_UI_POLISH.md)。
