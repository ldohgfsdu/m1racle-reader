# 当前状态

2026-10-31：正式签名、压缩 Release **1.0.0 / 93，Room17**。当前仓库 D:/03_M1racle Reader/reader-ui-release-20261025，分支 codex/reader-stability-20261030，构建使用 reader-env-local.ps1 的 $readerRepository；两个原工作树保留。

93 将 PDF 内置目录改为首次打开后后台自动合并，完成立即更新目录，保留手工节点，不再弹确认框或成功提示。读取期间显示状态并禁止重复扫描。本地文件直读、页树顺序索引和预建兄弟关系减少重复处理；不承诺固定秒数或提速倍数。[目录实现与验证](PDF_AUTOMATIC_OUTLINE_20261031.md)。继承 [92 首屏和固定视口输入](PDF_INPUT_ROUTING_20261031.md)。

唯一当前交付 D:/03_M1racle Reader/apk/M1racle-Reader-1.0.0-93.apk，27639066 字节（26.36 MiB）。SHA-256：08F2B9E9714E8B88A02E21141D06DB7D992EC825C447E864C1DE9B0B5DEACEC6。生产签名、16 KiB 对齐、R8 / 资源压缩、35 个 assets 同源通过，无 INTERNET、非 debuggable，保留 profileable shell=true。662 个编译输入在测试、构建及最终验证之间未变；构建、Outbox、交付、手机已安装包和手机 Download 摘要一致。92 文件保留。

全量 QA 单测 1882 项、Release 单测 1719 项，均 0 failures / 0 errors，各 2 项既有环境跳过；lint 0 errors / 179 warnings / 6 hints。模拟器最终相关原生整轮 **40/40 通过，0 失败、0 跳过**，包括新增自动目录及原有首屏、选字、手势、像素、恢复、隔离。模拟器首轮系统界面无响应弹窗导致 9 项焦点失败，恢复后保持原源码与断言，完整重跑通过；首轮证据保留。模拟器正常关闭，数据保留。

手机 db04f865 已从正式 92 保留数据覆盖安装 93，firstInstallTime 保持 2026-08-31 16:14:37，首页文库及既有 PDF 的 44/97、33/626 位置保留。Download 与安装摘要一致。手机 QA 自动化两次卡在启动后由本任务停止，不计通过。安装后手机切到其他应用，已停止后续界面操作并丢弃无关截图；手机目录详细显示、长书耗时和完整手势验收 pending，不能以模拟器代替。

状态 **READY_WITH_LIMITATIONS**：本轮 implemented、automated_verified 和同源正式交付完成；device_verified 为模拟器整轮与手机安装 / 首页有限检查。手机实际阅读手感、目录速度及人工验收，真实输入法、性能对照、TalkBack、厂商长截图仍需专项验证。源码与报告本地保留，未提交、推送或公开发布。证据 D:/03_M1racle Reader/output/outline-auto-20261031；[92 状态归档](history/CURRENT_STATUS_92_before_outline_auto_20261031.md)。
