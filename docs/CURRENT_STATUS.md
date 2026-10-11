# 当前状态

当前已安装正式包 **1.0.0 / 57，Room v17**（2026-10-11）。本轮按视频及Kyant LiquidBottomTabs修正底栏：透镜越过上下边缘，底栏轻微增长并随拖动偏移，径向高光、折射、速度形变与松手回弹。见[实现与验收](DOCK_VIDEO_REPLICA_20261011.md)。

## 当前产物

[正式签名APK](../.external_outbox/m1racle-reader-release-latest.apk)，**27,131,998 bytes**。
SHA256：`a57b1967df5fdfd4b699df8c6eb4f99fd5615cea91fd4251f3ccb2a0336a9f12`。
快照：`m1racle-reader-release-v1.0.0-20261011-144208.apk`。
生产签名连续、16KiB对齐通过，min31/target35/无INTERNET；构建/Outbox/手机Download摘要一致。

## 验证与边界

- automated_verified：最终源码底栏/材质/采样拓扑JVM53项通过；完整lintRelease通过，0 errors / 198 warnings / 4 hints。
- physical_device_verified：db04f865/API36原生11项通过，含72次逐帧往返拖动、透镜越界、底栏随动、折射及日夜像素。正式Release57保留数据覆盖安装，冷启动253ms，原有书库可见；日夜拖动与加号取消通过。系统日夜模式恢复auto，QA后台权限恢复ignore。
- 本轮底栏工作未改数据库与文档语义；包中包含工作区此前已存在的version57/Room17变更。未声称全量积压工作验收，未commit/push。

此前55版交付与接手审查状态保留在[历史快照](history/CURRENT_STATUS_before_dock_video_20261011.md)。
