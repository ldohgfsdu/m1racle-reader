# R25.2 — Reader More Nested Card Interaction (ChatGPT ground truth)

**Date:** 2026-08-07  
**Scope:** 以 ChatGPT Android「主菜单 → 添加到项目」为交互基准，废弃 R25.1 单 surface replace，改为 NESTED CARD + MAIN 弱化上下文。  
**Status:** implemented + automated_verified。**实机六条链路 + 0/25/50/75/100% 帧 PENDING（adb 无 device）。**

---

## 1. 交互模型（最终两种）

| 类型 | 入口 | 行为 |
|---|---|---|
| **INLINE** | 阅读方式 / 阅读界面 | MAIN 卡内 accordion；主 row 不动；child 48–52dp；文本与 title 列对齐；只改 MAIN 高度 |
| **NESTED CARD** | 设置教材页码 / 教材目录 / 页面缩略图 / 导出章节 | ChatGPT「添加到项目」：MAIN 原位保留；content alpha→~0.26；child 从真实 sourceBounds 展开 |

**废弃：** R25.1「MAIN 完全让位 / 视觉树只留一个 surface」。  
**禁止：** 任意位置生成巨大横向 foreground 黑板；双卡同等高对比；新 Popup/Overlay 坐标体系。

---

## 2. 状态机

```
CLOSED / MAIN / INLINE_READING_MODE / INLINE_READING_UI /
CHILD_PAGE_MAPPING / CHILD_TOC / CHILD_THUMBNAILS / CHILD_EXPORT
```

- `isChild()` 取代 `isAdvanced()`
- 打开：`onOpenChild`；关闭完成：`onChildCollapse` → MAIN
- 逆变换期间状态保持 `CHILD_*`（身份即状态 → 各自回自己的 source row）

---

## 3. Nested 时序（180–220ms，NestedMorphMs=190）

**Open**

1. 点击 task row → 同步捕获 `sourceBounds`（overlay-root）
2. MAIN 保持原 geometry；content alpha 1→`ParentContentContextAlpha`（0.26）
3. `NestedCardHost` 从 sourceBounds 连续插值 x/y/width/height/cornerRadius
4. 右边缘继承 MAIN 右；左扩 `NestedLeftExpand=72dp`（48–96 带）
5. p≥0.35 后 body 渐入；完成后 child 为唯一高对比焦点

**Close（完全反向）**

body 先退 → bounds 缩回 sourceBounds → MAIN content alpha 恢复 → `onChildCollapsed` → MAIN

---

## 4. 几何 / 材质

| Token | 值 |
|---|---|
| MAIN 宽 | 218dp |
| Nested 宽 | MAIN + 72dp（默认），clamp 到 back-clearance |
| Nested 左扩 | 48–96dp（默认 72） |
| 右边缘 | MAIN / source right（TopEnd） |
| 父 content alpha | 0.26（0.20–0.32），**surface glass 保持 0.90** |
| Glass | alpha 0.90 / 1dp border / 统一 shadow |
| Body reveal | p≥0.35 |
| IME | 顶边固定；只压可用高度 |

---

## 5. 删除 / 重命名

| 旧 | 新 |
|---|---|
| `ADVANCED_*` | `CHILD_*` |
| `isAdvanced` / `onOpenAdvanced` / `advancedClosing` | `isChild` / `onOpenChild` / `childClosing` |
| 单 surface XOR（MAIN 不组合） | MAIN 始终组合 + NestedCardHost |
| `siblingFade` 预淡出后替换 | `parentContentAlpha` 整层内容降权 |
| `AdvancedDefaultWidth` 284 全宽倾向 | `NestedDefaultWidth = MAIN + NestedLeftExpand` |
| `ReaderAdvancedCard` 调用名 | `NestedCardHost`（文件仍为 ReaderAdvancedCard.kt） |

---

## 6. 验证

### Automated

- `:app:testDebugUnitTest --offline` PASS  
- 结构门禁：`ParentContentContextAlpha`、MAIN+child 共存、NestedLeftExpand、无单 surface XOR  
- UI：child 打开后 MAIN 行仍在（context）；教材目录 count=2（header+parent row）  
- origin fidelity：四 child 各自 row 2..5  

### Release

- `assembleRelease` PASS → `.external_outbox/markleaf-release-latest.apk`  
- 已复制 `/storage/emulated/0/Download/markleaf-release-latest.apk`  
- SHA256: `0363d9db2b32049710b25ed1059cd28acbde378ad6cbc144f729f1421c0f479b`

### Device（PENDING）

`adb devices` 空。须实机录：

1. MAIN ↔ 阅读方式  
2. MAIN ↔ 阅读界面  
3. MAIN → 设置教材页码 → MAIN  
4. MAIN → 教材目录 → MAIN  
5. MAIN → 页面缩略图 → MAIN  
6. MAIN → 导出章节 → MAIN  

四 Nested 动画各 0/25/50/75/100% 帧证明 sourceBounds→childBounds→sourceBounds。

硬门禁未满足前 **禁止宣布完成**。

---

## 7. 未改

PDF renderer、滚动方向状态机、返回圆逻辑、中间胶囊、阅读 chrome 状态机。
