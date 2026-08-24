# Phase C — Release Hardening Plan

| 字段 | 值 |
|------|-----|
| 日期 | 2026-08-24 |
| 基线 HEAD | 455a9aa+（B 阶段全部收尾后） |
| 性质 | 发布收口规划；零代码改动 |
| 输入 | docs/B4_PENDING_ISSUES.md · docs/BATCH_B_FINAL_REPORT.md · docs/A3_FINALIZATION_REPORT.md · build.gradle.kts 签名现状 |

---

## 0. 发布形态定义（决定 blocker 判定基准）

两种形态，blocker 集不同：

| 形态 | 定义 | 签名要求 |
|---|---|---|
| **C-local**：本地/侧载分发给可信用户 | 直接交付 APK 文件安装 | debug keystore 可接受（现状）|
| **C-store**：公开渠道（Play 或官网直发） | 公开分发 | **必须**独立 release keystore |

**本计划默认以 C-local 为首个发布目标**，C-store 列为条件性后续。此定义使多数 pending 可判定为非阻塞。

## 1. Blocker 评估表（B4 全量 pending + 新增发现）

### 1.1 结论总表

| # | 项 | 阻塞 C-local？ | 阻塞 C-store？ | 独立批次？ | 处置 |
|---|---|---|---|---|---|
| P-1 | zip 身份统一（重复行） | 否——质量瑕疵非数据损坏；单入口使用可完全规避 | 否（同左，但建议修后再广发） | 是：Policy Batch 5（小批，方案已写明 §B4-P1） | **延期至 C-local 后首批** |
| P-2 | ZIP 导出取消 UX 歧义 | 否——完成态产物合法可用，仅反馈语义模糊 | 否 | 并入 UI 解冻批 | 延期 |
| P-3 | SourceTooLarge 文案误读 | 否——功能正确拒绝，仅文案误导 | 建议 C-store 前完成（公开用户无法被告知上下文） | 并入 UI 解冻批 | 延期（C-store 前置） |
| P-4 | Log.w 门控 | 否 | 否 | 搭车任意批次 | 延期 |
| P-5 | force-stop 中途复现 | 否——证据完整性项，非缺陷 | 否 | 设备会话搭车 | 延期（观察项） |
| 观察 | ThumbnailLoader closeAsync / B-1 不中断长程复测 | 否 | 否 | 设备会话搭车 | B-1 长程复测**建议 C-local 前**完成（发布诚实性） |
| **R-1** | **Release keystore（新增发现）** | 否 | **是——硬阻塞** | 是：C-store 前独立小批（生成 keystore、配置 signingEnv、验证 16KB page-align 重签约束） | 条件阻塞，随形态决策 |
| **R-2** | 版本发布纪律（version.properties bump + CHANGELOG 条目 + git tag） | **是**（可追溯性） | 是 | 并入发布执行批 | 见 §3 |
| **R-3** | README 刷新（截图时效、能力表述与 v0.1.x 一致） | 否（内容任务） | 是（门面准确性） | 独立文档批 | C-local 前建议完成 |
| **R-4** | 隐私/数据安全声明页 | 否（侧载不需要） | **是**（商店政策） | 文档批 | C-store 前置 |

### 1.2 判定依据摘要

- **P-1 不阻塞**的理由：缺陷表现 = 库中出现同名两行（内容等价、均可打开阅读）；无数据丢失、无崩溃；规避方式明确（固定用 FAB 导入 zip）。修复方案已写明待批准，属质量迭代。
- **P-2/P-3 不阻塞**的理由：导出与导入的**主路径行为正确**（完成产物完整、超限拒绝生效），问题仅在反馈语义层；且均落在 UI 冻结范围。
- **R-2 升为 blocker** 的理由：不可追溯的发布物违反工程公约 §7（可复现构建）与本仓库既有 release gate 设计意图。
- **R-1 为条件阻塞**：debug keystore 对 C-local 是既定策略（RELEASE_READINESS.md 已记载）；一旦分发对象超出个人可控范围即不成立。

## 2. Non-blocker 清单（明确延期，不进 C 阶段）

P-1、P-2、P-3(至 C-store)、P-4、P-5、两项观察项 —— 全部记录于 B4_PENDING_ISSUES.md 跟踪，C 阶段不展开。

## 3. C 阶段执行批次提案

| 批次 | 内容 | 产出 |
|---|---|---|
| **C-0（本文件）** | 发布规划与判定 | 本文档 |
| **C-1 发布执行批** | R-2：versionCode bump → CHANGELOG → git tag `v0.1.0` → assembleRelease → 三方 sha256 核对 → 交付 | tag + 发布 APK + 台账 |
| **C-2 证据闭环批**（设备会话） | B-1 不中断长程复测；P-5 force-stop 场景；ThumbnailLoader 观察；安装冒烟（§5 清单 E 组） | Device Evidence Closure 终版 |
| **C-3（可选）UI 解冻批** | P-2 + P-3 + SourceTooLarge 事件分支 | 小批 |
| **C-4（条件）C-store 前置批** | R-1 keystore + R-4 隐私声明 + P-1 zip 身份（若选择广发前修） | store 候选包 |

## 4. Evidence Requirements（证据要求）

### 自动化（每次发布候选必须）
1. `:app:testQaUnitTest` 全绿（当前基线 1149/0）
2. `:app:assembleRelease` PASS + outbox/Download 双侧 SHA256 一致
3. release dex 抽查：debug telemetry/log 字符串 absent（A3-1 方法复用）

### 设备（C-1 发布物安装核验）
4. 安装身份：versionName/versionCode 与 version.properties 一致
5. 冷启 Home 正常、最近列表渲染正常

### 设备（C-2 补证）
6. B-1 六点采样曲线（不中断全程自动化）
7. P-5 大范围导出 + 中途 force-stop 残留检查
8. ThumbnailLoader 长会话观察记录

### 人工验收（公约 §2.3，视觉/交互）
9. Markdown/PDF 打开阅读主路径
10. 外部打开 VIEW/SEND（md、pdf、zip 各一次）
11. FAB 导入（md、pdf、zip 各一次）
12. 设置三项（字号/行高/边距）生效

## 5. Final Acceptance Checklist（C-local 放行清单）

全部勾选方可放行：

- [ ] 测试：testQaUnitTest 全绿（≥1149 且 0 fail）
- [ ] 构建：assembleRelease PASS；outbox 与 Download SHA256 一致
- [ ] 卫生：release dex 无 debug telemetry 字符串
- [ ] 版本：version.properties 已 bump 且 ≥21；CHANGELOG 有对应条目；git tag 存在
- [ ] 设备安装：身份核对通过；冷启正常
- [ ] 冒烟：§4-9~12 十二项人工验收全过
- [ ] 证据：C-2 四项采集归档或明确标注 PENDING 不阻塞
- [ ] 文档：README 与发布版本一致；B4_PENDING_ISSUES 与实际同步
- [ ] 隐私：manifest 零权限复查（ReleaseHygieneHostTest 绿）

## 6. 明确不做（C 阶段边界）

新功能 · UI 重设计 · 架构重构 · ZIP identity 实现（P-1 待批准后另立）· TOC UX · EPUB · Liquid Glass 调参 · renderer/generation/手势模型改动。
