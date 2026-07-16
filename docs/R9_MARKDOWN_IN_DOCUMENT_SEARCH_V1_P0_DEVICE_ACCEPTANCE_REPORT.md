# R9 Markdown 文内搜索 V1 — P0 真机验收报告

**Object:** `R9_MARKDOWN_IN_DOCUMENT_SEARCH_V1`  
**Phase:** P0 device acceptance  
**Date:** 2026-07-16  
**最终结论:** **P0_DEVICE_ACCEPTED**  
**Commit:** 本报告随 P0 本地 checkpoint 提交（见 checkpoint 记录）  

---

## 0. 性能调查收口（同步）

| 项 | 内容 |
|----|------|
| Object | `R9_MARKDOWN_SCROLL_PERFORMANCE_RECOVERY` |
| Status | **`ready_for_human_perf_acceptance`**（**不是** device_accepted） |
| 相对 9d8a32a | 无连续滑动帧时间回归（正式 A/B） |
| 生产滚动代码 | 未修改 |
| 说明 | QA JIT/调试开销；性能悬浮层扰动；未证明全帧锁 120 FPS；无需基于现有证据改代码 |
| 原始证据 | `dist/r9-markdown-scroll-perf/`（**不入库**） |

---

## 1. 环境

| 项 | 内容 |
|---|---|
| 分支 | `master` |
| HEAD（验收时） | `9d8a32a` + 未提交 R9 P0 工作区 |
| APK | QA `com.markleaf.app.qa` / `app-qa.apk` |
| APK SHA-256 | `db0ba83d81e0d27939708606411745e567cedf47895640682e2f00d5ac7244dd` |
| 设备 | `25098PN5AC` |
| 证据（本地不入库） | `dist/r9-p0-device-acceptance/` |
| Fixture | `testdata/r9_search_p0/`（合成无隐私） |

---

## 2. 首次验收 vs 本轮补测

### 2.1 首次已可靠 PASS（引用旧证，未无意义重跑）

| 项目 | 首次结果 | 证据 |
|------|----------|------|
| `hello **world**` → `hello world` | PASS | R3_hello `1 / 1` |
| 英文大小写 | PASS | R4_sample `1 / 2` |
| 隐藏 URL | PASS | R3 `0 / 0` |
| 图片 source | PASS | R3 `0 / 0` |
| 图片 alt | PASS | R3 `0 / 0` |
| 多匹配 next（小文档） | PASS | C `1/80`→`4/80` |
| Quote / inline | PASS | R4 |
| FATAL / ANR | PASS | logs |

### 2.2 首次失败 / 不足的根因（fixture / 工具）

| 问题 | 性质 | 说明 |
|------|------|------|
| 大文档 needle 0/0 | 验收/自动化 | 导航与旧 token；非已确认生产缺陷 |
| 空 query 未见稳定 `—` | 证据不足 | 清空不彻底时误读 `0/0` |
| 中文 | adb 不可靠 | 需真实输入法 |
| `BODY_TOKEN_A` → `BODYTOKENA` | fixture 语法 | `_` 被 italic 解析 |
| 隔离 / External / 2000+ | 证据不足 | R5 补测解决 |

### 2.3 R5 补测 + 人工中文

| 项目 | 首次 | 本轮 | 最终生产判定 |
|------|------|------|--------------|
| 大文档 SEARCHNEEDLE | 不足 | **PASS** `1/44`，next×20 → `21/44` | **PASS** |
| 空 query `—` | 不足 | **PASS** open/clear=`—` | **PASS** |
| 快速输入取消 | 不足 | **PASS** 最终仅最新 query | **PASS** |
| 文档 A/B 隔离 | FAIL | **PASS** B 不继承 A | **PASS** |
| External Open E1/E2 | 不足 | **PASS** 不继承 query/结果 | **PASS** |
| 2000+ 截断 | 不足 | **PASS** `1/2000+`，清空 `—` | **PASS** |
| **真实中文输入** | 未充分 | **PASS**（见下） | **PASS** |
| 崩溃/ANR | PASS | PASS | **PASS** |

### 2.4 中文真实输入（人工）

| 项 | 结果 |
|---|---|
| 真实中文输入 | **PASS** |
| 查询 | `临床检索`（用户系统输入法，非 adb） |
| 中文匹配数量 | **2** |
| 初始状态 | **1/2** |
| next | **2/2** |
| previous | **1/2** |
| 乱码 | 无 |
| 崩溃/ANR | 无 |
| 同 block | 两项位于**同一 Markdown block** |
| 视觉 | 整块黄底（P0 block-level）；**无法**从视觉区分 1/2 与 2/2 |

**判定：** 搜索语义与计数/导航正确；同 block 不可视区分是 **已知 P0 限制 / P1 缺口**，**不是**搜索语义失败。

---

## 3. P0 已知限制（必须写明）

P0 **不是**完整搜索体验：

1. **只定位匹配所在 block**（block 级滚动）。  
2. **同一 block 内多个匹配无法视觉区分**（计数可切，高亮同一整块）。  
3. **整块背景高亮**，不是字符级高亮。  
4. **长段落内不能精确滚到匹配字符**。  
5. **不支持跨 block 匹配**。  
6. **P1/P2 尚未实现**（字符级高亮 / 块内精滚）。

---

## 4. 最终结论

**P0_DEVICE_ACCEPTED**

生产功能门禁（可见文本搜索、隔离、截断 UI、中文、空 query、大文档多匹配、无崩溃）均已通过。  
同 block 多命中仅计数可区分、视觉不区分，记为 **P0 产品边界**，由 **P1 字符级高亮** 解决。

**未 commit 边界（本报告随 checkpoint）：** 不含 dist 证据、APK、截图、性能原始产物。
