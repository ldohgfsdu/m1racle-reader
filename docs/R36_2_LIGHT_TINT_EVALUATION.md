# R36.2 Light Tint Evaluation — 2026-09-18

基线：clean HEAD `bed8bf4`（png 已移入 `.external_outbox/r36-2/recovery/`）。
本 batch 唯一目标：决定 light-tint authority 是否可以从 frozen `.86` 变更。
单次 adb 探测为空，无真机、无截图证据；本轮不批准、不改 frozen 断言。

## 1. 候选（5 个，不做微调扫参）

| ID | Light tint α | 地位 |
|---|---|---|
| A frozen baseline | .86 | 当前正式 authority（GlassMaterialRegressionTest / ReaderChromeMaterialTest 冻结值） |
| B candidate | .45 | 工作候选（代码 + 设备现装包均为此值；Batch 2 暂留，未批准） |
| C candidate | .55 | 本轮新增中间候选（harness only） |
| D candidate | .65 | 本轮新增中间候选（harness only） |
| E candidate | .75 | 本轮新增中间候选（harness only） |

## 2. 单变量控制（已证明，非声明）

对照只允许改变 light tint α。冻结：blur 26dp、radius/stroke/edge/shadow/lens/foreground/backdrop source/geometry/motion。

- Engine：`GlassMaterialEngine.resolve(role, env, previewPreset)` 仅在 preview 分支改 `blur=26dp + light/dark α=tint`；生产调用方（previewPreset=null）不受影响。`previewPreset` 唯一调用方是设置页 `BlurComparisonPreview`（grep 已核对）。
- 契约：`GlassOpticalPolicyTest.light tint candidates vary only tint at frozen blur`（10 个 tint preset × blur/shadow/highlight/vibrancy/lens/family/foreground/chromatic/brightness/saturation 全冻结断言）PASS。
- Harness：设置页对照新增 .75/.65/.55 标签，与 enum 同序；Caption 显示实时 blur/tint/折射。

## 3. 对照场景（真机轮必须同变量执行；本轮全部 NOT_RUN）

- A. PDF 高密度正文（黑字白底教材，fixture R36-2 A 页）：正文是否穿透干扰菜单文字；glass 是否变白塑料；hierarchy。
- B. PDF 图表/深色内容（fixture D 灰阶图 / E 彩色图 + 黑白文字）：复杂背景下 foreground contrast 是否稳定。**判别力最高的场景**（见 §5 分析）。
- C. Markdown 普通长文：控件与正文关系；是否过重；是否像独立卡片。
- D. Home / neutral backdrop：低复杂度背景下高 α 是否变实体板。
- E. Settings preview：仅 harness；Reader scene 优先级最高，不作为 authority 依据。

Evidence 目录：`.external_outbox/r36-2/light-tint/{baseline-086,candidate-045,candidate-055,candidate-065,candidate-075}/`（已建，当前仅 recipe JSON + 分析表；截图待真机同变量补齐：同页、同 scroll、同 chrome、同 viewport、同 theme）。

## 4. 离线合成分析（分析，不是视觉证据）

方法：tint #F5F5F5 × α over 代表性 backdrop，求有效表面色（未计 26dp 模糊；模糊压制高频字形穿透，不改变大面积亮度——结论偏保守方向可用，偏乐观方向不可用）。
前景：菜单文字不透明绘制于表面之上，对比度 = 黑字 vs 有效表面色。

| α | backdrop bleed | 白底表面 / 黑字对比 | 深色底(#182027)表面 / 黑字对比 | 纸色底 | 彩色(青/红)表面 |
|---|---|---|---|---|---|
| .86 | 14% | #F6F6F6 / 19.4:1 | #D6D7D8 / 14.6:1 | #F5F4F2 | #DEE9E7 / #F0E2E1 |
| .75 | 25% | #F8F8F8 / 19.8:1 | #BEC0C2 / 11.5:1 | #F5F3F0 | #CCDFDC / #EDD3D1 |
| .65 | 35% | #F8F8F8 / 19.8:1 | #A8AAAD / 9.0:1 | #F5F3EF | #BBD6D2 / #E9C6C3 |
| .55 | 45% | #FAFAFA / 20.1:1 | #929598 / 7.0:1 | #F5F2ED | #AACDC8 / #E6B8B4 |
| .45 | 55% | #FAFAFA / 20.1:1 | #7B8084 / **5.3:1** | #F4F1EB | #9AC4BE / #E3ABA6 |

读法：

- 白底上 5 个候选几乎无差（19.4–20.1:1）——**白页截图没有判别力**，不能作为批准依据。
- 深色底上单调恶化：.86→14.6:1，.45→5.3:1（接近正文 4.5:1 底线；小字号/secondary/thin icon 只会更差，且未计模糊残留结构）。
- 彩色底上 .45 表面色相偏移最大（#9AC4BE / #E3ABA6），layer 中性最差。

## 5. 评价矩阵（视觉列全部 NOT_RUN；分析列按 §4）

| 准则 | .86 | .75 | .65 | .55 | .45 |
|---|---|---|---|---|---|
| 5.1 Background suppression（分析） | 强（14% bleed） | 中上 | 中 | 中下 | 弱（55% bleed，深色区结构必穿透） |
| 5.2 Foreground readability（分析，黑字） | 白19.4/深14.6 | 白19.8/深11.5 | 白19.8/深9.0 | 白20.1/深7.0 | 白20.1/深**5.3** |
| 5.3 Material translucency（分析） | 偏实 | — | — | — | 偏透（深色底） |
| 5.4 Layer separation（分析） | 稳定 | — | — | — | 深色底风险 |
| 5.5 Lightness stability（分析） | 白↔深色差小 | — | — | — | 白↔深落差最大 |
| 5.6 content-first/quiet/clear（待真机） | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 场景 A/B/C/D/E 真机 | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN（Batch 2 旧截图非同变量对照，不作为本 batch 证据） |

## 6. .45 风险（本 batch 最大风险，已确认）

.45 不得默认成为结论。它在白页上“看起来没问题”是因为 backdrop 本身就是白色（§4 第一行）——这是 backdrop 的功劳，不是 recipe 的功劳。到了彩色图/黑色区域/复杂 PDF，它放过 55% 背景亮度，深色底表面掉到 #7B8084（黑字 5.3:1）。在 B 场景同变量对照完成前，.45 的真实位置未知。**禁止用白页截图批准 .45。**

## 7. 裁决：NOT APPROVED（本轮）

- 最终 authority 保持 frozen `.86`；4 个 frozen optical tests 不改，继续作为 gate。
- 工程状态：`LIGHT_TINT_ENGINEERING_CANDIDATE`；设备状态：`CANDIDATE_READY / DEVICE_APPROVAL_PENDING`。
- 站立工作候选仍为 `.45`（代码与现装包已有值，保持不动，不回滚——回滚本身也是一次无证据的 authority 变更）。
- 真机轮任务：harness 内 5 候选 × 场景 B（深色/彩色）优先对照，同变量截图进 `light-tint/` 五目录；白页场景仅作 sanity，不计票。
- 明确未发生：FINAL_VISUAL_PASS、authority 变更、frozen 断言更新、旧 .86 deprecated。批准条件：B 场景同变量证据 + 4 frozen tests 按新 authority 更新（token 引用，不散布 magic number）。
