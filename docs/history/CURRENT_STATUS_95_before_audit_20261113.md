# 当前状态

2026-11-13：当前开发与构建仓库为 D:/03_M1racle Reader/reader-ui-release-20261025。当前源码与正式交付均为 **1.0.0 / 95，Room17**。

正式包：[M1racle-Reader-1.0.0-95.apk](</D:/03_M1racle Reader/apk/M1racle-Reader-1.0.0-95.apk>)，**26.44 MiB**（27741565 bytes）。SHA-256：`A19D529EB4B2C16067380D565C10EB41E85C80C4CFEA3B9D3B4F23874B20EEE5`。使用生产证书签名，代码与资源压缩开启，签名和 16 KiB 对齐核验通过；构建输出、Outbox 与本地交付摘要一致。

本轮重做 PDF 选字交互：字符按行吸附、手柄移到文字外侧并保持 44dp 触控、拖动时显示 2 倍文字放大镜与当前字符边界，见 [选字交互报告](PDF_SELECTION_INTERACTION_20261113.md)。此前 94 的目录防循环、选字菜单缩放修正、core 模块边界、2 GiB 流式导入与章节 / 页数拆分保持不变。

本轮状态 **READY_WITH_LIMITATIONS**：implemented 完成；automated_verified 为 QA 1460 / Release 1285 项单测零失败、零错误，各 2 项既有环境跳过；lint 0 errors、175 warnings、6 hints。正式包 95 的签名、压缩与 16 KiB 对齐已核验，包内含本轮新选字实现（`pdf-selection-loupe` 仅出现在新包）。

device_verified 仍仅限 API35 软件图形渲染模拟器，且本轮未重跑模拟器。**构建时未连接物理手机**：95 未安装、未做真机手感验收，Download 交付未执行。用户要求的同一 PDF 实机对照（单字、整句、反向拖动、跨行拖动、放大状态下拖动、复制准确性）仍 pending，不能宣称已达 Adobe 手感。放大镜复用当前页 bitmap，低分辨率档位下会偏软，需真机确认。

两个旧工作树仍保留，不覆盖当前工作树。93 / 94 状态见 [94 报告](PDF_LARGE_FILE_SPLIT_20261106.md) 与 [交付状态归档](history/CURRENT_STATUS_93_before_large_pdf_20261106.md)。
