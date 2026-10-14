# 底栏透镜：细色散、等比采样与上下折射范围

当前请求是在70版PDF面板材质上，收敛静止透镜的色散，按用户新提供的五组酷安/Reader截图修正透镜内底栏的比例和上下范围。用户此次明确要求调整采样与范围，覆盖此前“保留69版透镜”的局部约束；PDF面板材质、真实前景局部变色/放大、输入与回弹保持。

## 根因与实现

- Backdrop 2.0.0的`lens(chromaticAberration = true)`把shader色散系数固定为1。原14dp边缘折射使七个波段的采样位置大幅分开，形成宽彩带；按住停下时折射仍存在，所以彩带不会自己消失。
- DockLens系数改为.08，沿用库的SDF和折射shader，并把折射位移收敛为5dp；两端波段在材质坐标中的最大采样距离为.8dp。色散随实际折射位移趋近零，不加速度开关或计时闪动，不绘制彩虹装饰。静止材料可以有轻微色散，不能把“运动才有色散”称为物理规律。这里仍是屏幕空间光学近似，不宣称完整光线追踪。
- 使用库公开的shader缓存与Android RenderEffect接口，取得`lens()`刚创建的同一shader，设置系数后重建当前光学效果并保留此前模糊/颜色链。Android在创建效果时快照uniform，不能只在效果创建之后改系数。未复制或自研shader，未增加依赖；该适配依赖已锁定的Backdrop 2.0缓存键，原生像素回归保护实际输出。
- 原采样在取消面板16dp扩张后只对Y轴额外压缩，圆形端帽因而被压扁。现在先等比取消扩张，再等比缩小实际捕获的面板，保持原底栏的宽高比例；不再单独重画内底栏。投影锚点从左端到右端连续跟随透镜位置，保证两端可见且不中途跳变。50dp投影高度在68dp外透镜内上下各留约9dp，配合5dp浅折射，防止原14dp折射吞没真实轮廓。
- 这组截图外透镜高度差异较小，主要差异在上下折射带与内轮廓。按住高度由67dp微调为68dp，折射带由10dp到12dp；保留56dp底栏、48dp静止透镜、1.5倍横向提起与速度形变。没有把整体恢复为早期过大的78dp透镜。

物理来源：[UCSD Physics 1C的色散讲义](https://courses.physics.ucsd.edu/2010/Spring/physics1cc/Lectures/S10P1CLec22C.pdf)。库来源：[固定版本lens实现](https://github.com/Kyant0/AndroidLiquidGlass/blob/65ab177e90e5c1d8c62e70cf7755841982da65f6/backdrop/src/commonMain/kotlin/com/kyant/backdrop/effects/Lens.kt)、[缓存](https://github.com/Kyant0/AndroidLiquidGlass/blob/65ab177e90e5c1d8c62e70cf7755841982da65f6/backdrop/src/commonMain/kotlin/com/kyant/backdrop/RuntimeShaderCache.kt)。

## 验证边界

保持原有色散像素下限、端帽对比与留白、折影2%上限、形变、输入等断言；仅将用户本次明确调整的尺寸预期同步为68dp/12dp。新增单色场景下彩边面积小于采样边缘12%的上限、静止无彩带、按住静止1600ms不发生颜色动画，仍需证明有真实轻微分色而非完全关闭。

首次QA构建的lint分析器在未改动的`PillSegmentedControl.kt`中以`SamDetector / Bad parent`崩溃，记录保留，不关检查或伪报通过。最终源码变更后重新构建并独立复验。

第一轮71候选的118项JVM通过，原生22/29通过，7项失败保存在`attempt1/`：等比采样后旧14dp折射吞没真实内轮廓，并将蓝字折进底部。色散上限、静止稳定性、下限均已通过，但这不能视为整体通过。随后修正实际投影范围与折射量，原端帽/折影断言和阈值保留；重新执行同一完整29项。

第二轮38dp投影过小，真实面板缩到文字中间并暴露原始页背景，21/29通过。原生截图与日志保存在`attempt2/`；没有拿第一轮旧截图代替本轮像素，ADB再次拉取会生成`files/`子目录，第二轮实际图片在该子目录。最终修正投影高度为50dp、连续投影锚点与5dp浅折射，原先的透色/背景模糊/折射断言不放宽。

## 最终验证与交付

- automated_verified：JVM118项全通过，Release lint 0 errors / 198 warnings / 4 hints；正式签名构建成功。
- device_verified（隔离QA/API35）：原生27/29通过，2项未通过：lightPickupShowsCapturedRailOutlineInsideLens、lightLeftCapHasClearSpaceInsideHeldLens，129.308秒。同一最终测试APK拒绝70版过量色散，旧版3775/7188个边缘像素超过12%上限；本轮仍保留真实分色下限，未把色散完全关闭。旧的端帽、背景模糊、2%折影、几何与输入阈值未放宽。
- 本轮两个未通过项仍为浅色左端帽的局部亮边对比，16/42行达到原.08局部峰值要求，低于原60%比例；70版对应结果25/42。弱色散后不能把彩边算作轮廓对比，失败保留，不宣称浅色左端对比已解决。其余27项通过。
- device_verified（正式71/API35）：日夜五位置按住、部分切色、跨栏与往返、停住/松手、首页末态及导入取消通过；`reference-vs-71.png`、`before-after-70-71.png`、日夜部分覆盖和录屏关键帧已查看。`light-dock-motion.mp4`、`dark-dock-motion.mp4`是真实录屏。
- 签名[Release](../.external_outbox/m1racle-reader-release-latest.apk) **27,131,998 bytes**，SHA256 `e67a0a9701b0879c627698b1d07c47fd313b4d9ad9bb8f94983be17607e71cde`；快照`m1racle-reader-release-v1.0.0-20261013-002125.apk`。生产证书连续、16KiB、min31/target35、无INTERNET和四份摘要验证通过。
- 保留数据70→71，首次安装时间未变；结束恢复密度420、夜间no和首页，没有主包崩溃记录。物理手机最终未连接，手机安装与本轮视觉/手感仍pending；70版首次浅色黑画面根因未定位。本轮未全量验证其他积压功能，未commit/push。

### 正式采集中的模拟器弹窗

71版第一次正式采集的颜色检查失败，最初误判为黑画面。查看`initial-light-black/light-start-ready.png`与新鲜UI树后确认，Reader已经绘制，模拟器“System UI 无响应”弹窗及其遮罩压暗了整个窗口；目录名保留最初诊断，不代表最终结论。已向用户更正，并依据新鲜UI树的`android:id/aerr_wait`边界点击“等待”。采集脚本修正为先处理该弹窗，再检查实际蓝色绘制，随后日夜采集与420密度恢复后的最终画面检查通过。只调整采集脚本，没有重新修改或构建生产源码。System UI卡顿根因未定位，不能和70版历史黑画面归为同一故障。

证据目录`../../output/dock-subtle-dispersion-20261013/`，汇总`verification.json`，原生`native-final.txt`及`verified-native/`，负向`baseline70-dispersion.txt`，同一测试APK摘要`negative-tested-test-apk.json`。失败的中间候选均保留，不冒充最终产物。
