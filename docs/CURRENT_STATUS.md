# 当前项目状态

更新：2026-10-02。本文是**当前**工程与发布状态的唯一入口。逐日阶段证据已归档到
[history/CURRENT_STATUS_202609_STAGE_LOG.md](history/CURRENT_STATUS_202609_STAGE_LOG.md)，
本文不再追加日期流水；一次会话的事实写进该会话的报告，不写进这里。

## 当前任务

会话对象以 [.memory/active_object.yaml](../.memory/active_object.yaml) 为准（`id` / `status` / `scope`）。
本次会话：`PROJECT_AUDIT_FIXES_20260930` → 已由本次审查修复收口，见 [2026-10-02 全面审查修复](PROJECT_AUDIT_FIXES_20260930.md)。

## 版本与工具链

| 项 | 值 |
|---|---|
| 版本身份 | **1.0.0 / versionCode 23**（`app/version.properties`，显式提交，不从 Git 推导） |
| 数据库 | Room **v14**（`MIGRATION_13_14` 已注册；13 → 14 增加 `sourceUri` / `sourceSha256`） |
| SDK | minSdk 31 / targetSdk 35 / compileSdk 37 / Build Tools 36.0.0 |
| 构建 | AGP 9.1.1 + Gradle 9.5.0 + AGP 内置 Kotlin 2.3.21 + KSP 2.3.6，JDK 17 |
| 权限 | 无 `uses-permission`，无 `INTERNET` |

版本号保持 1.0.0。`release-1.0.1-canonical`（versionCode 24）是从未合入本线的孤立分支，已删除；
下次真正发布 1.0.1 时再单调递增，不在无发布计划的提交里占用 versionCode。

## 自动化验证基线

`:app:testQaUnitTest`（offline，JDK 17）：**1591 项 / 2 失败 / 0 错误 / 0 跳过**。

两个失败不是代码缺陷，是主机架构限制：
`ReadingRepositoryTransactionTest` 的两项真实 Room 事务测试被
`Robolectric ... SQLiteLibraryLoader: Architecture 'aarch64' is not supported by SQLite library` 阻塞。
该构建主机是 aarch64（Termux + proot）。要拿到这两项的绿色结果，需要在 x86_64 上执行同一份测试字节码
（历史做法见 [2026-09-23 审查修复](PROJECT_AUDIT_FIXES_20260923.md) 的 transaction-x86 记录）。
不得因此声称 ARM 全量零失败，也不得删除或跳过这两项。

## 已交付的 Release 产物

| 项 | 值 |
|---|---|
| 文件 | `.external_outbox/m1racle-reader-release-latest.apk` |
| 大小 | **6,917,909 bytes / 6.60 MiB** |
| SHA-256 | `b4b5258fb60e40a89d7ccd9a5eedf35286af6e02241230e3cd6a0ade747927b3` |
| 签名证书 SHA-256 | `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`（CN=M1racle Reader，RSA 4096） |
| 签名方案 | APK Signature Scheme **v2**，单签名者，验签通过 |
| 身份 | `com.m1racle.reader` · 1.0.0 / versionCode 23 · minSdk 31 / targetSdk 35 / compileSdk 37 |
| 构建 | `PUBLIC_RELEASE_IDENTITY versionCode=23 versionName=1.0.0`，正式签名注入，minify + shrinkResources |
| 构建时间 | 2026-10-02 11:35 |
| 源码 | `master` `f007211` 及本轮 5 个提交（`checkpoint/pre-audit-2026-10-02` … `audit-2026-10-02`） |
| 设备副本 | `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`（同名覆盖） |

完整性校验：ZIP 233 条目、`testzip()` 无坏块；`AndroidManifest.xml` / `resources.arsc` / `classes.dex` 均非空；
构建输出 / outbox / Download 三份摘要一致；manifest 无 `INTERNET`（仅 Android 自动注入的包级动态接收权限）。
产物确实来自本次源码：`classes.dex`（4,618,172 B）与 09-27 包的 `classes.dex`（4,615,256 B）摘要不同。
**注意**：R8 会混淆内部类名，因此不能用 DEX 里的符号名判断产物新旧，只能用编译输入摘要与 dex 摘要。

## 设备验收

`adb devices` 为空，本轮及 2026-09-25 之后的所有改动 **device_verified = NOT_RUN**：
液态玻璃全量替换、PDF 菜单跟手/回弹、Hub 底栏软边缘与按压、EPUB 排版改造均未上过真机。
R32.4 记录的 native crash 闭环只覆盖 2026-08-27 的 APK，不覆盖当前 Backdrop 用法。
安装后需人工核对：EPUB 翻页与目录落点、图片比例、进度恢复、PDF/Markdown 打开与标注、
玻璃采样稳定性与深浅色观感。

## 仓库与交付风险

- **远端未备份**：仓库只有一个分支 `master`，**没有任何 upstream**，`origin/master` 停在
  2026-08-17（`bd0b344`），且 `origin` 指向的 `ldohgfsdu/markleaf` 并不是当前产品身份的仓库。
  用户已决定本轮不推送。**离线备份已于 2026-10-02 落到内部存储 `Download/`**：
  - `m1racle-reader-git-bundle-1.0.0-20261002.bundle` — 85,015,143 bytes，
    SHA-256 `c2fbf2e5538e42343c5cef017bb9d9ac21ab3a0ece82db08796c588b7202fc6d`。
    162 个提交、全部 tag；已实测 `git clone` 还原，HEAD = `e175051`，工作树干净。
  - `m1racle-reader-source-1.0.0-20261002.zip` — 2,521,850 bytes，
    SHA-256 `fd873c4c8f8328518d479be95161eb5f93f2f366be669a3125455a7d36e932a1`。纯源码快照。
  - `M1racleReader-备份还原说明.txt` — 校验、还原、推远端与构建前置的步骤。
  - `m1racle-reader-signing-1.0.0-20261002.zip` — 8,905 bytes，
    SHA-256 `4123ff2ec91a4e1413c06409b20e1fb59b5108833edea4a3902f41929aaa04a7`。正式签名私钥 `.p12` + 口令 + 签名 README；已实测解包后证书指纹
    仍为 `2d9daba0…`。**含私钥明文**，不得进 Git、公开网盘或代码托管；
    建议再转存一份到电脑或密码管理器的加密附件。泄露无法无损轮换：
    换证书等于换应用身份，已有安装必须卸载重装并丢失全部阅读数据。
  - **注意**：`git bundle` 只携带 ref 本身，不携带 reflog。2026-09-05 的 5 个恢复用 stash
    原本仅靠 `refs/stash` 的 reflog 可达，第一次打 bundle 时只保住了 1 个；已为 5 个各打
    `archive/stash-*` tag 并重打，复验 5 个提交与 5 个历史分支 tip 全部可从 bundle 取回。
- **历史分支已归档**：删除前的 8 个分支各有一个未合入提交，因此在删除分支前先打了
  `archive/*` annotated tag 保底（版本身份 1.0.1 候选、R41 RC、R38/R40/r33 文档线、R58/R60 的
  Room v13 落点、旧 master）。`release-1.0.1-canonical`（versionCode 24）按用户决定作废，
  其提交由 `archive/version-1.0.1-candidate-identity-2026-10-02` 保底。10 个 worktree 缩到 1 个。
  另有 5 个 `workspace-cleanup-20260905` 的 stash 作为历史恢复网保留，未清理。
- **签名私钥备份状态（2026-10-02 更新）**：原 keystore 位于设备内部存储
  `M1racle Reader/keystore/`，未写入仓库（`.gitignore` 覆盖 `*.p12` 等）。
  现已离线备份到内部存储 `Download/m1racle-reader-signing-1.0.0-20261002.zip`，
  但**仍只存在这一台设备上**，且为明文。`RELEASE_CHECKLIST.md` 中
  「生产密钥已在仓库外安全备份」一项仍不能勾选——真正的备份必须在另一台设备上。
- **无 CI**：仓库没有 `.github/`，全量测试只能在这台设备上手动触发；aarch64 上的 2 项 Room
  事务测试因此长期无法在此环境转绿。

## 未决项

1. 注入正式签名环境并产出与当前源码一致的签名 Release，做完 APK 完整性、签名、无 INTERNET 三项校验。
2. 真机安装验收上表列出的全部未验证项。
3. 私有远端备份（用户已授权推送时执行）。
4. `PdfViewerScreen.kt`（3502 行）与 `PdfViewerViewModel.kt`（2661 行）仍是超大文件，
   拆分需要独立任务，不在审查修复范围内。
5. `GlassMaterial.kt` 同时存在 `GlassRecipe` 与 `LiquidGlassStyle` 两套配方表示；
   `ProgressiveChrome` 角色注释自述 "Legacy 16dp + 0.62 plate; optical rewrite deferred"，
   属于「先这样以后再换」，应择期统一。

## 相关文档

- 文档路由：[PROJECT_INDEX.md](PROJECT_INDEX.md)
- 产品范围：[PRD.md](PRD.md)
- 发布前门禁：[../RELEASE_CHECKLIST.md](../RELEASE_CHECKLIST.md)
- 构建与交付：[APK_DELIVERY.md](APK_DELIVERY.md) · [DEVICE_INSTALL_AND_ADB_TEST.md](DEVICE_INSTALL_AND_ADB_TEST.md)
- PDF 阅读器技术交接：[PDF_VIEWER_HANDOFF.md](PDF_VIEWER_HANDOFF.md)
- EPUB 范围授权：[EPUB_SUPPORT_ARCHITECTURE_DESIGN.md](EPUB_SUPPORT_ARCHITECTURE_DESIGN.md)
