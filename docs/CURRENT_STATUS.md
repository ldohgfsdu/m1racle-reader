# 当前状态

当前已安装侧载正式包 **1.0.0 / 55，Room v15**。55版按酷安真机采样调整底栏尺寸、按压扩张与阅读玻璃控件，保留直接跟手和独立形变弹簧。EPUB继续保留51版恢复的43版阅读行为及字体功能。

## 当前产物

[正式签名 APK](../.external_outbox/m1racle-reader-release-latest.apk)，**27,183,175 bytes**。
SHA-256：`a53143d8cb9a9f18db84ef8880746b945b734feabf44f7d1ecdbc0a5f02752e6`。
快照：`m1racle-reader-release-v1.0.0-20261009-074330.apk`。
构建输出/Outbox/手机Download摘要一致；生产签名延续`2d9daba0…bf9d1`，16KiB对齐通过，包名/版本55/min31/target35/无INTERNET通过。Release minify包，已覆盖安装，未交付QA或Debug。

## 验证

- **implemented**：底栏基础56dp、透镜48→67dp、20dp侧边距、24dp图标；按压底栏宽高扩张5%，透镜每侧延伸3dp。日间高光和主题底缘渐淡，独立控件放大8.5%、胶囊7.5%，提亮与回弹；保留蓝色及稳定命中区域。
- **automated_verified**：55版最终源码定向QA JVM164项全部通过，无跳过。Release构建成功，完整Lint及lintVital通过，0 errors / 162 warnings / 4 hints。52版完整QA JVM1735项属于前版基线，未声称本轮完整重跑。
- **physical_device_verified**：最终源码真机db04f865/API36原生11项全部通过，含56dp尺寸、宽高5%扩张、固定命中区、逐帧跟手及形变/回弹、两种主题控件真实像素提亮/8.5%放大与回弹。正式Release55保留数据安装、冷启动197ms；日夜模式各10次拖动、4次上下滚动及加号取消通过，截图已检查。场景P95日间18ms/夜间16ms；阅读控件材料为真机隔离场景，未声称全部阅读页面人工遍历。

## 边界与工作区

2026-10-09设备恢复连接后已完成55版安装与验证。无数据库变更，未清除生产数据，现有书库可见。系统恢复自动日夜模式，QA后台启动权限恢复ignore。精确长期60/120Hz表现及人工Q弹手感仍需日常使用反馈，不能由录屏唯一反推酷安内部公式。其他既有未提交工作保留，未commit / push / 公开发布。

本轮见[复刻实现报告](COOLAPK_GLASS_REPLICA_20261008.md)，参考见[酷安采样](COOLAPK_DOCK_CAPTURE_20261008.md)。此前已装机状态见[54版历史快照](history/CURRENT_STATUS_before_coolapk_replica_20261009.md)，EPUB边界仍见[EPUB报告](EPUB_RESTORE43_FONT_20261007.md)。
