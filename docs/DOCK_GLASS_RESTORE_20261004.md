# 底栏液态玻璃恢复（2026-10-04）

用户要求修复消失的折射和缩放；当前指令取代 `design.md` 中上一轮取消底栏光学效果的裁决。

## 根因与实现

`GlassDock` 的底座与加号明确设置 `sampleBackdrop = false`，又绘制不透明主题背景；选中透镜改为普通灰色块，透镜放大幅度降到 14%。这不是设备没有运行 shader，而是调用路径主动关闭了光学采样。

恢复底座 / 加号的共享 Dock 光学材质，去掉不透明覆盖。选中透镜通过共享 SelectionLens 材质采样已处理的底座导出及独立蓝色图标 / 标签捕获层，不重复采样未处理页面。捕获层继续使用受保护的 `GlassBackdropSource`，没有自己的交互 / 可访问节点；前景在透镜范围挖空，防止图标重影。按压透镜放大约 1.39 倍、捕获图标约 1.2 倍，拖动速度形变及释放弹簧复原保留。加号按压放大仍为 1.12 倍。

四入口、独立导入操作、字号自适应、RTL、底部透明手势区及导航状态所有者保留。没有改变阅读器材质、持久化或依赖。

真实像素回归还发现细密条纹经 Android 大半径模糊后产生摩尔纹。仅 Dock 在 8dp 模糊之前增加 1dp 预过滤，原来的平滑度阈值 0.02 保留并通过。按压时 Home 透镜扩展至 Library 左侧，高光边缘不属于平滑度测量区，Library 改在右侧无图标间隙测量；没有扩大容差。

## 验证状态

- implemented：已接回光学调用链及按压放大。
- automated_verified：Hub / material / motion 相关单测 87 项，0 failures / errors / skipped。
- emulator_verified：Android 15 / API 35，`emulator-5554`；4 项 QA 像素回归全部通过：折射相对仅模糊的像素差异、深 / 浅色图标、条纹背景按压前 / 按住 / 松手的平滑采样与放大 / 复原。彩色棋盘背景实际静止 / 按住截图已视觉检查，透镜抬起、放大并折射图标，底座与加号边缘折射可见。
- automated_verified：Release Lint 0 errors / 159 warnings / 4 hints；`:app:assembleRelease` 成功，APK v2 正式签名及 16 KiB 对齐验证通过。
- emulator_verified：正式 Release `install -r` 覆盖安装 / 冷启动成功，四页入口、标签与选中状态核对通过，四页底栏截图已视觉检查，crash buffer 无输出。手势检查包括 QA 棋盘背景按住 Home 和横向拖动至设置；结束恢复正式包首页。
- pending：真实手机未连接，本轮包尚未在真实手机复验；没有宣称真机通过或全套单测重跑。

## 唯一交付产物

`.external_outbox/m1racle-reader-release-latest.apk`，1.0.0 / 31，6,994,053 bytes，SHA-256 `f54d0aa9c1ed1d06bc68e25a1937eeaa64ac578ebaac4b8e31f0fbc30f6cfbce`。本次构建输出、Outbox、模拟器已安装 base.apk 及 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk` 摘要一致。证书 SHA-256 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`。

QA 包仅为隔离设备测试；不作为交付包。当前未清除生产数据，未提交 / 推送。

本轮截图 / XML：`.local-backups/dock-glass-20261004/`；自动测试：`app/build/test-results/testQaUnitTest/` 与 `app/build/outputs/androidTest-results/connected/qa/`。
