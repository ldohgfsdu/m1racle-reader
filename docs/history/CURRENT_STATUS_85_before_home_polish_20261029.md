# 当前状态

2026-10-29：正式签名 Release **1.0.0 / 85，Room17**。当前开发和构建仓库为 `D:\03_M1racle Reader\reader-ui-release-20261025`；本机入口统一读取 `reader-env-local.ps1` 的 `$readerRepository`。原 `reader` 和 82 版工作树继续保留。

85 修复 Markdown 编辑旋转丢失选区及撤销 / 重做记录，并修正连续 PDF 随窗口尺寸变化退回上一页的问题。编辑器沿用原实例；PDF 以页面内阅读中心锚点重排。继承 83 的界面，启动图标、设置页纯 M 母标及单次 Reader 保留；APK 内两份品牌向量与已验签 83 一致。84 候选因 PDF 旋转回退被拒绝，不作为交付。详见 [本轮实现与验证](EDITOR_ROTATION_20261029.md)。

唯一交付文件 `D:\03_M1racle Reader\apk\M1racle-Reader-1.0.0-85.apk`，27603994 字节（26.33 MiB），SHA-256 `2DBF22BBBF95D530BF6E245BFDB2F3FE9010925B9D14E335D09BCAADB6A32668`。本次构建、Outbox、交付、模拟器安装包和模拟器 Download 的摘要一致。生产签名、16KiB 对齐、R8 / 资源压缩通过，无 INTERNET 权限。

同一份冻结源码的完整 QA 1836 项、Release 1683 项，均 0 failures / 0 errors，各 2 项既有环境跳过；lintRelease 0 errors / 179 warnings / 6 hints。专项原生 5/5。扩展原生 17/23，6 项失败在同一测试 APK 的旧 84 对照中同样失败，未新增失败；它们仍待独立核查，不能称扩展手势验收通过。

API35 模拟器已保留数据覆盖安装本轮正式 APK；Markdown 实际输入、横竖屏及窗口高度变化、撤销重做、保存重开和原文件保护通过；PDF 2/3→2/3→2/3；EPUB 重排保留 C2P01W01 锚点。本轮模拟器证据只覆盖说明的场景。物理手机未连接，手机安装、物理中文输入法及用户视觉 / 手感验收 pending，不沿用 82 或 83 的设备结论。当前为 READY_WITH_LIMITATIONS；未提交、未推送、未公开发布。

证据目录 `D:\03_M1racle Reader\output\editor-rotation-20261029\v85`。83 的状态保存在 [历史](history/CURRENT_STATUS_83_before_editor_rotation_20261029.md)。
