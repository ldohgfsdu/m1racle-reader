# 当前状态

当前候选 **1.0.0 / 71，Room v17**（2026-10-13）。正式签名Release已保留数据覆盖70→71，写入API35模拟器Download，构建/Outbox/已安装/Download四份摘要一致。物理手机未连接，手机安装与本轮观感待验。

按用户新提供的五组酷安/Reader截图，透镜色散系数从库固定1收为.08，折射位移14→5dp，取消宽彩带。色散沿用实际折射，没有速度开关或计时变化。透镜内真实面板等比缩小，连续投影锚点保持两端可见；投影高度50dp，外透镜按住67→68dp、上下折射带10→12dp。PDF面板材质、局部前景变色/1.2倍放大与弹性输入保持。见[71版报告](DOCK_SUBTLE_DISPERSION_20261013.md)。

## 当前产物

[签名APK](../.external_outbox/m1racle-reader-release-latest.apk)，**27,131,998 bytes**。
SHA256：`e67a0a9701b0879c627698b1d07c47fd313b4d9ad9bb8f94983be17607e71cde`。
快照：`m1racle-reader-release-v1.0.0-20261013-002125.apk`。
正式生产证书连续、16KiB对齐、min31/target35、无INTERNET。只交付Release。

## 验证与边界

- implemented：上述修正；本次按用户明确反馈调整透镜几何，覆盖上一轮只保留69版透镜不动的局部约束。没有自研shader或新增依赖。
- automated_verified：定向JVM118项全过，无失败/错误/跳过；Release lint 0 errors / 198 warnings / 4 hints，签名构建完整成功。首次lint分析器崩溃日志保留，未禁用检测。
- device_verified（API35隔离QA）：原生27/29通过，2项未通过：lightPickupShowsCapturedRailOutlineInsideLens、lightLeftCapHasClearSpaceInsideHeldLens。同一最终测试APK在70版拒绝宽彩带（3775/7188个采样边缘像素），本轮色散上限、真实分色下限、松手静止无彩带、按住静止1600ms不发生颜色动画、原端帽留白/.08对比、2%蓝字折影与输入检查执行结果见报告。
- device_verified（正式71包/API35）：日夜五位置按住、半覆盖图标、跨栏/快速往返、停住/松手、首页末态与导入取消通过。正式参考/71对照、70/71对照、部分覆盖、日夜录屏关键帧和恢复420密度后的最终浅色画面已查看。
- 主包未卸载/清数据，首次安装仍2026-10-03 06:05:17；截图同宽临时密度460，结束恢复420、夜间no和首页；没有主包崩溃记录。
- 71版首次正式采集被模拟器“System UI 无响应”弹窗压暗，Reader已绘制；曾误判为黑画面，查看实际截图后已更正。通过新鲜UI树的“等待”按钮处理，并把弹窗处理移到采集颜色检查之前，日夜重新采集通过。该System UI卡顿根因未定位，与70版历史黑画面分别记录。
- pending：物理手机安装、手机Download交付、真实手感与本轮用户观感。70版首轮浅色黑画面原因仍未定位，本轮正式采集以实际绘制检查保护，不能据此宣称原原因已修复。未全量重验积压阅读功能，未commit/push。

证据`../../output/dock-subtle-dispersion-20261013/`。70版历史见[状态快照](history/CURRENT_STATUS_70_before_subtle_dispersion_20261013.md)与[PDF材质报告](DOCK_PDF_MATERIAL_20261012.md)。
