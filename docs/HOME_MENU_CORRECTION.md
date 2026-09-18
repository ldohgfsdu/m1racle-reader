# 主页菜单修正（2026-09-11）

用户反馈三点移除菜单重复展示文件名、材质不合适。

- 根因：前一轮为完整文件名查看在仅 140–160dp 宽的操作菜单里增加了文件名区，造成重复及不必要的高度；移除确认框误用了默认 Material AlertDialog，未复用已有 GlassAlertDialog。
- 已移除菜单文件名区，恢复单行“从文库移除”；完整文件名仅在移除确认框用于确认对象。确认框复用项目现有 GlassAlertDialog，不新建材质配方。
- 菜单沿用既有 Popup 及 Menu 配方，sampleBackdrop=false；它是半透明底板，不具备背景模糊。已向用户确认所指材质位置，不擅自修改全局材质或打开不安全的跨窗口采样。
- automated_verified：主页定向测试 34 / 0 / 0 / 0，覆盖菜单不重复展示文件名、确认框核对对象、取消不移除、确认只执行一次以及既有导入/失败恢复。Release、lintVitalRelease、生产签名、源码摘要和 Download 摘要核对通过。原移除确认、取消保护、导入互斥及错误反馈保留。
- device_verified：NOT_RUN；本轮一次 ADB 探测为空。菜单/确认框实际观感仍待用户确认。
- 未重跑已知工具不兼容的完整 lintQa；前一轮失败事实保留。
- 证据：`.external_outbox/home-menu-correction/`。

交付：`/storage/emulated/0/Download/m1racle-reader-release-latest.apk`，6,935,210 bytes，SHA-256 `570554b693285a4c5667cd0337fad67bee0934579768b518b69a1de72912290f`。版本和生产签名不变，附 SHA-256 文件。
