# 92 正式版手机覆盖安装

2026-10-31，手机 db04f865 / 25098PN5AC 当前在线。正式包从 90 保留数据覆盖安装到 92，adb install -r 返回 Success；未卸载、未清库。firstInstallTime 在升级前后均为 2026-08-31 16:14:37。

手机 Download 路径：/storage/emulated/0/Download/m1racle-reader-release-latest.apk。已安装 base.apk、Download、电脑交付的 SHA-256 一致：5FCFC2757DFCA552FB5B51EEA1650C5D13B62A1BBC86236E95E911A2F866AE24。

升级前后首页文库保留，《诊断学（第10版）》继续阅读仍显示 33 / 626。随后手机截图实际显示《i人社会化攻略2.0.pdf》44 / 97 正文和可见邻页，正式包正常运行，当前进程崩溃缓冲为空。两次正文 UI 树采集无法取得空闲状态，因此使用实际截图确认显示；不把此次有限启动检查说成完整手势回归或原录屏问题的三次真机复验。

状态：手机安装、Download 交付、已安装摘要和有限启动显示检查 verified。手机完整手势、连续重开画面、持续滚动手感、TalkBack 和用户人工验收仍 pending。39/39 仍是模拟器原生整轮结果，不写成手机结果。

证据目录：D:/03_M1racle Reader/output/phone92-20261031。verification.json、package-before.txt、package-after.txt、ui-before.xml、ui-after.xml、open1.png、phone-open.mp4、installed-sha256.txt、download-sha256.txt、current-process-crash.txt。
