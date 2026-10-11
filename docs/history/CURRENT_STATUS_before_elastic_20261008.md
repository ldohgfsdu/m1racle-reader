# 当前状态

当前已安装侧载包 **1.0.0 / 53，Room v15**。Dock拖动位置直接跟随输入，松手与取消弹簧归位；保留一体蓝色胶囊、中央导入与玻璃折射。用户补充要求更明显的形变与Q弹，54版验证与构建进行中。EPUB继续保留51版恢复的43版阅读行为及字体功能。

## 当前产物

[正式签名 APK](../.external_outbox/m1racle-reader-release-latest.apk)，**27,183,175 bytes**。
SHA-256：`4c4496f99a4752f0cff2768cca9f984b2e892d066ed7b76401da0d9cf27fe7c7`。
快照：`m1racle-reader-release-v1.0.0-20261008-230940.apk`。
构建输出与Outbox摘要一致；生产签名延续 `2d9daba0…bf9d1`，16KiB对齐通过。Release minify包，未交付QA或Debug。

## 验证

- **implemented**：拖动位置与底栏位移同步更新，弹簧负责归位；54版增加单槽速度归一化与有正负位移的形变弹簧。
- **automated_verified**：53版37项定向QA JVM通过；54版39项定向QA JVM全部通过，含拉伸、压缩、松手越过静止形状及下一帧跟手断言。53版Release构建成功，Lint 0 errors / 162 warnings / 4 hints。52版完整QA JVM1735项属于前版基线，未声称本轮完整重跑。
- **physical_device_verified**：53版真机db04f865、API36最终8项光学与手势仪器测试全部通过，含72次逐帧位置断言。Release保留数据覆盖安装，冷启动、20次左右拖动完成。54版真机测试待本轮构建完成。

## 边界与工作区

真机已连接，53版已验证。无数据库变更，无清除生产数据。QA后台启动测试权限已恢复原值。其他既有未提交工作保留，未commit / push / 公开发布。

本轮跟手证据见 [53版拖动修复报告](DOCK_DIRECT_DRAG_20261008.md)，形变补充见 [54版弹性报告](DOCK_ELASTIC_20261008.md)。此前状态见 [52版历史快照](history/CURRENT_STATUS_before_direct_drag_20261008.md)，Dock外观来源见 [52版报告](OPTICAL_DOCK_20261008.md)，EPUB行为边界仍见 [EPUB报告](EPUB_RESTORE43_FONT_20261007.md)。
