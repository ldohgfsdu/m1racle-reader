# A2 Validation Closure Draft（验证收口草稿）

**状态**：DRAFT — 待人工补录两项后转正式 Closure
**代码基线**：HEAD `b6b8454`（A2 Batch 1 `f15ed94..4ed0ade`，Batch 2 `b066991`）
**构建产物**：`.external_outbox/markleaf-release-latest.apk`
SHA-256 `dff3b3cf4c2e7785d98e8cce4ebbaffc60297277a5a41ea9896d7bff4778de1e`（11,509,615 B，versionCode 21 / v0.1.0）
设备侧副本：`/storage/emulated/0/Download/markleaf-release-latest.apk`（SHA 一致）

---

## 1. 自动化验证（automated_verified）

| 门禁 | 结果 |
|---|---|
| `:app:testQaUnitTest` | **1094 tests / 0 failures** |
| `:app:assembleRelease` | PASS（产物见上） |
| 隐私门禁 | manifest 零网络权限，ReleaseHygieneHostTest 通过 |

## 2. 人工设备验证（manual / 无 adb）

| # | 场景 | 结果 | 备注 |
|---|---|---|---|
| 1 | 长会话内存（大 PDF 长滚动+缩放+深浅色切换） | **PASS** | 无渐进卡顿/崩溃/被杀 |
| 2 | 章节导出取消（P0-2） | **PENDING** | 见 §2.1 三项待补录 |
| 3 | fling 流畅度 | **PASS** | 无新增卡顿、顶栏方向行为正常 |
| 4 | 双指缩放回归 | **PASS** | 无白闪、settle 稳定、chrome 不误触发 |
| 5 | 横排/TOC 跳转/缩略图/外部打开冒烟 | 未逐项回报 | 与新发现问题合并观察 |

### 2.1 导出取消待补录（人工三问）

1. 点取消时导出处于哪个阶段？（复制页面中 / 正在写归档 WritingArchive / 其他）
2. 取消后所选目标位置是否生成了文件？（有 / 无）
3. 若有文件：能否正常打开且内容完整？（完整可打开 = FAIL 信号；打不开/残缺 = 符合预期）

> 无法确认则本项保持 **PENDING**，不阻塞其他项收口。

## 3. 发现的问题（均已开独立记录，不在 A2 处理）

### 3.1 百度网盘「打开方式」候选缺失墨阅 —— 诊断记录（只读，不改 manifest）

**静态事实（当前 VIEW 过滤器）**：
`scheme=content` AND `mimeType ∈ {application/pdf, application/x-pdf, application/octet-stream,
application/zip, application/x-zip-compressed, text/markdown, text/x-markdown, text/plain}`。

**候选根因（按可能性排序）**：
- H1 网盘以 `file://` URI 发射 → 与 content-only 方案完全不匹配；
- H2 Intent 未携带 MIME（或私有 MIME）→ 带 MIME 约束的过滤器无法命中；
- H3 网盘自身包可见性/白名单策略（Android 11+ `<queries>`），应用侧无法修复；
- H4 MIME 为我们列表外的罕见类型。

**待收集证据（由人工提供）**：
| # | 项 | 用途 |
|---|---|---|
| E1 | 设备 Android 版本 | 判断可见性策略适用性（H3） |
| E2 | 网盘内该 PDF「打开方式」弹窗的完整候选列表（截图或抄录） | 判断是全缺失还是排序靠后（H1/H2 vs H3） |
| E3 | 系统文件管理器对同一 PDF 的候选列表（墨阅在否） | 隔离网盘因素 |
| E4 | （可选，需 adb 恢复后）复现时抓取发射 Intent：`adb logcat -s ActivityTaskManager:I | grep -i "start"` | 直接判定 scheme/MIME（终局证据） |

**判读映射**：E3 有墨阅 + E2 没有 → H1/H2（可评估加兼容过滤器）；E3/E2 都没有 → H3 或安装身份问题；待 E4 定案。修复动作在证据到位前**不做**。

### 3.2 TOC 目录排版交互重构 —— Issue 记录（等待产品确认，不实现）

**需求描述（用户原话归纳）**：当前节应位于当前章之下；顶部为当前章标题，下方节列表独立滚动；手势先滚节列表，触顶/底后再带动外层章列表滚动。

**待产品确认项**：
| # | 问题 | 选项 |
|---|---|---|
| Q1 | 外层章列内容 | a) 全部章节可滚、当前章自动展开其节 b) 仅显示“当前章+其节”一段 |
| Q2 | 点击其他章的行为 | a) 直接跳转该章起始页 b) 仅展开该章节列表再选节 |
| Q3 | 影响范围 | “设置教材页码”等高级功能行保持不动？ |

**技术备注**（供后续实现参考，非承诺）：涉及嵌套滚动（NestedScrollConnection 连接节列表与章列表）、当前章节定位与自动展开、与现有 outline 数据源/教材页码绑定共存；预计独立批次交付，带交互契约测试。

## 4. 环境工具记录（非项目依赖）

- `sshpass 1.10` 经 apt 安装于构建主机（root 下 Termux `pkg` 不可用）；仅作为主机侧环境工具存在，**不加入任何项目构建/依赖/脚本**。
- 构建主机约束重申：默认 JDK 25 会使 Gradle 失败，须 `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64`。

## 5. 收口条件

- [ ] §2.1 三问得到回答 → 第 2 项定 PASS/FAIL
- [ ] E1–E3 提供 → 网盘问题定性（修复另立批次）
- [ ] Q1–Q3 产品拍板 → TOC 重构另立批次
- 以上均不影响：**A2 两批代码修复本身维持已验证状态收口**。
