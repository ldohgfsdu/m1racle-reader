# R25 Reader Advanced Foreground Card Unification — 移除 More 链 BottomSheet

**Date:** 2026-08-05
**Scope:** 按 r25.md 正式确定 Reader 三点菜单的最终二级交互体系：轻量二级（阅读方式/阅读界面）
回到 **Same Card Inline Expansion**（R24 的 Nested Overlay 路径已按最新指令移除）；四个任务型入口
（设置教材页码/教材目录/页面缩略图/导出章节）统一为 **Foreground Advanced Card**（source row →
更宽任务卡 shared-container transform）；More 链 BottomSheet 全部退出并删除。冻结项全部未动。
**Status:** 实现完成（unit+UI 1031 PASS + lintDebug + assemble Qa/Release PASS）。
Release `bc379ae8…` 已复制 `/storage/emulated/0/Download/`（SHA 一致）。
**实机录屏 PENDING**（adb 离线；r25 §17 A–G 七条链路 + 0/20/40/60/80/100% 帧 + 硬门禁 ①–⑭）。

---

## 1. 最终 overlay 状态机（R25 §12）

```kotlin
sealed interface ReaderMoreState {
    Closed                        // 44dp 三点圆
    Main                          // 6 行菜单卡
    InlineExpanded(section)       // 轻量：Same Card inline expansion（单 section）
    Advanced(kind)                // 任务型：Foreground Advanced Card（单 kind）
}
enum class PdfChromeSubmenu { READING_MODE, TOP_BAR }
enum class ReaderAdvancedKind { TEXTBOOK_PAGE, OUTLINE, THUMBNAILS, EXPORT }
```

固定路径：`Closed ↔ Main ↔ InlineExpanded(section)`；`Main ↔ Advanced(kind)`。
Inline 与 Advanced 构造级互斥。Back 层级：InlineExpanded→Main；Advanced→Main；Main→Closed。
`showOutlineSheet / showThumbSheet / exportPlan(仅数据) / TextbookPageForm` 旧状态全部删除。

## 2. 轻量二级：Same Card Inline Expansion（R25 §1）

- 阅读方式/阅读界面：Main Card 宽度与 anchor 不变，原 row 原位，chevron right→down；
  children 在 SAME CARD 内 `expandVertically`（220ms）reveal，siblings 平滑下移，
  card bottom 连续增长（cardHeight 跟随自然高）。ReadingMode↔ReadingInterface 平滑直切
  （A collapse + B expand 并行，一次一个 section）。
- 不创建 Nested Overlay/大卡/parent dim/elevation（R24 的 child overlay 路径已删除，
  不保留 legacy）。

## 3. ReaderAdvancedCard shell（R25 §3–§6）

`ReaderAdvancedCard(sourceRowTop, targetWidthDp, maxHeightDp, heightMode, kind, data,
imeAvailableHeightDp, onCollapse, content)`：

- **source bounds** = 被点 row 真实 bounds（parentRowTop = pad + index×52）；**target**：
  宽 = min(320dp, 屏宽−28)（300–328 带），右缘继承 source right，顶 = row top，
  高 = CONTENT 模式 min(内容自然高, maxHeight) / MAX 模式 = maxHeight。
- **maxHeight** = (安全可用高度)×0.72（避开 status/navigation/IME insets）；长卡固定
  outer bounds，body 内部滚动（header 固定）。
- **motion**：width/height/corner 三 Animatable（190–210ms emphasized decelerate），
  corner 10（row shape）→26 连续插值；top/right 连续，left/down 主扩张；collapse 严格
  reverse 回 source row；内容预布局 + clipToBounds reveal（无整组 fade 冒充）。
- **surface**：实体 `surfaceContainerHigh` @1f + elevation 8/spot 0.18/amb 0.12 + hairline
  border——PDF 人物与 parent 内容不可穿透；与 Main（surface@1f）形成卡压卡层级。
- **header 继承 row continuity**：同 icon、同 label、同 baseline，chevron right→down；
  点击 header = collapse 回 Main。
- **Parent 后层**：Main Card 完全保留（geometry 零 reflow），exposed 内容 alpha 降权
  0.65（清楚可辨，不透底）。

## 4. 四个 Advanced body（R25 §7–§10）

| 入口 | heightMode | body |
|---|---|---|
| 设置教材页码 | CONTENT | 一行状态摘要「PDF 第 X 页 · 教材页…」+ 小 label + 44dp 数字输入 + 取消/保存；autoFocus 延迟至 bounds settled；IME Done 直接提交 |
| 教材目录 | MAX | 计数行 + [导入 PDF 目录][手动新建] compact actions + LazyColumn（indent/当前 badge/PDF range）；点击跳 PDF 后层**卡保持打开**（连续预览）；header/Back 才回 Main |
| 页面缩略图 | MAX | 「23 / 218」状态行 + **2 栏** LazyVerticalGrid（真实 aspect 0.72、当前页 outline、页码标签）；点击跳页卡保持打开 |
| 导出章节 | CONTENT | 章节摘要一行「… · PDF 22–23」+ 更换章节 toggle（radio 列表 heightIn 200）；两个格式 radio（labelZh/hintZh 短说明）；页码范围两个 compact 字段同行；取消/选择保存位置（系统 SAF 流程接管） |

## 5. 删除的旧路径（Phase D）

- `TextbookOutlineSheet.kt`（Outline BottomSheet）——整文件删除；`OutlineEditorState` 迁移至
  `ReaderAdvancedCard.kt`。
- `PdfThumbnailSheet.kt`（Thumbnail BottomSheet）——整文件删除（缩略图逻辑迁入
  `ThumbnailsAdvancedContent`）。
- `ChapterExportDialogs.kt` 中 `ChapterExportSheet`（Export BottomSheet）——删除；仅保留
  `ChapterExportProgressDialog`（系统流程状态 dialog，非 More 链）。
- `showOutlineSheet / showThumbSheet` booleans、`printSheetOpen`、`TextbookPageForm`、
  `bigSheetOpen` chrome 隐藏、`ReaderAdvancedData` 之前的所有 sheet 渲染块——删除。
- 不保留任何 fallback/flag/兼容路径。

## 6. 测试结果

- unit+UI **1031 PASS**：状态机（Inline/Advanced 互斥、层级返回、快速路径）、UI 结构门禁
  （6 项主菜单、inline 展开/收起/互斥、Advanced 从 row 长出、取消/保存回 Main、parent 内容
  保留、Advanced 与 Inline 不共存、单 surface）、几何（Advanced 320dp 带、row top 继承、
  右缘连续、maxHeight 70–75%）。
- lintDebug PASS；assemble Qa/Release PASS；Release `bc379ae8…` 交付 Download（SHA 一致）。

## 7. 实机录屏（PENDING — adb 离线）

A 三点圆→Main→教材页码 Advanced→Main→圆；B Main→目录 Advanced→连续点击 ≥3 目录项→Main；
C Main→缩略图 Advanced→滚动→连续跳页→Main；D Main→导出 Advanced→改格式/页码→取消→Main；
E Inline expansion 与 Advanced 连续切换；F 每个 Advanced 快速开合 ≥10 次；G TextbookPage
IME 开/关/resize。每条 source-row→Advanced 导出 0/20/40/60/80/100% 帧。硬门禁 ①–⑭待设备。

## 8. 明确回答

- Reader More 中的设置教材页码/教材目录/页面缩略图/导出章节是否还使用 BottomSheet？—— **否**。
- 阅读方式/阅读界面是否改成 Advanced Card？—— **否**，继续 Same Card inline expansion。
- 四个高级功能是否都从对应一级 row morph 为 Foreground Advanced Card？—— **是**。
- 长内容是否通过整个卡片继续无限变高解决？—— **否**，达到 maxHeight 后 body 内部滚动。
- 旧 BottomSheet fallback 是否保留？—— **否**。
