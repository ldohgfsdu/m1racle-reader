# 当前状态

当前已安装侧载包 **1.0.0 / 54，Room v15**。Dock拖动位置直接跟随输入，形变独立弹簧负责横向拉伸、纵向压缩与松手回弹；保留一体蓝色胶囊、中央导入与玻璃折射。EPUB继续保留51版恢复的43版阅读行为及字体功能。

## 当前产物

[正式签名 APK](../.external_outbox/m1racle-reader-release-latest.apk)，**27,183,175 bytes**。
SHA-256：`451cbc060b586ca005cf175738231f61099a65a18064a626054033128760e818`。
快照：`m1racle-reader-release-v1.0.0-20261008-232636.apk`。
构建输出与Outbox摘要一致；生产签名延续 `2d9daba0…bf9d1`，16KiB对齐通过。Release minify包，未交付QA或Debug。

## 验证

- **implemented**：拖动位置与底栏位移同步更新；单槽速度驱动有正负位移的形变弹簧，松手缩放与形变回弹。
- **automated_verified**：54版39项定向QA JVM全部通过，含拉伸、压缩、松手越过静止形状及下一帧跟手断言。54版Release构建成功，Lint 0 errors / 162 warnings / 4 hints。52版完整QA JVM1735项属于前版基线，未声称本轮完整重跑。
- **physical_device_verified**：54版真机db04f865、API36最终8项光学与手势仪器测试全部通过，含72次逐帧位置及形变/回弹断言。Release保留数据覆盖安装，冷启动295ms、20次左右拖动完成，运动中截图已检查；本轮单场景P95帧耗时19ms。

## 边界与工作区

真机已连接，54版已验证；精确60/120Hz长期表现与人工手感仍需实际使用反馈。无数据库变更，无清除生产数据。QA后台启动测试权限已恢复原值。其他既有未提交工作保留，未commit / push / 公开发布。

本轮跟手证据见 [53版拖动修复报告](DOCK_DIRECT_DRAG_20261008.md)，形变补充见 [54版弹性报告](DOCK_ELASTIC_20261008.md)。此前状态见 [52版历史快照](history/CURRENT_STATUS_before_direct_drag_20261008.md)，Dock外观来源见 [52版报告](OPTICAL_DOCK_20261008.md)，EPUB行为边界仍见 [EPUB报告](EPUB_RESTORE43_FONT_20261007.md)。
