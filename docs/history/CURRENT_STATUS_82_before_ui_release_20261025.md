# 当前状态

2026-10-25：正式签名 Release **1.0.0 / 82，Room17**。在81实色菜单与品牌头基础上，设置页顶部改为纯母标 M，下方只显示一次 “Reader”；移除内嵌 Reader 字形与白底圆角图标，主题 onSurface 自动深色白标、浅色黑标。

展开品牌区约 144×74dp，母标裁到墨迹边界并保持原比例；下方 Reader 约 28sp。向上滚动叠化为左上约 32×16dp 母标和「设置」，反向回顶完整恢复。版本号移到“关于 Reader”，不再在品牌头重复。详见 [纯母标品牌区验收](SETTINGS_BRAND_20261025.md)。

交付包 `.external_outbox/m1racle-reader-release-latest.apk`，27,608,096 字节，SHA256 `87CEE1D254C6D2CF23356296D6C1C0DFE9F5156F519B3CAB63CB4EC5ECDF6FB1`。生产证书 v2 签名、16KiB 对齐通过，无 INTERNET 权限；构建、Outbox、设备安装包、Download 回读与证据副本摘要一致。

本轮重点 QA 21 tests 全通过，新增品牌头测试含 Reader-only 语义、严格几何、真实手势、回顶与 2× 字号。lintRelease 成功，0 errors、179 warnings、6 hints；新增 warning 为纯母标长路径的矢量性能提示。前轮全量 Release 1683 tests / 2 skipped 继续属于81基线，本轮未重复全量。

Android 35 模拟器实测深浅展开/收起/回顶和 2× 字号通过，截图与短录屏已留证；书库完整、crash buffer 为空，firstInstallTime 保持 2026-10-03 06:05:17。应用偏好小 / 默认 / 标准测试前后逐字节一致，系统字体缩放、三项动画、夜间模式和显示参数均恢复原值。

81 状态归档至 [历史](history/CURRENT_STATUS_81_before_settings_brand_20261025.md)。源码、截图、录屏、签名和哈希证据位于 D:\03_M1racle Reader\output\settings-brand-20261024-new。物理手机未测；未提交、未推送、未公开发布。
