# A3 阶段最终化报告（A3 Long Session Finalization）

| 字段 | 值 |
|------|-----|
| 日期 | 2026-08-23（夜间自主批次） |
| 基线 HEAD | 39be1fe（本报告提交为其后唯一增量，仅文档） |
| 性质 | 只读审计 + 设计 + 规划；零产品代码改动 |
| 关联批次 | A3-0 仓库卫生 · A3-1 Release hygiene · A3-2 Dead code · A3-3/3a Import infrastructure · Policy Batch 1/2/3 |

---

## 1. A3 已完成事项（事实清单）

| 批次 | 提交 | 内容 | 验证 |
|---|---|---|---|
| A3-0 仓库卫生 | b2c9875 | r7.md/R6 handoff 归档 docs/history；EPUB 文档标注 OUT OF SCOPE；忽略 benchmark/build | git clean |
| A3-1 Release hygiene | 11e48c0 | READER_PERFORMANCE_EXPERIMENTS_ENABLED 门控全部 debug telemetry/Log.i；R8 剥离验证（9 个 tag 全 absent） | 1094 tests / release dex 检查 |
| A3-2 Dead code | d71c939 | 零引用符号 12+、no-op 块、无效 alias/常量删除；ExternalImportOutcome 先迁移 androidTest 后删除 | 1094 tests / androidTest 编译 |
| A3-3 审查 | （报告） | sanitize×3 / provider probe×3 / 接受策略三角分析；产出合并裁决与测试需求 | 只读 |
| A3-3a Infra merge | 3b64786 | 单一 sanitizeFileName（golden 锁定）；ProviderMetadataSource 唯一采集器（matrix 9 例）；ExternalFileIntake 死路径删除 | 1113 tests |
| Policy Batch 1 | f56061a | T0 行为基线：门控矩阵 golden + FN⊆authority 证明 + E2E 差异对；修正 D1 为门控层伪影（入口级双路径一致） | 1125 tests |
| Policy Batch 2 | 5af1d0d | DocumentType.isAllowedImport 唯一权威 + text/* fallback 显式子规则（封闭拒绝表）；ALLOWED_MIMES 白名单与 Repository 冗余分支删除 | 1122 tests / parity 空 diff |
| Policy Batch 3 | 39be1fe | 200MB source limit 统一下沉双入口（hint 预检 + 无 hint 有界排水）；ImportResult.SourceTooLarge 统一错误语义；zip 解压三限独立保留 | 1128 tests / 边界矩阵 6 例 |

## 2. 当前状态

- **HEAD**：39be1fe（工作树在本文档提交前干净）
- **自动化基线**：`:app:testQaUnitTest` = **1128 tests / 0 failures**（JDK17 必需）；androidTest 源集编译通过
- **APK**：`.external_outbox/markleaf-release-latest.apk`（11.2MB，minify+shrink，已交付 `/storage/emulated/0/Download/markleaf-release-latest.apk`）；隐私门禁 manifest 零权限持续由 ReleaseHygieneHostTest 强制
- **adb**：离线（本会话全部 automated_verified，无 device_verified 声明）

## 3. 文档一致性审计（Part 1 — 只读记录，未修改）

| 文档 | 状态 | 问题 |
|---|---|---|
| `docs/PRD.md` | ✅ 一致 | 2026-08-23 范围修订版与本阶段无冲突 |
| `README.md` | ⚠️ 轻度超前 | 「外部打开」条目隐含 zip 可用（见 §4-A1）；其余能力表准确 |
| `AGENTS.md` | ✅ 一致 | 启动指针/PDF_VIEWER_HANDOFF 引用有效；outbox 交付流程有效 |
| `.memory/CURRENT_STATE.md` | ❌ 过期 | 停留在「HEAD b840438 / Phase 5 / 1082-1094 tests」；缺 A3 全部六批事实。**建议下一会话首个动作：按 §1 表重写工程状态段**（本批只记录不改） |
| `.memory/active_object.yaml` | ❌ 过期 | 仍挂 HOME_ALIGN closed 占位；应声明新 active object（建议：Batch B displayedBitmaps 内存界） |

## 4. Import Architecture Final Audit（Part 2 — 只读）

### 4.1 目标架构核验

```
External URI
  ↓ ProviderMetadataSource.collect        ← 唯一 ContentResolver 采集器（matrix 测试钉死）
  ↓ ExternalUriMetadata.resolve           ← 唯一名称/尺寸解析规则（纯 Kotlin）
  ↓ DocumentType.isAllowedImport          ← 唯一接受权威（Batch 2）
  ↓ MAX_BYTES boundary                    ← 200MiB 双入口统一（Batch 3）
  ↓ stage / import                        ← Path B 两阶段 / Path A 直导
  ↓ repository                            ← Room 权威
```

1. **第二套接受规则**：NONE。生产调用面恰好 3 处（Repository / Coordinator / Importer），全部直达同一函数；白名单与 OR 分支已物理删除。
2. **第二套大小限制**：NONE（源文件维度）。MAX_BYTES 单点定义于 ExternalDocumentImporter.companion，Repository 跨包引用同值。ZipPackageImporter 三限（5000 条目 / 200MiB 解压总量 / 80MiB 单条目）为解压侧独立维度，按约束保留不合并。MAX_MARKDOWN_BYTES(32MiB) 是打开期读取上限，非入库限制，语义不同不冲突。
3. **ExternalFileIntake 残留**：代码引用 NONE；仅存 4 处历史注释文字（KDoc 叙述），属合法历史记载。
4. **入口覆盖**：

| 入口 | 路径 | 接受权威 | 大小限制 | 备注 |
|---|---|---|---|---|
| SAF/FAB | HomeViewModel→importDocument | ✅ | ✅ hint+排水 | Batch 2/3 已覆盖 |
| VIEW | MainActivity→Coordinator→prepare/commit | ✅ | ✅ hint+复制环 | 同上 |
| SEND | MainActivity→Coordinator（同 ACTION 通道；SEND_MULTIPLE 明确 toast 拒绝） | ✅ | ✅ | 同上 |
| ZIP import | FAB zip→importZipPackage（解压三限）；**VIEW/SEND zip 见 A1** | ✅ | source ✅ + 解压 ✅ | — |

### 4.2 发现（只读，均不在本批修复）

- **[P1][功能缺口] Path B 不支持 zip 教材包提取**：manifest 对 VIEW/SEND 广告 `application/zip(+compressed)`；Coordinator/prepare 会放行并暂存 .zip，但 `DocumentType.resolve` 只产 PDF/MARKDOWN——commit 以 `MARKDOWN` 类型写入指向二进制 .zip 的行，NavGraph 将其路由到文本阅读器（乱码/失败）。CURRENT_STATE 与 README 的「外部打开含 zip」表述相对实际行为超前。
  建议：独立小批（Policy Batch 4 或 fix）：prepare/commit 检测 zip 名/mime 后改走 importZipPackage 等价提取，或显式 ToastUnsupported 收紧 manifest。需先补一个红 E2E 钉死现状。
- **[P3] 空白名在 fallback 门控层的字面接受**（""/" " 分类为无扩展名）：入口不可达（resolve 保证 FALLBACK_UNNAMED+扩展名归一），已有测试注释说明；如追求严格可在 fallback 加 blank-guard，非必需。

结论：导入链单一事实源目标达成；无平行权威残留。

## 5. displayedBitmaps 内存治理设计（Part 3 — 仅设计）

### 5.1 当前 retain 模型（ContinuousDocumentViewport.kt:190,292-374,1299）

- `displayedBitmaps: Map<Int, ImageBitmap>` 由 `remember(documentId)` 持有，初值 = state.pageBitmaps 快照；
- 增长通道两条：① LaunchedEffect(state.pageBitmaps…) 的 DisplayMergePolicy.ACCEPT 合并（366 行）；② tile commit 直接并入（1299 行）；
- **只增不减**：LaunchedEffect 仅遍历新快照的键做合并，缓存侧 `pageCache.retainOnly()` 缩窗后，离开窗口页面的旧位图仍滞留在 displayed 映射中；
- 生命周期终点 = documentId 变化或组合销毁（旋转重建 composition 会重置；进程内长时间连续阅读不会）。

缓存侧对照：PdfPageBitmapCache 有 HIGH 预算（MAX_HIGH_PAGES=1）+ retainOnly + trimMemory(OOM hook)，纪律良好——问题纯粹在 UI 层镜像副本。

### 5.2 风险场景（按危害排序）

1. **长会话大文档连续阅读**：每访问一页累计一张 HIGH 位图（典型 1080×2400×4 ≈ 10MB 级），翻阅 50 页 ≈ 数百 MB 驻留 → OOM/压缩卡顿；
2. **来回横跳阅读**（目录跳转往返）同样只增不减；
3. **pendingBitmaps/pendingMetadata 同构增长**（INTERACTION_FREEZE 积压），交互越长积压越大；
4. 低端机 trimMemory 只能清缓存侧，displayed 镜像不受 OOM hook 保护。

### 5.3 推荐窗口策略（设计）

以「当前页 ±1 + zoom protection」为保留窗口，滚动驱动收缩：

- **keep 集合来源**：viewport 已有 anchor page 计算（lastAnchorPage）——keep = {anchor-1, anchor, anchor+1} ∪ {zoomProtection}；
- **zoom protection**：scale>1 时把 pinch 起始页与其相邻页加入 keep（防止缩放中回收导致闪空白）；缩放回落≤1 即解除；
- **横向模式**：三页条布局天然窗口=当前 strip 三槽，直接复用 keep={current±1}，无需特殊分支；
- **收缩时机**：仅在 SETTLING/IDLE 相位执行 `displayedBitmaps = displayedBitmaps.filterKeys { it in keep }`（复用既有 interaction.phase 键控），拖拽/fling 中冻结（与 DisplayMergePolicy 冻结语义一致）；
- **pending 同步收缩**：同拍清理 pending 中 ∉ keep∪{即将进入} 的条目；
- **旋转/恢复**：remember(documentId) 天然重建，无需额外处理；但恢复后首帧 keep 应来自 restore 页而非第 0 页；
- **实现落点**：新增 viewport 内私有纯函数 `pruneDisplayed(displayed, pending, keep)`（可 JVM 单测），在现有 SETTLING effect 内调用；禁止触碰 tile 管线与 PdfPageBitmapCache。

### 5.4 所需测试

1. `PruneDisplayedTest`（纯 JVM）：keep 计算边界（anchor=0/-1、末页越界）、SETTLING 才收缩、zoom protection 保持、pending 同步收缩；
2. 既有 R7 连续视口契约测试回归（内存键控不变）；
3. 设备侧（Batch B perf 批）：`dumpsys meminfo` 前后对比 + 50 页连读脚本化 Bitmap 总量曲线。

### 5.5 是否进入 Batch B

**是，且列为 Batch B 首项**（B-1）。理由：唯一已知可静态证明的无界增长；修复面窄（单文件+单策略类）；收益直接作用于低端机可用性。

## 6. A3 剩余风险扫描（Part 4）

| 级别 | 项 | 说明 |
|---|---|---|
| P0 | NONE | — |
| P1 | Path B zip 缺口（§4.2-A1） | 功能缺口 vs 文档声明；用户可达 |
| P1 | CURRENT_STATE/active_object 过期（§3） | 下会话交接风险；纯文档修复 |
| P2 | PdfThumbnailLoader 并发模型未经本会话核验（11 处同步原语） | 归入 Batch B profiling 时顺带审查 |
| P3 | LocalImageLoader 4 处 Log.w 未走编译门控 | A3-1 范围仅 Log.i；解码失败路径告警合理，可选后续统一 |
| P3 | UI 层 SourceTooLarge 专用文案缺失 | 已注释标记，等 UI 解冻批次 |
| — | TODO/FIXME/HACK：NONE；@Deprecated 残留：NONE；debug residue（release dex）：NONE（A3-1 验证） | — |

重复逻辑扫描：sanitize/probe/acceptance 均已单点化（§4.1）；memory ownership：displayedBitmaps（§5）+ PdfThumbnailLoader（P2）；thread safety：热区（TileWorkQueue/Flusher/Telemetry/Performance）均有既定模型且被契约测试覆盖，未见新增裸共享可变态。

## 7. Batch B 规划草案（Part 5 — 未实现）

推荐顺序与理由：先修确定性内存缺陷（静态可证），再做需要设备的性能取证（GPU/帧率），最后消化运行时风险清单。

### B-1 displayedBitmaps memory bound
- 风险：长会话大文档 OOM/卡顿（§5.2）；实现若误伤 tile 管线会引入闪白回归
- 目标：displayed/pending 镜像收敛到 keep 窗口；OOM hook 覆盖 UI 镜像
- 验收：PruneDisplayedTest 全绿；R7 连续视口契约全绿；设备 50 页连读 Bitmap 曲线平稳（峰值差 <2 页位图）
- 禁止：修改 PdfPageBitmapCache/tile 管线协议；修改手势相位机；UI 视觉变化

### B-2 Liquid Glass GPU profiling（需真机）
- 风险：backdrop 采样/模糊在低端 GPU 的每帧成本未知；调参易破坏设计辨识度
- 目标：gfxinfo/Perfetto 取证 chrome 常驻帧成本，定位是否需要降级开关
- 验收：全屏滚动/开合 chrome 各 ≥3 组可复现 trace；结论量化到 ms/帧；不做任何视觉参数变更除非数据指向必要
- 禁止：凭手感调 blur/sampling 参数；替换材质库

### B-3 Real device performance evidence（需真机）
- 风险：Phase 4 热路径优化（CDV 单收集器/tile 索引）收益至今无实测背书
- 目标：帧率/内存/发热三项取证，关闭 A2 遗留 pending 项
- 验收：与 A2 closure 文档中的三问逐条对账并可归档
- 禁止：为数字好看改产品行为；无设备时死等（公约 §5）

### B-4 Runtime risks 清扫
- 风险：P1 zip 缺口（§4.2）、P2 ThumbnailLoader 审查、P3 文案/日志尾巴
- 目标：zip 路由修复（先红 E2E 后修）；ThumbnailLoader 并发结论入档；CURRENT_STATE/active_object 重写交接
- 验收：zip E2E 双向断言绿；文档三件套与 HEAD 一致
- 禁止：扩入 TOC/网盘 issue 文档范围（仍独立跟踪）

## 8. Pending issues（移交下会话）

1. 本报告 §3 两份 .memory 文档重写（首个动作）
2. §4.2 Path B zip 缺口 → B-4 首任务
3. §5 设计待 B-1 实现
4. adb 依赖项全部 device_pending（Home 视觉校准、沉浸栏实机、帧率实测、B-2/B-3 全部）
