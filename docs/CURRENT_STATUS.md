# 当前项目状态

更新：2026-09-05。此文件是当前工程与发布状态的唯一入口；阶段报告只描述当时证据。

## 当前任务

参考 RiseDiary 与 Kyant LiquidBottomTabs 优化四入口底栏，分支 `feat/risediary-inspired-dock`。通透外壳、移动选中胶囊、按压透镜、速度形变、图标折射和路由权威已实现；全量 1408 项测试、签名 Release 构建与交付校验通过，实机验收待完成。见 [底栏优化记录](DOCK_REFERENCE_OPTIMIZATION.md)。以下 APK 为本轮底栏优化版。

## 源码与候选

- 当前开发目录：`/root/code/MarkLeaf`；底栏分支 `feat/risediary-inspired-dock`。
- 当前修复包源码：`185c242b171465f6da6c1e69f54aba1fd87b0631`；包含此前 PDF 透明感回调和本轮底栏优化，基于 `691cb65` 候选继续修复，未改 canonical 分支或 tag。
- 本次仅底栏外观、手势、动画宿主与 Hub 过渡；沿用已有依赖、版本、导入入口和阅读器。
- 版本 `1.0.0 / 23`，Room v13；Markdown 批注、批注样式、阅读设置 token 与设计规范清理已合入。
- 候选 APK：6,702,410 bytes，SHA-256 `f249ab9e859a2a11e6a30b87cf385edd56ad85f701e5dda9683467142b618147`。
- production signer SHA-256：`2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`。
- 交付入口：本目录 `.external_outbox/m1racle-reader-release-latest.apk` 与 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`。

## 发布事实

2026-09-05 通过 GitHub API 与 `git ls-remote` 只读核准：

- `ldohgfsdu/m1racle-reader` 只有 `v1.0.0` Draft，`published_at=null`，尚未发布。
- 本地与远端 `v1.0.0` 指向 `a43633a`；Draft APK hash 为 `e8c0431ab2b38c522a6e12bd3e39349acf78269dd7189bd293b74514f7a64777`，不含最新四个产品提交。
- 本地及该远端均无 `v1.0.1` tag。旧 `POST_RELEASE_BASELINE_1_0_1.md` 的已发布描述与当前事实不符，已随旧工作区归档，不作为现行基线。
- `origin` 仍指向历史 `ldohgfsdu/markleaf`；本次未改远端、tag、Draft 或发布任何内容。

## 验证与剩余事项

- implemented：既有产品改动在 `691cb65`；PDF 背景稳定化及用户反馈后的透明感回调在 `ff1ee2a`；底栏优化在 `4c31592`，长按整体放大补齐在 `addf17d`，官方按压透镜与速度形变在 `185c242`。
- automated_verified：全量 testQaUnitTest 为 1408 / 0 / 0 / 0；Release 构建、Manifest、production signer、v2 签名与 outbox/Download SHA-256 核验通过。
- device_verified：最新候选未验证；不把旧版本的设备结果归给它。
- pending：最新候选真机验收（按压透镜、速度形变、图标折射）；构建报告中的 lintVital ASM 异常仍需独立诊断（构建成功不等于完整 lint 通过）；发布时需处理当前 tag/Draft 与候选源码不一致的问题。
- 未建立新的功能目标，未自动启动路线图下一阶段。

证据：[底栏优化及交付](DOCK_REFERENCE_OPTIMIZATION.md)、[PDF 修复及验证](PDF_CHROME_BACKGROUND_FLICKER_FIX.md)、[修复前候选构建报告](V1_0_0_FINAL_CANDIDATE_BUILD_REPORT.md)、[批注集成](P0_1_FINAL_INTEGRATION_REPORT.md)、[设计规范集成](DESIGN_SYSTEM_DEBT_CLOSURE_INTEGRATION_REPORT.md)、[工作区整理](WORKSPACE_CLEANUP_20260905.md)。
