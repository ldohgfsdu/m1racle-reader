# 当前项目状态

更新：2026-09-05。此文件是当前工程与发布状态的唯一入口；阶段报告只描述当时证据。

## 源码与候选

- 当前开发目录：`/root/code/MarkLeaf`；整理分支 `maintenance/workspace-state-cleanup`。
- 产品源码基线：`release-1.0.0-canonical` 的 `691cb65dd14459e2ee35c5aba01a9076832099bd`。
- 本次只整理状态和工作区，`app/`、依赖、版本与该基线一致。
- 版本 `1.0.0 / 23`，Room v13；Markdown 批注、批注样式、阅读设置 token 与设计规范清理已合入。
- 候选 APK：6,669,642 bytes，SHA-256 `19adf629e8f380c8ca49f946dfa740b6489e425d2204ea89e192d6f7c7e5fc89`。
- production signer SHA-256：`2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`。
- 交付入口：本目录 `.external_outbox/m1racle-reader-release-latest.apk` 与 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`。

## 发布事实

2026-09-05 通过 GitHub API 与 `git ls-remote` 只读核准：

- `ldohgfsdu/m1racle-reader` 只有 `v1.0.0` Draft，`published_at=null`，尚未发布。
- 本地与远端 `v1.0.0` 指向 `a43633a`；Draft APK hash 为 `e8c0431ab2b38c522a6e12bd3e39349acf78269dd7189bd293b74514f7a64777`，不含最新四个产品提交。
- 本地及该远端均无 `v1.0.1` tag。旧 `POST_RELEASE_BASELINE_1_0_1.md` 的已发布描述与当前事实不符，已随旧工作区归档，不作为现行基线。
- `origin` 仍指向历史 `ldohgfsdu/markleaf`；本次未改远端、tag、Draft 或发布任何内容。

## 验证与剩余事项

- implemented：既有产品改动已提交并合入 `691cb65`；本次状态整理已落地。
- automated_verified：既有集成报告记录 1393 tests / 0 failures / 0 errors / 0 skipped；9 月 5 日 Release 构建、Manifest、production signer 与 v2 验签通过。本轮复核候选文件 SHA-256 和源码一致性，未重跑产品测试或构建。
- device_verified：最新候选未验证；不把旧版本的设备结果归给它。
- pending：最新候选真机验收；构建报告中的 lintVital ASM 异常仍需独立诊断（构建成功不等于完整 lint 通过）；发布时需处理当前 tag/Draft 与候选源码不一致的问题。
- 未建立新的功能目标，未自动启动路线图下一阶段。

证据：[候选构建报告](V1_0_0_FINAL_CANDIDATE_BUILD_REPORT.md)、[批注集成](P0_1_FINAL_INTEGRATION_REPORT.md)、[设计规范集成](DESIGN_SYSTEM_DEBT_CLOSURE_INTEGRATION_REPORT.md)、[工作区整理](WORKSPACE_CLEANUP_20260905.md)。
