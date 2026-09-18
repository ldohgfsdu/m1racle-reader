# 主页功能修复（2026-09-11）

用户授权修复主页审查发现的问题；视觉风格和动画后续再做。本轮风险为中等，不修改数据库结构、实际删除范围或源文件处理。


后续用户反馈修正：三点菜单不再重复展示文件名，确认框改用项目统一组件，见 [主页菜单修正](HOME_MENU_CORRECTION.md)。

## implemented

- 普通条目与继续阅读卡片均有文档操作按钮和长按入口。菜单显示可滚动、可选择复制的完整文件名。
- 操作明确命名为“从文库移除”，打开确认框后才允许执行。确认文案说明清除阅读进度、书签、收藏、批注和应用内副本，无法撤销且不删除原始文件；取消不执行删除。
- 保留已有仓储事务及级联清理。删除失败恢复乐观移除的条目并显示错误，不静默吞掉失败；同一条目正在移除时忽略重复请求。
- 导入有单一忙碌状态：即时阻止重复请求，主页禁用加号并显示“正在导入”；状态支持无障碍播报，操作结束恢复可用。
- SourceTooLarge 独立展示大小限制，数值来自既有 ExternalDocumentImporter.MAX_BYTES；读取失败仍使用既有读取提示。
- 主页一次性事件由 Channel 排队，避免导入结果/失败提示在暂时没有收集者时丢失；协程取消继续传播。
- PRD 同步明确移除范围。未新增依赖，未调整磨砂、阴影、色彩或动画参数。

## automated_verified / device_verified / pending

- 最终全量 QA 单测：1481 tests / 0 failures / 0 errors / 0 skipped。
- 新增行为测试：HomeDocumentActionsUiTest（列表取消/确认、唯一继续阅读条目操作），HomeOperationsTest（导入互斥、失败后可重试、无订阅事件排队、删除失败恢复与防重复）。HomeImportEventTest 增加文件超限映射断言。
- 旧布局契约里“无操作按钮”的断言按授权的新功能替换为共用操作入口断言，并以实际 Compose 点击测试覆盖取消/确认，未跳过测试。
- lintQa 已执行并失败：既有 Compose UnrememberedStateDetector / Kotlin UAST API 的 IncompatibleClassChangeError（NoteEditDialog.kt、ReaderMoreHostUiTest.kt）；不声明 lint 通过，未禁用或放宽检查。
- 最终全量单测、生产签名 Release 和 lintVitalRelease 成功；包体 6,951,594 bytes，比此前状态基线增加 52,164 bytes。签名验证通过，证书与原生产签名一致，源码摘要与测试构建前记录一致。
- device_verified：NOT_RUN。一次 ADB 探测无在线设备；仍待实际导入耗时、SAF 返回、大小超限、移除取消/确认/失败、仅一本书、完整文件名及系统返回验收。

## 交付与证据

- 保留开始时已有未提交修改；本轮修改前快照 `.external_outbox/home-functional-fixes/*.before`。
- 日志、源码摘要和最终 APK 核验均放在 `.external_outbox/home-functional-fixes/`。
- 已核对用户指定的内部存储既有生产签名，证书摘要与前一交付一致。签名值仅通过进程环境注入，不写入仓库或日志。
- Release 已自动复制到 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`，与 outbox 摘要一致，并附 `.sha256`。
- SHA-256：`5f4713b2a77868b42ee14b09762bdf41ff5b23c54e6bfa20bb8d23183ec3715d`。
- 版本 1.0.0 / 23，未修改版本号、发布 tag 或远端。
