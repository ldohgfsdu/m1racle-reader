# 当前状态

当前交付为 **1.0.0 / 73，Room 17**。本包修正Dock真实折射、边缘连续细色散及点击Tab时的透镜/页面动画；原生36项和正式日夜模拟器流程通过。物理手机颜色、手感与用户最终观感待验。

当前证据见 [73光学与点击动画报告](DOCK_OPTICS_20261013.md)、[日夜参考标准](IOS_MATERIAL_STANDARD_20261013.md)和 [Code Wiki](code-wiki/README.md)。工作台维持删除。

## 当前产物

[73正式签名APK](../.external_outbox/m1racle-reader-release-v1.0.0-20261014-002640.apk)：27,121,682 bytes。Outbox latest当前指向同一文件。

SHA-256：111941442303ae681e2405e646ac22b0f820f8522817189ae7c6943442d204e9。

证书连续、16KiB对齐、minSdk31/targetSdk35、无INTERNET，保留profileable shell=true。冻结副本、Download、回读及安装源摘要一致。QA只用于隔离测试。

## 已验证范围

| 检查 | 结果 | 边界 |
|---|---|---|
| 全量QA/Release JVM | 1,780 / 1,637，0失败/错误，各2既有环境跳过 | 73冻结源码；原始XML已归档 |
| Release lint/签名 | 0 errors / 170 warnings / 4 hints，构建成功 | 共享工作区lint不证明之后并行顶栏源码已验收 |
| QA原生Dock/实际NavGraph | 35/35 + 1/1 | API35；旧QA72根因3项均失败，新版通过 |
| 正式73日夜 | 两端/中央、双向拖动、释放、全部Tab点击/返回、导入取消通过 | 实际录屏与中间帧已核对 |
| 保留数据升级与交付 | 当前模拟器实际71→73，firstInstallTime保持 | 仅模拟器；物理手机未连接 |

外部老子夹具未挂载与符号链接环境限制为既有跳过；未新增跳过或放宽原有光学阈值。结束为night=no、density420和正式应用首页，无本次主包崩溃。

## 当前源码及未完成项

- 73采用已保存的536文件源码归档及原始编译类；新shader字符串在签名DEX中逐字节核对。当前工作区另有用户授权的 [Markdown固定渐进模糊](MARKDOWN_PROGRESSIVE_CHROME_20261014.md)源码修改，**尚未进入73**，不能以73的通过为当前全部工作区背书。
- 顶栏任务在用户协调授权下继续验证“按钮显示/收起不改变模糊”的新语义，稳定后接续74的共同源码检查、签名构建及交付；本次未回滚其修改。
- iOS参考对照、自动验证和模拟器行为分别记录。物理手机实际屏幕颜色、触摸手感和最终视觉验收仍pending。
- 其他功能的实机流程边界继续见 [原始矩阵](STABILIZATION_20261013.md)，本次Dock通过不扩大为全功能验收。未commit/push；此前状态已 [归档](history/CURRENT_STATUS_72_before_optics_20261013.md)。
