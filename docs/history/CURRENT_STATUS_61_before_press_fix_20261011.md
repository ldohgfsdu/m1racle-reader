# 当前状态

当前候选包 **1.0.0 / 61，Room v17**（2026-10-11）。已保留数据覆盖安装到物理手机db04f865/API36，正式包日夜按住/往返拖动、末态首页和导入取消通过。用户已解锁，60版锁屏待验状态由本轮真机复验取代。

本轮改善底栏通透感：Dock及内部采样栏6dp背景模糊；浅色中性覆盖24%，深色纯黑40%；静止选中透镜中性覆盖6%，按压外层仍零填色。保留60版60dp栏高、48dp透镜、6dp留白与52×38dp加号，以及59版内部底栏折射。见[61版报告](DOCK_TRANSPARENCY_20261011.md)。

## 当前产物

[正式签名APK](../.external_outbox/m1racle-reader-release-latest.apk)，**27,131,998 bytes**。
SHA256：`a582639ac6ad68eb2eda184713a4c556b088337b76f7409fa42d34fa44c27944`。
快照：`m1racle-reader-release-v1.0.0-20261011-222305.apk`。
生产证书连续，16KiB对齐、min31/target35/无INTERNET检查通过，构建/Outbox/手机Download摘要一致。不是Debug/QA交付。

## 验证与边界

- automated_verified：定向JVM67项通过，lintRelease 0 errors / 198 warnings / 4 hints。首次2项旧材质参数断言失败，按新设计更新期望后重跑通过；未降低光学/像素/拖动断言。
- device_verified：API36真机组件11项通过（26.334s），覆盖透光、边缘折射、72帧往返拖动、按压外扩和固定命中。正式签名61包日夜静止/按住/往返拖动及导入取消通过，末态首页selected；已查看正式包静止前后及按住截图。
- 同手机同背景QA前后对照显示浅色白雾减轻、深色选中覆盖更轻。正式包首页亦保存60/61前后对照。酷安与本应用背景内容不同，不声称像素级复刻或相同场景光学强度完全一致。主观手感仍可继续按用户反馈校准。
- 手机夜间恢复auto，QA后台权限恢复ignore；未清除数据。无数据库/阅读语义变更，未全量重验积压清单，未commit/push。
- 本轮未启动模拟器；57/58版已知QA模拟器启动ANR仍为独立未决项，未宣称解决。

60版历史见[快照](history/CURRENT_STATUS_60_before_dock_transparency_20261011.md)，前轮尺寸见[60版报告](DOCK_PROPORTION_20261011.md)。
