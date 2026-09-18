# R36.1 Glass Authority Closure

日期：2026-09-17。依据 `docs/R36_GLASS_GROUND_TRUTH_AUDIT.md` 与 `/storage/emulated/0/r36.1.txt`。未进入 R36.2。未做光学调参。

起始 HEAD：`5284f801fffa62904ce80101ca687cfd415ea8c6`（工作树相对 HEAD 本就有未提交改动）。本阶段无独立 commit。

## Before

光学事实分散在：

| 路径 | 事实源 |
|---|---|
| PDF chrome / More / Capsule | `GlassMaterialEngine` |
| Markdown 顶栏 | `ReaderGradient.kt` 硬编码 16dp blur + 0.62 tint |
| Dock | L3 `M1racleReaderTheme.surface.overlay` + 2.dp elevation；引擎 Dock 配方无消费者 |
| Home Dropdown | 同上 overlay 实色 |
| Settings preview 1/2 | 直接 `drawBackdrop` / 魔法值 4.32dp、lens 16/32 |
| Dialog / Markdown Popup | `glassMaterial(sampleBackdrop=false)` 已走引擎 |
| `M1racleGlassPolicy` | 允许 Toolbar/Dock/Menu，不接到 resolver |
| Home/Hub `LayerBackdrop` | 捕获无消费者 |
| Menu `renderState` | role 恒为 INTERACTION |
| `GlassInteractionContext` | 五态机存在，生产 UI 零消费者 |

## After

```
                 GlassMaterialEngine
                        │
         ┌──────────────┼──────────────┐
         ↓              ↓              ↓
       Role         Environment    Capability
         └──────────────┼──────────────┘
                        ↓  (+ GlassInteractionState)
               GlassMaterialSpec
                        ↓
                   Renderers
         ┌──────────────┼─────────────┐
       Reader          Home         Fallback
```

Resolver：`Role × GlassEnvironment × GlassCapability × GlassInteractionState → GlassMaterialSpec`。

Environment：`APP_LIGHT/DARK`（Theme 默认）、`READER_LIGHT/DARK`（PDF `PdfChrome`、Markdown reader scaffold）。同一 dark 位数值等价（R36.1 不改视觉）。

Capability：`FULL_OPTICAL` / `BLUR_ONLY` / `SOLID_FALLBACK`。`GlassCapabilityProbe.detect()` 在 Theme 注入。API 33+ 为 FULL。SOLID 时不采样 backdrop。正常设备不改变既有外观。

Foreground：`GlassForegroundPolicy.contentAlpha` 固定 1，与 `surfaceColor.alpha` 无关。显隐仍由 `PdfChromeVisibility` / 顶栏 collapse 的 visibility alpha 负责。

## 已迁移路径

- Markdown 顶栏 → `GlassMaterialRole.ProgressiveChrome`（16dp / 0.62）。DstIn 白渐变仍在 renderer，标 **OPTICAL_REFINEMENT_PENDING**。
- Settings preview → `GlassPreviewPreset` → Engine role（Control / ProgressiveChrome / PreviewCapsule）。变体 1 的 AGSL 遮罩仍是 deferred renderer。
- Dock → Engine `Dock` 实色板（白/墨 0.98、blur 0、shadow 2.dp），匹配原 L3。
- Home Dropdown → `Dock` + `SOLID_FALLBACK` 的 `surfaceColor` / `shadow`。
- Dialog / Markdown Popup → 仍 `sampleBackdrop=false`，recipe 来自 Engine。

## 删除 / 标记

- 删除 Home 与 Hub 无消费者的 `rememberLayerBackdrop` + `GlassBackdropSource`。`HubGlassShell` 只剩页面框。
- `DockSelection`：**NO_CURRENT_CONSUMER**（配方保留，生产无 `glassMaterial` 调用）。选择指示仍是 3dp 实色条。
- `DocumentBackdropSource` 作为文档捕获别名；PDF/Markdown 仍用既有 DAG，overlay 不进 source。

## Interaction

`GlassInteractionContext` 保留为五态权威，生产指针层仍未接。Resolver 用 `GlassInteractionState`（IDLE / PRESSED / EXPANDED）。Menu 不再因 role 恒为 INTERACTION；展开卡传 `interaction=true`。

## 尚未视觉调整的参数

Control/Capsule/Menu 浅色 0.86；Panel 0.90；blur 18dp；lensEnabled=false（PreviewCapsule 除外）；无 edge light。R36.2 才允许动这些。

## Tests

定向 QA **93 / 0 / 0 / 0**（含新 `GlassAuthorityContractTest`）。`compileReleaseKotlin` 成功。未跑全量 `testQaUnitTest`。未 `assembleRelease`。

## UNKNOWN / deferred

- Kyant 2.0.0 每帧跟踪与 Combined 内部。
- ProgressiveGlass 真实现。
- `GlassInteractionContext` 指针接入。
- DockSelection 是否在未来液态 Dock 复活。
- `M1racleGlassPolicy` 仍是 usage 白名单，不是 optical authority。

R36.2 前提：拓扑保持 Document→Backdrop→Overlay；只调 recipe/renderer，不再分裂事实源。
