# 当前状态

当前构建候选包 **1.0.0 / 58，Room v17**（2026-10-11），已在API35模拟器覆盖安装。物理手机本轮离线，最后已验证安装的是57版；58版真机复验pending。

本轮增强底栏背景糊化并采用上游中性明暗配方，保持蓝色强调色和现有按压尺寸；补偿小数像素位置，消除模拟器逐帧拖动的取整误差。见[材质与精度校准](DOCK_FROST_20261011.md)，此前运动实现见[57版报告](DOCK_VIDEO_REPLICA_20261011.md)。

## 当前产物

[正式签名APK](../.external_outbox/m1racle-reader-release-latest.apk)，**27,131,998 bytes**。
SHA256：`4d4fcbe2ca4d444e738fa2bf3ff26ce17f7acc4b63fd8d5a143016408ee38b9b`。
快照：`m1racle-reader-release-v1.0.0-20261011-204526.apk`。
生产签名连续、16KiB对齐通过，min31/target35/无INTERNET；构建/Outbox/模拟器Download摘要一致。

## 验证与边界

- automated_verified：最终源码定向JVM54项全部通过；完整lintRelease通过，0 errors / 198 warnings / 4 hints。
- emulator_verified：API35原生11项全部通过，含72帧往返拖动、按压越界、底栏随动、回弹、日夜清晰图标和透光；未扩大1px容差。正式Release58覆盖安装，原有书库及继续阅读可见，日夜拖动往返与导入选择器取消通过。系统日夜恢复原先yes。
- physical_device_pending：手机ADB离线，未安装/复制到物理手机Download，不声称58版真机手感或视觉完全匹配。色散未单独调整。
- QA完整应用启动在构建期间出现一次启动ANR，后续启动虽返回成功，UI树仍未读到；组件原生测试11项通过不等同于QA完整应用启动通过。正式Release已完成可见界面与交互冒烟，未做性能结论。
- 本轮无数据库、文档语义或阅读逻辑变更，未全量重验积压清单；未commit/push。

此前57版真机验收保留在[历史快照](history/CURRENT_STATUS_before_dock_frost_20261011.md)。
