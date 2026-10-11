# 当前状态

当前侧载验收包 **1.0.0 / 52，Room v15**。Dock 按用户视频与截图改为一体胶囊：首页、文库、中央导入、书签、设置；沿用应用蓝色。中央按钮不属于导航项。按压透镜、真实页面折射、对称速度形变及取消归位已实现。EPUB继续保留51版恢复的43版阅读行为及字体功能。

## 当前产物

[正式签名 APK](../.external_outbox/m1racle-reader-release-latest.apk)，**27,183,175 bytes**。
SHA-256：`14e92f32ad819d327c63fcea70ac297d71551e6a76185d0666853a2f7dbc3fa4`。
快照：`m1racle-reader-release-v1.0.0-20261008-174729.apk`。
构建输出与Outbox摘要一致；生产签名延续 `2d9daba0…bf9d1`，16KiB对齐通过。Release minify包，未交付QA或Debug。

## 验证

- **implemented**：一体蓝色Dock、中央按钮、导航手势与透镜动画。
- **automated_verified**：最终完整QA JVM1735项，1733通过、2既有跳过、0失败 / 错误。Release构建成功，Lint 0 errors / 162 warnings / 4 hints。
- **emulator_device_verified**：最终7项GPU像素测试全部通过。实际主页夹具浅深色各30次全轨道拖动完成；截图检查通过。正式签名Release覆盖安装、冷启动、四入口切换、中央导入打开系统选择器及返回通过。Download与本地APK摘要一致。早期模拟器无响应与重跑说明见报告，物理手机性能仍待验收。

## 边界与工作区

物理手机未连接，真实手感与60/120Hz性能待人工验收。无数据库变更，无清除生产数据。其他既有未提交工作保留，未commit / push / 公开发布。

详细证据见 [本轮Dock报告](OPTICAL_DOCK_20261008.md)。此前状态见 [51版历史快照](history/CURRENT_STATUS_before_optical_dock_20261008.md)，EPUB行为边界仍见 [EPUB报告](EPUB_RESTORE43_FONT_20261007.md)。
