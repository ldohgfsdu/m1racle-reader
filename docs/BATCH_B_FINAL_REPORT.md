# Batch B Runtime Hardening — Final Report

| 字段 | 值 |
|------|-----|
| 日期 | 2026-08-23 夜间 |
| 起始 HEAD | ce31f00（B-0 前）→ 本报告时 HEAD 见 §7 |
| 目标 | 从「功能稳定」推进到「长期重负载稳定」；证据先行、测试先行 |
| 设备 | **离线**（会话内单次 `adb devices` 探测为空）——B-2/B-3 及全部真机验收项为 device_pending |

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
| 长 PDF 内存曲线稳定 / 泄漏实测 | ⏳ device_pending（B-3 采集：`dumpsys meminfo` 50 页连读脚本） |

## 3. B-2 — Liquid Glass GPU Profiling ⏸ device_pending

设备离线，禁止模拟。到岗执行计划（一次采集，三类场景 × ≥3 组）：

1. `dumpsys gfxinfo com.markleaf.app framestats` 重置前后对比：chrome 全显滚动 / chrome 开合动画 / 纯内容滚动（glass 不可见）；
2. Perfetto FrameTimeline（jank type/deadline missed）+ GPU frequency/usage 轨道；
3. 变量隔离：同文档同操作在「Liquid 开」与「材质降级开关」（若存在 debug 开关则用之，否则以 R26 前基线 commit 对照）下各跑一组；
4. 输出判定：blur/lens 每帧成本 ms 与占比 → 是否需要优化开关/降级路径（只报告，不调参）。

## 4. B-3 — Device Evidence Closure ⏸ device_pending

A2 遗留四项的采集绑定到与 B-2 同一批真机会话：导出取消人工验证清单（A2 closure §2.1 三问）、FrameTimeline 数据、meminfo 曲线（兼作 B-1 验收）、GPU 采样。全部要求真实设备，模拟器/Robolectric 数据不作为证据。

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
| P1 | zip 身份统一（§5.2-1，等批准） | B-4 续 / Policy Batch 5 |
| P1 | B-2/B-3 真机取证（阻塞全部性能结论） | 到岗首批 |
| P2 | B-1 内存曲线设备验收 | 同上 |
| P3 | SourceTooLarge 文案、Log.w 门控、ThumbnailLoader 观察项 | UI 批 / B-3 顺带 |

下一阶段建议：真机会话一次性完成 B-2+B-3+B-1 验收三合一采集；随后按批准结果执行 zip 身份统一小批；之后才考虑进入 C 阶段（发布收口：keystore/体积 gate/README 截图刷新）。

## 7. 提交与验证台账

| 提交 | 内容 | 测试 |
|---|---|---|
| ce31f00 | B-0 zip 契约修复 | 1134/0（含契约 6 例红→绿）|
| 878209f | B-1 displayedBitmaps retention | 1145/0（含策略 11 例；R7 全绿）|
| （本提交） | Batch B final report | 文档 |

最终验证（本 HEAD）：testQaUnitTest 1145/0 · assembleRelease PASS · APK 已交付 `/storage/emulated/0/Download/markleaf-release-latest.apk` · git clean。
