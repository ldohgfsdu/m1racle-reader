# Home liquid controls + delete reflow

## 1) 首页控件分层

| 层 | 元素 | 材质 |
|----|------|------|
| 内容 | 最近文件卡 | **实体/tonal** `surfaceContainerLow`，无 liquid |
| 内容 | 轻品牌头 | solid `background`，非 liquid |
| 浮动 | FAB（主操作） | **Liquid** Control（Reader 同 API） |
| 浮动 | 三点 icon button | **Liquid** 圆形 Control |
| 浮动 | 溢出菜单 | **Liquid** popover（非白方卡） |

原则：内容卡安静；按钮/菜单有分层；不四层同权 liquid。

## 2) 最终参数（`HomeLiquidControlTokens`）

| 控件 | 尺寸 | 圆角 | 材质 |
|------|------|------|------|
| FAB | **62dp** | **22dp** squircle | `markLeafLiquidSurface` Control：半透明 + vibrancy/blur/lens + soft shadow |
| 三点 | **38dp** hit | **Circle** | 同 Control 级 liquid；图标 20dp |
| Popover | min 152 / max 200 宽 | **22dp** | liquid Control；项高 48dp；仅「删除记录」 |
| 列表底 | nav + **96dp** | — | 保证末卡不被 FAB 挡 |

文件卡仍：圆角 24dp、左右 16、间距 12、内边距 16；标题 ≤2 行 20sp。

## 3) 删除空白残留 — 根因与修复

**根因：** `deleteById` 走 Room IO 异步，UI 仍展示旧 Flow 项直到 emit；列表顶部会短暂保留「已删卡」或回流时出现空档感。无 animateItem，但异步延迟即表现为残留。

**修复：**
1. `HomeListUpdate.visibleDocuments(source, pendingDeleteIds)` 纯函数过滤  
2. `HomeViewModel` 乐观：`pendingDeleteIds += id` 后立即从 UI 列表去掉  
3. Room 删除成功且 observe 确认无该 id 后清理 pending  
4. LazyColumn `key = { doc.id }`，无 exit placeholder 动画  

## 4) 截图

`adb devices` 空 → **device_verified PENDING**  
APK 已交付 Download；请拍：静态首页 / 三点菜单 / 删除回流 / FAB 悬浮。

## 验证
- unit `*Home*` PASS → `{SCRATCH}/unit-home-controls.log`  
- assembleRelease + outbox delivery
