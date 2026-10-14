# 当前状态

当前候选包 **1.0.0 / 66，Room v17**（2026-10-12）。正式签名包已生成并保留数据从65版覆盖安装到API35模拟器；日夜首页按住、往返拖动及导入取消通过。物理手机本轮未连接，66版真机安装和手机Download交付待连接。

修正用户红圈指出的镜片内部轮廓缺失：镜片56→78dp、横向1.6倍，10dp/4dp折射使实际底栏上下边缘及弯曲端部显露在镜片内部。真实栏与唯一前景仍只捕获一次；紧凑分段控件配方保持。保留蓝色、导航、导入、60dp栏布局及手势。见[66版报告](DOCK_INNER_CONTOUR_20261012.md)。

## 当前产物

[正式签名APK](../.external_outbox/m1racle-reader-release-latest.apk)，**27,131,998 bytes**。
SHA256：`9b35681b814e8d1d7afcc29853f2d6556fc66e8377295a6274e90370bc78c7e8`。
快照：`m1racle-reader-release-v1.0.0-20261012-140608.apk`。
生产证书连续、16KiB对齐、min31/target35/无INTERNET通过；构建、Outbox、模拟器已安装APK与Download摘要一致，物理手机交付pending。

## 验证与边界

- automated_verified：底栏及材质JVM117项全部通过；Release lint 0 errors / 198 warnings / 4 hints。
- device_verified（模拟器）：原生18项通过（79.794s），新增日夜镜片内部上下边缘和左侧弯曲端部像素检查，保留文字、颜色、命中几何、取消回程与72帧拖动检查。相同新增检查在旧65版日夜均失败。
- device_verified（正式包模拟器）：66版日夜首页按住/往返拖动、末态首页selected及导入取消通过；正式包日夜首页按住截图已查看，录屏已保存，无主包崩溃记录。灰底前后结构对比和彩色背景已核对。
- pending：66版物理手机复验及完整参考外观的用户验收，未全量重验积压功能。历史真机结果不能继承为66版真机通过；65版未满足用户标注的内部轮廓要求。
- 模拟器夜间恢复原值no，未清除主包数据，首次安装时间保持2026-10-03。启动阶段SystemUI无响应和存储尚未就绪导致的首次推送失败均已恢复；不算Reader通过或Reader故障。无数据库、阅读语义或依赖变更，未commit/push。

本轮证据在`../output/dock-inner-contour-20261012/`。65版历史见[快照](history/CURRENT_STATUS_65_before_inner_contour_20261012.md)与[报告](DOCK_REFERENCE_20261012.md)。
