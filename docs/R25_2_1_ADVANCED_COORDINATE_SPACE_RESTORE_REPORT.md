# R25.2.1 Advanced Card 坐标空间恢复 — 紧急回归修复报告

**Date:** 2026-08-06
**Scope:** 仅恢复 Advanced Card 屏幕几何（禁止继续叠补丁）。冻结：全部静态 UI、字体、
颜色、alpha、corner、Advanced width、body layout、目录定位、缩略图两栏、导出压缩布局、
IME 业务。第一目标：恢复 R25.1 已经正确的 Advanced Card 屏幕几何，再谈 motion。
**Status:** 实现完成 + 自动化验证通过（unit+UI 全量 PASS + lint + assembleRelease PASS，
Release APK 已交付 Download）。**静态截图硬门禁 PENDING（adb 离线）**。

---

## 1. 实机现象（用户 17750/17751/17752/17753）

四种 Advanced Card（设置教材页码/导出章节/页面缩略图/教材目录）全部发生**同方向系统性
X 轴错位**：整张 Advanced Surface 整体平移到屏幕左侧，left 越出屏幕、right 不再与
Main Card / source row 的 right edge 对齐。上一版正常 R25/R25.1 约 x≈80→680，当前约
x<0→≈480；**宽度大体仍在** → 是共享几何/坐标原点回归，不是四个 body 的排版问题。

## 2. 根因（明确，非「修复 offset」式结论）

**「right-anchor 容器内，root 坐标被重复应用为第二次平移」**

调用链：`PdfViewerScreen` → `ReaderMoreMorphSurface`（其外层 Box 用
`Modifier.align(Alignment.TopEnd)` 右锚定）→ z1 `ReaderAdvancedCard`（自身也
`align(TopEnd)`）。R25.2 把 rect 插值的 `targetLeft = sourceRight − targetWidth`
（360dp 屏上 ≈ 216 − 320 = **−104dp**）当作 `Modifier.offset(x = cardLeftDp)`
施加。Compose 的 `offset` 在 `align(TopEnd)` 完成右锚**之后**再平移整卡：右缘首先被
TopEnd 定在 Main/source right，随后又被 `offset(x = −104dp)` 整体左推 104dp
（@2x ≈ **208px**）——与实机 ~190–200px 左移一致。left 因此越出屏幕，right 因此偏离
Main right。错误本质是：**同一个左边界值既能推导 width（lerp 内部）又被当作第二次
位移量**；源与目标基准都挤在同一 fractional left 上叠 offset。宽度不受影响说明共享
width 的 lerp 成立，仅**位置 owner 出现两个**（anchor + offset.x）。

## 3. 修复（最小恢复 R25.1 模型，不再叠补丁）

- **删除** `Modifier.offset(x = cardLeftDp, y = cardTopDp)` 中的 **offset.x**。
- 卡片 X 轴**完全由 TopEnd 右锚定拥有**：`left = right − width` 由容器推导，不当作
  offset/translation；卡片只通过 `offset(y = cardTop)` + 自身 `size(width, height)`
  定位。修复后 p=0 → 逐像素等于被点 row；p=1 → right 与 Main/source right 完美重合；
  全程 0<p<1 各边只走 source→target 插值轨迹，无额外横向平移。
- master progress p 与「单一 progress 拥有 geometry+clip」原则**保留不变**
  （rect/corner/header/parent dim 仍由 p 派生）——修复只动 X 位置 owner。
- 坐标契约注释写入 `ReaderAdvancedCard.kt`（COORDINATE CONTRACT 段落）：

```
All geometry lives in ONE space ... RENDERING: the card is placed ONLY by its
top (y-offset) and its own width/height — the container anchors it at TopEnd ...
The left edge is derived (left = right − width), NEVER applied as offset.x /
translationX.
```

## 4. 契约纯函数与防复发测试

- `AdvancedGeometry.kt`：新增 `RootAdvancedRect` + `rootRectToOverlay(root,
  overlayRootLeft, overlayRootTop)` —— **root→overlay-local 唯一边界一次性转换**；
  已 local 的 bounds 不得再次转换（二次转换即本次回归）。
- 新增 `AdvancedCoordinateContractTest`（纯几何，5 例）：
  ①root 宽 706 / 四条任务 row 不同 Y → 各 target right **全等且 == Main/source
    right**、left ≥ 0、width 正确（≈既定 300–320dp）；②root origin 为 0 → root=overlay
    同空间；③overlay origin 非零（24,48）→ root→local 只转一次、数值正确；④二次转换
    被明确标注非法并钉死「再减一次 overlayLeft」的数值结果（防复发）；⑤fully-open
    rect 满足 ①left≥safeLeft/right≤safeRight。
- 全量回归：`testDebugUnitTest`（含既有 9+9 geometry/motion）+ `lintDebug` PASS +
  `assembleRelease` PASS。Release APK = `.external_outbox/markleaf-release-latest.apk`
  （10936855 bytes）已复制 `/storage/emulated/0/Download/`（SHA 一致）。

## 5. 接下来必须的硬门禁（PENDING — adb 离线）

1. 四张 fully-open 静态截图：A 设置教材页码 / B 教材目录 / C 页面缩略图 / D 导出章节。
   四张都必须完整位于屏幕安全区域、左边内容不被裁、右边缘与 Main Card 右锚点一致。
2. 60fps 录 source row → Advanced → source row：展开/收起 Rect 无横向瞬移。
3. 通过前不宣称 R25.2 motion complete。

## 6. 明确回答

- **本轮根因**是「root 坐标重复减 parent origin」还是其它？—— 属于 **「right-anchor
  容器内 root 坐标被重复应用为第二次平移（一次 anchor + 一次 offset.x）」**；即左界值
  既参与 width 推导又被当作 offset 再移动卡，等于把父/源 origin 重复减了一次
  （-104dp）。
- Advanced Card 当前是否仍存在整体向左 ~190–200px 错位？—— **否**（offset.x 已删除，
  X 归 TopEnd 锚定）。
- 四个 Advanced right edge 是否重新与 Main/source right anchor 一致？—— **是**（恒等
  由容器右锚定保证 + 测试断言）。
- 是否通过缩窄 Card / 逐页 magic offset 掩盖？—— **否**（未改 width/body，仅删重复平移）。
- 是否重新引入 BottomSheet？—— **否**。
- 是否在本次顺手重做静态 UI？—— **否**（冻结项全部未动，simple inline 动画亦未调整）。

## 7. 报告

- `docs/R25_2_1_ADVANCED_COORDINATE_SPACE_RESTORE_REPORT.md`
- APK：`.external_outbox/markleaf-release-latest.apk`（已复制 Download）
- Git：未提交（按会话约定）