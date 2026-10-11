# RiseDiary 底栏与阅读工具栏 — 2026-10-06

用户要求直接更换为本地 RiseDiary 底栏、将液态玻璃工作台加入正式版、修复关闭文件回主页闪动，并依据 Apple 分段控制 / 工具栏优化 EPUB 面板。另一聊天经用户授权停止修改、构建与模拟器操作，留下的大字号留白测试一并接收。

## 实现与根因

底栏从参考的 LiquidBottomTabs / LiquidBottomTab / LiquidGlassButton 及拖动、高光辅助类移植，持续曲线胶囊使用 shapes 1.2.0，Backdrop 仍2.0.0。保留原版权头与 GPL-3.0 / Apache-2.0 文本，见 `LICENSES/RiseDiary-NOTICE.md`。64dp基准、28dp图标、12sp标签及原透镜放大 / 弹簧；加号独立64dp、小幅2dp按压扩张。

导航是选中入口唯一事实源，取消 / 拒绝导航恢复 owner；快速手势的目标同步记入 requestedValue，避免 UP 比动画协程先到导致错误提交。隐藏采样条不接收点击，不重复提供语义；捕获通过 GlassBackdropSource 屏蔽祖先 host，透镜在捕获节点之外。原自研底栏与旧动画预览状态删除。

固定高度在大字号下不足以容纳真实字体回退的行高。改为测量实际标签高度增加上下留白，底栏与页面尾部避让使用同一高度来源；原生测试覆盖200% / 300%，实际模拟器200%可见完整标签。旧图形模式不正确处理持续曲线路径的命中测试，底栏交互回归改用原生模式，保持原断言；主机 Skia 不支持子着色器 eval，主机使用 BLUR_ONLY，实际 Release 模拟器核对完整光学路径。

用户视频的阅读正文退出淡化与新主页底栏共存，导致玻璃短暂采样旧阅读页。NavHost 普通阅读出入默认过渡改为 None，Hub 内短位移 / 淡入保留；底栏还需等 visibleEntries 全为 Hub 页面再恢复，消除生命周期晚一帧的采样窗口。

工作台移除 GLASS_LAB_ENABLED 构建开关，所有生产设置入口与路由一致。参数仅影响独立预览。

EPUB 面板宽上限288dp / 窗口82%，高上限520dp。返回靠前，目录 / 设置共用靠后的胶囊；单份原位图标成为等宽分段，共用轨道与移动选中块。选择持续显示，不兼作关闭动作；独立关闭、外侧、Back收起。切换只改内容，正文分页与阅读位置保持。依据 [Apple 分段控制](https://developer.apple.com/cn/design/human-interface-guidelines/segmented-controls) 与 [工具栏](https://developer.apple.com/cn/design/human-interface-guidelines/toolbars)，并复核 apple-hig 2026-10-05 快照中的日历与前后分组官方图例。288dp是本项目布局选择。

## 验收分层

- implemented：上述生产改动与许可证落地；版本43，Room仍15。
- automated_verified：QA1720 / 1718通过 / 2既有跳过 / 0失败或错误，222 suites；Release Lint0错误 / 161警告 / 4提示。实际生产 NavGraph 的20帧返回检查覆盖三类阅读器，底栏存在时没有退出阅读页；使用缺失文档ID进入空载 / 错误路由，验证导航生命周期隔离，正文加载另由实际模拟器检查。底栏手势 / 状态 / 字号、EPUB宽度 / 分段 / 固定坐标 / 关闭、正式工作台入口回归通过。
- emulator_verified：签名 Release43覆盖安装；工作台 Clear / 重置 / 返回、底栏正常点击与模拟器实际拖动、浅深色和200%字体通过。真实 Markdown正文、测试与长篇 EPUB、已加载 PDF返回主页，PDF共4次打开 / 返回。EPUB两图标分别固定在 `[839,179,902,242]` 与 `[965,179,1028,242]`，选择后重复点击维持菜单，关闭恢复正文。
- pending：物理手机观感与手感；手机未连接。录屏首帧已是主页，未覆盖退出中间帧，不能证明完整逐帧或FPS；主机动画时钟证明导航隔离。中途模拟器退出后恢复同一 AVD，43及数据保持；保留 UiAutomator null root、模拟器 MultiDisplayService 异常，未发现 reader 进程崩溃。

## 交付与证据

正式构建 `./reader-build-local.ps1 -Release`，8m53s / exit0。APK 7,141,724 bytes；SHA-256 `0ca427abb452c0568a465d0a8c8f249fea34be17fba01fc71e65c31014cd3806`。生产证书 SHA-256 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`，16 KiB zipalign、Manifest43、无 INTERNET验证通过；build / Outbox / installed / Download四份摘要一致。

`.external_outbox/m1racle-reader-release-latest.apk`；快照 `m1racle-reader-release-v1.0.0-20261006-004846.apk`；设备 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`。

`output/risediary-toolbar/` 包含构建与检查日志、XML / PNG、APK摘要 / 签名 / 对齐 / Manifest、原始返回录屏与采样、预览 `preview.jpg`。`feature/epub-anchor` / HEAD a57c324 + 未提交修改，生产差异摘要 `4d55e6d24f621959adf65cfeebc66f4d3b3ff7f1a5a30322d0d7de822a241ed6`。未提交 / 推送 / tag / 公开发布。当前状态见 [CURRENT_STATUS](CURRENT_STATUS.md)。
