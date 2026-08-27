# 阅读页滚动性能（本轮）

## 根因（修改前）

1. **TopBar 可见性驱动正文重组**：`ViewerSuccessScreen` / `ViewerScaffold` 读取 `topBarScrollState.visible`，阈值翻转时整棵 Scaffold（含 LazyColumn）进入重组路径；`animateFloatAsState` 也在与 body 同一组合边界。
2. **TOC 活动项订阅滚动索引**：`val activeTocItem by rememberActiveTocItem(...)` 使 `firstVisibleItemIndex` 每次变化都重组整个 Success 屏。
3. **LazyColumn 缺 contentType**：仅有 `type-index` key，回收池无法按 block 类型高效复用。
4. **进度写入偏勤**：`debounce(750)` 仍可能在连续滚动间隙写 Room；未在 `ON_STOP` 与「仅滚动停止 / 整数百分比」策略上收紧。
5. **代码高亮无跨 item 缓存**：`remember(code, language)` 仅限单次 composition；滚出再进入会再次扫描。
6. **图片 Loading 固定 140dp → Success 按比例**：解码完成易触发列表 re-layout 尖峰。

## 优化项

| 项 | 作用 |
| --- | --- |
| `ReaderTopBarOverlay` + `Animatable` + `graphicsLayer` | TopBar 独立组合边界；translation 仅图层失效；每可见性翻转一次吸附动画 |
| TOC `derivedStateOf` 挪入抽屉 | 正文不再订阅滚动索引做 TOC 高亮 |
| `key` + `contentType` | 稳定 key（type-index）与类型回收 |
| 进度 `snapshotFlow`：停止 / 整数% / leave / ON_STOP | 禁止每 pixel 写库；写路径仍在 `Dispatchers.IO` |
| `CodeHighlighter` LRU(48) | 同 code+lang+theme 不重建 AnnotatedString |
| 图片 Loading `aspectRatio(16:9)` | 降低进入视口后的高度跳变 |
| 表格 fade `derivedStateOf` | 横滑不重算单元格语义 |
| `:benchmark` + `benchmark` buildType | FrameTimingMetric 真机验证 |

## 验收文档

- `docs/sample-large.md`（约 2.5KB 级样例）
- `docs/sample-stress.md`（长段落 + 20 代码块 + 多表格 + 图片占位）

## 构建与真机 benchmark

```bash
# 功能验证（产物仅工程内 / dist/performance/，不进手机 Download）
./gradlew :app:assembleDebug :app:testDebugUnitTest

# 人工验收交付：Release only → dist/m1racle-reader-release.apk
./gradlew :app:assembleRelease

# Macrobenchmark（benchmark variant，脚本不变）
./gradlew :app:assembleBenchmark :benchmark:assembleBenchmark
# 真机（需已导入 docs/sample-stress.md 并打开过一次）
# benchmark 包名: com.m1racle.reader.benchmark
./gradlew :app:installBenchmark :benchmark:connectedBenchmarkAndroidTest
```

**APK 交付规则**见 `dist/APK_DELIVERY.md`：`/storage/emulated/0/Download/` 仅放 `m1racle-reader-release.apk`。

FrameTiming 关注 `frameDurationCpuMs` P50/P90/P99 与 `frameOverrunMs`。

### 指标说明（本环境）

当前构建环境无已连接物理设备 / adb，**无法在本轮产出 before/after 数值表**。
请在真机上对同一文档（`sample-stress.md`）各跑 5 iterations：

| 场景 | 指标 |
| --- | --- |
| 冷启动 | StartupTimingMetric |
| 打开文档 + 连续 fling 下/上 | FrameTimingMetric |

对比修改前/后的 P50 / P90 `frameDurationCpuMs` 与 `frameOverrunMs > 0` 占比。

## 仍可能的主线程成本（可接受 / 已知）

- **代码块首次进入视口**：若缓存未命中，高亮扫描仍在主线程首次执行（有 LRU，回滚再进不重复）。
- **Inline Markdown**：`remember(text, styles)` 在 item 首次组合时解析，非滚动中重解析。
- **图片解码**：`Dispatchers.IO`；完成回主线程改 state 触发该 item 一次重组。
- **Room**：仅停止 / 整数% / 离开 / 后台；`NonCancellable + IO`。

解析（`MarkdownParser`）只在打开文档时于 `Dispatchers.Default` 执行一次。
