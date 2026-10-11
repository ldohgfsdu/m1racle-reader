# 当前状态

当前交付 **1.0.0 / 41，Room v15**。EPUB 静止页不再保留整圈灰色描边或共同视口额外圆角遮罩；纸面背景填满窗口，移动纸页的固定设备圆角保留。目录 / 设置使用同窗口右上角固定尺寸面板，展开不整体缩放文字，切换不改变位置和尺寸，目录打开定位当前条目。遮罩覆盖整屏，面板内容单独避让安全区。

用户澄清视频中的白条属于正文图片，图片保持原样，不列为渲染问题。报告见 [本轮验收](EPUB_FILL_MENU_20261005.md)。本地 Markdown / PDF / 有限 EPUB，无 INTERNET，数据库不变。

## 当前产物

`.external_outbox/m1racle-reader-release-latest.apk`，**7,057,744 bytes**，SHA-256 `07c39d4157130a45753854f5f2d3b9eed6712f91057680c8a7179fe6564c8666`；快照 `m1racle-reader-release-v1.0.0-20261005-225736.apk`。正式签名证书、16 KiB 对齐、Manifest41 / 无 INTERNET 均通过。build / Outbox / installed / Download 四份摘要相同。已在线覆盖安装模拟器 `emulator-5554`，保留书库与阅读位置。手机没有连接，厂商屏幕与触感仍待用户实机验收。

## 验证

- 完整 QA **1721：1719 通过 / 2 既有跳过 / 0 失败或错误，221 suites**。Release Lint **0 errors / 150 warnings / 4 hints**，最终构建 **8m5s / exit0**。
- Native 自动回归覆盖静止四边 / 窗口角填充、取消精确恢复、固定移动圆角、无独立 PopupLayout、切换面板边界相同、外侧关闭不误翻页、长目录自动显示当前条目、实际设置回调，以及整屏遮罩与面板安全区。
- 最终 Release41 实际浅 / 深色菜单操作、目录 / 设置切换、字号 / 边距 / 行距调值 / 重置 / 返回关闭通过；状态栏隐藏、导航条可见且 DEFAULT，焦点保持 MainActivity。最终恢复原书 **8 / 464 页、小字号 / 1.55 / 标准边距**。
- 浅色静止左右 2 像素带 RGB255；深色 RGB15。浅色菜单顶部、侧边和底部遮罩采样均 RGB224，额外颜色断层消失。实际菜单标签与关闭按钮的浅色切换坐标完全一致。
- 最终包深色前翻、回翻、近归位与取消记录；开始 / 结束整张截图 **0 不同像素**。录屏解码 **199 帧**未出现整页空白；有限 VFR 记录不作为 FPS、每一种闪烁或所有手机的证明。冷开 / 重排加载与 UiAutomator null root 原始记录保留，不宣称已修复。

## 工作区

`feature/epub-anchor`，HEAD a57c324 加未提交修改，生产差异摘要 `b664a19034b219f87eda4137805d6a9cce379cca3576e4885a5da016097d7dec`。证据位于 `output/epub-fill-menu/`。上轮39状态见 [历史](history/CURRENT_STATUS_before_epub_fill_menu_20261005.md)。Release40 是中间验证产物；最终以41为准。未提交 / 推送 / 公开发布 / tag。
