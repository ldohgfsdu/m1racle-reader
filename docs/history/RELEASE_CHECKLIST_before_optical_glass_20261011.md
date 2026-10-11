# M1racle Reader 发布检查清单

本清单用于下一份 Release。旧 R32/R33 勾选项是历史证据，已归档到 [历史清单](docs/history/RELEASE_CHECKLIST_R32_R33_snapshot.md)，不能继承为本轮通过。当前状态见 [CURRENT_STATUS](docs/CURRENT_STATUS.md)。

## Automated verification

- [x] 本轮完整QA JVM1730项 / 0失败或错误 / 2既有跳过，记录HEAD、dirty fingerprint、命令与退出码。
- [x] 最终4项原生设备回归全部通过，包括原书手势、私有字体实际绘制 / 重排 / 重建 / 移除、字库生命周期与根目录Locator原文恢复。
## Signing

- [x] 正式签名由发布负责人通过四项 `M1RACLE_RELEASE_*` 环境变量安全注入；不使用 Debug、临时或未签名包。
- [x] `:app:assembleRelease` 成功；归档本轮源码、版本、APK 字节数及 SHA-256。
- [x] `apksigner verify --verbose --print-certs` 通过，并与现有正式证书一致。
- [x] Manifest 无 INTERNET；包名 `com.m1racle.reader`，minSdk 31 / targetSdk 35。
## Version

- [x] 使用 `app/version.properties` 中的明确版本；实际发布前单调升级 versionCode。
## Room migration policy

v12 is the first public database baseline. Do not fabricate v1-v5 schema snapshots. 已公开基线的升级必须经过真实的 12 → 13 → 14 migration。

- [x] Room 当前 v15（14 → 15 只加 `documents.readingAnchor`、`bookmarks.anchor` 两个可空列，见 MigrationV14ToV15Test），后续 schema 变更必须补 migration 和对应测试。
- [x] CHANGELOG 与 `docs/releases/vX.Y.Z.md` 描述本轮用户可见变更及限制。
- [ ] 真机安装、升级保留数据、Markdown/PDF/EPUB 打开与位置恢复通过。
- [ ] EPUB 超长段落翻页、TOC、字号重排和图片通过真机性能/视觉验收。
- [x] Outbox、实际交付文件摘要一致，设备端交付名为 `m1racle-reader-release-latest.apk`。
- [ ] 在仓库外安全备份正式签名材料；不把秘密、临时 QA 包写入发布目录。

Windows 使用 `gradlew.bat`，Unix 使用 `./gradlew`。统一 JDK 17、Android Platform 37.0、Build Tools 36.0.0；ARM 的 aapt2 override 只配置在本机。QA 单测可用 `:app:testQaUnitTest`，不向用户交付 QA APK。

## Pre-migration R32 APK identity and hash (historical evidence)

历史包名、临时证书和 R32 摘要只存于 [历史清单](docs/history/RELEASE_CHECKLIST_R32_R33_snapshot.md)，不作为当前发布门禁的通过证据。

## Known limitations

当前侧载验收包 **1.0.0 / 51**：EPUB恢复实际43版原生翻页与界面，只加字体。完整QA1730项 / 0失败或错误 / 2既有跳过、原生设备4项全过；正式签名包原书轻滑 / 纵滑、单页往返、宋体绘制 / 冷启动恢复和默认回退通过，9份文档与原文件字节及标注数据保留；Release Lint0 errors，签名 / 16KiB对齐 / 无INTERNET与五份摘要一致。43版表格投影为普通文本，旧SDK无法匹配的阅读位置会降级，手机手感与性能待验。详见 [CURRENT_STATUS](docs/CURRENT_STATUS.md) 与 [本轮报告](docs/EPUB_RESTORE43_FONT_20261007.md)。以下为历史记录。

46 版因翻页路径未经真机验证、已知会造成触控失效而停止交付；47 版因仍等待迟到通知而停止交付。两者的修复均包含在 48 中。

1.0.0/31 及更早的构建与验收记录已归档到 [历史清单](docs/history/RELEASE_CHECKLIST_31_snapshot.md)，不作为当前门禁。

正式包含 `<profileable android:shell="true"/>`（Macrobenchmark 保留）：仅允许 adb shell 获取 profiling 数据，无额外运行时权限；对外发布说明中需列明。
