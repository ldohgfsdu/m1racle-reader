# 当前状态

当前交付为 **1.0.0 / 75，Room17**。修正Markdown顶栏的清晰正文重影与大半径细条纹；保留固定模糊和独立按钮收纳。正式75日夜滚动/搜索、49项原生回归及全量JVM通过，物理手机与用户最终观感待验。

当前证据见 [75顶栏修正报告](MARKDOWN_TOPBAR_REPAIR_20261014.md)。74为[上一轮记录](MARKDOWN_PROGRESSIVE_CHROME_20261014.md)，其自动检查不代表用户视觉认可；旧状态已[归档](history/CURRENT_STATUS_74_before_topbar_repair_20261014.md)。Dock/Tab沿用73实现，工作台维持删除。

## 当前产物

[75正式签名APK](../.external_outbox/m1racle-reader-release-v1.0.0-20261014-131608.apk)，27,121,682 bytes；Outbox latest为同一产物。

SHA-256：`2dec60bfcefb58d1e6b434652daf94e9f04d250f6a092e6b07e113123d4f0cf1`。

证书连续、16KiB对齐、minSdk31/targetSdk35，无INTERNET；保留profileable shell=true。构建输出、Outbox、冻结副本、模拟器安装源及Download回读摘要一致；物理手机未连接。

## 已验证范围

| 检查 | 本轮结果 | 边界 |
|---|---|---|
| 全量QA / Release JVM | 1780 / 1637，0失败/错误，各2既有环境跳过 | 最终571文件检查点；XML已归档 |
| Release lint/构建/签名 | 0 errors / 170 warnings / 4 hints；正式签名和16KiB通过 | 本次75产物 |
| QA原生 | 49/49：顶栏7、Markdown6、Dock35、导航1 | API35硬件GPU、隔离QA包 |
| 根因负向与固定背景 | 相同测试APK能拒绝74的重影与条纹；按钮收起前后日夜背景RGBA差0 | 既有断言未削弱 |
| 正式75日夜流程 | 收起/展开及搜索位置通过；74→75保留firstInstallTime与可见书库 | 仅模拟器 |

## 未完成项

- 物理手机实际屏幕颜色、触摸手感、厂商系统长截图与用户最终视觉认可待验。本轮新光学路径实际设备证据为API35，不扩大到API31–32。
- 全功能实机边界仍见[原始矩阵](STABILIZATION_20261013.md)；本轮不把旧验收或自动检查当作公开发布完成。未commit/push。
