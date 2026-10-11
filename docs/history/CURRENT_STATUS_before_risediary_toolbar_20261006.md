# 当前状态

当前交付 **1.0.0 / 42，Room v15**。EPUB 原右上角胶囊直接向左、向下展开，目录 / 设置始终只有一组图标且保持原坐标；切换只换内部内容，面板位置尺寸不变，已选图标可收回。文字不整体缩放，遮罩覆盖全窗口，内容避让安全区。正文固定设备圆角、全窗口填充、分页 / 锚点和图片保持。

本地 Markdown / PDF / 有限 EPUB，无 INTERNET，数据库不变。用户指出的白条属于文章图片。报告见 [本轮验收](EPUB_ORIGIN_MENU_20261005.md)。

## 当前产物

`.external_outbox/m1racle-reader-release-latest.apk`，7,074,128 bytes，SHA-256 `46e9d3bbb23ea52ab0dba3ff4f1863d56f598affb65b8a7cbc4ceae0194b607a`；快照 `m1racle-reader-release-v1.0.0-20261005-233233.apk`。已复制模拟器 Download 并覆盖安装。物理手机未连接，人工观感与触感 pending。

## 验证

- 完整 QA1722：1720 通过 / 2 既有跳过 / 0 失败或错误，221 suites。Release Lint0 errors / 150 warnings / 4 hints；最终构建8m45s / exit0。
- Native 主机回归逐帧检查展开与收回：单份图标、坐标固定、右上锚点不变、容器单调增长；切换不改面板位置尺寸、安全区 / 全屏遮罩、外侧关闭不误翻页及实际设置回调均通过。
- 实际正式 Release42 覆盖安装 emulator-5554，保留书库；浅深色目录 / 设置 / 关闭坐标一致，两图标各一份。字号 / 边距 / 行距滑杆 / 重置 / 返回关闭实际操作通过，恢复原书8/464、小字号 / 1.55 / 标准边距、深色且隐藏控件。
- 系统状态栏隐藏、原生导航条保留、DEFAULT、焦点 MainActivity。正式证书、16 KiB对齐、Manifest42 / 无 INTERNET、build / Outbox / installed / Download四份摘要相同。crash buffer未见reader进程崩溃。
- 原位操作录屏36个VFR解码帧，保存原始视频与采样；未覆盖动画所有中间帧，不能作为 FPS / 手机手感证明。逐帧坐标由原生主机动画时钟测试提供。设置重排时 UiAutomator null root 的原始记录保留。

## 工作区

`feature/epub-anchor`，HEAD a57c324 + 未提交修改。生产差异摘要 `331696a96670dacd5a53888fed71e1e6c326a3ec005a8335a7f2ba68a82ff4a1`。证据 `output/epub-origin-menu/`；上轮41见 [历史](history/CURRENT_STATUS_before_epub_origin_menu_20261005.md)。未提交 / 推送 / 公开发布 / tag。
