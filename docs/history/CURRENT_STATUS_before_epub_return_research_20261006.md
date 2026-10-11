# 当前状态

当前交付 **1.0.0 / 43，Room v15**。底栏直接移植 RiseDiary 组件，保留四入口与独立导入；液态玻璃工作台加入正式版设置。修复阅读器返回主页交叉淡化导致底栏采样旧阅读页的问题。EPUB 目录 / 设置面板上限收窄为 288dp / 窗口 82%，等宽分段共用轨道与单个选中块，原图标坐标固定，已选段维持展开，“关闭” / 外侧 / 返回收起。

接收另一聊天发现的大字号留白问题：按实际标签高度增加底栏高度，页面尾部避让同步增加；64dp作为基准，实际字体高度可略增。详见 [本轮报告](RISEDIARY_TOOLBAR_20261006.md)。本地 Markdown / PDF / 有限 EPUB，无 INTERNET，数据库不变。

## 当前产物

`.external_outbox/m1racle-reader-release-latest.apk`，7,141,724 bytes，SHA-256 `0ca427abb452c0568a465d0a8c8f249fea34be17fba01fc71e65c31014cd3806`；快照 `m1racle-reader-release-v1.0.0-20261006-004846.apk`。已复制 emulator-5554 Download 并 `install -r` 覆盖安装，保留书库。构建 / Outbox / installed / Download 四份摘要一致。物理手机未连接，人工观感与触感 pending。

## 验证

- 完整 QA1720：1718 通过 / 2 既有跳过 / 0 失败或错误，222 suites。Release Lint0 errors / 161 warnings / 4 hints；正式构建8m53s / exit0。
- Native 主机回归覆盖底栏点击 / 拒绝导航 / 导航同步、快速拖动 / 取消 / RTL / 按压回收、200% / 300% 系统字号留白和窄屏；EPUB 单份图标与固定坐标、收窄宽度、选中段维持打开、关闭操作通过。
- 实际生产 NavGraph 主机逐帧检查 Markdown / PDF / EPUB 返回：底栏出现时 visibleEntries 不包含退出阅读页；设置路由实际打开工作台，返回恢复底栏。
- 实际 Release43：工作台入口 / Clear / 重置 / 返回、200% 字号、浅深色与实际底栏拖动通过；EPUB 切换 / 重点已选段 / 关闭坐标保持，正文位置保持。真实 Markdown、测试与长篇 EPUB、已加载 PDF 返回主页通过，PDF 共4次打开 / 返回。
- 正式证书延续、16 KiB对齐、Manifest43 / 无 INTERNET通过。模拟器曾退出，恢复同一 AVD 未清数据；恢复后版本仍43、四份摘要复核一致。crash buffer保留模拟器 MultiDisplayService 异常，没有 reader 进程崩溃。
- 返回录屏为稀疏 VFR 帧，首帧已是主页，未覆盖退出中间帧；不作为完整逐帧或 FPS 证据。导航隔离逐帧验证来自原生主机回归，实际手机手感仍 pending。

## 工作区

`feature/epub-anchor`，HEAD a57c324 + 未提交修改。生产差异摘要 `4d55e6d24f621959adf65cfeebc66f4d3b3ff7f1a5a30322d0d7de822a241ed6`。证据 `output/risediary-toolbar/`；上轮42见 [历史](history/CURRENT_STATUS_before_risediary_toolbar_20261006.md)。未提交 / 推送 / 公开发布 / tag。
