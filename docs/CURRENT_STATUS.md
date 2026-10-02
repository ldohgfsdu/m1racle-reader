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
| SHA-256 | `8c8569057b4fbfc6a76b4db34a8ea8261d5ae9020e0ba55cece7a545b75d8691` |
| 签名证书 SHA-256 | `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`（CN=M1racle Reader） |
| 构建时间 | 2026-09-27 22:33 |
| 设备副本 | `/storage/emulated/0/Download/m1racle-reader-release-latest.apk`（同名覆盖） |

**该产物早于本次审查修复**（`d666d80`、`8f2a9ea` 及 EPUB 排版改动），因此不是当前源码的构建。
当前源码尚未产出新的签名 Release：正式签名环境四项 `M1RACLE_RELEASE_*` 尚未注入，
`validatePublicReleaseConfiguration` 会拒绝出包，不得退回 Debug 或临时签名。

## 设备验收

`adb devices` 为空，本轮及 2026-09-25 之后的所有改动 **device_verified = NOT_RUN**：
液态玻璃全量替换、PDF 菜单跟手/回弹、Hub 底栏软边缘与按压、EPUB 排版改造均未上过真机。
R32.4 记录的 native crash 闭环只覆盖 2026-08-27 的 APK，不覆盖当前 Backdrop 用法。
安装后需人工核对：EPUB 翻页与目录落点、图片比例、进度恢复、PDF/Markdown 打开与标注、
玻璃采样稳定性与深浅色观感。

## 仓库与交付风险

- **远端未备份**：17 个分支均无 upstream，`origin/master` 停在 2026-08-17（`bd0b344`），
  比本线落后 100+ commits。当前所有成果只存在这台设备上。用户已决定本轮只在本地提交并打 tag，不推送。
- **签名私钥位置**：正式 keystore 存放在设备内部存储 `M1racle Reader/keystore`，
  未写入仓库、未提交备份。`RELEASE_CHECKLIST.md` 中"生产密钥已在仓库外安全备份"一项仍未勾选。
- **无 CI**：仓库没有 `.github/`，全量测试只能在这台设备上手动触发。

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
