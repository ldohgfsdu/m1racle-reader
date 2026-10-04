# 底栏选中层与手势区域修复 — 2026-10-03

implemented / automated_verified；真机 QA 首页长列表 verified，模拟器浅深色图案及选中切换 verified。四页生产包、Release 交付及人工验收 pending。

## 根因与改动

`GlassDock` 的底座模糊原页面，而零模糊的选中透镜再次采样原页面，导致选中处清晰文字覆盖已模糊的底座。现在底座通过 `surfaceExport` 导出处理后的玻璃，选中透镜只消费这层；导出位于选中层与前景图标之前，无反馈环，也没有额外原页面合成。

四页外层导航栏 padding 将滚动视口截断在手势区域上方。系统栏本身已透明且关闭 contrast scrim；修复移除外层底部裁切，把导航栏高度加到列表 / 设置滚动内容末尾及空态避让。底栏自身仍避让手势区，末项仍能完全滚出底栏遮挡。

产品范围：GlassDock、HomeScreen、LibraryScreen、BookmarksScreen、HubSettingsScreen。未改变玻璃配方、阅读器或数据库。

## 验证

- JDK 17，material / hub / home QA 定向单测：118 项，0 failures / 0 errors / 0 skipped，退出 0。覆盖底座导出拓扑、四页末尾 inset，以及首页视口到屏幕底部、末项完整露出、拖动 / 点击 / 加号 / 大字号约束。
- `:app:assembleQa` 退出 0。仅隔离测试用途，不交付 QA。
- 首轮旧测试断言仍要求视口停在导航栏上方，失败日志保留；更新为必须到屏幕底部的实际几何断言后通过，末项避让断言保留。
- 真机 `db04f865`（25098PN5AC，1220×2656）QA 内存长列表：截图确认选中项无清晰书名重新透出，滚动内容延伸到手势条背后。使用 QA 专用内存夹具，不写 Room，不清生产数据。
- Android 15 模拟器浅深色图案及文库选中位置：底座与选中层连续模糊，手势区域保持原图案。真机深色重启截图捕获了其他前台窗口的过渡帧，**不作为深色验证证据**。
- `:app:assembleRelease` 退出 1，`validatePublicReleaseConfiguration` 拒绝：缺少四项 M1RACLE_RELEASE 签名环境变量。遵守不处理签名密钥规则，未尝试读取签名附件，未使用临时 signer 或旧包冒充本轮。
- 未运行完整套件 / 本轮 lint；四页生产真机、正式 APK 与人工验收待补。

## 交接

unmanaged；runId / projectId / machineId unknown；Codex，GPT-6，版本 unknown，Windows。HEAD `a314247d9cc27f7b3adc1edb3074b65eee3afbfe`；既有 dirty 修改保留，无提交、推送。项目契约与中央公约不可用。

外部证据：`C:/Users/21234/.devhub/runs/unmanaged-reader-dock-gesture-20261003`。context / verification 绑定 HEAD、dirty fingerprint、源码摘要、命令、退出码及时间。Release 最新指针仍为旧 1.0.0 / 25，未声称本轮交付。版本未升级；恢复时先由本机安全环境注入正式签名配置，再递增 versionCode、构建 Release、更新产物 SHA-256 与设备 Download 并验收。


后续交付：本报告的底栏与手势区更改已包含在 1.0.0 / 27 正式签名包，主机 / 真机 Download / 安装包摘要一致。签名、完整验证及本次 EPUB 真机记录见 [EPUB 修复与交付报告](EPUB_READING_REPAIR_20261003.md)；本报告原有 QA 光学场景记录保留。
