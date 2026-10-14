# 62版按压透镜与整栏泛白修复

用户反馈61版按压透镜不对、按下后整条底栏发白，并提供酷安日夜录屏及两张截图。本轮逐帧提取两段约19秒录屏，核对静止、按住、拖动与回弹：按压透镜具有独立边缘及内部较矮底栏，远端底栏保持主题颜色。

implemented：DockInteractiveHighlight原来在整个胶囊上以Plus叠加8%白色，低能力路径则整栏25%白色，局部光也是Plus；改为仅局部10%白色普通SrcOver，低能力路径使用同范围径向渐变。Dock按压默认4%底色增量归零，保持静止底色。SelectionLens曾继承vibrancy，对已经增艳/模糊的内部栏再次增艳；现取消外透镜vibrancy，内部栏仍负责第一次材质处理。保留原内部栏、折射、色散、几何及手势。

automated_verified：新增JVM配方回归约束整栏颜色不变、内部栏处理而外透镜不重复处理。定向4类JVM50项通过，无修改旧测试阈值。本次Release lint 0 errors / 198 warnings / 4 hints。

device_verified：物理手机db04f865/API36。新增日夜像素回归先在旧QA包上失败：固定蓝色背景、按住首页、采样设置栏远离图标和透镜的位置，浅色RGB差和约0.318、深色约0.208；要求小于0.03。修复后13项完整组件测试全部通过（29.391s），旧透光、模糊、内部采样、72帧跟手、外扩和回弹检查不变。QA首次启动被MIUI后台权限ignore拦截，无测试结果；临时allow后重跑，最后恢复ignore。

正式签名62已保留数据覆盖安装；确认versionCode62。实际首页日夜按住、往返拖动、末态首页selected、导入取消通过。正式截图已查看。用户不同页面与QA彩格内容不同，不声称像素级复刻。低能力径向高光路径已实现，本轮设备实际测试为FULL_OPTICAL，未单独以低能力设备验收。

产物：27131998bytes，SHA256 `12d99bd4b18ecc81a9fedba58fab0d6296441afe9cbd85e2e4b5c77408aed75e`；生产证书连续、16KiB对齐、min31/target35/无INTERNET通过；构建/Outbox/手机Download相同。夜间模式恢复auto，无用户数据清除、无commit/push。

证据目录：`output/dock-press-fix-20261011/`。`*-sheet.jpg`与`reference-held.jpg`为用户视频逐帧参考；`red-phone-retry.txt`为旧实现两项失败，`fixed-phone-retry.txt`为13项通过；`fixed-build.txt`为50项JVM；`release-build.txt`及签名/对齐/摘要为本次正式构建；`after-confirmed-*`截图断言截图完成时手指仍按下。早先`after-*`截图时间未严格保证，不能用其较小透镜推断按压幅度。`release-smoke.json`及XML/视频为正式包证据。
