# Dock 按压拖动跟手修复（2026-10-08）

## 改动

用户提供52版真机视频，反馈按压后拖动卡顿。调用链为 `LiquidBottomTabs.onDrag → DampedDragAnimation.updateValue`。旧实现对每次位移取消并重启位置弹簧，使显示位置持续追赶目标位置；底栏位移也为每次移动启动 `snapTo` 协程。

拖动位置改为在输入回调中直接写入显示状态，只在松手、取消或导航所有者变更时启动位置弹簧。再次按下取消尚未结束的归位动画。底栏位移同步累积，仅释放时启动归位动画。保留按压缩放、速度形变、折射及采样结构；速度形变结束后回零，不依赖位置弹簧完成来复位。

## 验证

- **implemented**：直接跟手与松手弹簧分离。
- **automated_verified**：37项定向QA JVM测试通过，0失败、0跳过。新增LTR与RTL下一帧位置断言，覆盖连续移动、反向移动、按住停止、取消归位；保留选中归属、中央导入隔离、按压取消等回归。
- **physical_device_verified**：真机 `db04f865`，Android API36，1220×2656。最终8项光学仪器回归全部通过（`device-optical-unblocked.log`），包含72次逐帧移动与反向移动、按压预览及取消、浅深色图标像素、折射与背景透射。正式包 `install -r` 成功，冷启动289ms；52版、53版各20次左右拖动完成，最终主页与数据仍在。
- **release_verified**：正式签名 Release 1.0.0 / 53构建成功（10m50s）；Release Lint 0 errors / 162 warnings / 4 hints。签名及16KiB对齐通过，生产证书 `2d9daba0…bf9d1` 延续。

同机单轮对照：帧耗时P50 13→11ms、P90 22→17ms、P95 24→19ms；GPU P95 21→13ms。gfxinfo旧口径Janky比例32.79%→18.66%，新口径均0.03%，两种口径不能混用。正式包覆盖安装后冷启动，页面滚动位置与旧版截图有差异；此数据只作本轮场景记录，不作为固定60/120Hz或精确因果增益保证。下一帧跟手行为由独立位置断言验证。

早期仪器测试受MIUI后台启动与跨应用启动确认干扰，包含一次不完整运行及一次按压断言失败，未记为通过。定位日志后临时将独立QA包的MIUIOP(10021)从ignore设为allow，重新完整运行，8项全部通过；随后已恢复ignore。没有修改正式包权限或削弱测试断言。

本轮APK：27,183,175 bytes；SHA-256 `4c4496f99a4752f0cff2768cca9f984b2e892d066ed7b76401da0d9cf27fe7c7`。构建输出、Outbox与真机Download摘要一致，手机已安装versionCode53。唯一交付包 `.external_outbox/m1racle-reader-release-latest.apk`。

证据目录：`output/dock-drag-fix/`。主包只允许 `install -r`，无卸载、无清除数据，无数据库迁移。
