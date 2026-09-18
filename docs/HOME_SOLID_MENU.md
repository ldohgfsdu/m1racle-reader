# 首页三点菜单实色表面（2026-09-11）

用户明确不满意的是首页三点菜单材质。

- 原因：Popup 使用 sampleBackdrop=false 的 Menu 材质，仅半透明填充而没有背景模糊，底下文件名会直接透出。
- implemented：移除该 Popup 的玻璃绘制入口，改用现有 M1racleReaderTheme.surface.overlay 不透明表面，配现有 2dp overlay 阴影与原菜单圆角。普通条目和继续阅读共用；保留点击、长按、取消/移除确认及原按压反馈。
- 不修改全局玻璃配方、Dock 或确认框。无需新依赖或绘制引擎。
- automated_verified：主页定向测试 34 / 0 / 0 / 0。布局契约改为不透明 overlay 表面和轻阴影，禁止重新接回半透明玻璃绘制；实际 Compose 点击测试保留取消/确认及继续阅读流程覆盖。
- Release 和 lintVitalRelease 成功，生产签名与原证书一致，源码摘要及 Download 摘要一致。lintVital 分析仍打印既有 ASM NegativeArraySizeException，未声明完整 lint 通过。
- device_verified：NOT_RUN，一次 ADB 探测无在线设备；实际观感由用户安装验收。
- 证据 `.external_outbox/home-solid-menu/`。

交付：`/storage/emulated/0/Download/m1racle-reader-release-latest.apk`，6,935,210 bytes，与前版大小相同。SHA-256 `eab430a5f8e9edf82fc035db9eb8489394777068f26fe3910b30cd4624513656`。已附摘要文件，版本与签名不变。
