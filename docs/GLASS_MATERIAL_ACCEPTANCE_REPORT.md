# Glass Material Engine Polish Acceptance Report

**Phase:** Polish re-acceptance
**Status:** COMPLETE — PASS
**Date:** 2026-08-25（Asia/Hong_Kong）
**Scope:** 仅修复既有 Engine recipe、状态解析、渲染资源生命周期与 pressed 过渡；未新增 role、第二套 Material API、依赖、用户设置或旧实现

## 1. 测试环境与交付身份

| 项目 | 结果 |
|---|---|
| Build | release，R8/minify + resource shrink |
| APK | `.external_outbox/markleaf-release-latest.apk`，11,493,231 bytes |
| APK SHA-256 | `cf1277d6fa7c4c134dac18b87242c9e6a678839908a9c31a5cdac1326965d1e0` |
| 签名 | APK Signature Scheme v2 verified，1 signer |
| 安装身份 | 产物、`/storage/emulated/0/Download/markleaf-release-latest.apk`、设备 `base.apk` 三处 SHA-256 完全一致 |
| App | `com.markleaf.app`，versionName `0.1.0`，versionCode `22` |
| Device | Xiaomi `25098PN5AC` / `pandora`，SoC `SM8850` |
| Android | Android 16 / API 36 |
| Display | 1220 × 2656，520 dpi，120 Hz；Skia (Vulkan) |
| ADB | 无线真机 `198.18.0.1:36791`，状态 `device` |
| Test PDF | `27生理学笔记【合集】（带目录）.pdf`，384 页 / 75.2 MB；图片、图表、彩色批注和高密度正文混合背景 |
| 自动化 | `:app:testQaUnitTest`：1169 tests / 0 failures / 0 errors / 0 skipped |
| Engine source | `GlassMaterial.kt` SHA-256 `591b03e07a44bfcfc7b03146928b60d8a58c6c8e27c15077b83e26c5f797b8ea` |
| Dialog wrapper | `GlassAlertDialog.kt` SHA-256 `a025f572df1d8f5677f5a7a305003bbfe02c37aca8bcfeeff0059e30f369d8ea`，与失败基线一致 |

初次 Acceptance 失败基线 APK SHA-256 为
`2997b6520ab5ca6f64a958f4cbfd30fb38dd4f32ddeafc00e087e3cb6a0e669a`，证据保留在
`.external_outbox/glass-material-acceptance/`。本报告其余截图和性能数据均来自上表最终 release。

## 2. 实现结果

严格按授权顺序完成：

1. **Dialog isolation**：Dialog 继续走同一 `GlassMaterialRole.Dialog` soft-plate 渲染，Light/Dark surface alpha 提高到 `0.97`；保留 3% 透光和边缘高光，不退化为纯色 Card。
2. **Light idle / resource lifecycle**：新增 Engine 内部 `IDLE / INTERACTION` 渲染状态；完整 backdrop modifier 与 effect endpoint 通过 `remember` 复用，静态父级重组不再替换 effect lambda 或重建 draw cache。
3. **Dark material**：统一改为低亮度冷色 tint，Dark 不再通过“降低 alpha”模拟；同时保留更强阴影与边缘高光。
4. **Pressed transition**：surface alpha、highlight 和 scale 统一经 spring 插值；调用点移除原有 tween/直接 `if pressed` 替换。调用点只传递已有交互状态，布局、点击语义和业务逻辑不变。

Backdrop 节点仍遵循 create → reuse → dispose：节点 attach 时拥有一份 GraphicsLayer，稳定重组期间复用，detach 时释放；没有新增全局 bitmap/snapshot cache，也没有每次 recomposition 创建 blur resource。

## 3. 当前 Material preset

### 全局 Light / Dark 参数

| 参数 | Light | Dark |
|---|---:|---:|
| surface tint | `#FAFAFA` | `#111820` |
| brightness | `+0.20` | `-0.18` |
| saturation | `1.25` | `1.25` |
| shadow alpha | `0.10` | `0.46` |
| muted content alpha | `0.58` | `0.75` |
| pressed surface boost | `+0.04` | `+0.04` |
| pressed highlight boost | `+0.10` | `+0.10` |
| pressed content overlay | `0.04` | `0.05` |
| divider alpha | `0.06` | `0.06` |

Pressed spring：damping ratio `0.82`，`Spring.StiffnessMedium`，visibility threshold `0.001`。

### Role recipes

| Role | surface alpha L/D | blur | lens height / amount | shadow | highlight alpha L/D | stroke | pressed scale | backdrop |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| Control | 0.50 / 0.68 | 12 dp | 10 / 20 dp | 8 dp | 0.30 / 0.34 | 0.5 dp | 0.965 | Root |
| Capsule | 0.50 / 0.68 | 12 dp | 10 / 20 dp | 8 dp | 0.30 / 0.34 | 0.5 dp | 0.965 | Root |
| Menu | 0.60 / 0.74 | 16 dp | 16 / 32 dp | 16 dp | 0.30 / 0.30 | 0.5 dp | 0.965 | Root；export for SettingsCard |
| SettingsCard | 0.66 / 0.78 | 16 dp | 16 / 32 dp | 18 dp | 0.30 / 0.32 | 0.5 dp | 0.965 | Root + Menu |
| Dialog | 0.97 / 0.97 | 18 dp spec | 16 / 32 dp spec | 18 dp | 0.30 / 0.38 | 0.5 dp | 0.965 | soft plate，独立 platform window |

所有 role 的 `depthEffect=false`。Dialog 的 blur/lens 仍属于统一 spec，但独立 platform window 不能采样 app backdrop，因此实际使用同 recipe 的 translucent soft plate。

## 4. Light / Dark 截图矩阵

判定口径：`PASS` 表示未发现 P0/P1/P2 材质缺陷；交互状态与动画另见第 6 节。

### Light Mode

| 组件 | Evidence | 结果 | 观察 |
|---|---|---|---|
| Home FAB | [01-home-fab.png](../.external_outbox/glass-material-polish/light/01-home-fab.png) | PASS | 轻量、边缘干净，阴影未脱离。 |
| Home Menu | [02-home-menu.png](../.external_outbox/glass-material-polish/light/02-home-menu.png) | PASS | 覆盖卡片正文，无突然全屏变暗。 |
| Reader 返回按钮 | [03-reader-back.png](../.external_outbox/glass-material-polish/light/03-reader-back.png) | PASS | Control 权重克制，复杂 PDF 上仍清晰。 |
| Reader More 按钮 | [04-reader-more.png](../.external_outbox/glass-material-polish/light/04-reader-more.png) | PASS | idle 与返回按钮同语言。 |
| Capsule collapsed | [05-capsule-collapsed.png](../.external_outbox/glass-material-polish/light/05-capsule-collapsed.png) | PASS | 页码和章节稳定可读。 |
| Capsule expanded | [06-capsule-expanded.png](../.external_outbox/glass-material-polish/light/06-capsule-expanded.png) | PASS | 原位展开，表单区和 PDF 分层清楚。 |
| More Menu | [07-more-menu.png](../.external_outbox/glass-material-polish/light/07-more-menu.png) | PASS | 正文/彩色批注不会成为可读干扰。 |
| 阅读设置 Card | [08-reading-settings-card.png](../.external_outbox/glass-material-polish/light/08-reading-settings-card.png) | PASS | Root + Menu 采样形成独立二级层。 |
| 页码跳转 Overlay | [09-page-jump-overlay.png](../.external_outbox/glass-material-polish/light/09-page-jump-overlay.png) | PASS | 与 Capsule 连续，无突变 dim。 |
| Dialog | [10-dialog.png](../.external_outbox/glass-material-polish/light/10-dialog.png) | PASS | PDF 字形只剩不可读的微弱色影，正文不能在 Dialog 内明显辨认。 |

### Dark Mode

| 组件 | Evidence | 结果 | 观察 |
|---|---|---|---|
| Home FAB | [01-home-fab.png](../.external_outbox/glass-material-polish/dark/01-home-fab.png) | PASS | 低亮度冷色玻璃，边缘高光保留。 |
| Home Menu | [02-home-menu.png](../.external_outbox/glass-material-polish/dark/02-home-menu.png) | PASS | 深色 Home 上遮挡充分。 |
| Reader 返回按钮 | [03-reader-back.png](../.external_outbox/glass-material-polish/dark/03-reader-back.png) | PASS | 不再是中性灰塑料圆片。 |
| Reader More 按钮 | [04-reader-more.png](../.external_outbox/glass-material-polish/dark/04-reader-more.png) | PASS | Control 深度和高光稳定。 |
| Capsule collapsed | [05-capsule-collapsed.png](../.external_outbox/glass-material-polish/dark/05-capsule-collapsed.png) | PASS | 深色但不闷，层次未丢失。 |
| Capsule expanded | [06-capsule-expanded.png](../.external_outbox/glass-material-polish/dark/06-capsule-expanded.png) | PASS | 大面积材质仍呈冷色玻璃，不是均匀灰板。 |
| More Menu | [07-more-menu.png](../.external_outbox/glass-material-polish/dark/07-more-menu.png) | PASS | 白色 PDF 被充分隔离，Menu 高于 Control。 |
| 阅读设置 Card | [08-reading-settings-card.png](../.external_outbox/glass-material-polish/dark/08-reading-settings-card.png) | PASS | 二级 Card 更深，和父 Menu 可区分。 |
| 页码跳转 Overlay | [09-page-jump-overlay.png](../.external_outbox/glass-material-polish/dark/09-page-jump-overlay.png) | PASS | 控件对比和表面深度正常。 |
| Dialog | [10-dialog.png](../.external_outbox/glass-material-polish/dark/10-dialog.png) | PASS | Dialog 为视觉最高层，底层正文不可读。 |

### Light / Dark 对比结论

- Light 保持轻、柔和和正文优先；Menu/Card 有足够遮挡，但没有变成白色实体卡片。
- Dark 使用更低背景亮度、冷色 tint、较高 surface alpha、较强 shadow 和保留的白色 edge highlight；不是简单降低 alpha。
- Control < Menu < SettingsCard < Dialog 的层级在两套主题中一致；未见灰色塑料感、边缘脏灰或阴影漂移。

## 5. Dialog isolation 前后

| 阶段 | Evidence | 结果 |
|---|---|---|
| 初次 Acceptance 失败基线 | [baseline Light](../.external_outbox/glass-material-acceptance/light/10-dialog.png) / [baseline Dark](../.external_outbox/glass-material-acceptance/dark/10-dialog.png) | 下层 Menu/PDF 字形明显可读。 |
| Polish 中间值 0.94（拒绝） | [10-dialog-94-rejected.png](../.external_outbox/glass-material-polish/light/10-dialog-94-rejected.png) | 高对比正文仍可辨认，因此没有作为最终值。 |
| 最终 0.97 | [Light](../.external_outbox/glass-material-polish/light/10-dialog.png) / [Dark](../.external_outbox/glass-material-polish/dark/10-dialog.png) | 正文只留下不可读的微弱色影；仍可感知透光和边缘高光，PASS。 |

## 6. 交互状态

| 状态 | Evidence | 结果 |
|---|---|---|
| idle | 第 4 节两套矩阵 | PASS；无持续重绘、灰塑料感、突然变暗或阴影脱离。 |
| More long press | [Light](../.external_outbox/glass-material-polish/light/11-more-long-pressed.png) / [Dark](../.external_outbox/glass-material-polish/dark/11-more-long-pressed.png) | PASS；圆形边界保留，alpha/highlight/scale 经 spring 连续过渡。 |
| Capsule long press | [Light](../.external_outbox/glass-material-polish/light/12-capsule-long-pressed.png) / [Dark](../.external_outbox/glass-material-polish/dark/12-capsule-long-pressed.png) | PASS；没有直接缩放跳变或整块洗白。 |
| Menu row long press | [Light](../.external_outbox/glass-material-polish/light/13-menu-row-long-pressed.png) / [Dark](../.external_outbox/glass-material-polish/dark/13-menu-row-long-pressed.png) | PASS；反馈留在圆角行内，未出现旧版方形亮区。 |
| open / close | [Light video](../.external_outbox/glass-material-polish/light/14-more-open-close.mp4) / [Dark video](../.external_outbox/glass-material-polish/dark/14-more-open-close.mp4) | PASS；开关动画连续，组件与阴影未分离。 |

长按仍沿用现有点击语义，没有新增 context menu。Dialog 测试只使用取消/Back，不保存目录节点。

## 7. 性能与资源生命周期

`gfxinfo` 是应用级 Skia/Vulkan 总量，包含 PDF 页面、系统 RenderTarget 和 Glass；`meminfo` 为单次样本。以下数据用于判断重绘和资源是否有界，不单独作为泄漏结论。

### 规定场景

| Flow | Frames | modern jank / deadline miss | CPU p50/p90/p95/p99 | gfxinfo GPU total（purgeable） | meminfo Graphics |
|---|---:|---:|---|---:|---:|
| Light idle 5 s | **0** | 0 / 0 | 无新帧 | 492.58 MB（247.62 MB） | 42,304 KB |
| Light pressed + release/menu open | 114 | 1 / 1（0.88%） | 6 / 13 / 15 / 19 ms | 450.49 MB（258.16 MB） | 42,368 KB |
| Light PDF scroll，往返 4 次 | 1014 | 1 / 1（0.10%） | 6 / 8 / 9 / 11 ms | 428.35 MB（214.90 MB） | 57,064 KB |
| Dark idle 5 s | **0** | 0 / 0 | 无新帧 | 493.40 MB（135.45 MB） | 41,216 KB |
| 已 warm Menu 后再 idle 5 s | **0** | 0 / 0 | 无新帧 | 494.43 MB（152.09 MB） | 37,636 KB |

原始证据：

- [Light idle gfxinfo](../.external_outbox/glass-material-polish/performance/light-idle-gfxinfo.txt) / [meminfo](../.external_outbox/glass-material-polish/performance/light-idle-meminfo.txt)
- [Pressed gfxinfo](../.external_outbox/glass-material-polish/performance/light-pressed-gfxinfo.txt) / [meminfo](../.external_outbox/glass-material-polish/performance/light-pressed-meminfo.txt)
- [Scroll gfxinfo](../.external_outbox/glass-material-polish/performance/light-scroll-gfxinfo.txt) / [meminfo](../.external_outbox/glass-material-polish/performance/light-scroll-meminfo.txt)
- [Dark idle gfxinfo](../.external_outbox/glass-material-polish/performance/dark-idle-gfxinfo.txt) / [meminfo](../.external_outbox/glass-material-polish/performance/dark-idle-meminfo.txt)
- [Post-menu idle gfxinfo](../.external_outbox/glass-material-polish/performance/post-menu-idle-gfxinfo.txt) / [meminfo](../.external_outbox/glass-material-polish/performance/post-menu-idle-meminfo.txt)

Light idle 从失败基线的 **1120 frames / 5 s** 降至 **0 frames / 5 s**；Dark 仍为 0。静态阶段不再持续刷新 blur。

### 冷启动与 Menu cache

同一 app 进程从 Home 打开 PDF 后，More 开关 8 次：

| 样本 | GPU total | purgeable |
|---|---:|---:|
| Home cold | 43.75 MB | 3.05 MB |
| Reader / Menu 前 | 186.90 MB | 85.27 MB |
| cycle 1 | 327.54 MB | 189.86 MB |
| cycle 2 | 374.30 MB | 206.30 MB |
| cycle 3 | 418.22 MB | 215.30 MB |
| cycle 4 | 459.92 MB | 232.05 MB |
| cycle 5 | 493.28 MB | 237.72 MB |
| cycle 6 | 493.95 MB | 209.64 MB |
| cycle 7 | 494.43 MB | 178.20 MB |
| cycle 8 | 494.43 MB | 152.06 MB |

Reader before Menu 与 cycle 8 的 `meminfo Graphics` 为 33,552 KB → 37,636 KB；`TOTAL PSS` 为 211,957 KB → 201,256 KB。Skia GPU cache 会 warm 到设备预算高位，但 cycle 5–8 增量收敛，cycle 7–8 完全稳定；随后 idle 仍是 0 frame。证据支持“create → reuse → dispose + 驱动 cache 有界”，不支持每次 recomposition 继续创建 blur resource，也没有应用内存单调增长信号。

原始证据：[Reader before Menu](../.external_outbox/glass-material-polish/performance/cold2-reader-before-menu-gfxinfo.txt)、[cycle 1](../.external_outbox/glass-material-polish/performance/cold2-reader-menu-cycle-1-gfxinfo.txt)、[cycle 5](../.external_outbox/glass-material-polish/performance/cold2-reader-menu-cycle-5-gfxinfo.txt)、[cycle 8](../.external_outbox/glass-material-polish/performance/cold2-reader-menu-cycle-8-gfxinfo.txt)、[cycle 8 meminfo](../.external_outbox/glass-material-polish/performance/cold2-reader-menu-cycle-8-meminfo.txt)。

## 8. 行为验证

| Flow | implemented | automated_verified | device_verified |
|---|---|---|---|
| PDF 连续阅读、页码恢复、往返滚动 | 是 | PASS | PASS |
| More Menu 开关、行 pressed、子 Card | 是 | PASS | PASS |
| Capsule collapsed / expanded / jump | 是 | PASS | PASS |
| Dialog 打开、隔离、取消/Back | 是 | PASS | PASS |

最终无写入烟测覆盖 More → Capsule → SettingsCard → Dialog，后续冷启动/菜单循环结束后的 crash buffer 仍为 0 bytes：
[final-crash-buffer.txt](../.external_outbox/glass-material-polish/logs/final-crash-buffer.txt)。
系统主题已恢复 Light；PDF 阅读进度恢复到第 383 / 384 页。

## 9. 问题关闭与 Acceptance 决策

| 原问题 | 原等级 | 最终结果 | 是否继续修改 Engine |
|---|---|---|---|
| Dialog 内容穿透 | P0（Polish 输入） | PASS；0.97 隔离，正文不可读 | 否 |
| Dark 灰色塑料感 | P1 | PASS；低亮度冷色 tint + 高光/阴影深度 | 否 |
| Light idle 持续重绘 | P1 | PASS；1120 → 0 frames / 5 s | 否 |
| GPU cache / resource lifecycle | P1 | PASS；重组复用，detach 释放，warm cache 有界收敛 | 否 |
| Pressed alpha/highlight/scale 跳变 | P1 | PASS；统一 spring 插值 | 否 |

**最终未关闭问题：P0=0，P1=0，P2=0。**

**Acceptance：PASS。当前 Glass Material Engine 不需要继续修改。**

Engine 在本次结果上重新冻结。若后续要做完整 Perfetto/Simpleperf 或 PDF bitmap cache 归因，应作为独立性能任务，不应以此为由继续调整已通过验收的 Material recipe。
