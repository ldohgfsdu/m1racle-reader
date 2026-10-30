# 当前状态

2026-10-24：正式签名 Release **1.0.0 / 81，Room17**。在80母标图标基础上，本轮重做设置页品牌头，并把首页文档菜单统一为不透明、锚定、带轮廓图标的安全操作菜单。

设置页展开时 108dp 图标居中、应用名和版本在下；向上滚动连续折叠为左上 32dp 图标与「设置」，反向回顶完整恢复。日间、夜间、2× 系统字号和系统动画关闭均有 Android 35 模拟器原生证据；名称不裁切、不叠字。实现与测试见 [设置页品牌头与文件菜单](SETTINGS_MENU_20261024.md)。

首页 Markdown、PDF、EPUB 共用实色菜单；Markdown 独有「编辑正文」，移除项独立分隔并使用危险色。重命名、从文库移除均只验证取消路径，文件仍在；文件信息可打开关闭；导出打开系统保存器后取消，未写文件。

交付包 `.external_outbox/m1racle-reader-release-latest.apk`，27,604,500 字节，SHA256 `CFEAB6B3B3E73F9DDEAA60DEAA34546415EDFAE4620454C652514E85636507F6`。生产证书 v2 签名与 16KiB 对齐通过，无 INTERNET 权限；构建、Outbox、设备实际安装包、Download 回读与证据副本摘要一致。

全量 Release 单元测试 216 suites / 1683 tests，0 failures，0 errors，2 skipped；重点 QA 20 tests 全通过。`lintRelease` 成功，0 errors、178 warnings、6 hints。模拟器冷启动正常、书库完整、crash buffer 为空；`firstInstallTime` 保持 2026-10-03 06:05:17。

应用阅读偏好已还原为小 / 默认 / 标准，测试前后 DataStore 逐字节一致；系统字体缩放、三项动画、夜间模式和显示参数均恢复原值。物理手机未测；未提交、未推送、未公开发布。

80 状态归档至 [历史](history/CURRENT_STATUS_80_before_settings_menu_20261024.md)。源码冻结、截图、录屏、签名和哈希证据位于 `D:\03_M1racle Reader\output\settings-menu-20261024`。
