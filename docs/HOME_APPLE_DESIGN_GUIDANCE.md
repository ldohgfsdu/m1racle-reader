# 首页设计改进（2026-09-15）

## 范围与依据

用户提供 Apple Design 并要求改进。沿用 HOME_LAYOUT_POLISH 的首页范围与现有 Android Compose 组件、品牌文字样式。
参考 [Apple UI Design Dos and Don’ts](https://developer.apple.com/design/tips/) 的可读性与触达原则，以及 [Buttons](https://developer.apple.com/design/human-interface-guidelines/buttons) 的清晰操作标签建议。

只读检查确认：此前空状态只有提示文字，唯一导入入口为顶栏加号；空状态内容无法滚动。没有当前设备截图，因此不声称完成视觉审查。

## implemented

- 空首页在说明后增加“导入文件”按钮，复用现有 SAF 回调及 isImporting，导入时两个入口一致禁用。
- 使用既有 title / body 文字样式、居中说明和 24dp 页面边距。
- 空状态在受约束的安全视口中滚动，支持两倍字号和矮屏下触达操作。
- 空状态标题和“最近阅读”增加 heading 语义，便于辅助技术识别结构。
- 不增加依赖；不变更阅读、数据、导入业务规则。

## 验证

风险：低（局部界面与既有回调接线）。

- automated_verified：首页 QA 定向测试 39 tests / 0 failures / 0 errors / 0 skipped；新增导入触发及忙碌一致性、两倍字号矮屏滚动触达回归测试。
- 实现及测试文件检查无新增空白错误。
- device_verified：NOT_RUN；本轮唯一 ADB 探测为空。自动化布局断言不代表真实手势、TalkBack 或视觉验收通过。
- pending：深浅色观感、真实横屏/字体缩放、TalkBack 标题导航与 SAF 导入体验。
- 构建和当前候选结果以 [CURRENT_STATUS.md](CURRENT_STATUS.md) 为准。Release 签名环境未注入，本机签名凭据目录为空。

证据：`.external_outbox/home-apple-guidance/`。
