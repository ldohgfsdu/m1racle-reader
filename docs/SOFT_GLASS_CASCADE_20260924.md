# 柔光面板残余闪烁：逐级模糊验证

用户明确反馈 4px 预滤波版“还会有”，所以此前只算改善，视觉验收未通过。保持胶囊已确认路径，仅处理设置页柔光面板。

## 已确认的实现问题

固定小半径预滤波仍直接进入大半径滤镜；它减弱了输入细纹，但没有约束后续采样跨度。新增测试在原实现上两次失败（API 31 / 34）：`Sampling jump: [2.8094, 18.9752]`。这是实际 RenderEffect 链检查，不是假定 GPU 相差一帧。

依据：

- [Android RenderEffect JNI](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/hwui/jni/RenderEffect.cpp) 调用 `Blur::convertRadiusToSigma` 后传入 Skia。
- [Android Blur.cpp](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/hwui/utils/Blur.cpp) 的换算为正半径 `sigma = radius * 0.57735 + 0.5`。
- [Skia BlurEngine](https://skia.googlesource.com/skia/+/7755e6ab0bc0/src/core/SkBlurEngine.h) 对大 sigma 使用缩小输入的路径，直接卷积的 sigma 上限为 4。厂商设备内部实现未抓取，不把上游代码等同于设备驱动的逐条行为证明。

## 当前实现

面板将目标 Gaussian 方差分配给逐级滤镜：从 sigma 4 开始，后续增长最多四倍，最后一遍使用剩余方差。利用 Gaussian 方差可加性保持总模糊强度；不是继续在原强度上无限叠加模糊。叠加滤镜采样边距改为所有半径之和并向外取整。使用现有 Backdrop / Android RenderEffect，无新依赖或 shader。

本机 H0 的目标半径 104px 分解为约 6.06 / 26.85 / 100.03px。胶囊的单次 blur 和采样边距保持。无模糊模式仍无滤镜、无额外边距。

自动回归覆盖：实际滤镜链首级与后续采样跨度、总方差、叠加采样边距、各配方/主题/密度（含本机 3.25）、胶囊路径和无模糊路径。

## 状态

- implemented：逐级路径已实现。
- automated_verified：旧路径 red 证据已保存；新路径材质测试与 lint 进行中。
- device_verified：设备在线，新 Release 待安装录屏比较。
- pending：最终构建、同场景录屏、用户视觉验收。

证据：`.external_outbox/soft-glass-cascade/`。上轮 4px 录屏在 `.external_outbox/soft-glass-panel/final*.mp4`，作为本轮基线。
