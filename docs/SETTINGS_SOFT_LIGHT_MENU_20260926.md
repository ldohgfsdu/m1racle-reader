# 设置页柔光菜单 · 2026-09-26

用户看过 Download/1.txt 和 CoolMarket 最新截图后，要求先在设置页复现右下角那种柔光菜单。阅读器、主页、底栏和已交付的液态玻璃生产配方不动。

## 看到的参照

截图里的「堆叠 / 并排」菜单：背后文字变成柔和色块，仍能透出明暗；表面乳白；边缘是细白高光；阴影很轻；前景文字清晰；没有放大镜式折射。大块设置内容保持实色。

## 实现

只加在设置页玻璃对照舞台，打开设置时默认就是这一层。菜单和返回圆按钮都是舞台背景的兄弟节点，采样 `rememberLayerBackdrop()`，不进入 `GlassBackdropSource`。模糊半径固定，不跟选中态、按压、滚动或播放进度变化。菜单点选只改前景颜色。

返回交互按截图：左上是 44dp 圆按钮，箭头锁在圆内。按住时整颗圆缩到 0.94，箭头跟着圆走，正中的「标题」不参与这次缩放，也没有单独的水波纹。PDF 阅读页原有的圆形返回同样改为整颗按钮按压，光学配方仍是原来的液态玻璃。Markdown 和 EPUB 顶栏仍是普通图标按钮。

冻结在 `SoftLightMenu`：

| | 浅色 | 暗色 |
|---|---|---|
| logical blur | 32dp | 32dp |
| tint | 白 0.72 | `#1A1C1E` 0.72 |
| saturation | 1.08 | 1.08 |
| brightness | +0.025 | +0.025 |
| border | 白 0.42 → 0.11 / 1dp | 同左 |
| shadow | 0.10 / 18dp | 同左 |
| refraction | 0 | 0 |
| foreground | 1 | 1 |

32dp 走既有面板分级模糊，避免单次大半径。没有 lens、vibrancy，也没有 `graphicsLayer`。H0/H1/H2 和 `LiquidGlassStyle` 的数字没有改。液态玻璃、柔光家族和已淘汰对照仍留在同一页。

## 验证

- implemented：完成。
- automated_verified：`com.m1racle.reader.ui.material.*` 78 项，0 失败。随后把说明里的亮度从四舍五入的 `+0.03` 改成 `+0.025`，`SoftLightMenuTest` 再次通过。回归覆盖配方区间、分级模糊先于染色、零折射、默认层，以及菜单不在 `GlassBackdropSource` 内。`GlassMaterial`、`LiquidGlassStyle`、PDF、Dock、Home 源码不引用 `SoftLightMenu`。
- `:app:assembleRelease` 成功，其中 `lintVitalRelease` 通过。未另跑完整 `lintRelease`。
- device_verified：NOT_RUN。本轮 `adb devices -l` 为空。
- pending：安装后打开设置，滑到「玻璃配方对照」。默认是柔光菜单，舞台右下角是「堆叠 / 并排」。看浅色正文、暗色、图案和播放：底层文字是否进入菜单并被模糊、表面是否发白雾、前景是否清楚、播放时背景是否闪。未经过同机对照，不宣称视觉通过。

## 交付

正式签名 assembleRelease 通过。1.0.0 / 23。6,967,061 bytes / 6.64 MiB。
SHA-256 `87ec19b6fc22c5c279fe83c36e2f7128c5672d56a755924bfdabb7ac1a7b6f8a`。
构建输出、Outbox、`/storage/emulated/0/Download/m1racle-reader-release-latest.apk` 摘要一致。`apksigner verify` 通过，权限列表里没有 INTERNET。

这个安装包来自当前整个工作区，里面还带着本次之前就有的未提交改动。


## 2026-09-27 长按拖动补充

参考用户提供的 7.1 秒录屏 `VID_20260927_084006.mp4`，在设置页右下角「堆叠 / 并排」菜单加入长按拾起、阻尼跟手、方向拉伸及松手弹性归位。录屏无触点轨迹，40dp 位移上限、2.5% 拾起缩放和弹簧参数属于当前近似，待同机对照手感，不宣称精确复刻。

手势使用 Compose `detectDragGesturesAfterLongPress`。检测容器固定，内部浮层做位移；长按前滑动仍让出手势，拖动及取消不选中菜单项。取消和松手都回到原位。现有 Backdrop `layerBlock` 同时缩放表面和文字，模糊、染色、零折射配方保持固定。无新依赖。

中风险交互变更。11 项定向测试通过：`SoftLightMenuDragTest` 5 项、`SoftLightMenuTest` 4 项、`SoftLightBackButtonTest` 2 项。包含实际 Compose 指针注入及位移/归位断言。ADB 为空，device_verified=NOT_RUN。最终构建与交付状态见 `CURRENT_STATUS.md` 的 2026-09-27 条目。

手验：设置 → 玻璃配方对照 → 柔光菜单；长按「堆叠 / 并排」任一行后向四个方向拖动，检查阻力、形变、文字跟随和松手回弹。短按仍切换选中项，普通滑动仍可滚动设置页。分别检查浅色/暗色和背景播放时采样是否稳定。
