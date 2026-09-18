# R36 Interaction Architecture Audit — 2026-09-18

只读审计，不实现、不修。基线 clean HEAD `bed8bf4` + 本轮 light-tint harness（enum/labels/单测）。
下一阶段进入 Interaction Architecture 时的事实依据。

## 1. Surface inventory

| # | Surface | Role | Owner / trigger | Backdrop | Anchor | Exit destination | Layer | Document-layout impact |
|---|---|---|---|---|---|---|---|---|
| S1 | 返回键 + 页码胶囊 | Control / Capsule | PdfViewerScreen；scroll state machine / 正文单击 | root document backdrop（sibling overlay） | 顶部/底部 chrome 槽 | HIDDEN / CAPSULE_ONLY（PdfChromeLevel） | foreground transient | 无：overlay，不改 document layout |
| S2 | More 主菜单（6 行） | Menu (+Control 行) | ReaderMoreHost；More 按钮 | root + menuExport（SettingsCard 采样 menuExport） | More 按钮上方 | CLOSED（ReaderMoreStateMachine.onToggleOpen） | foreground transient | 无 |
| S3 | Basic inline（阅读方式/阅读界面） | Menu 内联展开 | 同一 host 内 section toggle；同 section 再点收回 MAIN | 同 S2 | 菜单内 | MAIN | 同一层内高度变化 | 菜单容器高度变；document 不动 |
| S4 | Advanced child（页面映射/目录/缩略图/导出） | SettingsCard（popover） | S2 → PREPARING_*（先收 Basic、等 Main 几何）→ CHILD_* morph（MoreMorphSpring：宽/高 animateTo，content 0.94→1.0 scale） | RootAndMenu | 菜单主体 | MAIN（onOptionSelected 回 MAIN；collapse 路径） | foreground transient | 容器 morph；document 不动 |
| S5 | Chrome visibility fade 节点 | Control/Capsule/Menu（按调用方 role） | PdfChromeVisibility；enter fadeIn(200)+slideIn / exit fadeOut(180,delay100)；第 3 组为 fadeIn(180,delay100)/fadeOut(180) | 调用方 backdrop | chrome 槽 | visible=false | 同一层 alpha+位移 | 外层 wrapper 抵消测量，足迹不变（截边修复） |
| S6 | Hub Dock | Dock（SOLID 不透明板） | Hub nav；press lens + velocity morph；选中=3dp 实色条（DockIndicatorHeight） | 无（直接画色板） | 底部 | 路由切换 | chrome | 无 |
| S7 | Hub tab 转场 | — | NavGraph hubEnter/hubExit（HubTabTransitions.enter/exit，按起止路由）；SEARCH 路由无 hub 转场 | — | 路由 | 反向路由 | nav | 路由级 |
| S8 | Home 行 DropdownMenu | Dock role（SOLID）行 + 系统 DropdownMenu | DocumentListRow 三点按钮 | 无 glass 采样 | 按钮锚点 | expanded=false | popup window | 无 |
| S9 | Dialogs（删除确认/导出/大纲导入/大纲编辑/正文编辑确认/批注编辑） | Dialog（fallbackPlate：跨 window 不采样） | 各调用方；GlassAlertDialog role=Dialog + interaction=true | 不采样（soft plate） | 居中 window | dismiss | platform window | 无 |
| S10 | Markdown 顶栏渐变 | ProgressiveChrome（16dp/.62 legacy） | ViewerScreen + readerGradient | reader backdrop | 顶部 | scroll | foreground | 无 |
| S11 | 章节导出 picker（ReaderAdvancedCard 内） | 无独立 role（继承 S4 SettingsCard popover） | picker 行点击 | 父 popover backdrop | 父容器内 | 选中即收起 | 父层内 | 无 |

## 2. Transition inventory

| 转换 | Origin preserved? | Reverse? | Generic fade? | Teleport? | Document mutation? | Competing surface? |
|---|---|---|---|---|---|---|
| More CLOSED↔MAIN | 是（同一锚点） | 是（toggle） | 否（容器展开 + 内容 morph） | 否 | 否 | S1 chrome 冻结（menu open 时 scroll 驱动暂停，关闭清状态） |
| MAIN→PREPARING_*→CHILD_* | 是（两阶段：先收 Basic 复原 Main 几何再 morph） | 否（单向；回 MAIN 经 onOptionSelected/collapse） | 否（MoreMorphSpring 宽/高 + 0.94 scale，damping .8 / stiffness 450） | 否 | 否 | S3/S4 互斥（r26.1 单一 exclusive state） |
| CHILD_*→MAIN | MAIN 恢复 | 准反向（非 morph 逆播，是状态回退） | 部分 | 否 | 否 | 同上 |
| Chrome FULL↔BUTTONS_ONLY↔HIDDEN↔CAPSULE_ONLY | 是（阈值 + 方向，36dp 藏 / +28dp 显 / fling 快捷 / 单击只切胶囊） | 是 | 是（fade+slide，时长 180–200ms 两组参数并存） | 否 | 否 | menu open 冻结 scroll 驱动；**两组 fade 时长并存（200/180 vs 180/100）是否应统一——只记录，不改** |
| Hub tab 切换 | 路由保留 | 是 | 待确认（HubTabTransitions，未细读曲线） | 否 | 否 | Dock press lens（ไม่改变选中语义） |
| S8 dropdown 出现 | 锚点 | 是（expanded=false） | 系统默认 | 否 | 否 | Dialog 确认（删除）可叠加 |
| Dialog 出现/消失 | 调用方状态 | 是（dismiss） | 待确认（GlassAlertDialog 未细读动画） | 否（window） | 删除确认会删记录（预期内数据变更，非 layout mutation） | S8 可在其下 |
| PDF_EDITOR 导航保护 | 编辑页路由驻留 | 是（退出后才导航） | nav 默认 | 否 | 编辑保存/覆盖（独立 batch，已有备份恢复） | 外部打开等待退出 |

## 3. Architecture risks（只记录，不修）

1. R11 已闭合（2026-09-18）：picker 是 S4 popover 内部行展开（默认 AnimatedVisibility fade+expand），无独立 glass surface/backdrop；但默认 `expandVertically` 与 motion-token 纪律的关系待下一阶段裁定（见风险 5 同类问题）。
2. 两组 chrome fade 时长并存（PdfViewerScreen :1563–1593 vs :1666–1667）；motion token 是否应收敛——下一阶段决定。
3. HubTabTransitions 曲线未细读；SEARCH 无 hub 转场是刻意还是遗漏——下一阶段确认。
4. GlassAlertDialog 动画未细读；Dialog 在跨 window 无采样路径下 interaction=true 的 press 语义——下一阶段确认。
5. S4 morph 用 scaleX/scaleY（内容 0.94→1.0）与 M1racleMotion “无 scale bounce”token 的关系——测试当前允许（MoreMorphSpring 断言存在），语义是否一致待下一阶段裁定。
6. Optical 与 interaction 回归区分：本审计即基线；R36.2 optical checkpoint（light→dark→edge→shadow→lens）完成前不动以上任何实现。

## 4. Viewport mutation map（当前结论）

所有 glass surface 均为 overlay / popup / window；无一修改 document layout。唯一几何操作是 S5 的测量抵消（wrapper 足迹守恒，有 PdfChromeVisibilityTest 覆盖）与 S4 容器 morph（ReaderMoreHostUiTest 覆盖）。
