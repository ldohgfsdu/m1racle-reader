# 透镜内底栏比例与左右端帽 — 2026-10-12

> 历史68版：端帽比例检查已通过，但未满足用户随后提供的放大视频。当前69版补齐真实前景放大、连续宽折射与去灰色叠加，见[重新对照报告](DOCK_VIDEO_RECHECK_20261012.md)。

用户继续指出67版透镜内的底栏偏大，最左、最右入口不能完整包住底栏端帽。用户给出的深色参考中，内部端帽距镜片端部约10dp，而67版扩张后真实底栏的端部几乎贴住镜片边缘。上一轮“内部边缘存在”的检查没有约束边缘之间的留白，不能代替此项验收。

## 原因与实现

67版将按住后已横向扩张16dp、同步纵向放大的整条Dock直接作为镜片输入；相同画面再经过镜片边缘折射，内部轮廓过满。端部是最明显的情况：56dp底栏的拾起扩张将左右端向外移约8dp，吃掉原本约10dp的透镜包覆余量。

68版保留实际外部底栏的伸展、4dp随动、56/48/67dp几何和速度形变。在透镜采样真实材料时消除这一次整栏拾起放大，采样比例为`1 / (1 + 16dp / railWidth × press)`；纵向再随按压渐变至静止镜片高度/栏高，常规按住时实际材料的光学高度为48dp，再由既有折射处理。变换支点使用真实栏中心在镜片逆变换坐标中的位置，覆盖左右端、途中和RTL。外部实际材料仍按原规则绘制，没有另画一个更小的内栏。

捕获结构改为真实材料、四个真实图标/标签、真实中央导入各捕获一次。只有材料采样做尺寸修正；四个前景仍通过同一像素染色，中央加号使用原色采样，避免缩小前景造成双图标或遗漏加号。仍只有一个可见Row。共享折射、光谱边缘和回弹配方沿用67版。

使用既有Kyant的[Backdrop绘制包装](https://github.com/Kyant0/AndroidLiquidGlass/blob/65ab177e90e5c1d8c62e70cf7755841982da65f6/backdrop/src/commonMain/kotlin/com/kyant/backdrop/backdrops/Backdrop.kt)与[LayerBackdrop逆变换](https://github.com/Kyant0/AndroidLiquidGlass/blob/65ab177e90e5c1d8c62e70cf7755841982da65f6/backdrop/src/commonMain/kotlin/com/kyant/backdrop/backdrops/LayerBackdrop.kt)，没有新增依赖、替代底栏、隐藏Row或自定义光学shader。

## 验证状态

- implemented：上述采样修正，候选版本1.0.0 / 68，Room仍17。
- automated_verified：定向JVM117项全部通过；Release lint 0 errors / 198 warnings / 4 hints，正式签名构建完整成功（12m24s）；证书、16KiB、min31/target35、无INTERNET及生产源码摘要连续核对通过。
- device_verified（API35模拟器隔离QA）：最终25项原生检查全部通过（115.955s）；相同最终测试APK在旧67版4项端帽回归全部失败。
- device_verified（正式68包/API35模拟器）：日夜左右按住截图、半覆盖即时切色、连续跨栏/快速往返、停住和松手、末态首页selected及导入取消通过。已查看`reference-and-endcaps.png`、`release-endcaps-all.png`和正式录屏关键帧。宽375.65dp接近参考375.38dp，结束恢复原420密度和夜间no；首次安装时间保持2026-10-03 06:05:17，无主包崩溃记录。
- pending：物理手机连接和用户参考观感验收；未全量重验阅读等积压功能。

新增4项原生像素回归分别检查日夜、左右端：镜片边缘前6dp不参与匹配，要求内部弯曲端帽在6–16dp区域内形成独立局部亮度峰，覆盖中心±8dp范围内60%以上行；上下轮廓在6–12dp内独立显露，覆盖中心±8dp范围内60%以上列。双侧亮度差仍大于.08。旧67版使用同一最终测试APK做负例。保留原21项检查，包括局部变色、实际内边缘、色散、快速缩进及停止外扩。

第一轮设备结果23/25：新增端帽4项均通过，原有内轮廓检查的2项仍在距离外缘2–12dp的旧横向窗口查找，无法完整覆盖此时已内移的端帽（411dp宽设备中心两侧42行只找到20/16行）。保留`native-initial.txt`和`initial-pixels/`。该横向窗口改为6–16dp，宽度仍10dp，双侧对比及60%覆盖阈值不变；排除外缘的范围更大，旧67版已在同一新窗口4/4失败。纵向进一步缩小真实材料采样，并新增上下6–12dp独立留白约束。

证据目录`../../output/dock-cap-fit-20261012/`。首轮默认并行构建进程内存不足退出，日志保留为`qa-build.txt`；使用单worker和进程内Kotlin编译控制内存，后续结果单独记录，失败不计为通过。

## 正式产物

1.0.0 / 68，`m1racle-reader-release-v1.0.0-20261012-200342.apk`，**27,131,998 bytes**，SHA256 `61a5354562bd7b0bc767e8e8e49a50d1a86f128fdf4757da1a1b2d5d93e04cbb`。正式构建、Outbox、模拟器已安装APK和`/storage/emulated/0/Download/m1racle-reader-release-latest.apk`摘要一致。物理手机未连接，真机交付及观感验收pending。没有commit/push。

最终证据见`verification.json`、`native-final.txt`、`baseline67-native.txt`、`jvm-results/`、`signature.txt`、`alignment.txt`及正式场景原始截图/录屏。默认并行构建与后续测试worker曾因系统提交内存不足退出；保留`qa-build.txt`和`qa-build-final.txt`。`qa-build-retry.txt`记录测试fixture未更新函数参数造成的编译错误，已修正测试调用，断言未放宽。暂停本任务模拟器后顺序构建、重新启动无快照加载的同一AVD，再执行最终设备检查。

最终原生像素证据为`verified-native-final/`。中间两次向已存在目录拉取产生了`verified-pixels/files/`嵌套，旧的根目录图片不是最终结果，保留作过程证据。正式包首次两轮场景脚本在UI树未就绪时取到了旧系统提示树而中断；最终脚本使用唯一远端文件并验证dump成功，保留`post-build.txt`、`release-scenes-retry.txt`与成功的`release-scenes-final.txt`。旧系统提示树不能当作本轮Reader崩溃或新的SystemUI ANR证据。主包的最终崩溃缓冲、版本、摘要和首页状态已独立重查。
