# R23 Reader More Surface 连续状态族收口（Main ↔ TextbookPageForm 容器变换）

**Date:** 2026-08-05
**Scope:** 按 r23.md 收口 Reader 的 secondary surface 交互。已正确的部分不动
（三点圆→Main morph、Main→Expanded 卡内展开、目录/缩略图/导出走 BottomSheet）。
本轮核心：**Main → 教材页码 Form 从「关菜单再弹新卡」改为「同一容器继续 morph 成 form」**，
并统一 surface family 的状态返回、透明度分层、IME 时序、JumpPageForm motion grammar。
**Status:** 实现完成（unit 1019 + Robolectric Compose UI 15 + lintDebug + assemble Qa/Release PASS）。
Release `43e04e22…` 已复制 `/storage/emulated/0/Download/`（SHA 一致）。
**实机录屏 PENDING**（adb 离线；r23 §十九 八条链路 + §二十二 逐帧截图待设备）。
未 commit。

---

## 1. 最终 overlay 状态机

```kotlin
sealed interface ReaderMoreState {
    data object Closed                // 44dp 三点圆
    data object Main                  // 圆 morph 成的主菜单卡
    data class Expanded(val section: PdfChromeSubmenu)   // 卡内二级展开（互斥，单 section）
    data object TextbookPageForm      // 同一容器 morph 成的教材页码 form
}
enum class PdfChromeSubmenu { READING_MODE, TOP_BAR }
```

固定路径（r23 §五/§十六）：

```
○ → Main → Expanded(section)     （Back/outside：Expanded → Main，不是 Closed）
○ → Main ↔ TextbookPageForm      （取消/保存/Back/outside：Form → Main，绝不直跳 Closed）
Main → Closed                    （用户 dismiss，morph 回圆）
```

状态互斥由构造保证（Expanded 单 section；Form 与 Expanded 不同变体）。JumpPageForm 仍是胶囊
自身的展开态（capsuleExpanded，与 More 族互斥——打开菜单会先收胶囊）。Sheet（目录/缩略图/
导出）不在本 family：三个互斥 boolean + `!menuActive` 门控，与 More 族不共存。

## 2. 三点圆 → Main（保持 R22 实现，未动）

单容器 morph：`Animatable` width/height（44→216 / 44→内容自然高，260ms FastOutSlowIn），
corner 固定 22dp（圆与卡是同一 shape 的零帧/终帧），TopEnd 锚点恒定；⋮ 90ms 早退、内容
delay 90ms 后进、关闭 200ms 原路倒放。

## 3. Main → Expanded（保持 R22 实现，未动）

卡内双 `AnimatedVisibility`（expandVertically 220ms + fadeIn delay 40ms），占位动画推
siblings 连续下移，cardHeight 跟随自然高；section 直切（A exit + B enter 并行）。

## 4. Main → TextbookPageForm（本轮核心）

同一 `ReaderMoreMorphSurface` 容器：

- 点击「设置教材页码」（`closeOnClick = false` 新增标志——不再走通用「点击后关菜单」）：
  state → `TextbookPageForm`。
- 阶段 1：菜单内容整体 fade out（140ms），点中的行不闪断。
- 阶段 2：容器 width 216→288dp、height 动画到 form 自然高（min(自然, IME 可用)）——
  同一 Animatable，与圆→卡同一套几何语言；surface alpha 同步 0.82→0.88（form 更实）。
- 阶段 3：form 内容 fade in（delay 40ms）；输入框自动聚焦延迟到容器 settle 之后
  （`delay(MorphExpandMs + 60)`，r23 §七：绝不在 morph 中途弹键盘顶飞 target bounds）。
- 返回：取消 / 保存 / Back / outside tap 全部走 **Form → Main**（`onFormCancelled` /
  `onFormConfirmed` 均返回 Main）——卡片 morph 回菜单，用户再决定 dismiss（r23 §五/§六）。
  `requestClosePdfMenu` 统一处理「Form → Main；Main/Expanded → 关回圆」。
- 移除旧路径：`PrintPageEditor.kt`（独立 288dp popover 层）整文件删除；PdfChrome 内
  print-editor 独立 catcher + AnimatedVisibility 层删除；`MoreFormDropGap`/`formTopDp`/
  `formMaxHeightDp` 删除；`printSheetOpen` boolean 删除。

## 5. PagePill → JumpPageForm（grammar 统一，r23 §八）

保持胶囊单 surface 展开（CapsulePopoverShape 顶部宽度连续、向下生长）。本轮微调内容节奏：
body fadeIn 增加 40ms delay——与 More 家族一致「容器先动、内容后跟」；关闭反向。
锚点不同（胶囊 vs 三点圆），motion grammar 相同。

## 6. Main → BottomSheet handoff（保持 R22 实现，未动）

目录/缩略图/导出：点击 → menu content 先退 → 容器 morph 回圆（200ms）→ `!menuActive`
门控后 BottomSheet 才出现（不叠层）；chrome 隐藏也等 morph 完成。sheet 内部业务未动
（r23 §十一）。

## 7. Motion specs

| 阶段 | 参数 |
|---|---|
| 圆 → Main（宽/高） | 260ms FastOutSlowIn |
| Main → Form（宽/高/alpha） | 260ms FastOutSlowIn（同一 Animatable + surfaceAlpha 0.82→0.88） |
| 菜单内容退出 / form 内容进入 | fadeOut 140ms / fadeIn 180ms delay 40ms |
| collapse 回圆 | 200ms，原路倒放 |
| nested expand / collapse | 220ms / 180ms |
| ⋮ 图标 | 开 90ms 早退；关 delay 110ms 后 90ms 回 |
| form 输入框聚焦 | 容器 settle 后（MorphExpandMs + 60ms） |
| JumpPageForm body | expandVertically 200ms + fadeIn delay 40ms |
| 透明度分层 | 顶部 chrome 0.78 / More 卡 0.82 / Form 0.88 / BottomSheet sheetContainer |

## 8. 删除的旧 surface / boolean / path

- `PrintPageEditor.kt`（独立 form 卡片）——整文件删除。
- PdfChrome 的 print-editor 独立层（catcher + AnimatedVisibility + formTopDp/formMaxHeightDp）——删除。
- `printSheetOpen` boolean、`MoreFormDropGap`——删除。
- 通用「点击一级项即关菜单」逻辑对 form 入口关闭（`PdfChromeMenuItem.closeOnClick` 门控）。
- 旧的 `ReaderMoreState.Form(form)` 泛型变体 → 独立 `TextbookPageForm`。

## 9. 测试结果

- unit **1019 PASS**（含状态机 17 个：form 全路径 Main↔Form、Form→confirm→Main、互斥）。
- Robolectric Compose UI **15 PASS**（新增 3 个 form 用例：Main→form 内容出现且菜单消失且
  仍只有单一 surface；取消 morph 回菜单；输入页码保存后 morph 回菜单）。
- lintDebug PASS；assemble Qa/Release PASS。
- Release `43e04e22…`（10.9MB）交付 Download，SHA 一致。

## 10. 实机录屏（PENDING — adb 离线）

r23 §十九 八条链路待录制：A 圆→Main→圆；B 圆→Main→阅读方式→Main→圆；
C 圆→Main→阅读界面→Main→圆；D 圆→Main→Form→Main→圆；E 胶囊→JumpPageForm→胶囊；
F/G/H Main→缩略图/目录/导出 BottomSheet→Reader。每条 transition 的 0/25/50/75/100%
截图待设备。逐帧门禁（§二十）待录屏验证。

## 11. 四句明确回答

- A. 三点菜单是不是独立 Popover？—— **不是**（圆本身 morph 成卡）。
- B. 阅读方式/阅读界面是不是另一张二级卡片？—— **不是**（同一卡片内部撑开）。
- C. 设置教材页码是不是关闭主菜单后凭空出现的新卡片？—— **不是**（主菜单卡本身继续
  morph 成 form，取消/保存原路 morph 回主菜单）。
- D. 目录/缩略图/导出是不是继续使用 BottomSheet？—— **是**（复杂任务不塞进 compact card）。
