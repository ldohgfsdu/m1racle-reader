# 阅读器展开菜单底板加厚

2026-09-06。用户反馈三点展开面板面积较大，现有底板过透。

## 实现

实际调用链：ReaderMoreHost 收起调用 Control，展开主面板调用 ExpandedMenu，嵌套面板由 AnchoredNestedPopover 调用 SettingsCard。ExpandedMenu / SettingsCard 共用集中配方 panelFrost：浅色 alpha 0.72，深色 alpha 0.78，blur 16dp。此前为 alpha 0.40、blur 8dp。

保持 B 的折射高度 4dp、折射量 6dp、无色散，以及既有染色颜色、高光和阴影。沿用父菜单导出、子面板 RootAndMenu 采样，不增加叠层或采样路径。

为避免影响首页继续阅读卡及紧凑工具菜单，展开阅读器面板使用独立语义角色 ExpandedMenu；紧凑 Menu、Control、Capsule、Dock 仍保持 0.40 / 8dp，Dialog 仍为 0.97。

## 验证

定向验证材质数值、展开层级、紧凑控件保持 B、所有状态无色散、采样拓扑与阅读器调用角色。现有断言按本次授权的面板参数更新，保留定值/层级约束。

ADB 一次探测为空。device_verified: NOT_RUN。待实机查看浅深主题下正文透出程度、主菜单及二级面板清晰度、展开收起观感。

证据目录：`.external_outbox/expanded-menu-20260906/`。

implemented：上述底板变更。automated_verified：GlassMaterialRegressionTest / GlassBackdropTopologyContractTest / ReaderChromeMaterialTest / ReaderChromeTokensContractTest / R25ReaderFoundationTest 共 49 / 0 / 0 / 0；git diff --check 通过。本轮未重跑全量，前一轮全量 1411 项的证据仍仅属于前一轮。

签名 Release 构建成功，6,702,410 bytes，SHA-256 `5c207627dee5e23f2c1ac95807ef77bc8f1633fb2be96b86d360b4c0cb04d331`；production 证书核对通过，Download 与 outbox 一致。已有 lintVital ASM 异常仍出现，不声明完整 lint 通过。
