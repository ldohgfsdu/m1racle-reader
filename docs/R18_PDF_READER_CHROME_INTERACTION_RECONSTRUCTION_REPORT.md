# R18 PDF Reader 顶部 Chrome 交互语义重构（r18.md 落地）

**Date:** 2026-08-04
**Scope:** ①滚动方向规则修正（UP=收纳 / DOWN=恢复，方向测量层不动）；②四态正交状态模型；
③正文单击只切胶囊；④滚动/点击/overlay 三控制器解耦；⑤胶囊 anchored 跳页卡（替代 BottomSheet）；
⑥轻量二级操作 anchored/inline；⑦三点菜单避让胶囊；⑧阅读方式/阅读界面/设置教材页码 inline accordion。
**Status:** 实现完成（unit + lint + assemble 全 PASS）；**实机验收 PENDING**（ADB 离线）。未 commit。

---

## 1. 方向与状态模型（r18 一、二）

- **R17 ground truth 保持不动**：`anchorDeltaY<0=CONTENT_MOVING_UP`（内容向上）、`>0=DOWN`；
  `mapScrollToContentMotion` 未改。本轮只改状态机产品行为表。
- **四态正交模型**（capsuleVisible × edgeButtonsVisible）：
  | 状态 | capsule | buttons | 滚动路径 |
  |---|---|---|---|
  | FULL | T | T | UP 收纳起点 / DOWN 恢复终点 |
  | BUTTONS_ONLY | F | T | UP 收纳第一级 / DOWN 恢复第二级 |
  | CAPSULE_ONLY | T | F | 仅单击进入；UP→HIDDEN(36)，DOWN→FULL(32) |
  | HIDDEN | F | F | UP 收纳终点 / DOWN 恢复起点 |

- **transition matrix（r18 一）**：
  - UP（内容向上，用户向上滑继续阅读）＝ 收纳：FULL →36→ BUTTONS_ONLY（胶囊先收）→56→ HIDDEN（两圆后收）。
  - DOWN（内容向下，用户向下滑寻找控制）＝ 恢复：HIDDEN →32→ BUTTONS_ONLY（两圆先现）→28→ FULL（胶囊后现）。
  - fling 同矩阵（一级/次）；overshoot remainder 保留；dead zone 0.5dp 噪声 epsilon；确认 12/24dp；cooldown 同向不阻碍——R17 机制全部保留。
- **CAPSULE_ONLY 是正式状态**（不得为兼容旧枚举禁止）。

## 2. 输入 ownership（r18 三、四）

| 控制器 | 负责 | 禁止 |
|---|---|---|
| Scroll controller | capsuleVisible + edgeButtonsVisible | 不经滚动直接改 overlay |
| Tap controller（正文单击） | 仅 capsuleVisible（FULL↔BO、HIDDEN↔CAPSULE_ONLY） | 触碰两圆 |
| Overlay controller | capsuleExpanded / overflowMenu / inline submenu | 直接复用 ChromeLevel 连带改两圆 |

- 单击验收：FULL tap→BO；BO tap→FULL；HIDDEN tap→CAPSULE_ONLY；CAPSULE_ONLY tap→HIDDEN；全程 buttons 不变。
- 菜单/跳页卡互斥（打开其一自动关闭另一）；两者打开期间滚动冻结；关闭时 resetForNewSession。

## 3. 胶囊 anchored 跳页卡（r18 五、六）

- 点击顶部胶囊 → 胶囊本身作为 header 保持原位，body 从胶囊下边缘向下展开
  （`expandVertically(expandFrom=Top)` + fade，200ms）；收起 body 向上折回；**无 BottomSheet、无全屏 dim**。
- 内容：当前页、数字输入（自动聚焦）、取消/跳转；系统键盘从底部出现（卡片顶部锚定不受影响）。
- 删除旧 `JumpToPageSheet`（BottomSheet）。
- 大型内容保留 Sheet：教材目录、页面缩略图、导出章节（ReaderSecondarySurfacePolicy：anchored/lightweight vs heavyweight）。

## 4. 三点菜单避让（r18 七）

- 菜单最终 top = Chrome row bottom + spacing（按钮 top + 44 + 8dp）；morph 从圆位置平滑下滑展开
  （`cardTopOffset` Animatable 0→52dp 与宽高并行），收起反向；right/TopEnd 锚定不变。
- 胶囊水平避让仅作防御：收窄目标带 120dp 硬下限（禁止消失），180ms 动画。
- 几何回归（JVM）：menuBounds ∩ capsuleBounds = ∅（垂直分离为主机制），横竖屏 + 水平重叠场景覆盖。

## 5. 阅读方式 / 阅读界面 / 设置教材页码 → inline accordion（r18 八）

- 一级菜单 6 项**全程保留**：阅读方式、阅读界面、教材目录、页面缩略图、导出章节、设置教材页码。
- 点阅读方式 → 该 row 正下方插入「上下连续 / 左右翻页」（当前项 check）；点阅读界面 → 「普通 / 全屏沉浸」；
  其余一级项目保留并平滑下移；最多一个 section 展开（A→B 自动折叠）；chevron 随展开旋转；
  菜单 surface 高度 200ms 平滑变化（解析式高度 + Animatable）。**删除 replacement 导航**（旧 below-block 隐藏逻辑）。
- 设置教材页码 → 该 row 下方插入紧凑输入区（当前页提示、数字输入、保存/清除）；删除旧 `SetPrintPageSheet`（BottomSheet）。

## 6. 自动化回归（r18 十）

- `PdfChromeScrollStateMachineTest`（28 用例）：UP 完整收纳链 / DOWN 完整恢复链 / 单击四态只切胶囊 /
  scroll+tap 交错无非法态 / 抖动·确认·cooldown / fling 矩阵 / session·reset / 帧率无关轨迹 / adapter ground truth。
- `ReaderChromeGeometryTest`（4 用例）：菜单-胶囊垂直分离（竖屏/横屏/水平重叠）、避让不消失。
- 全量 `:app:testDebugUnitTest` + `:app:lintDebug` + assembleDebug/Qa/Release PASS。

## 7. 构建与交付

- QA：`.external_outbox/markleaf-qa-latest.apk` SHA-256 `1415c8e9bf01eed1ecb0ccc6c6dd72d66bf5669b0381c4929ba01bdd83105209`
- Release：`.external_outbox/markleaf-release-latest.apk` SHA-256 `be3f4ba9264532b43145efd936b41bab56ab5b43809409adc2df00605dc0f5a8`
  → 已推送 `/storage/emulated/0/Download/markleaf-release-latest.apk`（校验 OK）。

## 8. 实机验收（PENDING — ADB 离线，r18 十一 A–G）

A. 向上连续慢拖：胶囊先收 → 两圆再收；B. 向下连续慢拖：两圆先现 → 胶囊再现；
C. 连续点击正文：只切胶囊，两圆纹丝不动；D. 两圆已隐藏后点击正文：只现胶囊（CAPSULE_ONLY）；
E. 点击胶囊：卡片从胶囊位置向下展开（不从底部冒出）；F. 点击三点：菜单与胶囊无重叠；
G. 阅读方式/阅读界面：子项同卡内展开、一级项目全程保留并平滑下移。

禁止在交互语义稳定前调整 36/56/32/28 阈值。
