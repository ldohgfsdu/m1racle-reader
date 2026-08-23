# R11 X-style reader top chrome immersive collapse

**Date:** 2026-07-19  
**Scope:** Markdown reader top chrome only (not PDF chrome rewrite, not R10 home)  
**Conclusion:** **部分通过** — 架构确认正确；feel-tune 已装机；真机手感对照 X 待用户最终验收（未 device_accepted / 未 commit）

## 1. 此前为何达不到 X 效果

R9 候选 C 结构是：

1. 状态栏区域 **固定实心** `Spacer(windowInsetsTopHeight(statusBars))`，不随滚动移动  
2. 正文包在 `windowInsetsPadding(statusBars)` 内，**永远不能画进状态栏后方**  
3. TopBar 仅 `translationY = 0 或 -ReaderTopBarHeight`，**离散 snap**，不含状态栏高度  
4. LazyColumn `contentPadding.top = ReaderTopBarHeight + 8`（不含状态栏，因外层已避让）

因此隐藏后顶部仍残留「状态栏高度空白」，无法真正沉浸。

## 2. 最终布局结构

```
Scaffold(contentWindowInsets = 0, topBar = 仅搜索模式)
└── Box(fillMaxSize)  // 非搜索
    ├── ReaderDocumentBody  // 全窗，无 statusBarsPadding / safeDrawingPadding
    │   └── LazyColumn(contentPadding.top = statusBar + bar + divider + 8.dp)
    └── ReaderTopBarOverlay  // zIndex=1, graphicsLayer.translationY = -hiddenOffsetPx
        ├── statusBars 高度 Spacer（随 chrome 一起移走）
        ├── ReaderTopBarRow (56.dp)
        └── divider 0.5.dp
```

- **仍使用 Scaffold**，但阅读态 **不再** 用 `Scaffold.topBar` 占永久高度（搜索模式除外）。  
- **正文根** 无 `statusBarsPadding` / `safeDrawingPadding`。  
- `MainActivity.enableEdgeToEdge()` 已存在，未重复配置。

## 3. hiddenOffsetPx / inset

| 量 | 计算 |
|---|---|
| `maxHiddenOffsetPx` | `statusBarTopPx + ReaderTopBarHeight + divider` |
| `hiddenOffsetPx` | 同向拖 1:1 累计；反向 jitter 阈值内不反向；clamp `0..max` |
| `collapseFraction` | `hiddenOffsetPx / maxHiddenOffsetPx` |
| settle | 强 fling 按方向；低速 `fraction >= 0.5` 全隐否则全显；spring 无回弹 |
| 显示 | `graphicsLayer { translationY = -hiddenOffsetPx }` |

反向 jitter / forceShow / reset / nestedScroll 不消费 保留 R9 语义。

## 4. 修改文件

| 文件 | 作用 |
|---|---|
| `ReaderTopBarScroll.kt` | 连续 offset + fling 记录 + settle 目标 |
| `ViewerScreen.kt` | overlay 结构、contentPadding、settle 动画 |
| `ReaderTopBarScrollStateTest.kt` | 连续模型单测 |

## 5. 验证

| 项 | 结果 |
|---|---|
| 单元测试 `ReaderTopBarScrollStateTest` | **PASS** |
| `:app:testDebugUnitTest`（本轮任务相关 + 全量命令） | **PASS**（exit 0） |
| `:app:compileDebugKotlin` / `compileQaKotlin` | **PASS** |
| `:app:assembleQa` | **PASS** |
| QA APK | `dist/r11-x-style-topbar/markleaf-qa-r11-x-style-topbar.apk` |
| SHA-256 | `c1f226eca6fafe4a2e032cb2fa7549738b0ec6e50d6bfd27f235c742128de004` |
| 设备 install | `198.18.0.1:44441` Success |
| gfxinfo（安装后滚动样本） | frames 1702, modern jank **0.06%**, deadline miss 1, missed-vsync 0, p95 14ms |
| lint | 未单独跑全量 lint（本轮未强制） |
| Perfetto | 未采（自动化样本未达 R9 正式 gate 协议） |
| 真机手感 | **待用户**（慢拖/反向/fling/搜索/横屏等） |

## 6. 性能

相对 R9 基线 modern janky ~0.08–0.10%，本轮短样本 **0.06%**，未见明显回退信号；p95 14ms 高于 8.33ms 120Hz 预算，与此前 Perfetto 形态类似，**不能**据此单独宣称改进或回退。

### Feel-tune matrix（2026-07-19，同会话长滚）

| 项 | 值 |
|---|---|
| APK | `dist/r11-x-style-topbar/feel-tune-20260719/markleaf-qa-r11-feel-tune.apk` |
| SHA-256 | `1be0985c37b10c198308ba437b8fca7b82f9b38b331b90ae6d9370ba72da301d` |
| 参数 | jitter **8.dp**；fling settle **1200 px/s**；spring ζ=1 stiffness **900** |
| frames | 5330 |
| modern jank | **0.02%** |
| legacy jank | 13.92% |
| p50 / p95 / p99 | 5 / 13 / 17 ms |
| deadline miss | 1 |
| missed-vsync | 0 |
| FATAL/ANR | none |
| checklist | `dist/r11-x-style-topbar/feel-tune-20260719/DEVICE_ACCEPTANCE_CHECKLIST.md` |

## 7. Status-bar edge fix (screenshot-driven, 2026-07-19)

### Root cause (from pixel analysis of device capture)

Previous collapsed shot: near-white mean RGB in y=0–120, first text ~y=220 → fixed **StatusBarCollapseScrim** was painting an opaque plate (haze `backgroundColor` + strong tint), so body text never appeared behind status icons and a hard band remained.

### Fix progression (no gesture/state-machine change)

1. `enableEdgeToEdge(SystemBarStyle.auto(TRANSPARENT, TRANSPARENT))` + theme transparent status/nav bars  
2. TopBar stays **solid** and still translates as a whole (status fill + row + divider)  
3. Body still has no `statusBarsPadding` / `safeDrawingPadding`  
4. **Removed** all status-bar protection: `StatusBarCollapseScrim`, `Brush.verticalGradient`, Haze blur/source, haze dependency  

X target: fully transparent status bar, body at native color behind clock/signal/battery, no fixed scrim/fog/divider.

### Verification (no-scrim collapsed capture)

- APK: `dist/r11-x-style-topbar/no-status-scrim-20260719/markleaf-qa-r11-no-status-scrim.apk`  
- SHA-256: `12d88549a53d6bc351fc7aaa94ebf55839a0b0c5d1a68f8b25a7d417b921ae11`  
- Screenshot: `collapsed-no-scrim.png`  
- Pixel check: `dark_pixels_y0_160 = 26613`, status-band meanR min/max **114.7 / 255** (content contrast under icons; not a flat white plate)
## 8. 剩余问题

- 真机确认毛玻璃是否够像 X  
- PDF 阅读页仍用独立 chrome，**未**共用本连续 offset  
- 未 device_accepted；未 commit  

## 8. 不标记完成的原因

任务要求：无完整真机手感闭环不得 `device_accepted` / 不得提交。当前结论仍为 **部分通过**。
