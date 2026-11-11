# 当前状态

2026-10-31：正式签名、压缩 Release **1.0.0 / 91，Room17**。开发仓库 D:/03_M1racle Reader/reader-ui-release-20261025，分支 codex/reader-stability-20261030；本机构建继续使用 reader-env-local.ps1 的 $readerRepository。原 reader 和 reader-audit-fixes-20261024 工作树保留。

91 修复 PDF 打开时的空底图发布和逐块显现：先准备恢复目标页的整页预览，再让所有可见页具备底图或明确失败后切入正文；高清瓦片在完整底图上细化。加载过程使用当前主题背景，邻页失败显示既有重试入口。阅读位置与缩放会话沿用现有权威来源。实现、开源对照、回归与边界见 [PDF 首屏报告](PDF_FIRST_FRAME_20261031.md)。继承 [90 选字与沉浸基线](TEXT_SELECTION_AND_IMMERSION_20261031.md) 和 87 品牌基线。

唯一交付：D:/03_M1racle Reader/apk/M1racle-Reader-1.0.0-91.apk，27638646 字节（26.36 MiB）。SHA-256：5F83C0BA19C120DC51FAB8ACBB75B13C839A64AD07DFC297C778600A72494A13。生产签名、16 KiB 对齐、R8 / 资源压缩、35 个 assets 同源核对通过，无 INTERNET、非 debuggable，保留 profileable shell=true。661 个编译输入在最终构建与验证之间未改变；构建、Outbox、交付、模拟器已安装包和模拟器 Download 摘要一致。

最终同源 QA 单元测试 1880 项、Release 单元测试 1717 项，均 0 failures / 0 errors，各 2 项既有环境跳过；lint 0 errors / 179 warnings / 6 hints。模拟器原生整轮 **37/38 通过，1 项失败，0 跳过**：首屏专项 4/4、渲染恢复、选字、手势生命周期、瓦片像素和隔离等通过；REAL_1X_HOLD_NO_TWITCH 的 directionalFrames >= 30 未通过，旧 90 QA 在相同模拟器和相同断言上亦复现。未放宽轨迹、帧数、位置、像素或字符断言；该失败仍待查，不宣称完整原生回归通过。

Android 35 模拟器已从正式 89 保留数据覆盖安装 91，firstInstallTime 保持 2026-10-03 06:05:17，既有合成 PDF 仍为 2/3。正式包深色模式连续重开三次，未观察到空白亮屏、瓦片散块或可见邻页白洞；当前正式进程崩溃缓冲无 fatal。QA 另以用户录屏封面制作的合成三页 PDF 做 30 fps 连续画面检查，三次首次正文均完整显示可见区域；该合成资料不是原书。模拟器夜间模式 no、方向 0/0、字体比例 1.0 已恢复，模拟器正常关闭并保留数据。

状态 **READY_WITH_LIMITATIONS**：implemented 和首屏专项 automated_verified 已完成，device_verified 仅限模拟器；**本轮物理手机未连接，手机安装、手机 Download 交付和手机 / 人工验收 pending**。没有继承 90 的手机结论作为 91 验收。既有拖动采样失败、真实输入法、持续滚动手感、性能对照、TalkBack、厂商长截图仍待专门验证；本轮不承诺固定启动提速。源码和报告保存在本地，未推送或公开发布。证据：D:/03_M1racle Reader/output/video-review-20261031；[90 状态归档](history/CURRENT_STATUS_90_before_first_frame_20261031.md)。
