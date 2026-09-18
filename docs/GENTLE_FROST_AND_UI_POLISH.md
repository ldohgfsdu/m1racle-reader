# 轻折射材质 B 与界面打磨

2026-09-06。用户选择设置页方案 B，要求所有同类悬浮控件统一，并打磨不符合设计风格的细节。本轮承接原工作区修改，不创建发布目标。

## 实现

- GlassMaterialEngine 的共享底板使用 B：折射高度 4dp、折射量 6dp、无色散。保留 8dp 模糊、浅色 `#FAFAFA` / 深色 `#121212` 的 0.40 染色层、vibrancy、8dp 阴影和 0.5dp 高光。
- Dock 取消独立的强折射和高光覆盖；Control / Capsule / Menu / SettingsCard 与 Dock 使用同一底板。对应首页加号、PDF 返回/更多/标题胶囊、菜单与嵌套面板等既有调用。
- Dock 选中层采样的是已磨砂底板和清晰图标，因此不二次模糊；按压折射改为共享 4/6dp，并关闭色散。保留选中位置、按压形变、清晰图标及采样层级。
- Dialog 独立窗口不能采样，保留 0.97 遮盖和阅读对比度；折射参数同样不超过 B。自定义展开轮廓仍受既有透镜支持范围约束。
- 删除设置页 A–E 对比区、预览配方/渲染入口及其专用交互测试；保留已选 B 的生产材质回归，不保留废弃方案运行路径。
- PDF 返回按钮与继续阅读卡接入已有材质按压反馈；首页、文库、书签实色卡片使用带裁剪的标准可点击 Surface，提供可见点击反馈。
- 文库筛选改为品牌色胶囊；进度条复用首页既有 ReadingProgressBar，并为共享进度条补充读屏进度语义。
- 文库文件大小、书签日期使用完整语义次要文字颜色，取消额外 0.55 淡化。
- 文库/书签/设置共用 Dock 内容避让尺寸；文库与书签同时应用系统导航栏 inset。

## 验证范围

中风险：共享材质和多个页面的控件呈现/反馈变化。完整 QA 单元回归覆盖既有行为；材质回归新增所有角色、浅深主题、按压/展开组合的无色散与轻折射上限检查。

ADB 一次探测为空，device_verified: NOT_RUN。真机待确认：B 的边缘观感、按住底栏、PDF 控件/菜单、卡片点击、文库筛选与进度条、底部最后一项避让。源码检查不能替代上述主观和真实设备验收。

证据目录：`.external_outbox/gentle-frost-20260906/`。

- implemented：上述材质与界面改动已实现。
- automated_verified：完整 `:app:testQaUnitTest` 1411 / 0 / 0 / 0；`git diff --check` 通过。对比功能已删除，因此删除其专用测试；生产材质与采样回归保留，并新增全状态无色散检查。

签名 Release 构建成功，APK 6,702,410 bytes，SHA-256 `d6e4d1227b9ed65980986c2e0b98d091c43ab5edb1f4e07d29070abac6f2f5c1`，production 证书验证通过。已覆盖 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`，与 outbox 摘要一致。构建仍出现已记录的 lintVital ASM / NegativeArraySizeException，不声明完整 lint 通过。device_verified: NOT_RUN。
