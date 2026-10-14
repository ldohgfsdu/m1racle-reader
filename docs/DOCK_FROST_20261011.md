# Dock 材质校准（2026-10-11）

## 范围与依据

延续用户“继续推进”的授权，对齐四段视频中底栏背后内容的糊化与中性明暗，不改变按压几何、跟手、底栏牵动或蓝色导航强调色。参考是酷安底栏，Snombile 是视频中的作者昵称。

旋转校正后的参考与57版关键帧位于 `output/ref-video-compare-20261011/recheck/`。不同页面文字和背景不相同，不能据此给出精确模糊半径。旧报告中的“静止透镜凸出”“按压2~2.5倍高”“蓝色透镜底”不作为本轮实现依据。

采用已固定版本的 [Kyant LiquidBottomTabs](https://github.com/Kyant0/AndroidLiquidGlass/blob/65ab177e90e5c1d8c62e70cf7755841982da65f6/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt) 作为材质起点：底栏模糊4→8dp；浅色从白色32%调整为#FAFAFA40%，深色从#24242422%调整为#12121240%。未照搬上游的底栏尺寸或边缘折射高度。

## 实现边界

- 仍通过唯一 GlassMaterialEngine 和角色配方实现；仅拆分 Dock 参数，没有增加引擎或依赖。
- 背景先在底栏处理，透镜再采样底栏与独立清晰前景；透镜自身不增加模糊，避免糊掉图标与标签。
- PDF Control/Capsule、菜单和其他面板配方不变。回归保留边缘、阴影、交互与拓扑约束，并明确检查角色隔离。
- 中性透镜、蓝色图标及标签保持；色散开关和透镜10/14dp按压折射保持。本轮没有独立调低色散强度，实际边缘效果待画面复核。
- versionCode58；数据库仍Room17，无数据迁移。

## 验证

最终源码检查与模拟器结果见下文。物理设备ADB当前离线，不继承57版的真机验收为58版结果。

旧版模拟器基线：API35，QA57，原生条纹模糊/前景清晰/按压越界测试1项通过；像素保存于 `output/dock-frost-20261011/before/`。

## 拖动精度补充

API35模拟器1080px宽度下，57版与材质初稿均在第36次移动出现 `741.73486 → 742.7349` 的误差，超过原1px容差。根因是布局Offset只接受整数像素，分区宽度与图层边界含小数；本轮将布局取整余数交给同一个采样感知图层，RTL按相反方向补偿。保持手势语义、形变、回弹、采样来源及命中区域，未扩大容差或改变测试断言。

最终源码定向JVM54项通过；API35模拟器原生11项全部通过，包括72帧往返拖动、按压越界、回弹、日夜清晰图标和背景透光。原生测试使用系统浅色作为默认夹具，日夜专项各自显式指定主题；首轮系统深色导致白底条纹夹具的灰度阈值失败，另有一次提交帧超时，未改动阈值。首轮、复查与最终日志均保留，最终日志为 `output/dock-frost-20261011/native-tests-final.txt`。

同场景对比：`output/dock-frost-20261011/frost-compare.png` 上方57版，下方最终源码QA58，均为系统浅色/同一条纹背景/按住首页。用于检查底色均匀度、前景清晰与边缘效果，不能证明不同页面文字的主观糊化完全匹配参考视频。最终像素位于 `output/dock-frost-20261011/final/`。

物理设备仍未连接。视觉复刻最终验收pending，不把模拟器结果写成真机通过；色散未单独调整。

## 正式包与交付

最终签名Release58构建成功，完整lintRelease为0 errors / 198 warnings / 4 hints。证书SHA256 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`，16KiB对齐通过，包名com.m1racle.reader，min31/target35/compile37，无INTERNET。

APK 27,131,998 bytes，SHA256 `4d4fcbe2ca4d444e738fa2bf3ff26ce17f7acc4b63fd8d5a143016408ee38b9b`；快照 `m1racle-reader-release-v1.0.0-20261011-204526.apk`。构建、Outbox与模拟器Download摘要一致。物理手机下载/安装pending。

模拟器从正式51版保留数据覆盖安装至58版，原有书库和继续阅读可见；正式包日夜往返拖动、导入文件选择器打开与取消通过。冷启动返回5109ms，仅记录当时模拟器结果，不作真机性能结论。录屏release-light-motion.mp4 / release-dark-motion.mp4与底栏动图release-dock-motion.gif；UI树确认两次往返及导入取消都回到首页。

QA完整应用启动在构建期间发生Process failed to complete startup的ANR；构建结束后再次冷启动返回成功9333ms，但UI树未能读取。组件测试11项通过与完整QA应用启动分开记录，该启动问题pending；正式Release界面及交互已确认。没有把宿主机负载推断写成已确认根因。

恢复模拟器原系统深色模式yes；没有改变QA权限，也未清除任何生产/QA数据。未commit/push。
