# R7 H1.2 横向分页交互策略与自动验收报告

日期：2026-07-13  
状态：`automated_device_accepted`；`human_feel_pending`  
设备：`db04f865` / `25098PN5AC`

## 1. H1.1B 产品策略丢失根因

H1.1B 正确抽取了 viewport-pixel 纯逻辑 ZoomPan core，但横向适配层错误地把连续模式的
`AxisOverscrollPolicy.Disabled` 和“放大时 Pager 完全关闭”当成跨模式产品规则。结果是共享
数学没有问题，模式策略边界却被抹平：四边 residual 被硬截断、放大后没有 Pager handoff，
并继续保留横向逐页 transform。页面改成顶部对齐后，未被 PDF 覆盖的 viewport 又暴露了继承
的灰色容器背景。

本轮没有回退到两套物理实现；修复方式是保留共享内核，把 capability、Pager ownership、
页面提交与布局全部放回横向 policy adapter。

## 2. 共享 core 与横向 policy adapter 的最终边界

共享 `ZoomPanPhysics` 继续负责：Pointer Epoch/pointer pair、pointer-set rebase、焦点保持缩放、
legal pan/bounds/residual、rubber 映射、fling decay、纯 spring integration、transform continuity
和 viewport-pixel 坐标契约。core 不依赖 Compose、Pager、协程、导航、渲染或持久化。

横向适配层负责：启用哪些轴的 visual overscroll、X/Y 上限、水平边缘 Pager handoff、
PAGE_COMMIT/CANCEL、翻页后的 transform reset、1x Pager ownership、页面排版和底部控件 overlay。
连续模式继续使用 `ContinuousOverscrollPolicy = Disabled`，没有调用横向 spring；其手感、zoom
session、P0C1.4 renderer、tile geometry/cache 与持久化均未改变。

## 3. 四边回弹参数与设备数据

- scale 范围仍为 1x..4x；`scale > 1.01` 时页面内部优先拥有手势。
- visual overscroll 上限：X 24dp、Y 20dp，和 scale 无关。
- spring：stiffness 260、damping ratio 0.82、最大 frame 32ms、积分步长 8ms、停止距离
  0.35px、停止速度 5px/s。
- legal offset 与 raw/visual overscroll 分离；只有越界 residual 进入 rubber，spring 只清除
  visual/raw overscroll。新 pointer down 取消当前 spring，不把 visual 值折入 legal offset。
- 真机 2.5x 数据：legal drag gain X/Y = 1.0/1.0；最大 visual overscroll X/Y =
  22.74938/20.0dp，均未超过阈值；记录到的最后一次 settle 为 16ms；回零后 page change = 0。

## 4. Pager handoff 状态机

状态为：

`INNER_PAN -> EDGE_RESISTANCE -> PAGER_HANDOFF_ARMED -> PAGER_HANDOFF_ACTIVE -> PAGE_COMMIT | CANCEL`

进入条件同时要求：legal X 位于真实边界（epsilon 0.75px）、继续向外拖、
`abs(deltaX) > abs(deltaY) * 1.4`、不是 rebase frame、不是 PINCHING、相邻页存在，并达到
42dp 累计外拖或 1250dp/s 水平速度。阈值越过帧仍由 inner page 消费，下一合格帧才交给
Pager，避免同一 delta 重复消费。同一 gesture generation 只允许一次 handoff；
`PagerSnapDistance.atMost(1)` 保证一次最多一页。

handoff 使用 `NestedScrollDispatcher.dispatchPostScroll` 把 active 后的正常 UserInput 交给 Pager，
结束时只调用零速 `dispatchPostFling`。没有 `dispatchRawDelta`，也没有人工注入 residual velocity。
真机明确 swipe 的累计 handoff 为 44.582634dp，page change count = 1。

## 5. PAGE_COMMIT 与 CANCEL transform 规则

成功 PAGE_COMMIT 时，outgoing 和 target 都原子重置为 `scale=1f`、legal offset zero、visual/raw
overscroll zero。横向 `horizontalTransforms` per-page map 已删除；返回旧页仍是 1x，但正常
`pageIndex` 更新保留。真机 outgoing scale 从 2.5375118 清为 1.0，target 为 1.0；返回原页后
page change count = 2、return-page scale = 1.0。

如果 handoff 取消，当前页的 scale 和 legal offset 保留，只让 visual overscroll spring 回零，
不触发 zoom reset。

## 6. 底部灰块根因与修复

顶部对齐后，PDF fitted rect 以下的空间没有被页面绘制，外层 viewport/Pager/page container 又
没有统一声明 PDF 白色背景，因此显示了阅读器容器的灰色底。现在三层都明确使用白色背景；
页面仍为 `TopCenter`、8dp top gap、保持真实宽高比。页码和左右按钮位于全尺寸 viewport 内的
bottom overlay，不再通过固定内容高度压缩 PDF viewport。

真机测量：viewport `1220 x 2656px`，页面 `1168 x 1615.6943px`，top gap 8dp，页面下方采样
ARGB 为 `FFFFFFFF`，分类 `PAGE_WHITE`；页面高宽比 1.3833，未拉伸。

## 7. QA-only 真实多指 MotionEvent 注入

`H1QaMotionEventTrace` 使用 `UiAutomation.injectInputEvent(event, true)`。每条 trace 固定
pointerId、DOWN/POINTER_DOWN/MOVE/POINTER_UP/UP 序列、viewport 归一化坐标、16ms frame、
eventTime、持续帧数及线性/加速速度曲线。已实现并覆盖：`PINCH_1_TO_2_5`、
`PINCH_MOVING_CENTROID`、`POINTER_2_TO_1_TO_2`、`ZOOMED_EDGE_DRAG_LEFT/RIGHT`、
`VERTICAL_EDGE_BOUNCE`、`ZOOMED_PAGE_HANDOFF`、`CANCELLED_EDGE_HANDOFF`。没有用
`adb shell input swipe` 伪造双指。

QA harness 直接承载生产 `PdfHorizontalPagedArea` 与 `ContinuousDocumentViewport`，只通过可选
observer 读取指标，生产逻辑不读取 probe。两个 Activity 只存在于 QA source set；debug APK
manifest 不包含它们。运行目标严格为 `com.markleaf.app.qa` / `com.markleaf.app.qa.test`。

## 8. 七个自动场景结果

| 场景 | 结果 | 核心证据 |
|---|---:|---|
| H1_FOUR_EDGE_OVERSCROLL | PASS | X/Y 四边有限回弹；gain 1.0/1.0；22.74938/20dp；回零；0 翻页 |
| H1_ZOOMED_HORIZONTAL_HANDOFF | PASS | 轻拉不翻页；44.582634dp 后只提交 1 页；outgoing/target 均 1x |
| H1_RETURN_PAGE_RESET | PASS | N→N+1→N 共 2 次提交；返回页 scale 1.0、offset zero |
| H1_VERTICAL_NO_NAVIGATION | PASS | Y overscroll 20dp；回零；page change 0；最终 `INNER_PAN` |
| H1_POINTER_2_1_2 | PASS | 同时执行 moving-centroid pinch；pointer jump 0dp；scale 4.0；0 翻页 |
| H1_BOTTOM_BACKGROUND | PASS | top gap 8dp；unused region `FFFFFFFF/PAGE_WHITE`；viewport 未压缩 |
| P0C1_4_REGRESSION | PASS | pointerDown request 1556、swap 783、release 前 coverage 1.0；transform 不变 |

所有失败分类均未触发：`EDGE_BOUNCE_MISSING`、`EDGE_BLANK_TOO_LARGE`、
`ZOOMED_PAGE_HANDOFF_MISSING/EARLY`、`MULTIPLE_PAGE_COMMIT`、`PAGE_ZOOM_NOT_RESET`、
`VERTICAL_DRAG_TRIGGERED_PAGE_CHANGE`、`POINTER_TRANSITION_JUMP`、
`BOTTOM_GRAY_BLOCK_PRESENT`、`TILE_RENDER_DURING_GESTURE_REGRESSION`。

## 9. 设备耗时

最终整套同进程执行：7/7 PASS，instrumentation 33.268s。最终逐场证据复跑：

- H1_BOTTOM_BACKGROUND：1.051s
- H1_ZOOMED_HORIZONTAL_HANDOFF：5.393s
- H1_FOUR_EDGE_OVERSCROLL：10.413s
- H1_POINTER_2_1_2：3.586s（包含 moving-centroid pinch）
- H1_RETURN_PAGE_RESET：5.653s
- H1_VERTICAL_NO_NAVIGATION：6.653s
- P0C1_4_REGRESSION：2.735s

## 10. 构建与静态门禁

- `:app:testDebugUnitTest`：569/569 PASS，0 failed/error/skipped。
- `:app:lintDebug`：PASS。
- `:app:assembleDebug`：PASS。
- `:app:assembleQa`：PASS。
- `:app:compileQaAndroidTestKotlin`：PASS。
- `:app:assembleQaAndroidTest`：PASS。
- `git diff --check`：PASS（仅已有 CRLF 提示，无 whitespace error）。
- 源码审计：无 `dispatchRawDelta`，无 `horizontalTransforms`；debug manifest 无 QA harness。

全量组合门禁耗时 75.4s；最终 QA androidTest 重编译成功。没有执行 git add/commit/push。

## 11. APK

- 路径：`dist/markleaf-debug-r7-h1-2-horizontal-policy.apk`
- 大小：74,843,170 bytes
- SHA-256：`AC56536F3B991B3C825BC5C690220B4824628461210C3B4727A890DB8EBCE8E8`

## 12. 真人主观验收状态

本轮最高状态严格保持为 `automated_device_accepted` / `human_feel_pending`，不标记
`device_accepted`。仍需用户真人确认：四边回弹自然、合法区跟手、放大后边缘切页可用且不易
误翻、回旧页为 1x、底部灰块消失、2→1→2 无跳动、手势中渲染观感正常。

自动化仅安装/运行并最终清理 QA 包；没有读取、备份、恢复、安装覆盖或修改
`com.markleaf.app` 主包数据。
