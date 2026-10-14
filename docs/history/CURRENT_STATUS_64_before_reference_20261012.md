# 当前状态

当前候选包 **1.0.0 / 64，Room v17**（2026-10-11）。正式签名包已保留数据覆盖安装到物理手机db04f865/API36，日夜按住、往返拖动及导入取消通过，并交付手机Download。

根据用户指出的根因，删除虚构内部底栏及独立1.2倍图标。实际Dock材质、图标、标签、加号只绘制一份，透镜捕获这份真实画面；修复取消回程过早点亮原图标。重新核对RiseDiary源码，保留整栏小幅伸展及跟随，不恢复隐藏的替代底栏。见[64版报告](DOCK_REAL_CAPTURE_20261011.md)。此前62/63版未满足用户视觉要求，其局部测试通过不构成酷安效果验收。

## 当前产物

[正式签名APK](../.external_outbox/m1racle-reader-release-latest.apk)，**27,131,998 bytes**。
SHA256：`779b2aed068398090128b4cf9c3f7e0d4de69a40e69b048495578f7b0cc8a5a2`。
快照：`m1racle-reader-release-v1.0.0-20261011-231855.apk`。
生产证书连续、16KiB对齐、min31/target35/无INTERNET通过；构建、Outbox、手机Download摘要一致。

## 验证与边界

- automated_verified：定向JVM63项通过；本次Release lint 0 errors / 198 warnings / 4 hints。
- device_verified：真机原生14项通过（30.258s），涵盖远端不泛白、透光、底缘文字折回、单一真实图标预览、取消回程、命中几何和72帧往返拖动。正式64包日夜按住/拖动、末态首页selected及导入取消通过，按压截图已查看。
- 结构已改为真实Dock来源，与酷安完整视觉一致性仍待验收；不能以测试通过宣称完全复刻。未全量重验积压功能。
- 手机夜间恢复auto，QA后台权限恢复ignore；未清除用户数据、未commit/push。无数据库及阅读语义变更。
- 本轮未启动模拟器，历史QA模拟器启动ANR未宣称解决。

最终构建、签名、像素及正式包录屏证据在`../output/dock-real-capture-20261011/`；试错及旧实现失败记录在`../output/dock-review-20261011/`。62版历史见[快照](history/CURRENT_STATUS_62_before_real_capture_20261011.md)。
