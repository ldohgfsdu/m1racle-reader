# EPUB 触控看门狗与翻页降级

用户报告 46 包「EPUB 滑不动、触控失效、卡死」，修复后仍报告「翻页还是会卡住」。本轮分两步解决：先给纸页层补上退出条件（→ 47），再去掉翻页的等待（→ 48）。产物为 1.0.0 / 48，Room v15；仍使用 Readium 3.3.0 的唯一 Publication / Navigator。

## 根因一：触控被永久占用（46 → 47）

`EpubPaperTurn` 在横滑越过 slop 后接管原生触摸流，并由 `active = overlay != null || awaitingCapture` 决定是否继续吞掉事件。46 的实现在两条路径上没有退出条件：

1. **捕获阶段无看门狗。** 唯一的 `timeout` 只在覆盖层挂载后才 post，且自身要求 `overlay != null`。从越过 slop 到覆盖层挂载之间，代码要等 `child.evaluateJavascript()`——Readium 的实现是 `awaitLoaded()`（`isLoaded.first { it }`，无限等待）加普通 `suspendCoroutine`。WebView 一旦不回调，`awaitingCapture` 永远为真，`ACTION_DOWN` 一律 `return true`，阅读器再也收不到触控。
2. **恢复阶段无退出。** `publishPage` 在 `renderedLocator()` 返回 null 时直接 `return@launch`，而 restoring 状态只能靠 `pageReady` 退出，覆盖层与 `active` 因此常驻。46 的 QA 记录里「图形线程等待导致输入和页面观察失效」即为此现象。

次要因素：每次横滑都新建约 10 MB 全屏位图并逐像素扫描；`onDrag` 无条件消费事件，纸页未接管时横滑可能两边都不处理。

## 根因二：翻页在等一个迟到的通知（47 → 48）

47 消除了卡死，但用户仍报告翻页卡顿。模拟器逐段计时（`PaperTrace`，已移除）给出了确切位置：

| 阶段 | 耗时 |
|---|---|
| 手势 → 捕获 + 导航 | 153 ms |
| **导航 → Readium 翻页通知** | **1.62 s** |
| 通知 → 动画结束 | 325 ms |

也就是说 47 的翻页动画必须等 Readium 报告"页面已就绪"，而 Readium 在页面其实早已开始滚动之后才发这个通知。用户看到的是：纸页盖住屏幕、触控被吞、1.6 秒毫无反应。

## 修复

### 47：交还触控 + 动画降级

- **统一看门狗**（`EpubPaperTurn.kt`）：`deadline` 与 `arm/disarm`，捕获 1500 ms、导航 3000 ms、落页 1200 ms、恢复 2000 ms 四段预算；超时走 `bail()`。预算刻意宽于正常一次翻页（实测 navigate → pageReady 约 1.64 s，1600 ms 的第一版预算会把已完成的翻页撤销）。
- **动画降级、翻页保留**：PixelCopy 失败、协程异常、捕获超时三条路径都改为 `degrade()`：先清状态再执行 `navigate()`。纸页是装饰，翻页是功能。
- **手势归属明确**：`onDrag` 仅在 `paper.active` 时消费；`imageAreas()` 用 `withTimeoutOrNull(400)` 限时；同尺寸位图复用。

### 48：不等通知

- **揭示走自己的时间线**（`REVEAL_BUDGET_MS = 320`）：手势释放后若已提交，动画在 320 ms 内自行完成，不再等待 Readium 的页面通知。`finish()` 的提交条件从 `commit && targetReady` 放宽为 `commit && (targetReady || release == true)`；`bail()` 同样按提交意图收尾，而不是一律回退。
- **DOM 定位改为事后精修**：`publishPage` 收到通知即 `pageReady(changed)` 放行动画，随后在后台用 `renderedLocator` 精修锚点并落库（`if (visible == null || pendingPage != page) return@launch` 防止覆盖更新的页）。定位精度不丢，但不再挡在翻页前面。
- **抠图移到导航之后**：纸页背景的逐像素抠图与 Readium 的重排并行，导航不再等待这260 万像素的循环。
- 新增契约用例 `a committed release never waits for the navigator page report`，锁定揭示预算与"通知即放行"结构。

## 虚拟机验证

Android 15 / API 35 模拟器 `Suanming_Pixel7`（`emulator-5554`），证据在 `output/font-turn-watchdog/`。

**47（QA 包 + 正式包）**

| 场景 | 结果 |
|---|---|
| 单次横滑 | 1 / 15 → 2 / 15，`reset commit=true` |
| 连续 6 次横滑（1.2 s） | 逐页前进到 8 / 15 |
| 8 次快滑（0.4 s） | 到 12 / 15，无叠加卡死 |
| 末页越界 | 停住，触控仍可用 |
| 菜单往返后横滑 | 12 → 13 → 14，触控未失效 |

**48（正式包 48 装机）**

| 场景 | 结果 |
|---|---|
| 翻页耗时 | 手势 → 动画结束 **约 0.64 s**（47 为 2.1 s，约 1/3） |
| 揭示正确性 | 滑后 1 s 截图已是新页（15/15「Privacy and troubleshooting」），无旧页闪回 |
| 连续横滑（1.0 s 间隔） | 13 → 14 → 15 逐页前进 |
| 连续回翻（1.0 s 间隔） | 15 → 13 → 12 → 11 → 10 逐页后退 |
| 快节奏（0.6 s 间隔） | 4 连滑全部生效 |
| 10 连滑（0.35 s 间隔） | 到末页，触控正常 |
| 点按响应 | 点按可切出顶栏 |
| 崩溃观测 | logcat 无 reader 进程 `ANR` / `FATAL` |

本地回归：QA JVM 1672 项（含新增 5 项纸页安全契约），0 失败 / 2 既有跳过。

## 边界

物理手机未连接，用户原书未取得；模拟器为软件渲染，本文的耗时与流畅度不作为手机性能结论。46 与 47 均已停止交付。外部字体导入、菜单闪帧与翻页动画观感仍待手机复验。
