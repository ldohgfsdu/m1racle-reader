# 当前状态

2026-11-13：当前开发与构建仓库为 `D:/03_M1racle Reader/reader-ui-release-20261025`。当前源码与正式交付均为 **1.0.0 / 97，Room17**。

正式包：[M1racle-Reader-1.0.0-97.apk](</D:/03_M1racle Reader/apk/M1racle-Reader-1.0.0-97.apk>)，**26.45 MiB**（27732541 bytes）。SHA-256：`4195636FC4033BAA327385A896F56B7CFD8AB96889984C61AE62CBC338E8F32D`。生产证书与 96 一致，代码与资源压缩开启；签名、APK ZIP 及全部 8 份原生库 ELF 的 16 KiB 对齐通过。构建输出、Outbox、本机交付、模拟器 Download 和已安装 base.apk 摘要一致。包名 `com.m1racle.reader`，minSdk31 / targetSdk35，无 INTERNET、非 debuggable；保留 shell profileable。

本轮依据用户 Acrobat 截图改进 PDF 选择：25dp 实色方角手柄直接连接字框，44dp 触控与可访问节点保持屏幕大小；水平拖动按字符边界校准，修正宽窄字与两端交叉偏差；拖动阈值按页面缩放换算，保留现有复制、高亮、批注和放大镜。详见[Acrobat 对照报告](PDF_SELECTION_ACROBAT_20261113.md)。此前 A1、A2、B1–B23 的修复继续保留，历史证据见[96 审查修复报告](AUDIT_FIXES_20261113.md)。

状态为 **READY_WITH_LIMITATIONS**：implemented 完成；automated_verified 为 QA1495 / Release1310 项单测，零失败、零错误，各有 1 项 Windows 符号链接环境跳过；core/pdf465 项通过。Reader 与 core/pdf lint 零错误，分别为 98 warnings、6 hints / 8 warnings，没有新增。宿主原生生产组件的日间、夜间、2.5 倍缩放绘制及像素、触区断言通过，三图已逐张查看；28 项选择手势回归通过。测试结果保存在 `output/pdf-selection-acrobat-20261113/verification-summary.json` 与 `unit-results/`。未变化模块的 96 证据不计作 97 新跑。

测试与正式包绑定 `output/pdf-selection-acrobat-20261113/selection-candidate-5-sha256.json`：763 项编译输入，core141 项；构建后复核零新增、零缺失、零变化。相对 96 只有 8 项输入改动和 1 项新增测试，无输入缺失。Code Wiki 生成与静态检查通过。本次按用户授权将这轮已验证源码与文档同步到 GitHub 的 `chore/deps-prune-20261108` 分支。两个旧工作树与正式 APK 保留；本机 `output/` 验证附件不入 Git。

本轮 device_verified 仅限 **API35 软件图形模拟器**：正式包保留数据升级、首页、三页 PDF 渲染、长按选择、跨行调整、松手保留和复制菜单退出通过，崩溃缓冲为空。系统剪贴板正文未回读；首次冷启动的 System UI Wait 对话框在等待并重启 Reader 后恢复，原始证据保留。使用 540×1200 / density210，结束后已恢复显示参数并关闭本轮模拟器。

**物理手机未连接、原 PDF 未提供**：手机 Download 传输/安装、同页 Adobe 对照、中文实文选字手感、复制准确性、实体触控/IME、滚动及性能仍 pending。宿主原生绘制及模拟器结果不替代真机验收。96 状态见[归档](history/CURRENT_STATUS_96_before_acrobat_20261113.md)，95 交互历史见[报告](PDF_SELECTION_INTERACTION_20261113.md)。
