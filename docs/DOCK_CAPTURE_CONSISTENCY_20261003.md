# 四入口玻璃底栏采样一致性修复

用户截图显示首页底栏白色，文库 / 书签 / 设置整条偏灰。此前悬浮调整未解决这一问题，不能以首页截图作为四页一致性的证据。

## 根因与改动

首页自身绘制背景，其余 Hub 页面透明，窗口中能看到的白色不在被捕获的 RenderNode 内。光学处理透明空像素后呈灰色。与按压状态、选中入口的配方无关。

`NavGraph` 的共享 `hubBackdrop` 现在用稳定的 `onDraw`，先记录主题的不透明页面背景，再记录页面内容；覆盖四入口和页面淡入淡出期间的空白。未给悬浮底栏下方增加 UI 铺层，没有改变阅读器材质与导航所有者。

参考 [Apple Materials](https://developer.apple.com/design/human-interface-guidelines/materials)：玻璃构成浮在内容上方的导航 / 控件层，应保持内容与标签可读；regular 用于需要保证可读性的场景，clear 主要适合视觉丰富的媒体背景。参考 [Adopting Liquid Glass](https://developer.apple.com/documentation/TechnologyOverviews/adopting-liquid-glass)：减少会干扰材质的自定义背景，核对不同显示设置。这里补全的是内容采样中缺失的页面底色，没有添加覆盖玻璃的背景。

Android 继续以 Compose / Backdrop 近似光学效果，不声称使用 Apple 原生组件。纯色空白页面下效果较轻，仍保留既有按压透镜、蓝色采样图标与拖动形变。

## 验证

JDK 17：material / hub / home / navigation QA 单测 **117 项，0 失败 / 错误 / 跳过**；新增共享捕获必须先记录不透明背景的拓扑回归。`:app:assembleQa`、`:app:lintRelease` 退出 0；Lint 0 errors / 156 warnings / 4 hints。未重跑完整套件。

Android 15 模拟器 `emulator-5554` 安装 QA 后，分别进入四页、检查页面标题、等待 UI 空闲再截图；四页截图已核对。浅色玻璃内无图标 / 无选中胶囊的同一坐标 `(540,2180)`：修复前文库 RGB `(233,233,233)`，修复后四页均 `(255,255,255)`。页面空白与阴影外的底部留白颜色一致。深色模式四页也做同位置一致性核对；设置页按住及释放检查通过。结束恢复浅色、100% 字号、首页。

外部账本 `~/.devhub/runs/unmanaged-reader-material-consistency-20261003/` 保存构建日志、四页 XML / 截图、像素断言结果、Git 指纹与安装 APK 摘要。身份 unmanaged，受监管 runId / projectId / machineId unknown，分支 `codex/fix-project-audit`、HEAD `a314247d9cc27f7b3adc1edb3074b65eee3afbfe`。无提交、推送或签名材料操作。

正式签名 Release、真机光学与性能验收仍待完成，QA 不作为正式交付包。
