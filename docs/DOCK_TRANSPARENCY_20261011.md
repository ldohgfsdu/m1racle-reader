# 61版底栏通透感

用户解锁手机后指出底栏比酷安不通透。本轮在物理手机db04f865/API36上先保存60版，再用相同QA文字/彩色格背景比较61版，避免以不同页面亮度推断透过率。

implemented：Dock背景模糊8→6dp，缩小亮文字散成的宽雾带；浅色中性覆盖40%→24%，增加背景颜色传递；深色底色#121212→纯黑、覆盖40%保持，不额外抬灰。静止选中透镜中性填充10%→6%。内部采样栏沿用Dock配方，按压外层透镜保持零填色。PDF控件、60版留白/加号、59版内部底栏及运动结构不变。

automated_verified：定向JVM67项全部通过。首次运行2项失败来自旧颜色/透明度契约，更新为本轮明确的新材质参数后重跑67通过；原像素/拖动断言和阈值不变，失败日志qa-build.txt保留。

device_verified：新版QA在物理API36上11项组件检查通过（26.334s），包含72帧往返拖动、透光、完整胶囊边缘折射、按压外扩、固定命中及清晰前景。

视觉证据：output/dock-transparency-20261011/text-before-after.png、light-before-after.png均为上60、下61，同手机同背景。浅色白雾减轻、背景色更明显；深色选中块更轻，文字产生的雾带变窄。coolapk-candidate.png为上酷安截图、下61手机QA；背景文字位置和字号不同，不声称同背景强度完全一致或像素级复刻。原始全屏图也保留。相同彩格背景、图标与标签之间5600个采样像素的平均RGB通道差由0.1898升至0.2390，说明该取样带背景色保留增强；这只是固定场景描述指标，不代表通用光学透过率。详见color-retention.json。

正式签名Release61已构建（222305快照），27131998bytes，SHA256 a582639ac6ad68eb2eda184713a4c556b088337b76f7409fa42d34fa44c27944。lintRelease 0errors/198warnings/4hints；生产证书连续、16KiB对齐、min31/target35/无INTERNET通过，构建/Outbox/手机Download摘要相同。

正式包保留数据覆盖安装Success，手机versionCode61确认。实际首页日夜按住/往返拖动、末态首页selected、导入取消全部通过，记录release-smoke.json及XML/视频。release-dark-before-after.png为上正式60/下正式61；release-light-held-crop.png、release-dark-held-crop.png已查看，放大透镜越过底栏并保留内层边缘与前景折射。

未清除数据，未commit/push，无数据库/阅读逻辑变更；QA后台权限恢复ignore、系统夜间恢复auto。已知较早模拟器QA启动ANR本轮不宣称解决。
