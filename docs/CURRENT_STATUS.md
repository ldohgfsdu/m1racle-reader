# 当前状态

当前代码和正式候选包为 **1.0.0 / 77，Room17**。用户否定76的顶栏过渡观感，77已替换为平台原生高斯尺度连续衔接。自动检查和正式模拟器流程通过；手机ADB连接已断开，最近核对的手机包是76，77手机安装、Download同步和最终观感待验。

当前证据见 [77连续过渡报告](MARKDOWN_TOPBAR_NATIVE_TRANSITION_20261014.md)。[75状态已归档](history/CURRENT_STATUS_75_before_native_topbar_20261014.md)。Dock/Tab沿用73实现，工作台维持删除，顶栏范围仍固定，按钮独立收起。

## 当前候选产物

[77正式签名APK](../.external_outbox/m1racle-reader-release-v1.0.0-20261014-183909.apk)，27,121,682 bytes；Outbox latest为同一产物。

SHA-256：`aa175ffd155856f075cc1c94896bb85ae5de8c12fb35d7527c49d6247dc46bfa`。

证书连续、16KiB对齐、minSdk31/targetSdk35，无INTERNET；profileable shell=true。构建输出、Outbox、冻结77副本、模拟器安装源和Download回读摘要一致。未声称同步到离线手机。

## 已验证范围

| 检查 | 本轮结果 | 边界 |
|---|---|---|
| 全量QA / Release JVM | 1780 / 1637，0失败/错误，各2既有环境跳过 | 571文件检查点；最终XML已归档 |
| Release lint / 构建 / 签名 | 0错误 / 170警告 / 4提示；正式签名和16KiB通过 | 第一轮内存退出已归档，独立重试成功 |
| QA原生 | 52/52：顶栏10、Markdown6、Dock35、导航1 | API35硬件GPU、隔离QA包；移动检查验证逐步窗口像素 |
| 根因负向 | 相同77测试APK拒绝75顶边闪动；保留全部既有断言 | 旧包差0.2424，新包差0.00263 |
| 正式77日夜流程 | 收起/展开及搜索位置通过；76→77保留firstInstallTime和可见书库 | 仅模拟器；初始不同安全区和位置的静态并排图未用于匹配证明 |

## 未完成项

- 77的物理手机安装、Download同步、屏幕颜色、实际触摸滚动及用户最终视觉认可待验。最近确认手机为76，且用户否定其过渡观感；76像素检查不能作为77手机验收。
- 全功能实机边界仍见[原始矩阵](STABILIZATION_20261013.md)。本轮新路径实际证据为API35，不扩大到API31–32或API36。当前源码已提交并推送至[公开GitHub仓库](https://github.com/ldohgfsdu/m1racle-reader/tree/master)，不代表正式APK发布或全积压清单完成。
