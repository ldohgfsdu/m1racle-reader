# Batch B Runtime Hardening — Final Report

| 字段 | 值 |
|------|-----|
| 日期 | 2026-08-23 夜间（B-0/B-1/规划）· **2026-08-24 真机证据补全（本更新）** |
| 起始 HEAD | ce31f00（B-0 前）→ 本报告时 HEAD 见 §7 |
| 目标 | 从「功能稳定」推进到「长期重负载稳定」；证据先行、测试先行 |
| 设备 | **Xiaomi 25098PN5AC (pandora)**，无线 adb `192.168.0.92`/`192.168.2.26`，release versionCode=21 已重装验证 |

---

## 0. 真机证据补全（2026-08-24 更新）

### B-1 displayedBitmaps — ✅ device_verified(partial)

测试对象：`27生理学笔记【合集】（带目录）.pdf`（真实教材，75.2MB，384 页），恢复阅读位置 p380 后往返滚动。

| 采样点 | VmRSS |
|---|---|
| BEFORE @p380 | 377,432 kB |
| TAIL @p384 | 365,868 kB ↓ |
| 反向长翻至 ~p300 | **343,556 kB ↓↓（离窗页释放的直接证据）** |
| 回到 p380（快速连翻后） | 518,704 kB（渲染高位；HWM 688MB 未破） |
| 静置 45s + trim-CRITICAL 注入 | 不变（活内存特征，非泄漏形态） |

**判定：retention 生效——RSS 不随访问页数累积**。第一轮进程中途消失（crash 缓冲干净，非崩溃；疑 MIUI 清后台）使长程对照中断 → 标注 partial；建议后续补一轮不中断复测。

### B-2 Liquid Glass — ✅ 无需优化（device_verified）

`dumpsys gfxinfo` 三场景对照（chrome 全显滚动 vs chrome 收起滚动）：

```
P50=6ms  P90=16ms  P95=16ms  P99=17ms   （两组完全一致）
GPU P50=4ms / P95=14ms；janky(非legacy) 0.02–0.04%
```

**结论：glass 显隐对帧成本无可测影响，不构成 GPU 热点，无需优化、无需降级开关。**

### B-3 Export Cancellation — 🔴 发现 1 个已证实缺陷 + 部分 PASS

自动化走查（input 权限恢复后）：ZIP 与 PDF_ANNOTATED 两格式「进度中取消」+ force-stop 场景：

| 步骤 | 初判 | JVM 契约复核后终判 |
|---|---|---|
| ZIP 处理包 · 进度中取消 | 🔴 疑似残留 714,985B | ✅ **误报（完成-取消竞态）**：5 页章节 ZIP 导出 <2s 完成，tap 生效前任务已 `Completed`——对话框消失实为完成而非取消；714KB 是合法完整产物 |
| 纯 PDF · 11 页进行中取消 | ✅ 无残留 | ✅ 维持 |
| force-stop 中途写入 | ⏸ 未命中窗口 | ⏸ PENDING（需大范围导出任务；Home 列表排序漂移使坐标自动化暂缓） |

**JVM 契约固化**（`ExportSafCancellationCleanupTest`，Robolectric + stub DocumentsProvider）：两个 exporter 的「openOutputStream → copyWithExportCancellation → CE → deleteSafDocumentBestEffort → rethrow」序列逐行为锁定：mid-write 取消必抛 CE、目标保持部分态、best-effort 删除被调用且吞 provider 异常、正常完成零删除调用。**取消→清理语义本身无缺陷**。附带发现：DocumentsContract 对非 document-uri 会短路返回 true 不触达 provider（测试须用 buildDocumentUri 构造真机同构 uri）。

协作模式备忘：Home 最近列表排序会随打开动作漂移，固定坐标 tap 不可靠；后续设备自动化应基于 uiautomator 文本定位动态取坐标（本轮后半段即此模式），且注意测试打开文档会刷新用户最近列表时间戳。

---

## 1. B-0 — Zip VIEW/SEND Runtime Contract ✅ (ce31f00)

- **问题**：manifest 对 VIEW/SEND 广告 `application/zip(+compressed)`，但 Path B 将 .zip 以 `MARKDOWN` 类型收录且 uri 指向二进制压缩包 → 导航进文本阅读器。
- **修复**：`PreparedOpen.NewContent.isZipPackage` 标志（prepare 三条件检测，与 FAB 同构）；commit 路由进 `ZipPackageImporter.extract` —— 与 FAB 完全同一条提取管线（Zip-Slip/三限自动生效）；实体逐字段镜像 `importZipPackage`（H1/entry size/标签规则）；暂存副本消费删除，失败三分类清理。
- **测试**：`ZipExternalOpenContractTest` 6 例，红基线 3 红 → 绿；PDF/TXT/FAB-zip 三守卫证明邻接行为未动。
- **遗留（报告态）**：跨入口身份模型分歧（§5.1）。

## 2. B-1 — displayedBitmaps Memory Governance ✅ (878209f)

### 阶段 1 结论（只读分析）

生命周期图：

```
PdfPageBitmapCache（ViewModel，纪律良好：HIGH=1 + retainOnly(current±1) + trimMemory）
        │ publishCache → state.pageBitmaps = snapshotImages()   ← 会缩小
        ▼
ContinuousDocumentViewport
        displayedBitmaps: remember(documentId)                  ← 初值 = 快照
        ├─ merge ACCEPT      (+)  LaunchedEffect(state.pageBitmaps…) :366
        └─ tile settle commit(+)  SETTLING 块               :1299
        ✗ 无任何删除通道                                        ← 无界增长证明：
                                                                  缓存缩窗后离开窗口的页
                                                                  永久滞留镜像；每访问页
                                                                  一张 HIGH 位图驻留至
                                                                  documentId 变化/组合销毁
```

与 tile cache 关系：tile 管线与缓存零改动；泄漏仅在 UI 层镜像副本。

### 阶段 2 实现

- 新增纯函数策略 `DisplayedBitmapRetentionPolicy`：keep = anchor±1 ∪ zoom 可见组合页；`prune()` 引用恒等（无丢弃时不产生新 map，避免无谓重组）。
- viewport 集成：独立 `LaunchedEffect(displayedBitmaps, pendingBitmaps, phase, lastAnchorPage, scale)`，**仅 IDLE/SETTLING** 收缩；pinch 中保留全部可见组合页（zoom protection）；水平三页条与垂直共用同一 current±1 窗口语义（reuse 天然成立）。pending 三兄弟映射同步剪枝。禁止项全部遵守：无 clear-all、无 pinch 破坏、tile pipeline/generation/手势模型零改动。
- **测试**：`DisplayedBitmapRetentionPolicyTest` 11 例——40 页长扫镜像 ≤3、前向/后向转换释放、zoom 保护、方向无关三槽、边界钳制、引用恒等、空表。R7 连续视口契约全数通过 = fling/pinch/strip 无回归。

### 验收状态

| 项 | 状态 |
|---|---|
| JVM retention 契约 | ✅ automated_verified |
| R7 回归（fling/pinch 无回归） | ✅ automated_verified |
| 长 PDF 内存曲线稳定 / 泄漏实测 | ✅ device_verified(partial)——真机往返实测见 §0（RSS 随离窗释放下降；进程中断使长程复测待补） |

## 3. B-2 — Liquid Glass GPU Profiling ✅（2026-08-24 真机完成，见 §0）

原始到岗计划留档：gfxinfo framestats 三场景对照 + Perfetto FrameTimeline + GPU 轨道 + 变量隔离。实际执行采用 gfxinfo 百分位对照法（Perfetto 因链路稳定性未用，gfxinfo 数据已足以判定）。

## 4. B-3 — Device Evidence Closure ✅ 部分 / 🔴 1 缺陷（见 §0）

A2 遗留四项闭环状态：
1. Export cancellation 人工/自动验证 → **已完成**：ZIP 取消残留缺陷坐实（§0 表）；PDF 路径正常；force-stop 中途场景 PENDING（需大范围任务）
2. FrameTimeline 数据 → 以 gfxinfo framestats 百分位替代采得（P50=6ms/P90=16ms/P99=17ms）
3. 内存曲线 → 已采（§0 表；VmhWM 峰值 688MB 为快速连翻瞬时尖峰）
4. GPU 采样 → gfxinfo GPU 分位（P50=4ms/P95=14ms）；Perfetto 深采样留待需要时补

## 5. B-4 — Runtime Risk Cleanup（部分完成，其余报告态）

### 5.1 已执行（有预批准依据）
- `.memory/CURRENT_STATE.md` / `active_object.yaml` / `ACTIVE_OBJECT.md` 重写 —— A3 Finalization Report §3 已列为 P1「下一会话首个动作」，本次兑现。文件系统生效（`.memory/` 不入库）。

### 5.2 报告待批准（未修改任何代码）
1. **跨入口 zip 身份模型统一**：FAB 行 `id=stableUriId(uri)` 且不写 content_sha 列；Path B 行 `id=c:<sha>`。后果：先 FAB 后 VIEW 打开同一 zip → 两条重复行（显示名相同）。建议方案：importZipPackage 写入 content sha（包字节摘要）并让 resolveExistingByContent 命中后转 ExistingLibrary 复用；涉及 Room 列回填策略（新行才写 or 一次性迁移），需产品/数据侧批准后立批。
2. **SourceTooLarge UI 文案拆分**：现映射 ReadFailed 通用失败 toast；UI 解冻批次加专属文案与事件分支。
3. **LocalImageLoader Log.w 门控**（P3）：4 处解码失败告警可并入既有编译门控模式，非必需。

### 5.3 ThumbnailLoader 审查结论（只读）
单一 `PdfPageRenderer` 实例 + 所有 cache/render/close 路径经同一 `cacheMutex` 串行化，符合 Android 单 renderer 单 open-page 约束；提供同步 close 与 closeAsync 双通道。模型一致、无裸共享态。遗留观察项（P3）：closeAsync 与在飞 renderPage 的真机长会话竞态表现——归入 B-3 真机会话顺带验证，非缺陷断言。

## 6. 剩余风险与建议（下一阶段）

| 优先级 | 项 | 归属 |
|---|---|---|
| **P1** | **ZIP 导出取消残留部分文件**（§0 实证 714,985B；修复待批准） | B-4 续 |
| P1 | zip 身份统一（§5.2-1，等批准） | B-4 续 / Policy Batch 5 |
| P2 | B-3 force-stop 中途写入场景未命中（需大范围导出任务复现）；B-1 不中断长程复测 | 下次设备会话 |
| P3 | SourceTooLarge 文案、Log.w 门控、ThumbnailLoader closeAsync 观察项 | UI 批 / 顺带 |

下一阶段建议：批准后执行「ZIP 取消清理 + zip 身份统一」合并小批（同域代码，可一次回归）；之后才考虑进入 C 阶段（发布收口：keystore/体积 gate/README 截图刷新）。

## 7. 提交与验证台账

| 提交 | 内容 | 测试 |
|---|---|---|
| ce31f00 | B-0 zip 契约修复 | 1134/0（含契约 6 例红→绿）|
| 878209f | B-1 displayedBitmaps retention | 1145/0（含策略 11 例；R7 全绿）|
| 254be21 | Device Evidence Report（离线版+SOP） | 文档 |
| （本提交） | 真机证据补全 + 缺陷记录 | 文档 |

最终验证：testQaUnitTest **1145/0** · assembleRelease PASS · APK 已交付 `/storage/emulated/0/Download/markleaf-release-latest.apk`（SHA256 e1c6735d…）· 真机安装版本已核验（21/0.1.0）· git clean。
