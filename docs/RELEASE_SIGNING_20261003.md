# Release 签名构建与侧载交付 — 2026-10-03

用户明确授权使用所附签名包，为当前修改构建并交付正式 Release APK。本轮为正式签名侧载验收包，没有公开发布或 tag；真机与人工验收未完成。

## 身份与改动

unmanaged；runId / projectId / machineId unknown。Agent 为 Codex，模型 GPT-6，版本 unknown，平台 Windows。项目契约与中央工程公约在本机不可用。

Git 分支 `codex/fix-project-audit`，HEAD `a314247d9cc27f7b3adc1edb3074b65eee3afbfe`；保留全部既有工作区改动，不提交、不推送。当前 APK 包含此前玻璃分段、底栏加号、PDF 胶囊材质、设置材质对比及 Markdown 顶栏 / 滚动边缘修改。本轮代码仅将 `app/version.properties` 的 versionCode 从 23 升到 24，并同步两个明确契约：正式版本仍需精确匹配；导入入口需位于独立底栏加号，忙时禁用，不带浮动缩放。未删除测试或放宽约束。

## 自动化验证

JDK 17；命令 `gradlew.bat :app:testQaUnitTest :app:lintRelease :app:assembleRelease --console=plain`，退出码 **0**。构建源 dirty fingerprint `307b29e1dfdcc88f75a6066af6cb29ba5fecd66768a0573995178af028226e83`；时间 2026-10-03T06:04:01.409615+00:00 至 2026-10-03T06:04:46.908858+00:00。首次完整测试暴露旧 versionCode / 首页顶栏导入断言；修正后完整重跑通过。一次构建日志包装进程因 Windows 输出编码失败而中断，已单独记录，没有将该次作为成功证据。

- full：**1635 项 / 0 失败 / 0 错误 / 2 跳过**，实际通过 1633 项。跳过为《老子》外部教材 fixture 缺失、Windows 无符号链接能力，均为原有环境限制。
- fast：此前 81 项 Markdown / 材质定向回归均包含在本轮完整套件内，包括灰色过渡根因、重叠淡入淡出、窄屏 200% 字号和控件点击区域。
- static：Release Lint **0 errors / 158 warnings / 4 hints**；警告未被静默或忽略。
- build：正式签名 Release，R8 minify 与资源 shrink 开启；未构建或交付 Debug，没有临时签名或二次重签。
- binary：`apksigner verify --verbose --print-certs`、`zipalign -c -P 16 -v 4`、`aapt dump badging` 均退出码 0。包名 `com.m1racle.reader`，1.0.0 / 24，minSdk 31 / targetSdk 35，无 INTERNET，无 debuggable 标记。

签名证书 SHA-256 `2d9daba0f92c1d19d290dd58657391a71c16c3008e8a2b90065ba7e8602bf9d1`，与原 2026-10-02 正式备份 APK 一致。凭据仅在构建子进程环境中注入；密钥临时副本受当前用户 ACL 保护并在构建退出后移除，口令没有作为命令参数或源码写入。原用户附件保留。此前附件检查误输出口令已向用户告知；本轮签名日志没有记录私密内容。

## 产物与交付

版本 **1.0.0 / 24**；大小 **6,993,653 bytes**（6.67 MiB）。

SHA-256：`eb7739d10413e59df6664f07d7e0e730c76b542b6f047d4eea22aae08220db1e`。

来源 `:app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`。主机交付位置 `.external_outbox/m1racle-reader-release-latest.apk`，同目录 `.sha256`；本次 Outbox 与本次构建输出的摘要相等，没有复制旧 Release 冒充新包。

复制至 Android 15 模拟器 `emulator-5554`：`/storage/emulated/0/Download/m1racle-reader-release-latest.apk`；交付时间 2026-10-03T06:12:30.819003+00:00。主机、Download 文件和实际安装 base.apk 三者 SHA-256 一致。当前无在线真机；模拟器交付不代表手机已收到。

## 设备验证与剩余事项

emulator：`adb install -r` 退出码 0，冷启动 MainActivity 成功，当前 Reader crash buffer 无崩溃；导入三种生成 / 已有 QA fixture，检查 Markdown 顶部透明、重叠时柔和边缘、上下滚动、目录跳转、搜索打开与返回，PDF 页面显示和阅读方式 / 阅读界面竖排菜单，EPUB 正文显示。重新冷启动后最近阅读记录保留。截图与 UI XML 绑定本轮已安装 Release 摘要。

physical device：**NOT_RUN**。未宣称真机通过，也未验证原 23 → 24 的真机升级保留数据、长段 EPUB 性能、完整翻页手感或人工验收。当前公开发布清单仍有这些未勾选项。

## 证据

仓库外目录 `C:/Users/21234/.devhub/runs/unmanaged-reader-release-20261003`：`release-build.log`、`build-result.json`、`verification-summary.json`、`test-results-full/`、`lint-results-release.xml`、`apk-signature.txt`、`apk-alignment.txt`、`apk-manifest.txt`、`artifact-verification.json`、`device-install.json`、`release-*.xml/png` 和 `handoff.json`。旧失败 / 中断日志与此前状态原文保留。

最终文档整理未改变已验证代码；`verified-code-snapshot.json` 与 `handoff.json` 保存源码指纹用于核对。
