# 放大透镜内层底栏（2026-10-11，59版）

## 用户纠正与实现

用户指出参考按压放大时，透镜内部还包含底栏，而Reader缺少这一层。目标是补上内层底栏，不是去掉它。最初将用户的话理解反了，纠正后按此目标实现。

对照固定提交65ab177e90e5c1d8c62e70cf7755841982da65f6的[LiquidBottomTabs](https://github.com/Kyant0/AndroidLiquidGlass/blob/65ab177e90e5c1d8c62e70cf7755841982da65f6/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt)：上游外部透镜采样原页面和一个较矮、带完整材质及前景的隐藏底栏。此前Reader采样可见底栏与独立图标行，缺少这个较矮底栏的轮廓。

新增DockLensContent角色，仍由唯一GlassMaterialEngine渲染。保留56dp可见底栏，单独记录48dp内层底栏及图标行；内层与页面合成供48→约67dp外透镜采样。内层8dp模糊和中性40%遮罩沿用Dock，按压折射高度/量渐变到24dp，高光渐变到1，图标保持清晰。外层仍10/14dp折射和色散，拖动、小数位置补偿及回弹保持。

隐藏来源只采样页面root；材质在捕获遮蔽域之外解析，捕获内容不包含外部透镜，避免循环。原页面在较矮内层底栏之外参与外透镜采样，符合上游叠层。此说明更新58版“仅处理后底栏+前景”的旧拓扑。

Room17、阅读和导入语义不变；versionCode59，无新增依赖。没有承诺静止凸出或按压2倍高，也没有改主题强调色。

## 验证证据

- 定向JVM67项全部通过，涵盖运动、角色隔离、拓扑和光学策略；API35原生11项通过，含72帧拖动，原1px容差未变。日志：output/dock-inner-rail-20261011/native-tests.txt。
- 同背景按压对比inner-rail-before-after.png：上方QA58，下方QA59。新版透镜内出现较矮底栏的圆角轮廓及折射边缘；条纹夹具会产生彩色干涉环，不能代替真实页面与参考视频的完整视觉验收。
- QA完整页面与Harness启动出现无响应，保留harness-dark-held.png与harness-logcat.txt。未因组件11项通过就记作完整QA页面通过，原因尚未确认；58版已有QA启动问题。
- 正式Release及实际页面检查已完成，结果见下文。物理手机曾离线，构建完成后重新连接，后续真机结果见下文。不清除任何数据。

58版历史状态见[快照](history/CURRENT_STATUS_58_before_inner_rail_20261011.md)，58版材质/精度工作见[报告](DOCK_FROST_20261011.md)。未commit/push。

## 正式包与真机结果

Release59签名构建成功，lintRelease为0 errors / 198 warnings / 4 hints。首次构建JVM因native memory allocation不足中断（build-memory-error.log）；关闭本轮模拟器后使用单并行重建成功，未改源码绕过检查。

APK 27,131,998 bytes，SHA256 `adfb2ccf108946d8fd315c976918b4dd909f2cdfc9559440d18c8ac1f3c3ac4f`，快照m1racle-reader-release-v1.0.0-20261011-211545.apk。证书SHA256 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`；16KiB对齐通过，versionCode59/min31/target35/无INTERNET。构建、Outbox、物理手机Download摘要一致。

手机构建末段恢复连接，正式59版保留数据覆盖安装成功，原有书库和继续阅读可见；首次冷启动190ms，日夜录制启动137/139ms。正式包浅色/深色各完成按住、往返拖动和回到首页；实际裁剪图release-light-held-crop.png / release-light-drag-crop.png / release-dark-drag-crop.png中可见内层底栏上、下缘及拖动折射，release-dock-motion.gif为本次59版真机深色录屏裁剪。不同背景和强调色，不宣称逐像素匹配参考。

导入选择器为该手机com.android.fileexplorer，打开后取消返回Reader首页，原数据保留。release-smoke.json / release-verification.json及末态XML记录结果。

真机原生11项全部通过（phone-native-final.txt，26.276s），保留原1px容差。最初调用了不存在的测试类，随后纠正；首次正确调用因QA后台启动MIUIOP10021=ignore受阻，中断后临时允许再跑，全套通过。结束恢复ignore；手机系统日夜恢复auto，临时录屏/截图已拉取后清理，正式APK保留Download。错误和重试日志保留，不抹去失败尝试。真机QA Harness可见；模拟器QA启动无响应仍单列，不断言其原因已解决。

本轮没有清除数据、卸载应用或公开发布；未commit/push。
