# 当前状态

当前交付为 **1.0.0 / 74，Room 17**。Markdown顶栏采用固定渐进模糊：按钮显示或收起均不改变模糊高度、强度和下缘。包含73版Dock真实折射、细色散与Tab点击动画；最终共同源码的42项原生检查及正式日夜滚动/搜索通过。物理手机颜色、手感与用户最终观感待验。

当前证据见 [74固定渐进模糊报告](MARKDOWN_PROGRESSIVE_CHROME_20261014.md)、[73光学与点击动画报告](DOCK_OPTICS_20261013.md)、[日夜参考标准](IOS_MATERIAL_STANDARD_20261013.md)和 [Code Wiki](code-wiki/README.md)。工作台维持删除。

## 当前产物

[74正式签名APK](../.external_outbox/m1racle-reader-release-v1.0.0-20261014-061125.apk)：27,121,682 bytes。Outbox latest当前指向同一文件。

SHA-256：e0591c1f0a6fa20a55304129cf1debeccfd5471d653a3a16312f5dd44226e3b7。

证书连续、16KiB对齐、minSdk31/targetSdk35、无INTERNET，保留profileable shell=true。冻结副本、Download、回读及安装源摘要一致。QA只用于隔离测试。

## 已验证范围

| 检查 | 结果 | 边界 |
|---|---|---|
| 全量QA/Release JVM | 1,780 / 1,637，0失败/错误，各2既有环境跳过 | 74冻结源码；原始XML已归档 |
| Release lint/签名 | 0 errors / 170 warnings / 4 hints，构建成功 | 正式签名、16KiB与最终源码571文件核对 |
| QA原生 | 42/42：Dock35、实际NavGraph1、顶栏5、shader1 | API35硬件GPU；软件GPU既有Dock差异及旧新包对照详见74报告 |
| 固定模糊根因回归 | 日夜收纳前后相同正文背景区域RGBA差为0 | 同一最终测试APK能拒绝此前随按钮缩短模糊的包 |
| 正式74日夜 | 上滑收起、反向展开、打开/关闭搜索通过 | 实际录像与中间帧已核对，7个正文标题坐标不变 |
| 保留数据升级与交付 | 当前模拟器73→74，firstInstallTime保持；可见8个既有文档保留 | 仅模拟器；物理手机未连接 |

外部老子夹具未挂载与符号链接环境限制为既有跳过；未新增跳过或放宽原有光学阈值。结束为night=no、density420和正式应用首页。

## 当前源码及未完成项

- 74采用已保存的571文件源码归档，正式DEX含最终shader与独立按钮调用签名。本次交付包含固定渐进模糊以及既有Dock/Tab修改；未回滚其他授权工作。
- D盘余量阻止模拟器冷启动后，AVD保留数据复制到C盘并逐镜像校验，原AVD保留；当前指针、硬件渲染配置及备份见74报告，未修改APK行为。
- iOS参考对照、自动验证和模拟器行为分别记录。物理手机实际屏幕颜色、触摸手感和最终视觉验收仍pending。
- 其他功能的实机流程边界继续见 [原始矩阵](STABILIZATION_20261013.md)，本轮通过不扩大为全功能验收。未commit/push；此前73状态已 [归档](history/CURRENT_STATUS_73_before_progressive_chrome_20261014.md)。
