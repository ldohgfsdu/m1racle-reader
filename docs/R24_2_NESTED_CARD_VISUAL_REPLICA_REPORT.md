# R24.2 ChatGPT Nested Card Visual Replica

**Date:** 2026-08-05
**Scope:** 按 r24.2（17395.mp4/17391–17394 vs 17389.mp4 逐帧复核）修正视觉结果，**不改交互模型**
（source-row→Nested Child overlay 结构保留）。冻结：滚动状态机、两圆滑动、PagePill、教材页码
业务、目录/缩略图/导出 BottomSheet、状态机主体、Main→Form。
**Status:** 实现完成（unit+UI 1033 PASS + lintDebug + assemble Qa/Release PASS）。
Release `7fb97e97…` 已复制 `/storage/emulated/0/Download/`（SHA 一致）。
**实机录屏 PENDING**（adb 离线；A–E 录屏 + 0/20/40/60/80/100% 帧待设备，不自行宣布 PASS）。

---

## 1. 停止高 alpha 毛玻璃 → 实体主题 Surface（r24.2 第一）

- **Parent More**：`containerAlpha 0.97 → 1f`，主题 `surface` 实体色——PDF 人物在 card
  bounds 内完全不可见（不再是"代码 0.99 所以理论上不可见"，视觉事实即实体覆盖）。
- **Nested Child**：`containerAlpha 0.99 → 1f` + **`surfaceContainerHigh`**（tonal 分层：
  一张卡压在另一张卡上，浅色主题略深/深色主题略亮的实体前景卡）。
- 顶部 Chrome（back/pill/圆）保持半透明 0.80——另一层视觉语言，未统一。
- Main→Form（17394）冻结，form 0.97 未动。

## 2. Child Focus + Parent Recede（r24.2 第二/第十）

- child active 时，**exposed parent 内容**（仍露在 child 外的 label/icon/chevron）统一动画
  降到 **42% emphasis**（35–45% 带，`ParentEmphasisAlpha`，140ms emphasized）。
- **只降 parent 内容 alpha**：parent background 保持实体 surface（禁止重新透出 PDF）、
  geometry 零变化、rows 不移动不删除。
- child collapse 时 parent 内容原路回 1（reverse）。
- 验收点：ReadingInterface child 打开时上方"阅读方式"原位降权变灰 ✓；ReadingMode child
  在第一行（无 exposed row），观察下边界与后层。

## 3. Child = 独立完整前景卡（r24.2 第三）

- 完整 rounded shape（四角，非仅底角）：corner **10dp（source row）→ 26dp（target card）
  连续插值**（Animatable 驱动，首帧即 row 圆角）。
- 实体 `surfaceContainerHigh` + 轻 elevation（8dp / spot 0.18 / ambient 0.12）+ hairline
  border——边界肉眼可辨、非重阴影；不再像"parent 内部的白 section"。

## 4. source/target bounds grammar（r24.2 第四，保持）

- sourceBounds = 被点 row 真实 bounds；targetBounds = 内容需要（width 双 Animatable 插值，
  未硬锁 parent 宽）；top/right continuity；parent bounds 与 sibling 坐标全程不变。

## 5. bounds+clip reveal（r24.2 第五/第六，保持 + 微调）

- child：header 首帧即 source 连续内容（`childAlpha` 展开 **snap 1**——source 位置与
  parent row 完全重合，无可淡内容）；children 预布局 + clipToBounds 随 bottom 揭示；
  opacity 仅 0.85→1 轻辅助；collapse 完全 reverse（clip 收回 + alpha 1→0 跟随）。
- Main：内容首帧预置 alpha=1，clip 揭示主导，190ms（160–200 带）；无空白肥皂块阶段
  （若实机仍见，优先调 bounds progress 让首行更早进入 clip，不加 fade）。

## 6. Parent More 静态视觉收紧（r24.2 第七）

- **删除 Main 一级菜单 divider**（参考主菜单无 section line；divider 只保留在 Nested
  Child 内 header 与 children 之间——细 0.3 alpha、横向 padding 16）。
- **一级 label 字重 Medium → Normal**（降一档，避免过黑过粗；层级由缩进/字号体系承载）。
- icon 70–75% emphasis（onSurface 75%）、label 100%、chevron 与 icon 同等级（75%）。
- row rhythm 保持 52dp（50–52 带），touch target 不减。

## 7. Nested Child 内部布局（r24.2 第八）

- header 与 source row 完全同位置/同 icon/同 label；chevron right→down 连续旋转。
- header 下方细低对比 divider；children 左缘 indent 78dp（与 header 文本区明确区分，
  不重复一级 icon）；selected check 靠右。
- ReadingMode = 上下连续✓/左右翻页；ReadingInterface = 普通/全屏沉浸✓（未加新模式）。

## 8. 测试结果

- unit+UI **1033 PASS**（几何测试更新：Main 菜单高度 = 3×2 + 52×6，无 divider）。
- lintDebug PASS；assemble Qa/Release PASS；Release `7fb97e97…` 交付 Download（SHA 一致）。

## 9. 实机录屏（PENDING — adb 离线）

A 三点圆→Main→圆 ×5；B Main→ReadingMode→Main；C Main→ReadingInterface→Main；
D ReadingMode→ReadingInterface handoff；E 快速 nested 开合 ×10；每条导出
0/20/40/60/80/100% 帧。硬门禁 ①–⑩（PDF 人物不透 card、child 内无 parent ghost、
exposed parent 降权且 geometry 不动、child 独立前景卡、Main 无 divider、header 连续、
children clip reveal、parent 坐标不变、collapse 严格 reverse、无空白肥皂块）待设备复核。

## 10. 明确回答

- More/Child 是否仍为半透明 glass surface？—— **否**（实体主题 surface，alpha = 1f）。
- Nested active 时 exposed parent content 是否仍保持 full emphasis？—— **否**（42% 降权）。
- Child 覆盖区是否还能看到 parent glyph？—— **否**（实体 surfaceContainerHigh 完全覆盖）。
- Nested Child 是否是一张独立实体前景 card 而不是 parent 内 section？—— **是**
  （完整圆角 + tonal separation + 轻 elevation + z1 sibling）。
- Parent 是否因为 child 展开发生 reflow？—— **否**（bounds 与 sibling 坐标全程不变）。
