# 视频底栏复刻（2026-10-11）

## 参考与范围

用户提供三段底栏录屏，随后明确指出按压透镜应越过底栏，拖动也应牵动整条底栏，并指定 Kyant0/AndroidLiquidGlass。视频与下载源码均作为参考材料。

上游组件：[LiquidBottomTabs](https://github.com/Kyant0/AndroidLiquidGlass/blob/65ab177e90e5c1d8c62e70cf7755841982da65f6/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt)，固定提交 `65ab177e90e5c1d8c62e70cf7755841982da65f6`。组件属于 catalog 示例，现有 Backdrop 2.0.0 依赖包含底层渲染器；本轮没有新增引擎或依赖。

## 实现

沿用示例的联动参数，将其接入 Reader 四入口和中间导入的手势语义：

- 静止底栏56dp、透镜48dp；按压透镜按78/56比例扩大至66.86dp，越过底栏上下边缘。
- 底栏按压增加16dp宽度，按同一比例放大高度；水平拖动通过EaseOut驱动最多4dp的底栏偏移，松手spring(1,300)复位。
- 透镜按压X/Y分别使用spring(.6,250) / spring(.7,250)，保留自然回弹；速度形变沿用上游有符号速度的横向拉伸/纵向压缩公式。
- 位置直接跟手；命中区域保持固定，按住预览、松手提交，取消返回导航所有者；中间导入不被拖动手势抢占，保留RTL与多指取消。
- 复用上游InteractiveHighlight的径向加亮shader，随手指移动。底栏与透镜折射/高光使用共同按压进度spring(1,1000)，保留色散与内阴影。
- 透镜仅采样已处理的底栏和隐藏的蓝色前景，前景按压放大1.2倍；采样源与消费者保持兄弟关系，避免自采样。
- 导入动作增加透光与上沿反光，按压放大与提亮连续过渡。

上游样例改编已补充Apache-2.0署名，源码与许可文件固定提交编号。保留工作区原有未提交变更；本轮底栏修正不改数据库或阅读行为。正式包会包含工作区已经存在的版本57/Room17变更。

## 验证

automated_verified：最终源码定向JVM53项通过，无跳过；签名Release构建及完整lintRelease通过（0 errors / 198 warnings / 4 hints）。

device_verified：db04f865/API36原生11项通过，含透镜上下越界、底栏随动、72次逐帧往返拖动、光学形变与回弹、日夜像素及取消预览。动态边界改在UI线程读取，避免与图层矩阵更新竞争，逐帧1px容差保持。

正式Release57已保留数据覆盖安装，冷启动253ms，原有书库/阅读记录可见；正式包日夜模式往返拖动与加号文件选择器取消均通过。系统恢复自动日夜模式、QA后台权限ignore。实际录屏release-motion.mp4，底栏局部动图release-dock-motion.gif。

交付27,131,998 bytes，SHA256 `a57b1967df5fdfd4b699df8c6eb4f99fd5615cea91fd4251f3ccb2a0336a9f12`，构建/Outbox/手机Download摘要一致。生产签名连续、16KiB对齐通过，min31/target35/无INTERNET。

边界：本轮针对底栏与共享材质，没有声称全部积压清单或所有阅读场景重验；长期帧率与主观手感仍需使用反馈。未commit/push。

真机像素测试采用帧提交同步，避免读取旧缓冲区。条纹模糊检查使用局部高频差异而非整段亮度极差，区分渐变高光与未模糊的细条纹，保持0.02阈值与灰度透光检查。

构建阻塞修正：Markdown编辑页的持久URI授权只传Android允许的读写常量，避免Lint对动态掩码误报；保持原授权位含义，不抑制Lint。

证据目录：`D:/03_M1racle Reader/output/dock-reference-20261011/`。
