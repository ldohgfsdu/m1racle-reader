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
| **C-3 Release Promotion / Finalization** | 将 C-2.2 修复提交提升为最终 C-local 基线；重建、验签、记录 digest；旧 tag 不移动 | 最终 tag + 发布 APK + 台账 |
| **C-4 Release Closure** | 固化最终 source/tag/artifact/验证/验收证据；同步 checklist、CHANGELOG、known issues | Closure Report；C-local 状态 CLOSED |
| **后续 UI 解冻批（未排期）** | P-2 + P-3 + SourceTooLarge 事件分支 | C-local 后小批；不属于 C-3 |
| **后续 C-store 前置批（未排期）** | R-1 keystore + R-4 隐私声明 + P-1 zip 身份（若选择广发前修） | 不属于 C-local closure |

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

- [x] 测试：testQaUnitTest 全绿（1156 tests，0 failures/errors/skipped）
- [x] 构建：assembleRelease PASS；timestamped/outbox/Download SHA256 一致
- [x] 卫生：release dex 无约定的 debug telemetry 字符串
- [x] 版本：version.properties=22；CHANGELOG 有对应条目；最终 annotated tag 存在
- [x] 设备安装：C-2.3 对干净 `604d404` 构建完成身份、安装与冷启核对
- [x] 冒烟：C-2 主矩阵与 C-2.3 针对性回归构成 `HUMAN_ACCEPTED`；pinch zoom 保持独立 PENDING，不冒充 PASS
- [x] 证据：C-2.3 已归档；其余项目明确列为非阻塞 PENDING
- [x] 文档：README 与发布版本一致；B4_PENDING_ISSUES 与实际同步
- [x] 隐私：无 INTERNET/用户网络权限；ReleaseHygieneHostTest 绿（AndroidX 自动生成的非导出 receiver 内部权限除外）

## 6. 明确不做（C 阶段边界）

新功能 · UI 重设计 · 架构重构 · ZIP identity 实现（P-1 待批准后另立）· TOC UX · EPUB · Liquid Glass 调参 · renderer/generation/手势模型改动。

---

## 7. C-1 Release Execution Record（2026-08-24）

| 项 | 值 |
|---|---|
| Release commit | `5febda3bc0ea2582cdff276457ffec466e072650`（tag **v0.1.0**，annotated，指向精确）|
| versionCode | **22**（app/version.properties + APK badging 已核）|
| versionName | **0.1.0**（app/build.gradle.kts + APK badging 已核）|
| APK | `.external_outbox/markleaf-release-v0.1.0-20260824-153727.apk` = `-latest.apk`，11,223 KB |
| SHA256 | `e05386e2b9c32002044f7afefcb558efcd2798539c8b90c1f3693ae941b3e534` |
| 构建时间 | 2026-08-24 15:37 (UTC+8) |
| 签名 | debug DN `CN=Android Debug`（C-local 既定策略；apksigner verify 通过）|
| testQaUnitTest | **1149 / 0 failures / 0 errors**（`--rerun-tasks`）|
| dex 卫生抽查 | CHROME_STATE / PDF_SYSBARS / MARKDOWN_REFRESH 全 absent |
| 交付 | `/storage/emulated/0/Download/markleaf-release-latest.apk`（双侧 SHA 一致）|

### Pending（不阻塞 C-local 放行的收尾项）
1. 设备安装核验：安装时 adb 掉线（端口轮换）；APK 已在设备 Download 目录，人工安装后 `dumpsys package com.markleaf.app | grep version` 应显示 `22 / 0.1.0`
2. C-2 设备证据闭环（B-1 长程复测、P-5、ThumbnailLoader 观察、冒烟十二项）
3. CHANGELOG.md 已创建（v0.1.0 条目 + 已知限制两则）
4. Release 全量重建期间 `lintVitalAnalyzeRelease` 的自定义 detector 迁移打印 ASM `NegativeArraySizeException`；lintVital、R8、资源收缩与 assembleRelease 均成功，构建工具链告警诊断 pending，不阻塞 C-local

### Checklist 对照（§5）
- [x] 测试全绿 · [x] 构建 PASS + 双侧 SHA 一致 · [x] dex 卫生 · [x] 版本 bump=22/tag/CHANGELOG
- [ ] 设备安装身份核对（待设备在线）· [ ] 冒烟十二项（人工）· [x] 文档一致 · [x] 隐私零权限（门禁测试常绿）

---

## 8. C-3 Release Promotion / Finalization Record（2026-08-24）

### 8.1 发布来源与 tag 历史

| 项 | 值 |
|---|---|
| Final release source commit | `604d404978b5f09fdb4a9c825259ef6793f59949` — C-2.2 export cancellation fix |
| C-2.3 acceptance report commit | `7594a30dbe52667e663d54e5840395309fcfe26a` |
| C-2.3 status | **HUMAN_ACCEPTED** — ZIP mid-cancel / PDF cancel 均无 0-byte 或 partial 残留；正常 ZIP 回归通过 |
| 原 tag | annotated `v0.1.0` 保持指向 `5febda3bc0ea2582cdff276457ffec466e072650`，未移动、未覆盖 |
| 最终 tag | annotated `v0.1.0-c-local` 指向 `604d404978b5f09fdb4a9c825259ef6793f59949` |
| 新 tag 理由 | 原 `v0.1.0` 已公开标识 C-1 RC；移动会静默改写历史，因此以新 tag 标识 C-local 最终基线 |

### 8.2 最终构建与 APK 身份

最终构建在 detached、干净的精确提交 `604d404978b5f09fdb4a9c825259ef6793f59949` 上执行；不是从后续文档提交推导。

| 项 | 值 |
|---|---|
| `:app:assembleRelease --rerun-tasks` | **PASS** — 2026-08-24 22:26:31–22:31:19 UTC+8，289s，52 tasks executed |
| APK | `.external_outbox/markleaf-release-v0.1.0-20260824-223117.apk` = `.external_outbox/markleaf-release-latest.apk` |
| Size | **11,493,231 bytes** |
| SHA-256 | `4dd9c7ebacec6276fbca0615107ce2885f35c8a70bf81de0be818b39035a3ba4` |
| 三方一致 | timestamped / outbox latest / `/storage/emulated/0/Download/markleaf-release-latest.apk` 全部同 SHA、字节一致 |
| APK identity | `com.markleaf.app`, versionCode **22**, versionName **0.1.0**（`aapt dump badging`） |
| Signature | `apksigner verify` **PASS**；APK Signature Scheme v2；`C=US, O=Android, CN=Android Debug`（C-local 既定签名） |
| `:app:testQaUnitTest --rerun-tasks` | **PASS** — 1156 tests / 0 failures / 0 errors / 0 skipped；2026-08-24 22:31:30–22:33:33 UTC+8，123s |
| Release hygiene | `CHROME_STATE` / `PDF_SYSBARS` / `MARKDOWN_REFRESH` 均 absent；无 INTERNET 权限 |

C-2.3 实机安装包 SHA 为 `7285123ca78bf531580fe7bfda64c4e0e0d6737be342b2a9fe5c5711fd722e95`。本次最终重建 APK 的 digest 不同，但两者均由干净的精确 source commit `604d404` 构建，版本与签名身份一致。C-2.3 的 device claim 只覆盖其已安装 APK 与该 source commit；本记录不把新 digest 虚构成已再次装机验证。

### 8.3 已知 PENDING（不阻塞 C-local）

- C-2 原报告的 pinch zoom 仍为 PENDING，未在 C-2.3 针对性复测中改写为 PASS。
- B4 P-1 跨入口 ZIP 身份统一、P-2 ZIP 导出取消 UX 歧义、P-3 SourceTooLarge 专属文案、P-4 LocalImageLoader `Log.w` 门控、P-5 force-stop 中途写入复现均保持待批准/待排期。
- ThumbnailLoader 长会话竞态观察与 B-1 不中断长程复测仍为观察项。
- C-store 仍被独立 release keystore 与公开发布文档要求阻塞；本次状态只适用于 C-local。
- 最终 digest `4dd9c7...` 未单独重装；设备行为验收证据来自同一精确 source commit 的 C-2.3 fix-build。

## 9. C-3 Final Status

**READY_FOR_C_LOCAL_RELEASE**

`604d404978b5f09fdb4a9c825259ef6793f59949` 已提升为最终 C-local release source baseline；原 tag 历史保持不变，最终 artifact、digest、版本、签名、自动化和 C-2.3 人工验收证据均已落账。

---

## 10. C-4 Release Closure（2026-08-24）

最终收口报告：[`C4_RELEASE_CLOSURE_REPORT.md`](C4_RELEASE_CLOSURE_REPORT.md)。

- [x] Source：`604d404978b5f09fdb4a9c825259ef6793f59949`
- [x] Annotated tag：`v0.1.0-c-local` 可解析并精确指向 source；旧 `v0.1.0` 保持指向 `5febda3bc0ea2582cdff276457ffec466e072650`
- [x] Artifact：11,493,231 bytes；SHA-256 `4dd9c7ebacec6276fbca0615107ce2885f35c8a70bf81de0be818b39035a3ba4`
- [x] Identity：`com.markleaf.app` / versionCode `22` / versionName `0.1.0`；APK Signature Scheme v2 验签通过
- [x] Automated：assembleRelease PASS；testQaUnitTest 1156/0；release dex hygiene PASS
- [x] Device：C-2.3 `HUMAN_ACCEPTED`，证据 commit `7594a30dbe52667e663d54e5840395309fcfe26a`
- [x] Pending：B4 P-1/P-3/P-4/P-5 与 ThumbnailLoader/B-1 观察项冻结为 non-blocking；未实施
- [x] Scope：无生产代码、UI、新功能或架构变更

### C-4 Final Status

**CLOSED**
