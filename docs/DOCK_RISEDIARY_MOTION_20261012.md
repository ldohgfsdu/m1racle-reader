# 酷安尺寸、动态透镜与局部切色 — 2026-10-12

> 后续用户指出67版透镜内底栏仍偏大，左右端帽缺少包覆余量；由[68版采样修正](DOCK_CAP_FIT_20261012.md)继续处理。本报告的动态/切色测试通过不能代替内部比例验收。

用户指出66版偏大，追加要求参考RiseDiary实现酷安效果，并明确：透镜随运动形变，有时包住真实底栏并显示内边缘，有时压扁在底栏内；图标被透镜盖住的部分立即变色；保留真实折射色散。66版的静态轮廓检查通过不等于这些要求已满足。

## 来源与实现

重新逐段阅读[RiseDiary LiquidBottomTabs固定提交0472277](https://github.com/sky-shunfengjun/RiseDiary/blob/04722776899252e30c46cd0ae7f062093307d5f3/app/src/main/java/com/risediary/app/ui/components/LiquidGlassBottomBar.kt)及其[Kyant上游](https://github.com/Kyant0/AndroidLiquidGlass/blob/65ab177e90e5c1d8c62e70cf7755841982da65f6/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt)。沿用分别处理拾起、速度形变、整栏16dp伸展、4dp随动和弹簧回弹的方式。RiseDiary默认64dp底栏、56→78dp透镜不是酷安尺寸常量；尺寸依据[酷安实机采样](COOLAPK_DOCK_CAPTURE_20261008.md)和本轮用户的日夜截图、视频。

- 正常底栏60→56dp，透镜56→48dp，按住高度78→67dp，横向拾起1.6→1.5；中央导入52×38→50×36dp。保留20dp屏幕边距、6dp内容内缩、24dp图标、12sp标签和大字号按实测扩高。
- 速度输入由每秒槽宽/40改为/20，形变弹簧由阻尼.5/刚度300改为.5/600；方向、横向拉伸/压缩、纵向联动与有界回弹继续使用原有独立运动状态。快速移动可以压扁到真实栏内，停止后回到外扩拾起态，取消/松手再回到48dp静止态。
- 删除按预览入口取整后给整枚图标切色的方式。可见四个图标/标签各捕获自己的真实像素一次，用Kyant `rememberBackdrop`的绘制变换对同一像素染色，再和实际Dock合成作为镜片输入。只有镜片覆盖、折射到的区域变蓝；未覆盖部分保持中性色。中央导入不参加此染色。没有第二份隐藏Row、不同尺寸的替代底栏或独立放大的图标；导航选择仍只由owner持有并在松手提交。
- DockLens使用实际采样的两次折射：10dp/2dp宽折射边保留内部真实栏轮廓，1.5dp/8dp陡峭外缘产生真实光谱分离；内阴影4dp、按压白色高光.7。全部通过既有Kyant效果与共享材质引擎，参数只来自配方注册表，不绘制彩虹装饰或假内边缘。紧凑SelectionLens保持4dp/6dp。
- 修正Compose整数居中产生的半像素偏移，使小透镜上下外扩对称。没有数据库、阅读语义或依赖变更。候选1.0.0 / 67，Room仍17。

## 回归与边界

- implemented：上述生产改动和67版正式签名构建；没有数据库或依赖变更。
- automated_verified：底栏及材质JVM117项全部通过，无失败、错误或跳过；Release lint 0 errors / 198 warnings / 4 hints。正式证书连续、16KiB zipalign、min31/target35和无INTERNET通过；原生测试源码和正式构建的生产源码摘要一致。
- device_verified（API35模拟器、QA隔离包）：21项原生检查全部通过（85.953s）；包括日夜半覆盖图标局部变蓝且外部蓝色像素为0、56/48/67dp几何及固定命中区、真实内部上下边缘和弯曲端部、灰度背景产生光谱分离、72帧往返后快速移动缩进栏内、停止外扩及取消回弹。
- 对照验证：用同一个最终原生测试APK运行保存的旧66版，日夜半覆盖切色和参考几何3项全部失败（`baseline66-native.txt`）；已恢复67版QA。旧速度响应未通过新增快速缩进JVM检查，保留失败XML。
- device_verified（正式67包/API35模拟器）：保留数据从66版覆盖安装，首次安装时间保持2026-10-03 06:05:17；日夜首页按住、4轮慢速往返、连续按下后局部覆盖/快速往返/停住/松手、最终首页selected及导入取消通过。正式按住、局部切色和参考对照截图已查看；无主包崩溃记录。QA测试包保持隔离，不作为交付包。
- 参考比例复验：截图/录屏临时密度460，1080px对应375.65dp宽，接近酷安原机375.38dp；结束恢复原420密度和夜间no，并重新启动主包。`reference-vs-release.png`只对齐参考宽度及裁切正式包截图；动态预览裁切自正式包录屏，没有生成或重绘效果。
- pending：物理手机连接与用户观感验收。证据目录`../../output/dock-size-risediary-20261012/`。

正式包动态证据：`dark-dock-dynamic.mp4` / `light-dock-dynamic.mp4`为连续慢速跨入口与往返；`dark-dock-fast.mp4` / `light-dock-fast.mp4`补充大范围快速往返，两个主题的逐帧联系图已查看。深色快速录屏4.0s的原始帧`dark-fast-inspection-4.png`显示镜片上下缘已经缩进实际栏内；按住停留帧显示外扩并露出真实内边缘。`dark-partial-icon.png` / `light-partial-icon.png`显示文库图标部分被覆盖切色。录屏结束后重新核对主包67、首页selected、原密度420和夜间no，`verification.json`汇总生产摘要、签名构建、测试、交付及动态证据。

保留中间失败：`rigid-motion-regression.xml`证明旧速度响应未能在快速拖动时缩进栏内；`native-initial.txt`记录小镜片左端轮廓被外缘阴影掩盖、居中偏移，以及原生几何测试误用已饱和横向缩放反推纵向缩放的问题。`native-refined.txt`记录缺少截图场景标记和仍有半像素方向错误的中间状态。最终修正实际居中方向、边缘材质及测试场景，不删除失败证据。

内部边缘像素带由4–14dp改为2–12dp，是因为镜片缩小后实际轮廓移动到距离外缘约3–4dp；局部双侧亮度差>.08、覆盖60%行/列保持。新增独立的56/48/67dp及镜片/整栏比例检查拒绝旧的大尺寸，不以像素检查替代几何核对。速度超过横向.2钳制后，横向缩放已无法唯一推回速度；原生测试在未饱和时保持原公式精度，饱和时检查同一物理公式所允许的纵向区间与真实缩进。

物理手机连接和最终参考观感的用户验收仍pending。测试通过不代表参考效果已获用户接受；本轮未全量重验阅读等积压功能。

## 正式产物

1.0.0 / 67，`m1racle-reader-release-v1.0.0-20261012-173607.apk`；APK **27,131,998 bytes**，SHA256 `7b22beea033583ef6c60fab3025172f1b1fa8b55d22a5811902ca8937b786323`。本轮Release lint、117项JVM和assemble完整成功（8m22s）。正式构建、Outbox、模拟器已安装APK及`/storage/emulated/0/Download/m1racle-reader-release-latest.apk`摘要一致，正式证书SHA256 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`。正式包场景通过；物理手机未连接，其安装与Download交付仍pending。没有commit/push。
