# R36.2 Dark Recipe Evaluation — 2026-09-18

基线 clean HEAD `99ce4b7`（light 已回归 frozen .86/18dp）。
本轮只动 dark tint 候选；production frozen 不动（除非发现明确 architecture/contract bug——本轮未发现）。
单次 adb 探测为空，无真机；视觉列 NOT_RUN，无批准。

## 1. CURRENT DARK AUTHORITY（生产 resolver 实测链，非文档猜测）

调用链：`resolve(role, dark=true)` → baseRecipe（下表）→
`idleSurfaceAlpha = recipe.darkSurfaceAlpha`（GlassMaterial.kt:368）→
`surfaceColor = darkTint.copy(alpha)`（:384），press +0.04（:369），α 上限 .98（:385）。
`SOLID_FALLBACK` 时：blur→0、vibrancy off、family→FALLBACK，tint/edge 取 fallbackPlate（#FAFAFA/.97 ｜ #121212/.97），shadow 保留 role 基线（:342–358）。

| Role | tint | α | blur | shadow r/α | highlight | lens | vibrancy | fg | family | layer |
|---|---|---|---|---|---|---|---|---|---|---|
| Control | Black | .80 | 18dp | 6dp/.28 | 无 | off | on | 1/.75 | INTERACTIVE | Root |
| Capsule | Black | .80 | 18dp | 6dp/.28 | 无 | off | on | 1/.75 | CONTENT | Root |
| Menu | Black | .80 | 18dp | 6dp/.28 | 无 | off | on | 1/.75 | CONTENT | Root+menuExport |
| ExpandedMenu | Black | .86 | 18dp | 6dp/.28 | 无 | off | on | 1/.75 | CONTENT | Root+export |
| SettingsCard | Black | .86 | 18dp | 6dp/.28 | 无 | off | on | 1/.75 | PANEL | RootAndMenu |
| Dialog | #121212 | .97 | 18dp | 18dp/.46 | .5dp/.38 | off | off | 1/.75 | PANEL | window 不采样 |
| Dock | #171717 | .98 | 0 | 2dp/.20 | 无 | off | off | 1/.75 | FALLBACK | 无 backdrop |
| ProgressiveChrome | Black | .62 | 16dp | 0 | 无 | off | off | 1/.75 | CONTENT | Root |
| PreviewCapsule（仅预览） | Black | .30 | 4.32dp | 6dp/.46 | .5dp/.78 | on 16/32 | on | 1/.75 | INTERACTIVE | — |

共性：brightness 0、contrast 1、saturation 1、depth off（PreviewCapsule 除外）、edge 宽 0（Dialog/Preview 除外）。
来源：`regularFrost`（Black/.80/18dp/6dp/.28）、`panelFrost`（+.06 α）、`fallbackPlate`（.97/18dp/18dp/.46）、`appOverlayPlate`（#171717/.98）、`progressiveLegacy`（.62/16dp）、`previewCapsule`。

## 2. DARK_ROLE_OPACITY_DELTA（intentional hierarchy）

- Control 系 `.80`；Panel 系 `.86`。依据：`panelFrost` 注释“Larger reader panels stay denser than compact overlay chrome”——显式设计意图。
- 定义：**DARK_ROLE_OPACITY_DELTA = +.06（panel over control）**。Light 侧 +.04，各自冻结。
- 规则：候选 family 整体平移、保持 +.06；禁止统一成单值。

## 3. 候选 families（delta 语义，frozen blur）

| Family | Control | Panel | 语义 |
|---|---|---|---|
| D0 FROZEN BASELINE | .80 | .86 | 当前生产 authority |
| D1 SLIGHTLY MORE TRANSMISSIVE | .74 | .80 | baseline −.06 |
| D2 MORE TRANSMISSIVE | .68 | .74 | baseline −.12 |
| D3 SLIGHTLY DENSER | .86 | .92 | baseline +.06 |

- 实现：`DarkD1/D2/D3` previewPreset = 全 role 统一 delta（`darkSurfaceAlpha + Δ`，coerce 0–1），light α / blur / 其余全不动。Dialog .97+.06 与 Dock 已在 resolve .98 上限——clamp 已记录。
- Harness 注记：无特殊 comparison blur——dark 候选渲染于生产 frozen blur（overlay/panel 18dp），blur 与 tint 实验不混。
- Control/Panel 最佳区间若分叉，记录分叉、不强行统一。

## 4. 离线合成分析（分析，非视觉证据；tint Black；前景白字；数值已用脚本验算）

| family | 白底表面 / 白字对比 | 深色底#182027 / 白字对比 | 纸底 | 彩色(青/红) |
|---|---|---|---|---|
| D3 (.86/.92) | #242424 / 15.5:1 | #030405 / 20.5:1 | #222120 | #0B1614 / #1E0F0E |
| D0 (.80/.86) | #333333 / 12.6:1 | #050608 / 20.3:1 | #31302D | #101F1D / #2A1614 |
| D1 (.74/.80) | #424242 / 10.0:1 | #06080A / 20.1:1 | #3F3E3B | #152926 / #371D1A |
| D2 (.68/.74) | #525252 / 7.8:1 | #080A0C / 19.8:1 | #4E4C49 | #19322E / #442320 |

读法：

- 白底（场景 A，最高优）：D0 有效表面 #333333——已是深灰板。D1/D2 向 #424242/#525252 中灰漂移，dirty slab 风险单调上升；D3 #242424 最实。**若 A 场景下 D1 已显脏，D1/D2 在该场景即 FAIL，不用等真机猜——最终仍需真机确认。**
- 深色底（场景 B）：四档白字对比 19.8–20.5:1 无判别力；风险是反向的——表面与背景同化（D2 #080A0C vs 背景 #182027），靠 6dp/.28 阴影和白字维持边界，edge/shadow 本轮冻结，只记录。
- 彩色底（场景 C）：tint 纯黑无色偏，污染方向是“压暗 + 失饱和”，D2 最重。
- 判别力排序：A（白底灰板）> E（中性底）> C（彩色压暗）> B/D（深色底/长文，易全军通过）。

## 5. 评价矩阵（视觉列 NOT_RUN）

场景 A/B/C/D/E × family D0–D3 × 维度 7.1–7.7：真机轮逐格记录 PASS/BORDERLINE/FAIL，不做总分。
分析侧先行判断（待真机推翻或确认）：

- 7.4 Gray contamination：D2 在 A 场景高风险（#525252 中灰板）；D0 #333333 是否显脏由真机回答。
- 7.3 Surface separation：B 场景 D2 风险最高（表面 #080A0C 近背景），shadow 冻结、只记录。
- 7.6 Role hierarchy：四档均保持 +.06 delta（契约保证），此维度离线 PASS。
- 7.7 Cross-background stability：D2 白↔深落差最大（#525252 ↔ #080A0C），最可能“白底脏、深底消失”两头失败。
- 7.1/7.2/7.5：真机判定；分析仅排除“白字对比不足”（四档白底最低 7.8:1，AA 可过，thin icon 待真机）。

## 6. Edge/Shadow 纪律

任何 candidate 不得靠调 edge/shadow/lens 获救：本轮只记录 tint 自身成立度（BORDERLINE/FAIL），下一批才处理 edge/shadow。B 场景边界问题、C 场景染色问题如实记录为 tint 维度的 BORDERLINE，不在本轮修。

## 7. 前景独立（契约）

`foreground.contentAlpha = 1f` 由构造硬编码（:407–410），与 surface α 无关。新契约 `dark candidates keep foreground policy and frozen channels`（GlassOpticalPolicyTest）断言 Dark preset 下 foreground/muted/brightness/blur 全不变。PASS 后写入 §9。

## 8. Preview 地位

Settings preview（D0–D3 切换，dark toggle）仅 harness。评价优先级：Reader PDF > Reader Markdown > 真实 overlay/menu > Settings preview。不因预览卡片批准 recipe。

## 9. 裁决：NOT APPROVED（本轮，无真机）

- Production dark authority 未动（D0 = .80/.86，与 frozen 一致）；无 architecture/contract bug 发现。
- 工程状态：DARK families D1–D3 harness 就绪，隔离契约 PASS（含前景独立、frozen 通道、+.06 层级保持、light 侧零影响）。
- 最终语义：Production DARK = CLOSED_ON_FROZEN_BASELINE（D0）；Experiment = DEVICE_APPROVAL_PENDING。
- 验证：`compileReleaseKotlin` PASS；`testQaUnitTest` **1506 / 0 / 0 / 0**；`assembleRelease` 仍 BLOCKED_EXTERNAL_CONFIGURATION（未绕过）。
- 真机轮：D0–D3 × 场景 A（最高优）/E/C/B/D 同变量对照进 `.external_outbox/r36-2/dark/` 四目录；edge/shadow/lens 保持冻结，只记录 tint 自身成立度。
- 明确未发生：FINAL_VISUAL_PASS、authority 变更。批准 = 显式 promotion 事件。
