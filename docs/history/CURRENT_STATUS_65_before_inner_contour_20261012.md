# 当前状态

当前候选包 **1.0.0 / 65，Room v17**（2026-10-12）。正式签名包已生成并保留数据覆盖安装到API35模拟器，浅深色按住、往返拖动及导入取消通过。物理手机本轮未连接，65版真机覆盖安装和手机Download交付待连接。

按本轮酷安视频和截图，移除胶囊外的底部渐变遮罩；整栏静止无折射，按住后增强折射与边缘高光。沿用真实Dock单份捕获、外扩镜片、拖动形变和回弹，保留四入口及蓝色，调整选中标签与横向渐变加号。见[65版报告](DOCK_REFERENCE_20261012.md)。

## 当前产物

[正式签名APK](../.external_outbox/m1racle-reader-release-latest.apk)，**27,131,998 bytes**。
SHA256：`7518cd95e74a0b0b7dbd492d6d3805ba01f0a9472466999163c4faaefb61984a`。
快照：`m1racle-reader-release-v1.0.0-20261012-130333.apk`。
生产证书连续、16KiB对齐、min31/target35/无INTERNET通过；构建、Outbox、模拟器Download摘要一致，物理手机交付pending。

## 验证与边界

- automated_verified：底栏及材质JVM116项全部通过；Release lint 0 errors / 198 warnings / 4 hints。
- device_verified（模拟器）：原生16项通过（77.715s），覆盖日夜栏外颜色、静止/按压折射、图标、真实来源、底缘文字、远端颜色、命中几何、取消回程和72帧往返拖动。相同新增像素检查在64版日夜均失败，65版通过。
- device_verified（正式包模拟器）：65版日夜按住/往返拖动、末态首页selected及导入取消通过。实际页面与彩色背景的日夜静止/按压截图已查看，录屏已保存，未见主包崩溃。
- pending：65版物理手机复验及完整参考外观的用户验收，未全量重验积压功能。上轮64版真机结论不能继承为65版真机通过。
- 模拟器夜间恢复原值no，未清除主包数据；QA包曾因旧签名不匹配重建隔离沙箱。无数据库、阅读语义或依赖变更，未commit/push。

本轮证据在`../output/dock-reference-20261012/`。64版历史见[快照](history/CURRENT_STATUS_64_before_reference_20261012.md)与[报告](DOCK_REAL_CAPTURE_20261011.md)。
