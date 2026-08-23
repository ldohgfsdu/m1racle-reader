# R25.1 Advanced Card Geometry / Context / IME Closure

**Date:** 2026-08-05
**Scope:** 按 r25.1 只修实机截图暴露的四类缺陷：①viewport-aware target rect（低位 source
允许上移，17733 底部裁切消失）；②真正 IME-aware geometry + sticky action footer（17730 键盘
下 actions 不可见）；③目录/缩略图 context-aware 初始定位（17731/17732 不再从首项/PDF1 开始）；
④Export 密度压缩。R25 整体架构冻结（Main/inline/row→card 模型、de-emphasis、实体 surface、
corner/elevation、morph 均不改）。
**Status:** 实现完成（unit+UI 1040 PASS + lintDebug + assemble Qa/Release PASS）。
Release `5cb0c0f3…` 已复制 `/storage/emulated/0/Download/`（SHA 一致）。
**实机复录 PENDING**（adb 离线；A–E 五条 + 关键帧待设备）。

---

## 1. Viewport-aware target rect（R25.1 §1）

新增纯几何模块 `com.markleaf.app.pdf.AdvancedGeometry`（无 Compose 依赖，纯函数可测）：

```
height = min(contentDesiredHeight, maxAdvancedHeight, availableHeight)
preferredTop = sourceRowTop
targetTop = preferredTop.coerceIn(availableTop + margin,
                                  availableBottom − margin − height)
```

- availableTop = status bar + margin；availableBottom = 屏高 − nav bar − margin
  （**IME 出现时 = IME top − margin**）。
- **targetTop 不再硬等于 sourceRowTop**：高位 source（教材页码/目录/缩略图）主要 left+down；
  低位 source（导出章节）向下空间不足时自动上移（LEFT+UP+DOWN 综合扩张），fully-open
  outer bounds 永不超 safeBottom（17733 底部按钮裁切硬门禁）。
- source identity 保持：动画 0% 严格是原 row bounds，header/icon/label 从 source 连续。
- `ReaderAdvancedCard` 用 Animatable 同时驱动 top/width/height/corner（190–210ms
  emphasized decelerate）；target 重算（IME show/hide）→ 平滑 re-animate，无瞬跳。

## 2. IME-aware geometry + sticky footer（R25.1 §2/§4）

- IME 出现：availableBottom 缩至 IME top → target rect 重算 → 卡整体平滑上移，
  header/input/actions 同时可见（短表单优先整体移动，不无意义开 body scroll）。
- **TextbookPage**：body（摘要+label+输入）heightIn(max) 内滚，**sticky action footer**
  （细 divider + 取消/保存）固定于卡底，键盘显示/隐藏都完整可点击。
- **Export**：同样 sticky footer（取消/选择保存位置）固定；body 独立滚动。
- Outline/Thumbnail 无 footer，body 直接滚动；outer 卡到 maxHeight 后不再长。
- focus 仍在 morph settled 后请求（MorphExpandMs+60）。

## 3. Context-aware initial scroll（R25.1 §3）

- **Thumbnail**：`rememberLazyGridState(initialFirstVisibleItemIndex =
  thumbnailInitialRow(current))`——两栏 grid，current=23(1-based) 时首屏围绕 18–25，
  当前页位于视口上 1/3～中部，**绝不从 PDF1 开始**；selected outline 保持清晰。
- **Outline**：`LaunchedEffect(currentIndex) { listState.scrollToItem(outlineInitialRow(
  currentIndex)) }`——当前阅读项定位到视口上 1/3 附近并保留上文 3 行；当前 badge 保持。
- 规则：每次从 Main 新打开即定位（无持久化）。

## 4. Export 密度压缩（R25.1 §5）

- 章节摘要一行「… · PDF 22–23」+ 更换章节 toggle（列表 heightIn 200 内滚）。
- 格式 secondary 压缩为一行短说明：ZIP =「PDF + 元数据 JSON」、纯 PDF =「标注章节与页码」
  （删除常驻长解释文案）；radio 结构清晰。
- 页码范围两个 compact field 同行；sticky footer「取消 / 选择保存位置」。
- 所有影响导出结果的业务参数（章节/格式/起止页）全部保留。

## 5. 测试结果

- **unit+UI 1040 PASS**：
  - 新增 `AdvancedGeometryTest`（9 个纯几何：高位 source 保 top、低位 Export 上移、height
    clamp、极小屏、nav inset、IME 缩小/恢复、target 必在 availableRect 内、thumb/outline
    initial row）。
  - UI：outline 打开定位到 current 项（章节13 首屏可见、章节0 不在首屏）；TextbookPage
    取消/保存经语义 invoke 验证回 Main（Robolectric performClick 无法命中动画中卡的 footer
    ——语义门禁更贴近结构断言）；其余 R25 结构门禁保持。
- lintDebug PASS；assemble Qa/Release PASS；Release `5cb0c0f3…` 交付 Download（SHA 一致）。

## 6. 实机复录（PENDING — adb 离线）

A Export row→Advanced（17733 底部裁切消失）；B TextbookPage IME show/hide（取消/保存始终
完整可见、卡平滑移动）；C current=23 打开 Thumbnail 首屏直接见 PDF23 附近；D current「插图1」
打开 Outline 首屏见插图1 于中上区域；E Export body 滚动时 sticky footer 保持。
每条 source-row→Advanced 导出 0/20/40/60/80/100% 帧。

## 7. 明确回答

- Advanced targetTop 是否仍硬等于 sourceTop？—— **否**（viewport-aware coerceIn）。
- 低位 source 是否允许 target card 向上调整？—— **是**。
- IME 显示时 TextbookPage actions 是否始终可见？—— **是**（sticky footer + 卡上移）。
- Thumbnail 是否仍每次从 PDF1 开始？—— **否**（initial 定位到当前页附近）。
- Outline 是否仍每次从目录第一项开始？—— **否**（定位到当前阅读项）。
- Export primary action 是否仍可能被 safeBottom 裁切？—— **否**（target rect 必在安全区）。
- Reader More 是否重新引入 BottomSheet？—— **否**。
