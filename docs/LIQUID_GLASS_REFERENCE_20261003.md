# 液态玻璃参考与底栏调整

本页是初次参考阶段记录。后续按压、图标采样和移除底部铺层的当前实现见 [悬浮修正](FLOATING_DOCK_20261003.md)。

参考 [RiseDiary 底栏源码](https://github.com/sky-shunfengjun/RiseDiary/blob/master/app/src/main/java/com/risediary/app/ui/components/LiquidGlassBottomBar.kt)，本机只读参考提交 `95d3f362a3fa8d95d8971b0f3c0f4081c95443f1`；查看其首页截图，未安装或运行该 App。GPL-3.0 项目仅用于视觉和行为参考，本轮未复制其源码或改变依赖。

Apple [Meet Liquid Glass](https://developer.apple.com/videos/play/wwdc2025/219/) 说明透镜、触摸响应、运动及背景适应是材质的重要组成，材质主要服务导航和控件层。[SwiftUI 新设计说明](https://developer.apple.com/videos/play/wwdc2025/323/) 展示共享采样区域和交互形变。本项目以 Android Compose / 既有 Backdrop 实现近似效果。

## 实现

- Dock：8dp 模糊、0.14 透色、24dp 透镜高度 / 折射量。保留四个导航入口、68dp 高度与 16dp 侧边距。
- 选中胶囊静止时使用 0.10 灰色覆盖，不显示额外透镜、高光或阴影；按压或拖动时移除覆盖，启用 10dp / 14dp 透镜、交互高光、8dp 内阴影及色散。
- 材质接收实际导航按压 / 拖动状态，保留原有弹簧移动与拾起形变。光学降级保持无透镜、无色散。
- 未实现 RiseDiary 的捕获图标放大折射、速度驱动形变或 Apple 全套背景自适应 / 滚动收缩。纯色背景下折射视觉较轻，不声称与原生 Liquid Glass 等效。

## 验证与交接

JDK 17：`:app:testQaUnitTest`（material / hub / home）113 项，0 失败 / 错误 / 跳过；`:app:assembleQa`、`:app:lintRelease` 均退出 0，Lint 0 errors / 156 warnings / 4 hints。没有在本轮重跑全套。

Android 15 模拟器 `emulator-5554` 安装隔离 QA 包成功；四入口切换、浅深色、深色 200% 字号检查通过，按住选中书签入口的截图确认高光和形变。完成后恢复浅色、100% 字号、首页。捕获日志未见 FATAL EXCEPTION；未进行真机或性能验收。

证据与恢复信息位于本机外部账本 `~/.devhub/runs/unmanaged-reader-glass-reference-20261003/`。会话 unmanaged，受监管身份 unknown；Git 分支 `codex/fix-project-audit`、HEAD `a314247d9cc27f7b3adc1edb3074b65eee3afbfe`，全部修改未提交。保留之前审查及排版修改。

QA SHA-256：`40debba93e9730296799e2c7e843bf230e30f4a2879dd0f2e20b851a4368b670`，已与模拟器安装包一致性核对。QA 不是正式交付包；正式签名 Release 与真机验收仍待完成。
