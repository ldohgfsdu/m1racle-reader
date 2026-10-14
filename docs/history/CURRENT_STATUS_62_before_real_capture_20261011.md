# 当前状态

当前候选包 **1.0.0 / 62，Room v17**（2026-10-11）。正式签名包已保留数据覆盖安装到物理手机db04f865/API36，日夜按住、往返拖动及导入取消通过。

重新对照用户提供的酷安两段录屏和截图，修复按压整栏泛白：删除整栏白色加法叠加，局部高光改为普通透明混合；整栏按压不再增加底色覆盖。透镜取消对已处理底栏的第二次增艳，保留内部底栏采样、彩边及按压外扩。见[62版报告](DOCK_PRESS_FIX_20261011.md)。61版6dp模糊、浅色24%/深色40%底色及60版几何保留。

## 当前产物

[正式签名APK](../.external_outbox/m1racle-reader-release-latest.apk)，**27,131,998 bytes**。
SHA256：`12d99bd4b18ecc81a9fedba58fab0d6296441afe9cbd85e2e4b5c77408aed75e`。
快照：`m1racle-reader-release-v1.0.0-20261011-224419.apk`。
生产证书连续、16KiB对齐、min31/target35/无INTERNET通过；构建、Outbox、手机Download摘要一致。

## 验证与边界

- automated_verified：定向JVM50项通过；本次Release lint 0 errors / 198 warnings / 4 hints。
- device_verified：先用旧实现复现新增日夜远端底栏像素测试2项失败，修复后完整组件13项通过（29.391s）。涵盖整栏颜色、透光、内部采样、固定命中和72帧往返拖动。正式62包日夜按住/拖动、末态首页selected及导入取消通过，按压截图已查看。
- 酷安录屏已逐帧提取核对。不同页面背景下不声称像素级复刻；主观手感仍可按用户反馈继续校准。未全量重验积压功能。
- 手机夜间恢复auto，QA后台权限恢复ignore；未清除用户数据、未commit/push。无数据库及阅读语义变更。
- 本轮未启动模拟器，历史QA模拟器启动ANR未宣称解决。

61版历史见[快照](history/CURRENT_STATUS_61_before_press_fix_20261011.md)。
