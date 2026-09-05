# 当前项目状态

更新：2026-09-05。此文件是当前工程与发布状态的唯一入口；阶段报告只描述当时证据。

## 当前任务

PDF 顶部控件背景随文字/图案移动、缩放而闪烁。修复在 `fix/pdf-chrome-background-flicker`，详见 [修复记录](PDF_CHROME_BACKGROUND_FLICKER_FIX.md)。首版被用户反馈‘太不透’，视觉验收未通过。已回调文档采样对比度 0.20 → 0.65，保留采样底色修复；17 项定向测试通过，回调版构建与实机验收待完成。下方 APK 身份暂为首版。

## 源码与候选

- 当前开发目录：`/root/code/MarkLeaf`；修复分支 `fix/pdf-chrome-background-flicker`。
- 当前修复包源码：`e75b7c432dad081223bc56bcd45322f13cbf3a08`；基于 `691cb65` 候选继续修复，未改 canonical 分支或 tag。
- 当前改动仅 PDF 玻璃背景合成及回归测试；依赖和版本沿用基线。
- 版本 `1.0.0 / 23`，Room v13；Markdown 批注、批注样式、阅读设置 token 与设计规范清理已合入。
- 候选 APK：6,686,026 bytes，SHA-256 `8a12f7df3c3c1590c8bec98a02f259cc8479c0dbcfb7f97a20cc3179c9b7e267`。
- production signer SHA-256：`2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`。
- 交付入口：本目录 `.external_outbox/m1racle-reader-release-latest.apk` 与 `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`。

## 发布事实

2026-09-05 通过 GitHub API 与 `git ls-remote` 只读核准：

- `ldohgfsdu/m1racle-reader` 只有 `v1.0.0` Draft，`published_at=null`，尚未发布。
- 本地与远端 `v1.0.0` 指向 `a43633a`；Draft APK hash 为 `e8c0431ab2b38c522a6e12bd3e39349acf78269dd7189bd293b74514f7a64777`，不含最新四个产品提交。
- 本地及该远端均无 `v1.0.1` tag。旧 `POST_RELEASE_BASELINE_1_0_1.md` 的已发布描述与当前事实不符，已随旧工作区归档，不作为现行基线。
- `origin` 仍指向历史 `ldohgfsdu/markleaf`；本次未改远端、tag、Draft 或发布任何内容。

## 验证与剩余事项

- implemented：既有产品改动在 `691cb65`；PDF 背景稳定化修复在 `e75b7c4`。
- automated_verified：本轮完整 testQaUnitTest 为 1396 / 0 / 0 / 0；Release 构建、Manifest、production signer、v2 签名与 outbox/Download SHA-256 核验通过。
- device_verified：最新候选未验证；不把旧版本的设备结果归给它。
- pending：最新候选真机验收；构建报告中的 lintVital ASM 异常仍需独立诊断（构建成功不等于完整 lint 通过）；发布时需处理当前 tag/Draft 与候选源码不一致的问题。
- 未建立新的功能目标，未自动启动路线图下一阶段。

证据：[当前修复及验证](PDF_CHROME_BACKGROUND_FLICKER_FIX.md)、[修复前候选构建报告](V1_0_0_FINAL_CANDIDATE_BUILD_REPORT.md)、[批注集成](P0_1_FINAL_INTEGRATION_REPORT.md)、[设计规范集成](DESIGN_SYSTEM_DEBT_CLOSURE_INTEGRATION_REPORT.md)、[工作区整理](WORKSPACE_CLEANUP_20260905.md)。
