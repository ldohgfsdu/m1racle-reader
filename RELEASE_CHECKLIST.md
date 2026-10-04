# M1racle Reader 发布检查清单

本清单用于下一份 Release。旧 R32/R33 勾选项是历史证据，已归档到 [历史清单](docs/history/RELEASE_CHECKLIST_R32_R33_snapshot.md)，不能继承为本轮通过。当前状态见 [CURRENT_STATUS](docs/CURRENT_STATUS.md)。

## Automated verification

- [x] 本轮完整单测与相关回归通过，记录 HEAD、dirty fingerprint、命令和退出码。
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

本轮正式侧载验收包 1.0.0 / 31 已按用户授权签名、覆盖安装并复制至 Download。完整单测 1654 项 / 0 失败 / 0 错误 / 2 既有环境跳过，EPUB 42 项全通过；Release Lint 无错误。两处同文件目录的标题页首、双向跨节边界和阅读页恢复经 API 36 真机确认。其他书、内联字符锚点、图片、性能和 Markdown / PDF 本轮未全面实机复查；人工手感及公开发布 / tag 未完成。

此前 EPUB 手机验收包的 Outbox、构建输出、Download 和安装包 SHA-256 `09617de1f9df60e2581bdd3b926c9d44b90549639ae70028f2528ad0c84c38c4` 一致，详情见 [章节修复报告](docs/EPUB_CHAPTERS_20261003.md)。本机环境修复后的正式重建包为 `42817bacdbb116efce2296c73af13fc8f8c444de1b5b8413bcde3b938c48d6c0`，源码与版本不变，完整单测和 Release Lint 复验通过；新包已安装到 Android 15 模拟器并通过基础阅读冒烟，模拟器 Download 与实际 base.apk 摘要一致，真实手机复制与安装待完成。当前产物以 [CURRENT_STATUS](docs/CURRENT_STATUS.md) 为准。既有签名附件保留，未写入新的源码备份。
