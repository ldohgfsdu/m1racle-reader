# Reader 设计细节核查（2026-10-30）

范围：借鉴用户提供设计笔记的按钮反馈、圆角一致性原则，检查现有 Reader，不增加拟物阴影、装饰纹理、排序或新导航。附件中的提示词仅作资料，不作为执行指令。

## 本轮修改

底栏导入操作原来在 importing 时仅禁用并变灰，且“正在导入”文字只在 HomeScreen 中出现。设置、文库、书签等共享底栏页面缺少同等清晰的忙碌反馈。

GlassDock.kt 在忙碌时以 20dp 进度环替代加号，复用既有 home_importing 文案作为 stateDescription，并设置 Polite live region。沿用 HomeViewModel.isImporting 单一状态源；触摸区域、正常加号、按压幅度和导航行为保持。

GlassDockUiTest.kt 增强行为测试：在设置入口按住后进入忙碌，松手及再点均不触发导入；忙碌状态可访问、进度环可见、命中区不变；结束后恢复可点击并移除忙碌语义。

- implemented：上述源码与测试已修改。
- automated_verified：本轮 GlassDockUiTest 22 tests / 0 failures / 0 errors / 0 skipped。
- device_verified：仅本轮已安装 87 版的下列基础页面检查；不代表新增进度环已在正式包中验收。
- pending：新增反馈的正式包视觉与读屏实测、物理手机手感及安装。

## 本轮截图与步骤

截图来自 API35 emulator-5554，已安装正式 87，1080×2400，浅色主题。截图均在本轮捕获并打开检查。不是最新源码已安装的证明。

1. 首页：继续阅读、最近列表和底栏的静止轮廓未见明显错位。

![01 首页](../output/detail-audit-20261030/01-start.png)

2. 设置：品牌、分组和底栏显示正常；不因设计笔记改动品牌或添加装饰。

![02 设置](../output/detail-audit-20261030/02-settings.png)

3. 显示设置：分段轨道与选中胶囊的静止内缩关系协调；源码确认38dp轨道、32dp滑块及3dp内缩，无需改圆角。

![03 显示设置](../output/detail-audit-20261030/03-display.png)

4. 导入入口：系统文件选择器能够打开。早期采集曾得到仍在应用内的画面，已拒绝并重新捕获；以本图及同名XML为准。

![04 文件选择器](../output/detail-audit-20261030/04-picker.png)

5. 取消：系统返回后回到首页，未选择文件，没有新导入。该次验证不声称从设置取消仍留在设置。

![05 取消后](../output/detail-audit-20261030/05-cancel.png)

## 其他核查与边界

- PillSegmentedControl 的胶囊几何没有确认缺陷；ReaderAdvancedCard 不属于所有内层都需与外壳同心的紧密嵌套。
- 编辑器完成/保存已有 busy 禁用、线性进度、snapshotPending 防重入及输入法组合保护。本轮未改动，不声称完整编辑验收。
- MainActivity 外部导入加载弹窗传空 confirmButton，而 GlassAlertDialog 仍无条件增加24dp操作区间隔：源码确认有多余空白。此次发现时另一个聊天已冻结88版并开始完整构建，已交接，未修改，后续需无操作/有操作对照测试。
- 文件信息/重命名使用 Material AlertDialog，其他部分使用 GlassAlertDialog。不同圆角数值本身不足以认定缺陷，需相同场景视觉核查，未强制全局替换。
- ReaderOutlinedTextField 带标签的外层留白可能承担浮动标签空间；未采纳子代理直接删除建议，避免裁切标签。尚未完成字号放大下的对照测量。
- 截图不能证明完整可访问性；忙碌语义已自动验证，真实 TalkBack 播报仍未测试。

## 构建协作

用户明确决定由“继续开发 Reader”聊天统一打包。该聊天正在相同源码仓库构建88版，本轮已停止重复启动的构建，不修改版本号，不交付旧APK。完整测试、签名压缩、源码摘要和最终APK以该聊天的本轮结果为准。原87交付状态不能充作本轮新包结果。

m1racle workers：几何只读代理返回了审查结果；编辑交互代理遭 Grok 接口 output.encrypted_content 参数错误，重试未成功，编辑调用链由主代理核查。

## 可复核证据

- output/detail-audit-20261030/dock-tests.log
- output/detail-audit-20261030/GlassDockUiTest.xml（定向结果已复制保存，避免完整测试覆盖）
- output/detail-audit-20261030/task-only.diff（仅本轮相对已有WIP的修改）
- output/detail-audit-20261030/changed-source-sha256.json
- output/detail-audit-20261030/01–05 截图与XML

未提交、未推送。本报告不覆盖并发聊天的整体发布验收。


## 88 集成结果

统一打包已完成。完整回归发现忙碌进度节点重复语义导致两项首页禁用断言失败，已将忙碌描述归属外层禁用按钮，并清空指示器重复语义；未修改断言。首页与 Dock 28/28 通过，最终同源完整 QA 1847 项、Release 1690 项均零失败，各两项既有环境跳过。上述旧截图不作为 88 新跑证据；TalkBack 和真实导入忙碌视觉验收仍 pending。24dp 加载对话框间距建议留待后续。正式包与原生失败边界见 [88 报告](PDF_RENDER_METADATA_20261030.md)。
