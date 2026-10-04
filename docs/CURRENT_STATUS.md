# 当前状态

当前功能基线 **1.0.0 / 32，Room v15**。EPUB R04 已合入 `reader` 的 `feature/epub-anchor`，正式包源码基线 `0fe7064a0c624779a28fd95e7097caa8d388dd67`。已完成自动化验证、正式签名打包和 API 35 模拟器验收；**真实手机验收待完成**。本轮未推送、未创建发布或 tag。详见 [R04 合入与验收](R04_INTEGRATION_20261005.md)。

## 当前实现

- 本地 Markdown、PDF、有限 EPUB 阅读；无 INTERNET 权限、账号、广告或云同步。
- EPUB 使用持久化文本锚点恢复位置，支持同章完整 quote、前 12 字匹配及章节 / 百分比兜底。非精确恢复保留原锚点，直到用户主动翻页或目录导航；章首 / 百分比兜底每次打开只提示一次。
- EPUB 章节分页、目录真实锚点、紧凑顶栏和排版重排能力保留。Room 14 → 15 的一次性迁移见 [文本锚点第一阶段](EPUB_ANCHOR_20261004.md)。
- Hub 底栏保留已确认的 iOS 通透度、按压放大和完整底栏边缘采样；本轮只修正过期测试断言，没有改变生产玻璃配方。
- 液态玻璃工作台仅在 Debug / QA 开启，Release 隐藏；Release 的既有材质对比入口仍在。

## 唯一交付产物

`.external_outbox/m1racle-reader-release-latest.apk`，**7,024,976 bytes**。

SHA-256：`4aae1477cee6902e13ed0a89ee45a11b2f9f542f73290f3ee0be8ac4d78097c9`。

快照：`m1racle-reader-release-v1.0.0-20261005-001455.apk`。正式签名和 16 KiB 对齐验证通过。构建输出、Outbox、模拟器已安装包、模拟器 Download 四处摘要一致；新包已覆盖安装到 `emulator-5554`，保留原数据。尚未交付真实手机。

## 验证基线

- QA JVM：完整运行加修复项定向复测，去重覆盖 **1696 项，1694 通过，2 项既有跳过，0 个未解决失败**。不是一次完整运行全绿；初次错误和复测证据均在合入报告中。
- QA 仪器测试源码编译通过；本轮没有运行 connected 仪器套件。
- Release Lint：**0 errors / 144 warnings / 4 hints**。
- 正式包模拟器 **13 项检查通过**：覆盖升级、既有 EPUB、大小字号重排及进程重启恢复、目录跳转后重启、Markdown / PDF 打开、Release 工作台隐藏、APK 一致性和无崩溃。
- 真实手机、长书性能、深色 / 旋转专项及降级提示设备观感仍未完成本轮验收。此前真机结果不能作为新包真机验收证据。

## 工作区与证据

主工作区 `reader`；`reader-r04` 的 R04 代码已快进合入，保留该工作区供追溯。当前报告：[R04_INTEGRATION_20261005.md](R04_INTEGRATION_20261005.md)；本机证据：工作区根目录 `output/r04-integration/` 和 `output/r04-*.log`。上一状态已归档至 [历史快照](history/CURRENT_STATUS_before_r04_integration_20261005.md)。本机环境入口见 [环境修复说明](../../环境修复说明-20261003.md)。
