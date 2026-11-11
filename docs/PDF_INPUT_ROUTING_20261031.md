# PDF 固定视口手势入口

状态：implemented、automated_verified，device_verified 仅限模拟器；正式签名压缩 92 已交付，物理手机安装与人工验收 pending。延续 91 的首屏修复，关闭既有横向拖动采样失败。

## 证据与根因

91 首屏专项已通过，但完整 38 项回归中 REAL_1X_HOLD_NO_TWITCH 只得到 27 个有效移动点，旧 90 同样复现。新增诊断保留原有轨迹和所有断言，先记录事件时间、窗口收到的坐标以及实际页面位移，再修改实现。

- 原测试事件时间每步仅累加 16 ms，忽略同步注入耗时；时间落后从 495 ms 增长至 1789 ms，并产生终点过冲。改用实际 uptime 后，终点由 -701.999 回到精确的 -691.1992，但仍有 10 个输入点未产生位移，下一点补成双倍。
- 两个显示帧后的采样仍重复缺失，因此不是简单的取样过早。QA 临时 Activity 探针确认 36 次窗口输入的坐标和事件时间全部正确。诊断探针和显示等待均已撤回。
- 旧手势入口安装在正在平移的三页槽上。页面跟手移动后，连续输入可能在该节点得到相同局部位置，被 Compose 的移动事件去重过滤，形成间歇停顿与补跳。手动坐标转换无法恢复已经被过滤的事件。

证据目录 D:/03_M1racle Reader/output/drag-sampling-20261031：frame-sampling-summary.json、clock-fixed-summary.json、dispatch-summary.json、对应原始逐帧记录与 instrumentation 日志。

## 开源依据与实现选择

遵循用户要求，先检索和核对维护中的官方开源实现及许可证：

| 来源 | 维护与许可证 | 本轮借鉴与限制 |
|---|---|---|
| [AndroidX Compose HitPathTracker](https://github.com/androidx/androidx/blob/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/input/pointer/HitPathTracker.kt) | AndroidX 官方源码，Apache-2.0 | 源码存在针对局部位置未变化的移动事件优化；当前上游另有修复开关。未升级依赖或设置全局实验开关；将输入节点放到固定视口，避免其坐标系追随纸页。 |
| [AndroidX Espresso MotionEvents](https://github.com/android/android-test/blob/main/espresso/core/java/androidx/test/espresso/action/MotionEvents.java) | Android 官方测试项目，Apache-2.0 | MOVE / UP / CANCEL 默认取事件生成时的 SystemClock.uptimeMillis；测试夹具沿用同一时基，保留原路径和最小发送间隔。没有复制第三方实现或引入新依赖。 |

生产路径改为固定的 PdfHorizontalPagedArea 根视口拥有拖动、缩放和点击，三页槽只负责绘制与平移。既有手势控制器、槽轮换、缩放、惯性和提交算法保留；删除已无调用的移动槽坐标换算。旧换算的 JVM 测试改为验证固定视口连续 36 步在平移中严格保持 1:1，仍为零容差。

## 修复前后对照

仅固定视口路由修复后，保留实际时间戳和原始即时采样，REAL_1X_HOLD_NO_TWITCH 原断言通过：36/36 输入点均有位移，0 个遗漏，终点 -691.1992。没有增加等待、重复计数、减少轨迹、降低至少 30 帧要求，拖动增益仍要求 0.97..1.03、反向抖动和持指提交计数仍要求 0。详见 original-assertions-native.txt 和 original-assertions-summary.json；后续最终整轮须单独验证，不能用该诊断替代。

首屏底图、可见邻页和错误重试行为继续沿用 [91 首屏实现](PDF_FIRST_FRAME_20261031.md)。真机安装及人工验收没有沿用历史结论。

## 候选 92 最终原生整轮

冻结源码后重新构建隔离 QA，在 Android 35 / Suanming_Pixel7 上执行完整相关整轮，另加入 Activity 配置重建项，共 **39/39 通过，0 失败，0 跳过**，耗时 167.127 秒。没有拼接先前诊断结果。覆盖首屏 4 项、实际渲染恢复、瓦片像素、选字批注、横向三页槽、手势生命周期、数据隔离和配置重建。

原始输出 native-final92.txt 明确报告 OK (39 tests)；native-summary.json 同时检查全部 39 个完成状态，不能仅以 adb 退出码为通过。最终整轮的逐点记录独立保存在 final92-drag-evidence.txt，最后一组 36/36 移动、0 遗漏、终点 -691.1992，摘要为 final92-drag-summary.json。先前实验和旧失败继续保留，未覆盖证据。

最终同源全量 QA 单测 1880 项、Release 单测 1717 项，均 0 failures / 0 errors，各保留 2 项既有环境跳过；lint 0 errors / 179 warnings / 6 hints。使用 reader-build-local.ps1 -Release 串行执行测试、lint 和 assembleRelease，最终构建耗时 12m 59s，日志 final-serial-checks.log。没有用隔离 QA 替代生产变体检查。

全量单测首次发现 R7H11b 的源码结构检查仍要求旧 slot.stableId / slot.role 手势入口，1880 项中仅该项失败。该检查已按新架构更新：精确断言两个入口的全部 key，断言它们位于三页槽循环之前，继续禁止位图、瓦片、缩放进入重启 key；没有删除检查或改成无条件通过。其余生产与 AndroidTest 源码摘要未变，详见 native-source-consistency.json。首次失败日志单独保留在 contract-adaptation-first-checks.log，最终全量重新执行。

## 正式产物与同源门禁

- 交付 D:/03_M1racle Reader/apk/M1racle-Reader-1.0.0-92.apk，27638646 字节（26.36 MiB）；91 文件保留。
- SHA-256：5FCFC2757DFCA552FB5B51EEA1650C5D13B62A1BBC86236E95E911A2F866AE24。
- 生产证书 SHA-256：2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1；签名、16 KiB 对齐、R8 / 资源压缩、35 个 assets 字节核对通过。
- Manifest 包名 com.m1racle.reader、versionCode 92、minSdk 31 / targetSdk 35、无 INTERNET、非 debuggable；保留 profileable shell=true。
- 661 个编译输入摘要在最终构建和验证间不变；构建、Outbox 和交付 APK 摘要一致。验证脚本要求最终完整 39 项全部通过，已移除 91 的既有失败例外，详见 verification.json。

## 正式包模拟器验证与边界

Android 35 模拟器从正式 91 保留数据 install -r 升级到 92，firstInstallTime 仍为 2026-10-03 06:05:17，文库和既有合成 PDF 的 2/3 位置保留。安装包及模拟器 Download 与构建、Outbox、交付摘要全部一致。没有卸载或清除生产包数据。

正式包深色模式重开三次，截图显示完整目标页及可见邻页；录屏抽帧未观察到白色亮屏、孤立瓦片或邻页空洞。横向实际滑动验证 2 → 3 → 2，正文点击能收起控件，双击放大及恢复 1x 有实际截图证据，再切回连续模式与 2/3。详见 release-emulator-smoke.json、release92-dark.mp4、release92-horizontal.mp4、相应 UI 树和抽帧图。该文件是既有合成测试 PDF，不是用户原书；本轮不声称在原书或手机上复验。

冷启动模拟器曾出现 System UI 无响应，选择等待后恢复；记录为模拟器启动问题，Reader 进程崩溃缓冲为空。测试后夜间模式 no、方向 0/0、字体比例 1.0 和连续阅读方式已恢复，模拟器正常关闭并保留数据。

状态 READY_WITH_LIMITATIONS：本轮首屏、横向输入与同源交付范围完成；物理手机未连接，手机安装、手机 Download、人工手感和 TalkBack 等专项验收 pending。模拟器不能代替这些结论。未提交、推送或公开发布。91 状态保留在 [归档](history/CURRENT_STATUS_91_before_input_routing_20261031.md)。
