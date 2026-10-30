# 当前状态

2026-10-30：正式签名、压缩 Release **1.0.0 / 89，Room17**。开发仓库 D:/03_M1racle Reader/reader-ui-release-20261025，分支 codex/reader-stability-20261030；本机构建使用 reader-env-local.ps1 的 $readerRepository。原 reader 与 reader-audit-fixes-20261024 工作树保留。

89 修复重叠书签操作产生重复记录、EPUB 命名目录锚点、PDF 整页与瓦片缩放差异、缩放结束提交丢失及真实尺寸更新后的页内阅读位置漂移；补齐渲染到保存重开的集成回归和 QA 滚动采集入口。继承 88 渲染元数据优化、Markdown BOM 修复和 87 界面品牌基线。详见 [本轮修复与证据](STABILITY_FIXES_20261030.md)。

唯一交付：D:/03_M1racle Reader/apk/M1racle-Reader-1.0.0-89.apk，27605878 字节（26.33 MiB）。SHA-256：82591CDDD4C83CA0A00EBF23E39885630DD62C055EA2E09E94D4AC795B46D8A1。生产签名、16KiB 对齐、R8 / 资源压缩、35 个 assets 同源校验通过，无 INTERNET、非 debuggable，保留 profileable shell=true。构建、Outbox、交付、已安装包及模拟器 Download 摘要一致。

同源 QA 单元测试 1853 项、Release 单元测试 1696 项，均 0 failures / 0 errors，各 2 项既有环境跳过；lint 0 errors / 179 warnings / 6 hints。最终隔离 QA 原生回归 **40/40 通过**，包含全部六项历史 PDF 失败、瓦片像素、真实 Room 并发书签、EPUB 目录点击、PDF 渲染保存重开、Markdown 旋转和迁移/事务回归；未放宽断言或像素阈值。

API35 x86_64 模拟器已保留数据覆盖安装 89，firstInstallTime 仍为 2026-10-03 06:05:17，原文档与阅读记录保留。正式 PDF 竖横屏、两种阅读模式、退出重开及后台恢复均保持第 2/3 页；临时书签添加/移除闭环通过，Markdown 标题正文正常，EPUB 恢复第 10/18 页。原阅读模式和屏幕方向已恢复。

QA 合成 Markdown 页完成 6 次向前、6 次向后滚动采集：176 帧，janky 99 帧（56.25%），P50 44ms、P90 105ms。已保留 gfxinfo、framestats 和内存快照；该模拟器结果偏高，无修改前对照，不能判定性能通过、回退或内存泄漏。物理手机未连接，真机安装、真实中文 IME、持续阅读手感、性能及厂商长截图流程均 pending。

实现和本轮自动回归状态为 automated_verified；设备证据仅来自模拟器，尚未完成物理手机及人工验收。源码与文档保存在本地提交，未推送、未公开发布。证据：D:/03_M1racle Reader/output/stability-fixes-20261030/v89；[88 状态归档](history/CURRENT_STATUS_88_before_stability_20261030.md)。
