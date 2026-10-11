# 当前状态

当前基线 **1.0.0 / 38，Room v15**。修复用户实机截图中的 EPUB R 角形变：圆角不再乘手势进度，所有纸页始终使用完整设备四角半径，移动仅露出原有轮廓。前后翻、松手和拉回取消均不缩放 R 角；仅设备 / 窗口配置变化重新读取。报告见 [固定圆角修复](EPUB_FIXED_CORNERS_20261005.md)。

37 版其余功能保留：下层页四分之一跟随、深色原纸面随距离渐亮、停稳页身份与取消不提前保存、全窗口纸页、原生导航条保留 / 状态栏隐藏、单份 UI 书名 / 平滑控件以及右上角锚定设置。阴影仍有起止渐变，圆角几何不参与渐变。

本地 Markdown、PDF、有限 EPUB，无 INTERNET、账号、广告或云同步。未新增依赖、数据库变更或出版方 CSS 全支持。本轮没有扩展到其他功能。

## 当前交付

`.external_outbox/m1racle-reader-release-latest.apk`，**7,041,360 bytes**，SHA-256 `b64d43b8b12dd9a5aaedc755e6b61cfc876593767a57fb578e0fe1bf25a2d2ef`。快照 `m1racle-reader-release-v1.0.0-20261005-202739.apk`。正式证书不变；签名、16 KiB 对齐、Manifest 38 / 无 INTERNET 检查通过。构建、Outbox、模拟器实际 base.apk 与 Download 四份摘要一致。已在线覆盖安装 `emulator-5554`，保留书库与进度，没有清数据。真实手机未连接，新包实机验收待完成。

## 本轮验证

- 完整 QA **1714 项：1712 通过、2 既有跳过、0 失败 / 错误，221 suites**。新增 Native 渲染回归覆盖 3 / 5 / 12 / 50 / 90% 拖动的上下角，保留实际设备半径更新、松手身份、取消、跟随、色值、菜单和沉浸回归。Release Lint **0 errors / 149 warnings / 4 hints**，完整构建 **8m 35s / exit0**。
- 旧正式包实际复现：5% 轻拖的右上角采样点仍是纸色，10% 才裁掉。新正式包同一点从 5% 到 95% 持续裁掉；前翻、后翻、拉回与浅色轻拖均实际截图验证固定轮廓，见 `corner-measurements.json`、`light-corner-measurements.json`。
- 浅 / 深色最终恢复第 **8 / 464 页**，正文 PNG 与各自起始页精确一致；单份 UI 书名、状态栏隐藏 / 原生导航栏显示 / DEFAULT 均保留。覆盖安装后冷启动书库与阅读恢复，崩溃缓冲为空。首次立即 UI 抓取曾读到未就绪首页 / 正在分页，等待 fresh UI 后完成验收，没有将早期加载问题宣称已修复。
- 实际截图与 screenrecord 位于 `output/epub-fixed-corners/`，VFR 录屏不用于 FPS / 性能声明。此前严格整页像素诊断边界保留，未将旧失败改为通过；不声明 Apple Books 全页像素一致或新包真实手机通过。

## 工作区

分支 `feature/epub-anchor`，HEAD a57c324 加未提交工作区修改。生产差异摘要 `5e26d0ab4a12011268eaaf58ae829091f04cece3555b12050b6b8ff9b8b2f5ed`，原字节留档 `production.diff`。前状态见 [37 版快照](history/CURRENT_STATUS_before_epub_fixed_corners_20261005.md)。未推送 / 公开发布 / tag。
