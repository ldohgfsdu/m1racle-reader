# 首页实机问题修复与排版整理（2026-09-11）

用户授权修复实机审查全部问题并改善首页观感。参考实际设备截图 `.external_outbox/home-audit-live/`，保留现有白/灰配色与阅读入口，不引入依赖或新的视觉素材。

## implemented

1. HomeContent 固定拥有顶部状态栏与底部导航安全区域；LazyColumn 只占剩余空间并裁剪绘制，修复滚动时的上下文字重叠。新增行为测试检查视口上下界、滚动前后顶栏位置。
2. 搜索与导入置于固定顶栏，删除悬浮加号和对应留白常量，避免覆盖列表操作区。保留导入防重复和状态播报。
3. 继续阅读标题 20sp/Medium，普通文件名 15sp/Medium，进度使用 12sp caption。普通标题允许两行；PDF 显示标题省略扩展名，真实文件名不变。
4. 继续阅读标签与按钮不再共用占高 48dp 的行；按钮独立对齐右上角，标题与进度保持紧凑的垂直关系。
5. 增加独立“文件信息”入口，完整文件名可选择复制，格式和已知大小可查看；无需进入移除流程。
6. 移除手工 Popup 的整行锚点和自绘菜单行；复用平台 DropdownMenu，在按钮自身的 Box 中定位。沿用不透明表面及轻阴影、移除确认组件。

状态层导入互斥、事件排队、删除事务及失败恢复未改；列表行是主页与文库的共用组件，两个入口保持同一排版。PRD 已同步当前行为。

## 验证检查点

- 主页定向测试通过，包含新的 HomeViewportUiTest、独立信息查看、取消及移除确认。旧布局契约按新行为替换，由视口真实几何测试证明系统区隔离，没有跳过断言。
- automated_verified：最终全量 QA 单测 **1484 / 0 / 0 / 0**。新增视口/导入入口及信息查看行为测试，更新旧 FAB 动效与玻璃菜单接线契约，未跳过测试。生产签名 Release 与 lintVitalRelease 成功，源码摘要匹配。完整 lintQa 未重跑，不能视为完整 lint 通过。
- ADB 原地址 198.18.0.1:41681 已失效，连接返回 Connection refused；已请求新的端点。此前截图只作为修改前证据，不作本轮实机验收。
- 待验收：同页顶端/滚动/末尾截图、菜单锚点、信息查看、取消返回、搜索与导入触达、字体/深色/横屏与实际观感。
- 证据目录 `.external_outbox/home-polish/`，保留修改前文件快照及测试构建日志。

## 交付（2026-09-12）

- Release：6,936,018 bytes，SHA-256 `c8032c071a4685198f1379de51b6475f26c6f9fd85b384d24bede5775e7ba909`。
- production signer SHA-256：`2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`，验签通过。
- 已自动覆盖 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`，与 Outbox 摘要一致；校验记录见 `.external_outbox/home-polish/apk-verification.json`。
- device_verified：NOT_RUN（新版本）；无线 ADB 端点失效，尚未安装及验证本轮界面。
