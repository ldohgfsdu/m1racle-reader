# 当前状态

2026-10-31：正式签名、压缩 Release **1.0.0 / 92，Room17**。开发仓库 D:/03_M1racle Reader/reader-ui-release-20261025，分支 codex/reader-stability-20261030；本机构建继续使用 reader-env-local.ps1 的 $readerRepository。原 reader 和 reader-audit-fixes-20261024 工作树保留。

92 延续 91 的 PDF 完整首屏修复：先准备目标页整页预览，所有可见页具备底图或明确错误后显示正文，高清瓦片在完整底图上细化。另将横向拖动、缩放和点击移到固定视口，修复移动页面局部坐标被去重导致的停顿补跳。原有手势、阅读位置、选字与沉浸逻辑沿用。实现、开源依据和诊断见 [固定视口手势报告](PDF_INPUT_ROUTING_20261031.md)，首屏实现历史见 [91 报告](PDF_FIRST_FRAME_20261031.md)。

唯一当前交付：D:/03_M1racle Reader/apk/M1racle-Reader-1.0.0-92.apk，27638646 字节（26.36 MiB）。SHA-256：5FCFC2757DFCA552FB5B51EEA1650C5D13B62A1BBC86236E95E911A2F866AE24。生产签名、16 KiB 对齐、R8 / 资源压缩、35 个 assets 同源核对通过，无 INTERNET、非 debuggable，保留 profileable shell=true。661 个编译输入在最终构建与验证之间未改变；构建、Outbox、交付、模拟器已安装包和模拟器 Download 摘要一致。91 交付文件保留。

最终同源 QA 单元测试 1880 项、Release 单元测试 1717 项，均 0 failures / 0 errors，各 2 项既有环境跳过；lint 0 errors / 179 warnings / 6 hints。模拟器相关原生整轮 **39/39 通过，0 失败，0 跳过**，包括首屏 4 项、渲染恢复、选字、手势生命周期、瓦片像素、配置重建和隔离。此前 REAL_1X_HOLD_NO_TWITCH 失败已关闭：原始即时采样与全部严格断言保留，最终整轮 36/36 移动点均响应、0 遗漏。旧结构单测精确改验固定视口入口及完整重启 key，最终全量重新通过；首次失败证据保留。

Android 35 模拟器已从正式 91 保留数据覆盖安装 92，firstInstallTime 保持 2026-10-03 06:05:17，既有合成 PDF 仍为 2/3。正式包深色模式重开三次完整显示目标与可见邻页；横向滑动 2 → 3 → 2、正文显隐控件、双击缩放与恢复、切回连续阅读均验证。录屏抽帧未观察到白色亮屏或散块；这是合成 PDF 的模拟器证据，不是原书或真机验收。冷启动模拟器 System UI 无响应等待后恢复，Reader 崩溃缓冲为空。夜间模式 no、方向 0/0、字体比例 1.0 已恢复，模拟器正常关闭并保留数据。

状态 **READY_WITH_LIMITATIONS**：本轮 implemented、automated_verified 与同源签名交付完成，device_verified 仅限模拟器；**物理手机未连接，手机安装、手机 Download 交付和手机 / 人工验收 pending**。真实输入法、持续滚动手感、性能对照、TalkBack、厂商长截图仍需专项验证；不承诺固定启动提速。源码和报告保存在本地，未提交、推送或公开发布。证据：D:/03_M1racle Reader/output/drag-sampling-20261031；[91 状态归档](history/CURRENT_STATUS_91_before_input_routing_20261031.md)。
