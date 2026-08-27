# R22.6 Glass Interaction Layer 报告

**日期：** 2026-08-26
**性质：** 能力建设任务。Glass Material Engine **零改动**（`GlassMaterial.kt` SHA-256
`591b03e07a44bfcfc7b03146928b60d8a58c6c8e27c15077b83e26c5f797b8ea` 前后一致）；
不接入任何生产组件；无新依赖。

---

## 设计

### 新增文件

- `app/src/main/java/com/markleaf/app/ui/material/GlassInteraction.kt`
- `app/src/test/java/com/markleaf/app/ui/material/GlassInteractionContractTest.kt`（13 tests）

### 内容

1. **`GlassInteractionState`**：IDLE / HOVER / PRESSED / DRAGGING / EXPANDED。
2. **`GlassInteractionTransitions`**：合法迁移冻结表（自环 = 幂等重入允许）：
   - IDLE → {HOVER, PRESSED, EXPANDED}（EXPANDED 支持合成点击直达展开）
   - HOVER → {IDLE, PRESSED}
   - PRESSED → {DRAGGING, EXPANDED, HOVER, IDLE}
   - DRAGGING → {EXPANDED, PRESSED, IDLE, HOVER}
   - EXPANDED → {DRAGGING, PRESSED, IDLE}
3. **`GlassInteractionContext`**：可复用单实例上下文 —— state、pointer(x,y)、
   velocity(x,y)、progress(0..1 钳制)、isPointerActive，外加单调 `revision` 变更戳
   （生命周期可控性与泄漏诊断入口）。非法迁移拒绝返回 false 且状态/revision 不动
   （手势竞态安全）；`reset()` 强制归位清全部动态输入。

## 与 Material Layer 的边界（架构红线，测试双向锁定）

| 层 | 职责 | 禁止 |
|---|---|---|
| Material（引擎+配方） | blur / opacity(surface alpha) / tint / stroke(highlight) 的静态解析 | 感知交互层 |
| Interaction（本层） | 产出当前交互状态与动态参数输入（pointer/velocity/progress） | 引用任何 Material 符号；写入 preset |

`GlassInteractionContractTest` 以源码钉断言：GlassInteraction.kt 不含任何
Material 引擎/配方符号；GlassMaterial.kt 不含 `GlassInteraction`。未来动态效果
（radial highlight / directional edge light / press glow / drag response）由渲染
适配层在组件自己的绘制阶段读取 context 完成映射——改的是消费端绘制，不是配方。

## 性能契约（本轮即锁定）

- **零每帧分配**：字段就地更新 + 单实例复用（API 只收原始类型参数）。
- **零持续 redraw**：本层不是 Compose 可观察状态、无任何绘制入口（源码钉断言），
  自身不可能触发重组/重绘；idle 0-redraw 基线在结构上不可被本层破坏。
- **生命周期可控**：组件 remember 单实例持有；`reset()` 显式归位；revision 可审计。

## 扩展点预留（本轮零实现、零成本）

radial highlight、directional edge light、press glow、drag response —— 数据源
（pointer 相对位置、速度向量、进度、状态）已就绪；实现时新增消费端绘制适配，
不动本契约与 Material preset。

## 未接入组件（明确声明）

PdfViewerScreen 胶囊/圆钮、ReaderMoreHost、Home 等现有 UI **均未触碰**；本轮只
交付能力与契约。接入属后续任务（需真机验收配合）。

## 验证

`GlassInteractionContractTest`（13）：全对迁移矩阵、代表性非法跳转被拒、完整手势
walk、tap/direct-expand 路径、自环幂等、pointer down/move/up/cancel 就地更新、
progress 钳制、reset 清场、revision 仅随被接受变更递增、双层隔离钉。
