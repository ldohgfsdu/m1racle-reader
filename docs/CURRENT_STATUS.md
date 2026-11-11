# 当前状态

2026-11-06：当前开发与构建仓库为 D:/03_M1racle Reader/reader-ui-release-20261025。当前源码与正式交付均为 **1.0.0 / 94，Room17**。

正式包：[M1racle-Reader-1.0.0-94.apk](</D:/03_M1racle Reader/apk/M1racle-Reader-1.0.0-94.apk>)，**26.44 MiB**（27725374 bytes）。SHA-256：`F674B911D4B0A5B9296A07C4E4F7396CC058192B77144F61F3478455FA28F81E`。使用生产证书签名，代码与资源压缩开启，签名和 16 KiB 对齐核验通过；构建输出、Outbox、本地交付、模拟器已安装包及 Download 副本摘要一致。

本轮交付包含目录防循环、选字菜单缩放修正，完成 core:model / core:database / core:pdf 边界；PDF 导入上限为 2 GiB，采用流式复制、文件随机读取和磁盘临时缓冲。修复拖动、惯性、点按、首次布局及重建恢复中的坐标跳动。阅读菜单支持按章节层级或固定页数直接拆分 PDF，包含范围选择、进度、取消、输出回读与失败清理。

本轮状态 **READY_WITH_LIMITATIONS**：implemented 完成；automated_verified 为 QA 1926 / Release 1762 项单测零失败、零错误，各 2 项既有环境跳过；四模块 lint 零错误、187 条警告。59 项原生测试零失败、零跳过，3 项时序用例额外两轮均通过。实际章节 / 页数拆分的 7 份 PDF 经独立工具逐页核验，192 个输出页面与源页文字和渲染完全一致。最终正式包保留数据安装、冷启动、首页、设置、PDF 阅读及实际 2 + 1 页拆分检查通过，未见 Reader 崩溃或 ANR。

device_verified 仅限 API35 软件图形渲染模拟器。当前未连接物理手机，本轮未完成手机安装或人工阅读手感验收。模拟器帧时间仍偏高，不能宣称手机流畅度已验收。大文件原生证据为至少 224 MiB 的合法稀疏 PDF 和 2000 页页树，不等同于完整 2 GiB 或高密度图像实书验收。

最终编译输入冻结为 **source-final-v94，705 项（core 137 项）**，原生测试、单测、正式构建及交付前后清单一致。该源码基线已提交到当前分支；生成目录与 `__pycache__` 已排除。两个旧工作树仍保留，不覆盖当前工作树。详见 [本轮报告](PDF_LARGE_FILE_SPLIT_20261106.md)；证据目录 D:/03_M1racle Reader/output/pdf-large-split-20261106。93 的状态保留于 [交付状态归档](history/CURRENT_STATUS_93_before_large_pdf_20261106.md)。
