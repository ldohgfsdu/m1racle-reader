# 已确认磨砂底板铺开

2026-09-06。用户确认底板版 `38a8893c4a59b8e589a36a292f7dece40ce65b2a46df517089dd1cdf33d1f880` “效果还可以”，授权铺开底板效果，同时询问加号位置。本轮承接当前工作区，不创建发布目标。

## 范围与实现

材质由 GlassMaterialEngine 集中提供。抽出实际由 Dock / Control / Capsule 三类调用的 regularFrost：现按 Kyant `LiquidBottomTabs` 对齐为浅/深 alpha 0.40、8dp 模糊、夜间 tint `#121212`（浅色仍 `#FAFAFA`）。Control 和 Capsule 直接使用同一配方；Dock 只保留自身的透镜尺寸、高光、vibrancy 和按压形变差异。加号、PDF 返回/更多按钮、PDF 标题胶囊等现有调用直接获得这套底板，不在各页面复制参数。

菜单 alpha 从 0.60/0.74 调为 0.64/0.76；嵌套面板已有 0.66/0.78，比菜单再厚一层。对话框保持其独立窗口的 0.97 遮盖，确保正文不会干扰对话框文本；选中透镜仍用于折射清晰图标。正文背景的 0.65 对比度保护继续生效，避免为了统一 alpha 撤回已修正的阅读器背景波动防护。

已核对 HomeScreen、ContinueReadingCard、PdfViewerScreen、ReaderMoreHost、AnchoredNestedPopover 与 GlassAlertDialog 的真实材质调用。仅调整已有玻璃表面，不新增依赖、采样路径或业务功能。

## 加号建议

当前 Home 导入按钮为 56dp 独立浮动按钮，位于四入口底栏右上方。建议保持这个动作入口：四个 tab 始终负责目的地导航，加号触发已有系统文件选择器。把它做成第五个同型导航项会混淆动作与选中状态；与四入口整条并排则需要统一各 Hub 的占宽与导入可用范围，超出本次材质推广所需。此轮加号同步获得新底板，位置与导入流程保持现状。

参考平台的导航/操作分工：[Material BottomAppBar](https://github.com/material-components/material-components-android/blob/master/docs/components/BottomAppBar.md)、[BottomNavigation](https://github.com/material-components/material-components-android/blob/master/docs/components/BottomNavigation.md)。具体放置建议是结合本项目四入口和现有导入流程的设计判断。

## 验证检查点

中风险：配方应用到多处页面。更新用户授权变更的材质期望值；既有层级、PDF 背景调制与按压回归保留，无放宽容差。首轮全量中两项 ReaderChromeMaterialTest 仍期望旧 Control/Capsule alpha 0.50/0.68；已同步为用户批准的 0.62/0.74，并同步 Menu 期望值，原容差/层级/颜色断言保留。

`:app:testQaUnitTest` 与 `:app:assembleRelease` 同一次 Gradle 运行成功：1411 / 0 / 0 / 0。production signer SHA-256 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`、v2 签名、包名/版本 1.0.0 (23)、Manifest 无 INTERNET。APK 6,702,410 bytes，SHA-256 `005581c01b114486a1861cc356a404fc94acad28d2724fb01ac5e598aed6704c`。outbox 与 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk` 摘要一致。源码为 `5284f801fffa62904ce80101ca687cfd415ea8c6` 加本轮未提交工作区。证据目录 `.external_outbox/glass-rollout-20260906/`。

implemented / automated_verified 如上。用户对上一版底板的主观反馈已确认；这不替代本轮扩展页面的设备验收。ADB 本轮一次探测为空，device_verified: NOT_RUN。pending：PDF 顶部按钮/标题胶囊、菜单/嵌套面板和加号的整体观感；既有 lintVital ASM 异常仍出现，不声明完整 lint 通过。

## 夜间藏青修正

用户反馈夜间底板发蓝。根因是共享 `darkTint` 为 `#111820`（R17 G24 B32，偏蓝），盖在石墨页面 `#0F0F0F` / `#171717` 上会看成深蓝。曾改为 `InkSurface` `#171717`。

## 对齐 Kyant 底板

用户要求按 Kyant `LiquidBottomTabs`（RiseDiary 同源）对齐：夜间 `#121212`，浅/深透明度 0.40，模糊 8dp。随后要求菜单和嵌套面板也铺开，不再加厚。Dock / Control / Capsule / Menu / SettingsCard 现共用同一配方；Menu 仍导出给嵌套面板采样，SettingsCard 仍叠 RootAndMenu。Dialog 在独立窗口、`sampleBackdrop = false`，保持 0.97 以免正文透出。

用户反馈 PDF 控件没有变化。阅读器 host 曾设置 `overDocument = true`，把采样对比度压到 0.65，浅色 PDF 白纸上几乎看不出 alpha/blur 变化，且 Control/Capsule 没有 vibrancy。已删除文档专用采样路径，regularFrost 打开 vibrancy，PDF 返回/更多/标题胶囊与底栏同一套底板。页缝仍先画不透明 surround 再采样。

随后用户反馈颜色不对。Kyant `LiquidBottomTabs` 不对采样做 colorControls；我们还叠了 brightness ±0.20 与 saturation 1.25。已改为 brightness 0、saturation 1，identity colorControls 不再调用。APK SHA-256 `0cfbef9730e31423c9e3b91fc2d9140ff21e41ca719385cd0b576a7043e51eb8`。证据 `.external_outbox/kyant-color-20260906/`。device_verified: NOT_RUN。
