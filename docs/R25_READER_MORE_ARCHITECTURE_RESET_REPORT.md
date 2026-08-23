# R25 Reader More Menu Interaction Architecture Reset

**Date:** 2026-08-07
**Scope:** 删除 Reader More 链路上已混杂的多套 overlay/popup/card-stack 路径，重建
唯一交互架构：一个显式状态机 + 一个几何宿主 + 一套前景几何/材质 token。
**Status:** implemented + automated_verified（984 testcases PASS / lint EXIT=0 /
assembleRelease PASS，APK 已交付 Download）。**实机录屏 PENDING（adb 离线）。**

---

## 1. 代码层状态图（唯一事实源）

```kotlin
enum class ReaderMoreState { CLOSED, MAIN, INLINE_READING_MODE, INLINE_READING_UI,
    ADVANCED_PAGE_MAPPING, ADVANCED_TOC, ADVANCED_THUMBNAILS, ADVANCED_EXPORT }
```

```
                    ┌──────────────────────────────────────┐
                    │              CLOSED                  │  44dp 三点圆
                    └──────────────────────────────────────┘
                                   │ ⋮ tap（onToggleOpen）
                                   ▼
                    ┌──────────────────────────────────────┐
                    │               MAIN                   │  6 行主菜单（同一张卡）
                    └──────────────────────────────────────┘
       section tap │            │ task row tap（4 个高级入口，各记录真实 sourceBounds）
                   ▼            ▼
      ┌───────────────────┐  ┌────────────────────────────────────────────┐
      │INLINE_READING_MODE│  │ ADVANCED_PAGE_MAPPING   ← row 2（设置教材页码）│
      │INLINE_READING_UI  │  │ ADVANCED_TOC            ← row 3（教材目录）    │
      │（主 row 下方原地    │  │ ADVANCED_THUMBNAILS     ← row 4（页面缩略图）  │
      │ 展开 child rows，   │  │ ADVANCED_EXPORT         ← row 5（导出章节）    │
      │ 只改变卡片高度）    │  └────────────────────────────────────────────┘
      └─────────┬─────────┘   │ header tap / Back / outside / 保存/取消/SAF 接管
                │ option 选中  │   → advancedClosing=true（状态保持 ADVANCED_*）
                ▼             │     逆变换 foregroundBounds → 各自来源 row
               MAIN ◄─────────┴── onAdvancedCollapsed()（morph 完成后）→ MAIN
```

- 固定路径：`CLOSED ↔ MAIN ↔ INLINE_*`；`MAIN/INLINE_* → ADVANCED_* → MAIN → CLOSED`。
- Inline 与 Advanced 由构造互斥（单一 enum 值）；高级卡逆变换期间状态**保持
  ADVANCED_***（`advancedClosing` 阶段），身份即状态——**删除** originKind /
  advancedRenderingKind 兜底机制。
- 唯一几何宿主 `ReaderMoreHost`（z0 父菜单卡 + z1 前景高级卡，单一 TopEnd anchor，
  单一 overlay-root 坐标空间，无任何 Popup / 第二套坐标）。

## 2. 几何与动画契约

| 项 | 值 |
|---|---|
| 前景宽度 | `min(屏宽−28, 320dp)`，右对齐主菜单（right 继承 source right） |
| 前景高度 | `52dp header + 实测 body 高`，cap `maxHeight≈72% safe`；短卡（设置页码/导出）按内容高，长卡（目录/缩略图）body 内滚 |
| top/bottom | safe-area 约束；**顶部 clamp 用 no-IME bottom**（键盘只压缩底部、绝不推顶） |
| sourceBounds | 点击时同步捕获该 row 真实 bounds（全 Rect，`localBoundingBoxOf(overlay)`）；morph 全程 x/y/width/height/cornerRadius 连续插值 |
| 动画 | 190ms（180–220 带内）；header 前 35% p（≈66ms，60–90ms 带内）共享过渡；body 几何展开 35% 后进入；收起完全反向 |
| 父菜单 | 整层统一降 0.40 content alpha（surface+内容一起），交互 blocker；不 dim PDF |
| 材质 | 圆/胶囊/主菜单/高级卡同一 `readerChromeGlassSpec`：alpha 0.84 / 1dp border / 6dp elevation / spot 0.16 ambient 0.10 |
| 测量 | 参考列 `requiredWidth/requiredHeight(targetWidth×maxHeight)` 固定——body 高度测量与 morph 动画零反馈环 |

## 3. 旧路径删除清单

| 删除项 | 位置 | 替代 |
|---|---|---|
| `PdfChromeSubmenu`（READING_MODE/TOP_BAR） | 全仓 | 并入 `ReaderMoreState`（INLINE_READING_MODE / INLINE_READING_UI） |
| `ReaderAdvancedKind`（TEXTBOOK_PAGE/OUTLINE/THUMBNAILS/EXPORT） | 全仓 | 并入 `ReaderMoreState`（ADVANCED_*） |
| `advancedOriginKind` 捕获 + `advancedRenderingKind()` 兜底 | ReaderMoreHost | 状态在逆变换期间保持 ADVANCED_*，身份即状态 |
| `AdvancedHeightMode`（CONTENT/MAX 双模式） | ReaderAdvancedCard | 统一几何：52dp+实测 body 高，cap maxHeight |
| advanced 卡内 `reportNaturalHeight`（intrinsic 测量） | ReaderAdvancedCard | 参考列固定尺寸 + `onGloballyPositioned` 实测 body |
| `MoreSurfaceAlpha = 1f`（不透明塑料块） | ReaderMoreHost | 统一 glass token（0.84/1dp/6dp） |
| `ParentRecedeAlpha = 0.65`（仅内容层降权） | ReaderMoreHost | `ParentContextAlpha = 0.40` 整层统一降权 |
| `advancedRowIndex` 的 kind 参数 | ReaderMoreHost | state→row 映射（2..5，各高级态独有） |
| `OptionIndent = 78dp`（28dp 大幅右漂） | ReaderMoreHost | `70dp = 主标题列 + 20dp` |
| 输入框内 placeholder「印刷页码」（与 label 重复） | ReaderAdvancedCard | 只保留 field 上方 label |
| `imeAvailableHeightDp` 透传（未被使用） | ReaderMoreHost/AdvancedCard | 卡内直接读 WindowInsets.ime |
| `MenuExitMs`（未使用常量） | ReaderMoreHost | — |
| `ReaderMoreMorphSurface` 名称 | PdfViewerScreen | `ReaderMoreHost` |
| `PdfExpandableMenu.kt` 文件名 | 仓库 | `ReaderMoreHost.kt` |
| 旧状态机测试/UI 测试（锚在旧 sealed interface） | test | 重写为 8 态机器 + ReaderMoreHostUiTest |

**未动**（本轮范围外）：PDF 布局、阅读滚动状态机、顶部两圆与中间胶囊逻辑（胶囊仅因
glass 默认 token 变更而材质统一，逻辑零改动）。

## 4. 验证

- unit+UI：**984 PASS**（新增/重写：ReaderMoreStateMachineTest 14、ReaderMoreHostUiTest 16、
  AdvancedOriginFidelityTest 7、R25ReaderFoundationTest 11、AdvancedGeometryTest 10 含
  IME keep-top 2 用例；MotionContinuityGeometryTest 纯几何契约未变全绿）。
- lintDebug EXIT=0；assembleRelease PASS → `.external_outbox/markleaf-release-latest.apk`
  （10936855 B）已复制 `/storage/emulated/0/Download/markleaf-release-latest.apk`。
- 关键回归门禁（automated）：morph p=0 帧恒等于来源 row rect（目标任意变化不可篡改
  源）；四个高级态各自映射 row 2..5（无公共 anchor）；inline 区在高级卡 in-flight
  期间保持展开（parent 几何冻结）；mid-collapse 采样帧 body 恒为来源 body（永不变
  成设置教材页码）；`advancedClosing` 期间状态不离开 ADVANCED_*。

## 5. 实机验收（PENDING — adb 离线）

8 条链路录制：MAIN↔阅读方式、MAIN↔阅读界面、MAIN↔设置教材页码（含键盘出现/收起）、
MAIN↔教材目录、MAIN↔页面缩略图、MAIN↔导出章节，以及四个高级界面分别收回各自来源
row（设置教材页码→设置教材页码行，目录→目录行，缩略图→缩略图行，导出→导出行）。

每条高级 morph 截 0/25/50/75/100% 五帧：来源 row 边界与卡首帧贴合、轨迹无跳位、
收起与展开严格反向。

硬门禁：
1. 任何时刻最多一个 foreground advanced surface；
2. 不得出现「左侧大卡 + 右后方半截父卡」L 型双黑块（父菜单应读作整体降权的上下文层）；
3. 高级界面不得从错误 row 收回；
4. 目录/缩略图不得突破 viewport（cap 后仅内部滚动）；
5. 不得为修视觉新增第三套 Popup/Overlay 坐标系统（本轮全程单一 overlay-root 空间）。

adb 恢复后执行：
```bash
adb install -r /storage/emulated/0/Download/markleaf-release-latest.apk
adb logcat -s MORE_SURFACE   # top/right 四状态恒等证据
```
